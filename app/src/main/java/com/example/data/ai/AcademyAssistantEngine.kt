package com.example.data.ai

import android.util.Log
import com.example.data.local.*
import com.example.util.FormatUtils
import com.example.util.WhatsAppMessageType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

sealed interface AssistantAction {
    data class CollectFeeProposal(
        val studentId: String,
        val studentName: String,
        val className: String,
        val ledgerType: String,
        val targetChargeId: String?,
        val chargeTitle: String,
        val amount: Long,
        val maxDue: Long
    ) : AssistantAction

    data class MarkAttendanceProposal(
        val studentId: String,
        val studentName: String,
        val classId: String,
        val className: String,
        val dateKey: String,
        val status: String
    ) : AssistantAction

    data class SaveExamMarksProposal(
        val studentId: String?,
        val studentName: String,
        val examId: String?,
        val examName: String,
        val subjectId: String?,
        val subjectName: String,
        val obtainedMarks: Double,
        val totalMarks: Int,
        val feedback: String
    ) : AssistantAction

    data class SendWhatsAppProposal(
        val studentId: String,
        val studentName: String,
        val messageType: WhatsAppMessageType,
        val reasonSummary: String
    ) : AssistantAction
}

data class AssistantTurnResponse(
    val replyText: String,
    val action: AssistantAction? = null,
    val usedCloudAi: Boolean = false
)

data class AcademyStudentSnapshot(
    val student: Student,
    val className: String,
    val tuitionPending: Long,
    val paperFeePending: Long,
    val otherFeePending: Long,
    val openCharges: List<FeeCharge>
)

data class AcademyContextSnapshot(
    val academyName: String,
    val isAdmin: Boolean,
    val todayDateKey: String,
    val classes: List<AcademyClass>,
    val subjects: List<Subject>,
    val exams: List<Exam>,
    val students: List<AcademyStudentSnapshot>,
    val todayAbsentStudentIds: Set<String>,
    val todayPresentCount: Int,
    val todayAbsentCount: Int,
    val todayLeaveCount: Int
)

/**
 * Interactive Gemini-style Academy Assistant engine.
 * - Uses ONLY the user's own Gemini API key when online and configured (never developer quota).
 * - Falls back cleanly to deterministic Roman-Urdu / English local intent parsing when offline or without key.
 */
class AcademyAssistantEngine {

    suspend fun processPrompt(
        prompt: String,
        imageBase64Jpeg: String?,
        snapshot: AcademyContextSnapshot,
        userApiKey: String,
        isOnline: Boolean
    ): AssistantTurnResponse {
        val cleanKey = userApiKey.trim()
        val cleanPrompt = prompt.trim()

        if (cleanKey.isNotEmpty() && isOnline) {
            return try {
                withTimeout(50_000L) {
                    callGeminiAssistant(
                        prompt = cleanPrompt.ifBlank { "Check this paper carefully and calculate marks with feedback." },
                        imageBase64Jpeg = imageBase64Jpeg,
                        snapshot = snapshot,
                        apiKey = cleanKey
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: TimeoutCancellationException) {
                Log.w(TAG, "Gemini assistant timed out; falling back to local parser")
                val local = parseLocalIntent(cleanPrompt, imageBase64Jpeg != null, snapshot)
                local.copy(
                    replyText = "Cloud AI timed out, using local assistant:\n\n${local.replyText}",
                    usedCloudAi = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Gemini assistant error", e)
                val local = parseLocalIntent(cleanPrompt, imageBase64Jpeg != null, snapshot)
                local.copy(
                    replyText = "Gemini API issue (${e.localizedMessage ?: "error"}). Local fallback:\n\n${local.replyText}",
                    usedCloudAi = false
                )
            }
        }

        return parseLocalIntent(
            prompt = cleanPrompt,
            hasImage = imageBase64Jpeg != null,
            snapshot = snapshot,
            offlineReason = when {
                cleanKey.isEmpty() -> "No personal Gemini API key saved yet"
                !isOnline -> "Device is offline"
                else -> null
            }
        )
    }

    private suspend fun callGeminiAssistant(
        prompt: String,
        imageBase64Jpeg: String?,
        snapshot: AcademyContextSnapshot,
        apiKey: String
    ): AssistantTurnResponse = withContext(Dispatchers.IO) {
        val modelOrder = listOf("gemini-3.8-flash", "gemini-2.5-flash")
        var lastError: Exception? = null
        for ((idx, modelId) in modelOrder.withIndex()) {
            try {
                return@withContext callGeminiModelOnce(
                    modelId = modelId,
                    prompt = prompt,
                    imageBase64Jpeg = imageBase64Jpeg,
                    snapshot = snapshot,
                    apiKey = apiKey
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                val msg = e.message.orEmpty().lowercase()
                val shouldTryFallback = idx < modelOrder.lastIndex &&
                    (msg.contains("no longer available") ||
                        msg.contains("not found") ||
                        msg.contains("not supported") ||
                        msg.contains("http 404"))
                if (!shouldTryFallback) {
                    throw e
                }
                Log.w(TAG, "Model $modelId unavailable, trying fallback model: ${e.message}")
            }
        }
        throw lastError ?: IllegalStateException("Gemini request failed")
    }

    private fun callGeminiModelOnce(
        modelId: String,
        prompt: String,
        imageBase64Jpeg: String?,
        snapshot: AcademyContextSnapshot,
        apiKey: String
    ): AssistantTurnResponse {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 40_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        try {
            val contextText = buildCompactContextPrompt(snapshot)
            val fullInstruction = buildString {
                appendLine("You are the smart AI Assistant inside '${snapshot.academyName}' Android app.")
                appendLine("Respond naturally in the same language/style as the user (Roman Urdu, Urdu, or English). Keep responses helpful, concise, and direct.")
                appendLine("You can understand commands like recording fees ('uski fee aa gayi', 'Ali ki 3000 jama karo'), checking uploaded test paper photos ('ye paper check kar'), marking attendance ('Ali aaj absent hai'), summarizing pending dues/attendance, or preparing a single parent WhatsApp alert.")
                appendLine("IMPORTANT: Only use student/class/exam/subject data from the LIVE ACADEMY DATA below. Never invent fake students or amounts.")
                if (!snapshot.isAdmin) {
                    appendLine("NOTE: Current user role is TEACHER (attendance & view-only). Do NOT propose COLLECT_FEE actions; explain that only Admin collects fees.")
                }
                appendLine()
                appendLine("Return ONLY a valid JSON object with these fields:")
                appendLine("""
                    {
                      "reply": "Clear response to show the user in chat",
                      "actionType": "NONE | COLLECT_FEE | MARK_ATTENDANCE | SAVE_EXAM_MARKS | SEND_WHATSAPP",
                      "studentId": "exact student id or empty string",
                      "studentName": "matched student name if known",
                      "ledgerType": "TUITION | PAPER_FEE | OTHER",
                      "chargeId": "specific charge id if known or empty string",
                      "amount": 0,
                      "attendanceStatus": "PRESENT | ABSENT | LEAVE",
                      "dateKey": "${snapshot.todayDateKey}",
                      "examId": "exact exam id or empty string",
                      "examName": "exam name if known",
                      "subjectId": "exact subject id or empty string",
                      "subjectName": "subject name if known",
                      "obtainedMarks": 0,
                      "totalMarks": 0,
                      "whatsappType": "FEE_REMINDER | ATTENDANCE_ALERT | EXAM_RESULT",
                      "feedback": "short grading notes if checking a paper"
                    }
                """.trimIndent())
                appendLine()
                appendLine("=== LIVE ACADEMY DATA ===")
                appendLine(contextText)
                appendLine()
                appendLine("=== USER MESSAGE ===")
                appendLine(prompt)
            }

            val partsArray = JSONArray().apply {
                put(JSONObject().put("text", fullInstruction))
                if (!imageBase64Jpeg.isNullOrBlank()) {
                    put(
                        JSONObject().put(
                            "inlineData",
                            JSONObject()
                                .put("mimeType", "image/jpeg")
                                .put("data", imageBase64Jpeg)
                        )
                    )
                }
            }

            val payload = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", partsArray)))
                put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let { s ->
                BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()

            if (code !in 200..299) {
                val errMsg = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull()
                throw IllegalStateException(errMsg ?: "HTTP $code")
            }

            val root = JSONObject(body)
            val candidates = root.optJSONArray("candidates")
                ?: throw IllegalStateException("Empty Gemini response")
            if (candidates.length() == 0) {
                throw IllegalStateException("No candidate returned")
            }
            val respParts = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts")
                ?: throw IllegalStateException("Missing response parts")

            val rawText = buildString {
                for (i in 0 until respParts.length()) {
                    append(respParts.getJSONObject(i).optString("text", ""))
                }
            }.trim()

            return parseGeminiJsonResponse(rawText, snapshot)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseGeminiJsonResponse(
        rawText: String,
        snapshot: AcademyContextSnapshot
    ): AssistantTurnResponse {
        val cleaned = rawText
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val json = runCatching { JSONObject(cleaned) }.getOrNull()
            ?: return AssistantTurnResponse(replyText = rawText, action = null, usedCloudAi = true)

        val reply = json.optString("reply").ifBlank { rawText }
        val actionType = json.optString("actionType", "NONE").uppercase()

        val matchedStudent = resolveStudent(
            studentId = json.optString("studentId"),
            studentName = json.optString("studentName"),
            snapshot = snapshot
        )

        val action: AssistantAction? = when (actionType) {
            "COLLECT_FEE" -> {
                if (!snapshot.isAdmin || matchedStudent == null) null
                else buildFeeProposal(
                    st = matchedStudent,
                    preferredLedger = json.optString("ledgerType", FeeLedgerType.TUITION),
                    preferredChargeId = json.optString("chargeId").takeIf { it.isNotBlank() },
                    requestedAmount = json.optLong("amount", 0L)
                )
            }
            "MARK_ATTENDANCE" -> {
                if (matchedStudent == null) null
                else {
                    val status = when (json.optString("attendanceStatus").uppercase()) {
                        AttendanceStatus.ABSENT -> AttendanceStatus.ABSENT
                        AttendanceStatus.LEAVE -> AttendanceStatus.LEAVE
                        else -> AttendanceStatus.PRESENT
                    }
                    val dateKey = json.optString("dateKey").takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
                        ?: snapshot.todayDateKey
                    AssistantAction.MarkAttendanceProposal(
                        studentId = matchedStudent.student.id,
                        studentName = matchedStudent.student.name,
                        classId = matchedStudent.student.classId,
                        className = matchedStudent.className,
                        dateKey = dateKey,
                        status = status
                    )
                }
            }
            "SAVE_EXAM_MARKS" -> {
                val exam = resolveExam(
                    examId = json.optString("examId"),
                    examName = json.optString("examName"),
                    preferredClassId = matchedStudent?.student?.classId,
                    snapshot = snapshot
                )
                val subject = resolveSubject(
                    subjectId = json.optString("subjectId"),
                    subjectName = json.optString("subjectName"),
                    classId = exam?.classId ?: matchedStudent?.student?.classId,
                    snapshot = snapshot
                )
                val totalMarks = json.optInt("totalMarks", subject?.totalMarks ?: 100).coerceAtLeast(1)
                val obtained = json.optDouble("obtainedMarks", 0.0).coerceIn(0.0, totalMarks.toDouble())
                AssistantAction.SaveExamMarksProposal(
                    studentId = matchedStudent?.student?.id,
                    studentName = matchedStudent?.student?.name ?: json.optString("studentName").ifBlank { "Select Student" },
                    examId = exam?.id,
                    examName = exam?.name ?: json.optString("examName").ifBlank { "Select Exam" },
                    subjectId = subject?.id,
                    subjectName = subject?.name ?: json.optString("subjectName").ifBlank { "Select Subject" },
                    obtainedMarks = obtained,
                    totalMarks = totalMarks,
                    feedback = json.optString("feedback")
                )
            }
            "SEND_WHATSAPP" -> {
                if (matchedStudent == null) null
                else {
                    val waType = when (json.optString("whatsappType").uppercase()) {
                        "ATTENDANCE_ALERT" -> WhatsAppMessageType.ATTENDANCE_ALERT
                        "EXAM_RESULT" -> WhatsAppMessageType.EXAM_RESULT
                        else -> WhatsAppMessageType.FEE_REMINDER
                    }
                    AssistantAction.SendWhatsAppProposal(
                        studentId = matchedStudent.student.id,
                        studentName = matchedStudent.student.name,
                        messageType = waType,
                        reasonSummary = reply.take(120)
                    )
                }
            }
            else -> null
        }

        return AssistantTurnResponse(replyText = reply, action = action, usedCloudAi = true)
    }

    /**
     * Local offline Roman-Urdu + English intent parser so core assistant actions work even offline or before adding a key.
     */
    internal fun parseLocalIntent(
        prompt: String,
        hasImage: Boolean,
        snapshot: AcademyContextSnapshot,
        offlineReason: String? = null
    ): AssistantTurnResponse {
        val lower = prompt.lowercase()

        if (hasImage) {
            val defaultExam = snapshot.exams.firstOrNull()
            val defaultSubject = snapshot.subjects.firstOrNull { it.classId == defaultExam?.classId }
            val matchedStudent = findStudentInText(lower, snapshot)
            return AssistantTurnResponse(
                replyText = buildString {
                    append("Paper photo attached! ")
                    if (offlineReason != null) {
                        append("($offlineReason — full AI photo checking ke liye upar 'Add Key' se apni Gemini API key save karein aur internet ON rakhein.)\n\n")
                    }
                    append("Aap neeche diye gaye card mein check kiye gaye marks directly save kar sakte hain.")
                },
                action = AssistantAction.SaveExamMarksProposal(
                    studentId = matchedStudent?.student?.id,
                    studentName = matchedStudent?.student?.name ?: "Select Student",
                    examId = defaultExam?.id,
                    examName = defaultExam?.name ?: "Select Exam",
                    subjectId = defaultSubject?.id,
                    subjectName = defaultSubject?.name ?: "Select Subject",
                    obtainedMarks = 0.0,
                    totalMarks = defaultSubject?.totalMarks ?: 100,
                    feedback = "Enter marks manually or add your Gemini API key for automatic AI paper grading."
                ),
                usedCloudAi = false
            )
        }

        val matchedStudent = findStudentInText(lower, snapshot)
        val extractedAmount = Regex("\\b(\\d{2,6})\\b").findAll(lower)
            .mapNotNull { it.groupValues[1].toLongOrNull() }
            .firstOrNull { it >= 50L } ?: 0L

        val isFeeIntent = listOf("fee", "fees", "tuition", "paper fee", "jama", "vasool", "aa gayi", "aagayi", "aayi", "paid", "pay")
            .any { lower.contains(it) }
        val isAttendanceIntent = listOf("absent", "present", "leave", "hazri", "ghair hazir", "chutti", "attendance")
            .any { lower.contains(it) }
        val isWhatsAppIntent = listOf("whatsapp", "abbu", "parent", "msg", "message", "bhejo", "alert", "reminder")
            .any { lower.contains(it) }
        val isPendingSummary = listOf("pending", "baqi", "baki", "defaulter", "kitni", "summary", "dikhao", "batao")
            .any { lower.contains(it) }

        if (isWhatsAppIntent && matchedStudent != null) {
            val msgType = when {
                lower.contains("absent") || lower.contains("attendance") || lower.contains("leave") ->
                    WhatsAppMessageType.ATTENDANCE_ALERT
                lower.contains("result") || lower.contains("marks") || lower.contains("paper") ->
                    WhatsAppMessageType.EXAM_RESULT
                else -> WhatsAppMessageType.FEE_REMINDER
            }
            return AssistantTurnResponse(
                replyText = "${matchedStudent.student.name} ke guardian ko WhatsApp message bhejne ke liye neeche button dabayein:",
                action = AssistantAction.SendWhatsAppProposal(
                    studentId = matchedStudent.student.id,
                    studentName = matchedStudent.student.name,
                    messageType = msgType,
                    reasonSummary = "Single-student ${msgType.name.lowercase().replace('_', ' ')}"
                ),
                usedCloudAi = false
            )
        }

        if (isFeeIntent && matchedStudent != null) {
            if (!snapshot.isAdmin) {
                return AssistantTurnResponse(
                    replyText = "Sirf Admin fee receive kar sakta hai. Aap currently Teacher view-only mode mein hain.",
                    usedCloudAi = false
                )
            }
            val preferredLedger = when {
                lower.contains("paper") -> FeeLedgerType.PAPER_FEE
                lower.contains("other") || lower.contains("admission") -> FeeLedgerType.OTHER
                else -> FeeLedgerType.TUITION
            }
            val proposal = buildFeeProposal(
                st = matchedStudent,
                preferredLedger = preferredLedger,
                preferredChargeId = null,
                requestedAmount = extractedAmount
            )
            return if (proposal != null) {
                AssistantTurnResponse(
                    replyText = "${matchedStudent.student.name} (${matchedStudent.className}) ki ${proposal.chargeTitle} (${FormatUtils.formatCurrency(proposal.amount)}) record karne ke liye neeche Confirm dabayein.",
                    action = proposal,
                    usedCloudAi = false
                )
            } else {
                AssistantTurnResponse(
                    replyText = "${matchedStudent.student.name} ke naam par koi unpaid fee charge pending nahi hai (Tuition pending: ${FormatUtils.formatCurrency(matchedStudent.tuitionPending)}, Paper Fee pending: ${FormatUtils.formatCurrency(matchedStudent.paperFeePending)}).",
                    usedCloudAi = false
                )
            }
        }

        if (isAttendanceIntent && matchedStudent != null) {
            val status = when {
                lower.contains("absent") || lower.contains("ghair") -> AttendanceStatus.ABSENT
                lower.contains("leave") || lower.contains("chutti") -> AttendanceStatus.LEAVE
                else -> AttendanceStatus.PRESENT
            }
            return AssistantTurnResponse(
                replyText = "${matchedStudent.student.name} ki aaj (${FormatUtils.formatDateKey(snapshot.todayDateKey)}) ki attendance '$status' mark karne ke liye Confirm dabayein.",
                action = AssistantAction.MarkAttendanceProposal(
                    studentId = matchedStudent.student.id,
                    studentName = matchedStudent.student.name,
                    classId = matchedStudent.student.classId,
                    className = matchedStudent.className,
                    dateKey = snapshot.todayDateKey,
                    status = status
                ),
                usedCloudAi = false
            )
        }

        if (isPendingSummary || lower.contains("absent")) {
            val totalTuitionDue = snapshot.students.sumOf { it.tuitionPending }
            val totalPaperDue = snapshot.students.sumOf { it.paperFeePending }
            val defaulters = snapshot.students
                .filter { it.tuitionPending + it.paperFeePending + it.otherFeePending > 0L }
                .sortedByDescending { it.tuitionPending + it.paperFeePending + it.otherFeePending }
                .take(6)

            val absentStudents = snapshot.students.filter { it.student.id in snapshot.todayAbsentStudentIds }

            val summary = buildString {
                if (lower.contains("absent") || lower.contains("attendance")) {
                    appendLine("Aaj ki Attendance (${FormatUtils.formatDateKey(snapshot.todayDateKey)}):")
                    appendLine("• Present: ${snapshot.todayPresentCount} | Absent: ${snapshot.todayAbsentCount} | Leave: ${snapshot.todayLeaveCount}")
                    if (absentStudents.isNotEmpty()) {
                        appendLine("• Absent Students: ${absentStudents.joinToString(", ") { it.student.name }}")
                    }
                    appendLine()
                }
                appendLine("Pending Fees Summary:")
                appendLine("• Monthly Tuition Pending: ${FormatUtils.formatCurrency(totalTuitionDue)}")
                appendLine("• Paper Fee Pending: ${FormatUtils.formatCurrency(totalPaperDue)}")
                if (defaulters.isNotEmpty()) {
                    appendLine("• Top Pending Students:")
                    defaulters.forEach { d ->
                        val tot = d.tuitionPending + d.paperFeePending + d.otherFeePending
                        appendLine("  - ${d.student.name} (${d.className}): ${FormatUtils.formatCurrency(tot)}")
                    }
                }
            }
            return AssistantTurnResponse(replyText = summary.trim(), usedCloudAi = false)
        }

        val hintPrefix = if (offlineReason != null) "($offlineReason — offline helper active)\n\n" else ""
        return AssistantTurnResponse(
            replyText = "${hintPrefix}Aap mujhe yeh commands de sakte hain:\n• \"<Student Name> ki fee aa gayi\" (e.g. Ali ki 3000 tuition jama karo)\n• \"<Student Name> aaj absent hai\"\n• \"Pending fees aur absent students batao\"\n• Paper check karwane ke liye + icon se test photo attach karein (personal Gemini API key ke saath).",
            usedCloudAi = false
        )
    }

    private fun buildFeeProposal(
        st: AcademyStudentSnapshot,
        preferredLedger: String,
        preferredChargeId: String?,
        requestedAmount: Long
    ): AssistantAction.CollectFeeProposal? {
        val openCharges = st.openCharges.filter { it.remainingAmount > 0L }
        if (openCharges.isEmpty()) return null

        val specific = preferredChargeId?.let { id -> openCharges.find { it.id == id } }
        val chosenLedger = specific?.ledgerType ?: when {
            preferredLedger == FeeLedgerType.PAPER_FEE && st.paperFeePending > 0L -> FeeLedgerType.PAPER_FEE
            preferredLedger == FeeLedgerType.OTHER && st.otherFeePending > 0L -> FeeLedgerType.OTHER
            st.tuitionPending > 0L -> FeeLedgerType.TUITION
            st.paperFeePending > 0L -> FeeLedgerType.PAPER_FEE
            else -> FeeLedgerType.OTHER
        }

        return when (chosenLedger) {
            FeeLedgerType.TUITION -> {
                val maxDue = st.tuitionPending
                if (maxDue <= 0L) return null
                val amt = if (requestedAmount > 0L) requestedAmount.coerceAtMost(maxDue) else maxDue
                val firstCharge = specific ?: openCharges.firstOrNull { it.ledgerType == FeeLedgerType.TUITION }
                AssistantAction.CollectFeeProposal(
                    studentId = st.student.id,
                    studentName = st.student.name,
                    className = st.className,
                    ledgerType = FeeLedgerType.TUITION,
                    targetChargeId = firstCharge?.id,
                    chargeTitle = firstCharge?.title ?: "Monthly Tuition",
                    amount = amt,
                    maxDue = maxDue
                )
            }
            else -> {
                val charge = specific
                    ?: openCharges.firstOrNull { it.ledgerType == chosenLedger }
                    ?: openCharges.firstOrNull()
                    ?: return null
                val maxDue = charge.remainingAmount
                val amt = if (requestedAmount > 0L) requestedAmount.coerceAtMost(maxDue) else maxDue
                AssistantAction.CollectFeeProposal(
                    studentId = st.student.id,
                    studentName = st.student.name,
                    className = st.className,
                    ledgerType = charge.ledgerType,
                    targetChargeId = charge.id,
                    chargeTitle = charge.title,
                    amount = amt,
                    maxDue = maxDue
                )
            }
        }
    }

    private fun findStudentInText(lowerPrompt: String, snapshot: AcademyContextSnapshot): AcademyStudentSnapshot? {
        // Exact full name match first, then first-name / roll-no match (longest match wins)
        return snapshot.students
            .sortedByDescending { it.student.name.length }
            .firstOrNull { st ->
                val fullName = st.student.name.trim().lowercase()
                val roll = st.student.rollNo.trim().lowercase()
                (fullName.length >= 2 && lowerPrompt.contains(fullName)) ||
                    (roll.isNotBlank() && Regex("\\b${Regex.escape(roll)}\\b").containsMatchIn(lowerPrompt)) ||
                    fullName.split(" ").any { part -> part.length >= 3 && Regex("\\b${Regex.escape(part)}\\b").containsMatchIn(lowerPrompt) }
            }
    }

    private fun resolveStudent(
        studentId: String,
        studentName: String,
        snapshot: AcademyContextSnapshot
    ): AcademyStudentSnapshot? {
        if (studentId.isNotBlank()) {
            snapshot.students.find { it.student.id == studentId }?.let { return it }
        }
        if (studentName.isNotBlank()) {
            val clean = studentName.trim().lowercase()
            return snapshot.students.firstOrNull {
                it.student.name.equals(clean, ignoreCase = true) ||
                    it.student.name.lowercase().contains(clean) ||
                    clean.contains(it.student.name.lowercase())
            }
        }
        return null
    }

    private fun resolveExam(
        examId: String,
        examName: String,
        preferredClassId: String?,
        snapshot: AcademyContextSnapshot
    ): Exam? {
        if (examId.isNotBlank()) {
            snapshot.exams.find { it.id == examId }?.let { return it }
        }
        if (examName.isNotBlank()) {
            val clean = examName.trim().lowercase()
            snapshot.exams.firstOrNull {
                it.name.lowercase().contains(clean) || clean.contains(it.name.lowercase())
            }?.let { return it }
        }
        return snapshot.exams.firstOrNull { preferredClassId != null && it.classId == preferredClassId }
            ?: snapshot.exams.firstOrNull()
    }

    private fun resolveSubject(
        subjectId: String,
        subjectName: String,
        classId: String?,
        snapshot: AcademyContextSnapshot
    ): Subject? {
        if (subjectId.isNotBlank()) {
            snapshot.subjects.find { it.id == subjectId }?.let { return it }
        }
        val classSubjects = if (classId != null) {
            snapshot.subjects.filter { it.classId == classId }
        } else {
            snapshot.subjects
        }
        if (subjectName.isNotBlank()) {
            val clean = subjectName.trim().lowercase()
            classSubjects.firstOrNull {
                it.name.lowercase().contains(clean) || clean.contains(it.name.lowercase())
            }?.let { return it }
        }
        return classSubjects.firstOrNull() ?: snapshot.subjects.firstOrNull()
    }

    private fun buildCompactContextPrompt(snapshot: AcademyContextSnapshot): String {
        return buildString {
            appendLine("Today: ${snapshot.todayDateKey} | Attendance Today: ${snapshot.todayPresentCount}P, ${snapshot.todayAbsentCount}A, ${snapshot.todayLeaveCount}L")
            appendLine("Classes: ${snapshot.classes.joinToString(", ") { "${it.name}(id=${it.id}, fee=${it.monthlyFee})" }}")
            appendLine("Subjects: ${snapshot.subjects.take(40).joinToString(", ") { "${it.name}(id=${it.id}, classId=${it.classId}, total=${it.totalMarks})" }}")
            appendLine("Exams: ${snapshot.exams.take(20).joinToString(", ") { "${it.name}(id=${it.id}, classId=${it.classId})" }}")
            appendLine("Students (${snapshot.students.size} active):")
            snapshot.students.take(80).forEach { st ->
                val chargesStr = st.openCharges.take(3).joinToString("; ") {
                    "${it.ledgerType}:${it.title}(id=${it.id}, due=${it.remainingAmount})"
                }
                appendLine("- ${st.student.name} [id=${st.student.id}, roll=${st.student.rollNo}, class=${st.className}, tuitionDue=${st.tuitionPending}, paperDue=${st.paperFeePending}, otherDue=${st.otherFeePending}${if (chargesStr.isNotEmpty()) ", openCharges=[$chargesStr]" else ""}]")
            }
        }
    }

    companion object {
        private const val TAG = "AcademyAssistantEngine"
    }
}
