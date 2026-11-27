package com.example.data.ai

import android.util.Log
import com.example.data.sync.NetworkMonitor
import com.example.util.FormatUtils
import com.example.util.WhatsAppHelper
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

data class ParentMessageRequest(
    val type: WhatsAppMessageType,
    val academyName: String,
    val studentName: String,
    val className: String,
    val dateLabel: String = "",
    val attendanceStatus: String = "",
    val subjectOrExamTitle: String = "",
    val obtainedMarks: String = "",
    val totalMarks: String = "",
    val percentageLabel: String = "",
    val grade: String? = null,
    val tuitionPending: Long = 0L,
    val paperFeePending: Long = 0L,
    val otherFeePending: Long = 0L
)

data class StudentSummaryRequest(
    val academyName: String,
    val studentName: String,
    val className: String,
    val status: String,
    val presentDays: Int,
    val absentDays: Int,
    val leaveDays: Int,
    val recentExamsSummary: List<String>,
    val tuitionPending: Long,
    val paperFeePending: Long,
    val otherFeePending: Long
)

data class ClassSummaryRequest(
    val academyName: String,
    val className: String,
    val activeStudentsCount: Int,
    val leftStudentsCount: Int,
    val presentCountLast30Days: Int,
    val absentCountLast30Days: Int,
    val leaveCountLast30Days: Int,
    val tuitionPendingTotal: Long,
    val paperFeePendingTotal: Long,
    val latestExamTitle: String?,
    val latestExamAvgPercent: Double?
)

sealed interface AiTextResult {
    data class Success(
        val text: String,
        val usedCloudAi: Boolean
    ) : AiTextResult

    data class FallbackError(
        val message: String,
        val fallbackText: String
    ) : AiTextResult
}

/**
 * Engine-agnostic contract for optional academy text drafting & summarization.
 * Keeping AI behind this interface ensures zero dependency on developer cloud quota.
 */
interface AcademyAiEngine {
    val engineName: String
    suspend fun draftParentMessage(request: ParentMessageRequest): AiTextResult
    suspend fun summarizeStudent(request: StudentSummaryRequest): AiTextResult
    suspend fun summarizeClass(request: ClassSummaryRequest): AiTextResult
}

class LocalTemplateAiEngine : AcademyAiEngine {
    override val engineName: String = "Local Templates"

    override suspend fun draftParentMessage(request: ParentMessageRequest): AiTextResult {
        val text = when (request.type) {
            WhatsAppMessageType.ATTENDANCE_ALERT -> WhatsAppHelper.buildAttendanceMessage(
                academyName = request.academyName,
                studentName = request.studentName,
                className = request.className,
                dateLabel = request.dateLabel,
                status = request.attendanceStatus
            )
            WhatsAppMessageType.EXAM_RESULT -> WhatsAppHelper.buildExamResultMessage(
                academyName = request.academyName,
                studentName = request.studentName,
                subjectOrExamTitle = request.subjectOrExamTitle,
                testDateLabel = request.dateLabel,
                obtainedMarks = request.obtainedMarks,
                totalMarks = request.totalMarks,
                percentageLabel = request.percentageLabel,
                grade = request.grade
            )
            WhatsAppMessageType.FEE_REMINDER -> WhatsAppHelper.buildFeeReminderMessage(
                academyName = request.academyName,
                studentName = request.studentName,
                className = request.className,
                tuitionPending = request.tuitionPending,
                paperFeePending = request.paperFeePending,
                otherFeePending = request.otherFeePending
            )
        }
        return AiTextResult.Success(text = text, usedCloudAi = false)
    }

    override suspend fun summarizeStudent(request: StudentSummaryRequest): AiTextResult {
        val totalDays = request.presentDays + request.absentDays + request.leaveDays
        val attPct = if (totalDays > 0) (request.presentDays * 100.0 / totalDays).toInt() else null
        val attLine = if (totalDays == 0) {
            "• Attendance: No attendance recorded yet."
        } else {
            "• Attendance: ${attPct}% present (${request.presentDays}P / ${request.absentDays}A / ${request.leaveDays}L across $totalDays days)."
        }

        val testLine = if (request.recentExamsSummary.isEmpty()) {
            "• Test Trend: No exam results recorded yet."
        } else {
            "• Test Trend: ${request.recentExamsSummary.take(3).joinToString("; ")}."
        }

        val totalPending = (request.tuitionPending + request.paperFeePending + request.otherFeePending).coerceAtLeast(0L)
        val feeLine = if (totalPending == 0L) {
            "• Fees: All tuition and paper fee dues are cleared."
        } else {
            "• Fees: Total pending ${FormatUtils.formatCurrency(totalPending)} (Tuition ${FormatUtils.formatCurrency(request.tuitionPending)}, Paper ${FormatUtils.formatCurrency(request.paperFeePending)})."
        }

        val statusLine = "• Status: ${request.status.lowercase().replaceFirstChar { it.uppercase() }} in ${request.className.ifBlank { "assigned class" }}."
        return AiTextResult.Success(
            text = listOf(statusLine, attLine, testLine, feeLine).joinToString("\n"),
            usedCloudAi = false
        )
    }

    override suspend fun summarizeClass(request: ClassSummaryRequest): AiTextResult {
        val attTotal = request.presentCountLast30Days + request.absentCountLast30Days + request.leaveCountLast30Days
        val attPct = if (attTotal > 0) (request.presentCountLast30Days * 100.0 / attTotal).toInt() else null
        val attLine = if (attPct == null) {
            "• Attendance: No attendance records in the last 30 days."
        } else {
            "• Attendance (30d): $attPct% present (${request.presentCountLast30Days}P / ${request.absentCountLast30Days}A / ${request.leaveCountLast30Days}L)."
        }

        val examLine = if (request.latestExamTitle != null && request.latestExamAvgPercent != null) {
            "• Latest Test (${request.latestExamTitle}): Class average ${FormatUtils.formatPercent(request.latestExamAvgPercent)}."
        } else {
            "• Latest Test: No graded exams yet."
        }

        val totalPending = request.tuitionPendingTotal + request.paperFeePendingTotal
        val feeLine = if (totalPending == 0L) {
            "• Fee Status: All class tuition and paper fees cleared."
        } else {
            "• Fee Status: Pending ${FormatUtils.formatCurrency(totalPending)} (Tuition ${FormatUtils.formatCurrency(request.tuitionPendingTotal)}, Paper ${FormatUtils.formatCurrency(request.paperFeePendingTotal)})."
        }

        val strengthLine = "• Enrollment: ${request.activeStudentsCount} active student(s)" +
            if (request.leftStudentsCount > 0) ", ${request.leftStudentsCount} left." else "."

        return AiTextResult.Success(
            text = listOf(strengthLine, attLine, examLine, feeLine).joinToString("\n"),
            usedCloudAi = false
        )
    }
}

/**
 * End-user BYOK (Bring Your Own Key) Gemini engine.
 * Only executes if the user explicitly saves their own Gemini API key in Settings,
 * ensuring 100% of API quota belongs to that user rather than the app developer.
 */
class UserKeyGeminiAiEngine(
    private val localFallback: LocalTemplateAiEngine,
    private val apiKeyProvider: suspend () -> String
) : AcademyAiEngine {

    override val engineName: String = "User Gemini API Key"

    override suspend fun draftParentMessage(request: ParentMessageRequest): AiTextResult {
        val fallback = (localFallback.draftParentMessage(request) as AiTextResult.Success).text
        val prompt = buildString {
            appendLine("Write a polite, concise, clear single-parent WhatsApp message for an evening academy teacher/principal.")
            appendLine("Do not mention any other students. Keep it brief, respectful, and ready to send via WhatsApp.")
            appendLine("Academy: ${request.academyName.ifBlank { "Alhamd Science Evening Academy" }}")
            appendLine("Student: ${request.studentName} (${request.className})")
            when (request.type) {
                WhatsAppMessageType.ATTENDANCE_ALERT -> {
                    appendLine("Topic: Attendance Alert")
                    appendLine("Date: ${request.dateLabel}")
                    appendLine("Status: ${request.attendanceStatus}")
                }
                WhatsAppMessageType.EXAM_RESULT -> {
                    appendLine("Topic: Test Result")
                    appendLine("Test/Subject: ${request.subjectOrExamTitle}")
                    appendLine("Date: ${request.dateLabel}")
                    appendLine("Marks: ${request.obtainedMarks} / ${request.totalMarks} (${request.percentageLabel})")
                    if (!request.grade.isNullOrBlank()) appendLine("Grade: ${request.grade}")
                }
                WhatsAppMessageType.FEE_REMINDER -> {
                    appendLine("Topic: Fee Reminder")
                    appendLine("Monthly Tuition Pending: ${FormatUtils.formatCurrency(request.tuitionPending)}")
                    appendLine("Paper Fee Pending: ${FormatUtils.formatCurrency(request.paperFeePending)}")
                    if (request.otherFeePending > 0L) {
                        appendLine("Other Fee Pending: ${FormatUtils.formatCurrency(request.otherFeePending)}")
                    }
                    appendLine("Total Pending: ${FormatUtils.formatCurrency(request.tuitionPending + request.paperFeePending + request.otherFeePending)}")
                }
            }
            appendLine("Output only the WhatsApp message body with no surrounding commentary.")
        }
        return generateWithUserKey(prompt, fallback)
    }

    override suspend fun summarizeStudent(request: StudentSummaryRequest): AiTextResult {
        val fallback = (localFallback.summarizeStudent(request) as AiTextResult.Success).text
        val prompt = buildString {
            appendLine("Summarize this student's academy progress in 3 to 4 crisp bullet points starting with '•'.")
            appendLine("Cover attendance, test trend, and fee status accurately using ONLY the facts below.")
            appendLine("Academy: ${request.academyName}")
            appendLine("Student: ${request.studentName} | Class: ${request.className} | Status: ${request.status}")
            appendLine("Attendance: ${request.presentDays} present, ${request.absentDays} absent, ${request.leaveDays} leave")
            appendLine("Tests: ${request.recentExamsSummary.ifEmpty { listOf("None recorded") }.joinToString("; ")}")
            appendLine("Tuition Pending: ${FormatUtils.formatCurrency(request.tuitionPending)}")
            appendLine("Paper Fee Pending: ${FormatUtils.formatCurrency(request.paperFeePending)}")
            appendLine("Other Fee Pending: ${FormatUtils.formatCurrency(request.otherFeePending)}")
            appendLine("Keep it factual, concise, and actionable for the academy principal.")
        }
        return generateWithUserKey(prompt, fallback)
    }

    override suspend fun summarizeClass(request: ClassSummaryRequest): AiTextResult {
        val fallback = (localFallback.summarizeClass(request) as AiTextResult.Success).text
        val prompt = buildString {
            appendLine("Summarize this class overview in 3 to 4 crisp bullet points starting with '•'.")
            appendLine("Cover enrollment, attendance, test performance, and fee balance using ONLY the facts below.")
            appendLine("Class: ${request.className}")
            appendLine("Students: ${request.activeStudentsCount} active, ${request.leftStudentsCount} left")
            appendLine("Last 30d Attendance: ${request.presentCountLast30Days}P, ${request.absentCountLast30Days}A, ${request.leaveCountLast30Days}L")
            appendLine("Latest Exam: ${request.latestExamTitle ?: "None"} | Avg: ${request.latestExamAvgPercent?.let { FormatUtils.formatPercent(it) } ?: "N/A"}")
            appendLine("Tuition Pending Total: ${FormatUtils.formatCurrency(request.tuitionPendingTotal)}")
            appendLine("Paper Fee Pending Total: ${FormatUtils.formatCurrency(request.paperFeePendingTotal)}")
        }
        return generateWithUserKey(prompt, fallback)
    }

    private suspend fun generateWithUserKey(prompt: String, fallback: String): AiTextResult {
        val userApiKey = apiKeyProvider().trim()
        if (userApiKey.isEmpty()) {
            return AiTextResult.FallbackError(
                message = "No personal Gemini API key saved in Settings; using free local template.",
                fallbackText = fallback
            )
        }

        return try {
            val responseText = withTimeout(45_000L) {
                callGeminiRestWithUserKey(apiKey = userApiKey, prompt = prompt)
            }
            if (!responseText.isNullOrBlank()) {
                AiTextResult.Success(text = responseText.trim(), usedCloudAi = true)
            } else {
                AiTextResult.FallbackError("AI returned an empty response; using local template.", fallback)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: TimeoutCancellationException) {
            Log.e(TAG, "User Gemini API request timed out after 45s")
            AiTextResult.FallbackError("AI request timed out; using local template.", fallback)
        } catch (e: Exception) {
            Log.e(TAG, "User Gemini API request failed", e)
            AiTextResult.FallbackError(
                message = e.localizedMessage ?: "AI unavailable; using local template.",
                fallbackText = fallback
            )
        }
    }

    private suspend fun callGeminiRestWithUserKey(apiKey: String, prompt: String): String? =
        withContext(Dispatchers.IO) {
            val modelOrder = listOf("gemini-3.8-flash", "gemini-2.5-flash")
            var lastError: Exception? = null
            for ((idx, modelId) in modelOrder.withIndex()) {
                try {
                    return@withContext callGeminiModelOnce(modelId = modelId, apiKey = apiKey, prompt = prompt)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    val msg = e.message.orEmpty().lowercase()
                    val shouldTryFallback = idx < modelOrder.lastIndex &&
                        (msg.contains("no longer available") ||
                            msg.contains("not found") ||
                            msg.contains("not supported") ||
                            msg.contains("(404)"))
                    if (!shouldTryFallback) {
                        throw e
                    }
                    Log.w(TAG, "Model $modelId unavailable, trying fallback model: ${e.message}")
                }
            }
            throw lastError ?: IllegalStateException("Gemini API request failed")
        }

    private fun callGeminiModelOnce(modelId: String, apiKey: String, prompt: String): String? {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            val payload = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", prompt))
                        )
                    )
                )
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
                throw IllegalStateException(
                    if (!errMsg.isNullOrBlank()) "Gemini API error ($code): $errMsg"
                    else "Gemini API error ($code)"
                )
            }
            val json = JSONObject(body)
            val candidates = json.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val parts = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts") ?: return null
            return buildString {
                for (i in 0 until parts.length()) {
                    append(parts.getJSONObject(i).optString("text", ""))
                }
            }.ifBlank { null }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "UserKeyGeminiAiEngine"
    }
}

/**
 * Coordinates between user-controlled AI enablement, user's own API key, network state, and local templates.
 */
class AcademyAiCoordinator(
    private val networkMonitor: NetworkMonitor,
    private val userApiKeyProvider: suspend () -> String,
    private val localEngine: LocalTemplateAiEngine = LocalTemplateAiEngine(),
    private val cloudEngine: AcademyAiEngine = UserKeyGeminiAiEngine(localEngine, userApiKeyProvider)
) {
    fun buildLocalParentMessage(request: ParentMessageRequest): String {
        val res = when (request.type) {
            WhatsAppMessageType.ATTENDANCE_ALERT -> WhatsAppHelper.buildAttendanceMessage(
                academyName = request.academyName,
                studentName = request.studentName,
                className = request.className,
                dateLabel = request.dateLabel,
                status = request.attendanceStatus
            )
            WhatsAppMessageType.EXAM_RESULT -> WhatsAppHelper.buildExamResultMessage(
                academyName = request.academyName,
                studentName = request.studentName,
                subjectOrExamTitle = request.subjectOrExamTitle,
                testDateLabel = request.dateLabel,
                obtainedMarks = request.obtainedMarks,
                totalMarks = request.totalMarks,
                percentageLabel = request.percentageLabel,
                grade = request.grade
            )
            WhatsAppMessageType.FEE_REMINDER -> WhatsAppHelper.buildFeeReminderMessage(
                academyName = request.academyName,
                studentName = request.studentName,
                className = request.className,
                tuitionPending = request.tuitionPending,
                paperFeePending = request.paperFeePending,
                otherFeePending = request.otherFeePending
            )
        }
        return res
    }

    suspend fun draftParentMessage(
        request: ParentMessageRequest,
        aiEnabled: Boolean
    ): AiTextResult {
        if (!aiEnabled) {
            return localEngine.draftParentMessage(request)
        }
        if (!networkMonitor.isOnline.value) {
            val fallback = buildLocalParentMessage(request)
            return AiTextResult.FallbackError(
                message = "Offline: using ready-made local template.",
                fallbackText = fallback
            )
        }
        return cloudEngine.draftParentMessage(request)
    }

    suspend fun summarizeStudent(
        request: StudentSummaryRequest,
        aiEnabled: Boolean,
        preferCloudAi: Boolean
    ): AiTextResult {
        if (!aiEnabled || !preferCloudAi) {
            return localEngine.summarizeStudent(request)
        }
        if (!networkMonitor.isOnline.value) {
            val fallback = (localEngine.summarizeStudent(request) as AiTextResult.Success).text
            return AiTextResult.FallbackError(
                message = "Offline: showing instant local summary.",
                fallbackText = fallback
            )
        }
        return cloudEngine.summarizeStudent(request)
    }

    suspend fun summarizeClass(
        request: ClassSummaryRequest,
        aiEnabled: Boolean,
        preferCloudAi: Boolean
    ): AiTextResult {
        if (!aiEnabled || !preferCloudAi) {
            return localEngine.summarizeClass(request)
        }
        if (!networkMonitor.isOnline.value) {
            val fallback = (localEngine.summarizeClass(request) as AiTextResult.Success).text
            return AiTextResult.FallbackError(
                message = "Offline: showing instant local summary.",
                fallbackText = fallback
            )
        }
        return cloudEngine.summarizeClass(request)
    }
}
