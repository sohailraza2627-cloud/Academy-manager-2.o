package com.example

import com.example.data.ai.AiTextResult
import com.example.data.ai.LocalTemplateAiEngine
import com.example.data.ai.ParentMessageRequest
import com.example.data.ai.StudentSummaryRequest
import com.example.data.ai.UserKeyGeminiAiEngine
import com.example.data.sync.CloudSyncManager
import com.example.util.FormatUtils
import com.example.util.WhatsAppHelper
import com.example.util.WhatsAppMessageType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademyLogicUnitTest {

    @Test
    fun `currency formats as Rs with comma grouping`() {
        assertEquals("Rs 12,500", FormatUtils.formatCurrency(12500L))
        assertEquals("Rs 0", FormatUtils.formatCurrency(0L))
        assertEquals("-Rs 500", FormatUtils.formatCurrency(-500L))
    }

    @Test
    fun `grade boundaries match specification`() {
        assertEquals("A+", FormatUtils.computeGrade(90.0))
        assertEquals("A", FormatUtils.computeGrade(80.0))
        assertEquals("B", FormatUtils.computeGrade(70.0))
        assertEquals("C", FormatUtils.computeGrade(60.0))
        assertEquals("D", FormatUtils.computeGrade(50.0))
        assertEquals("F", FormatUtils.computeGrade(49.9))
    }

    @Test
    fun `receipt numbers with device prefix and month keys format accurately`() {
        assertEquals("R-A-0001", FormatUtils.formatReceiptNumber("R", "A", 1))
        assertEquals("PF-B-0042", FormatUtils.formatReceiptNumber("PF", "B", 42))
        assertEquals("TAB1", FormatUtils.normalizeDevicePrefix("tab-1!"))
        assertEquals("A", FormatUtils.normalizeDevicePrefix("   "))
        assertEquals("2026-12", FormatUtils.shiftMonthKey("2026-11", 1))
        assertEquals("2026-10", FormatUtils.shiftMonthKey("2026-11", -1))
        assertEquals("AR", FormatUtils.initialsFor("Ali Raza"))
    }

    @Test
    fun `last write wins applies remote only when newer`() {
        assertTrue(CloudSyncManager.shouldApplyRemote(null, 1000L))
        assertTrue(CloudSyncManager.shouldApplyRemote(1000L, 1001L))
        assertFalse(CloudSyncManager.shouldApplyRemote(1000L, 1000L))
        assertFalse(CloudSyncManager.shouldApplyRemote(1500L, 1000L))
    }

    @Test
    fun `whatsapp normalization handles Pakistan 03xx, +92, 0092 and rejects invalid inputs`() {
        assertEquals("+923001234567", WhatsAppHelper.normalizeWhatsAppNumber("0300-1234567"))
        assertEquals("+923001234567", WhatsAppHelper.normalizeWhatsAppNumber("0300 123 4567"))
        assertEquals("+923219876543", WhatsAppHelper.normalizeWhatsAppNumber("+92 321 9876543"))
        assertEquals("+923331112233", WhatsAppHelper.normalizeWhatsAppNumber("00923331112233"))
        assertEquals("+923451234567", WhatsAppHelper.normalizeWhatsAppNumber("3451234567"))
        assertEquals("923001234567", WhatsAppHelper.toWaMeDigits("03001234567"))
        assertNull(WhatsAppHelper.normalizeWhatsAppNumber("0300123"))
        assertNull(WhatsAppHelper.normalizeWhatsAppNumber("   "))
        assertNull(WhatsAppHelper.normalizeWhatsAppNumber("abc1234567"))
    }

    @Test
    fun `whatsapp history appends previous number without duplicating latest entry`() {
        val fixedMillis = 1764240000000L
        val first = WhatsAppHelper.appendHistory("", "+923001111111", fixedMillis)
        assertTrue(first.startsWith("+923001111111"))
        val dedup = WhatsAppHelper.appendHistory(first, "+923001111111", fixedMillis)
        assertEquals(first, dedup)
        val second = WhatsAppHelper.appendHistory(first, "+923002222222", fixedMillis)
        val entries = WhatsAppHelper.parseHistoryEntries(second)
        assertEquals(2, entries.size)
        assertTrue(entries[0].startsWith("+923002222222"))
        assertTrue(entries[1].startsWith("+923001111111"))
    }

    @Test
    fun `individual whatsapp builders format single-student messages cleanly`() {
        val att = WhatsAppHelper.buildAttendanceMessage(
            academyName = "Alhamd Science Evening Academy",
            studentName = "Ayesha Khan",
            className = "Class 10",
            dateLabel = "27 Nov 2026",
            status = "ABSENT"
        )
        assertTrue(att.contains("Ayesha Khan"))
        assertTrue(att.contains("Absent"))

        val fee = WhatsAppHelper.buildFeeReminderMessage(
            academyName = "Alhamd Science Evening Academy",
            studentName = "Bilal Ahmed",
            className = "Class 9",
            tuitionPending = 3000L,
            paperFeePending = 500L
        )
        assertTrue(fee.contains("Rs 3,000"))
        assertTrue(fee.contains("Rs 500"))
        assertTrue(fee.contains("Rs 3,500"))
    }

    @Test
    fun `local template ai engine drafts messages and summaries offline`() = runBlocking {
        val engine = LocalTemplateAiEngine()
        val msgResult = engine.draftParentMessage(
            ParentMessageRequest(
                type = WhatsAppMessageType.EXAM_RESULT,
                academyName = "Alhamd Science Evening Academy",
                studentName = "Hamza Ali",
                className = "Class 10",
                dateLabel = "27 Nov 2026",
                subjectOrExamTitle = "Physics Monthly Test",
                obtainedMarks = "46",
                totalMarks = "50",
                percentageLabel = "92.0%",
                grade = "A+"
            )
        )
        assertTrue(msgResult is AiTextResult.Success)
        msgResult as AiTextResult.Success
        assertFalse(msgResult.usedCloudAi)
        assertTrue(msgResult.text.contains("Hamza Ali"))
        assertTrue(msgResult.text.contains("46 / 50"))

        val sumResult = engine.summarizeStudent(
            StudentSummaryRequest(
                academyName = "Alhamd Science Evening Academy",
                studentName = "Hamza Ali",
                className = "Class 10",
                status = "ACTIVE",
                presentDays = 18,
                absentDays = 2,
                leaveDays = 0,
                recentExamsSummary = listOf("Physics: 92%"),
                tuitionPending = 0L,
                paperFeePending = 500L,
                otherFeePending = 0L
            )
        ) as AiTextResult.Success
        assertFalse(sumResult.usedCloudAi)
        assertTrue(sumResult.text.contains("90% present"))
        assertTrue(sumResult.text.contains("Rs 500"))
    }

    @Test
    fun `user key gemini engine refuses cloud call and falls back locally when key is blank`() = runBlocking {
        val local = LocalTemplateAiEngine()
        val cloud = UserKeyGeminiAiEngine(localFallback = local, apiKeyProvider = { "   " })
        val result = cloud.draftParentMessage(
            ParentMessageRequest(
                type = WhatsAppMessageType.FEE_REMINDER,
                academyName = "Alhamd Science Evening Academy",
                studentName = "Zainab",
                className = "Class 9",
                tuitionPending = 2500L,
                paperFeePending = 300L
            )
        )
        assertTrue(result is AiTextResult.FallbackError)
        result as AiTextResult.FallbackError
        assertTrue(result.fallbackText.contains("Zainab"))
        assertTrue(result.fallbackText.contains("Rs 2,800"))
    }

    @Test
    fun `assistant local intent parses fee, attendance and paper photo actions cleanly`() {
        val engine = com.example.data.ai.AcademyAssistantEngine()
        val student = com.example.data.local.Student(
            id = "s1",
            name = "Ali Raza",
            fatherName = "Tariq",
            phone = "03001234567",
            address = "Lahore",
            classId = "c1",
            rollNo = "101",
            admissionDate = 0L,
            guardianWhatsapp = "+923001234567"
        )
        val charge = com.example.data.local.FeeCharge(
            id = "fc1",
            studentId = "s1",
            classId = "c1",
            ledgerType = com.example.data.local.FeeLedgerType.TUITION,
            feeSubType = "MONTHLY_TUITION",
            title = "Nov 2026 Tuition",
            monthKey = "2026-11",
            baseAmount = 3000L,
            discountAmount = 0L,
            netAmount = 3000L,
            paidAmount = 0L,
            dueDate = 0L
        )
        val snapshot = com.example.data.ai.AcademyContextSnapshot(
            academyName = "Alhamd Science Evening Academy",
            isAdmin = true,
            todayDateKey = "2026-11-27",
            classes = listOf(com.example.data.local.AcademyClass(id = "c1", name = "Class 10", monthlyFee = 3000L)),
            subjects = listOf(com.example.data.local.Subject(id = "sub1", classId = "c1", name = "Physics", totalMarks = 50)),
            exams = listOf(com.example.data.local.Exam(id = "ex1", classId = "c1", name = "Monthly Test", examDate = 0L)),
            students = listOf(
                com.example.data.ai.AcademyStudentSnapshot(
                    student = student,
                    className = "Class 10",
                    tuitionPending = 3000L,
                    paperFeePending = 0L,
                    otherFeePending = 0L,
                    openCharges = listOf(charge)
                )
            ),
            todayAbsentStudentIds = emptySet(),
            todayPresentCount = 0,
            todayAbsentCount = 0,
            todayLeaveCount = 0
        )

        val feeTurn = engine.parseLocalIntent("Ali ki tuition fee 3000 aa gayi", hasImage = false, snapshot = snapshot)
        assertTrue(feeTurn.action is com.example.data.ai.AssistantAction.CollectFeeProposal)
        val feeProp = feeTurn.action as com.example.data.ai.AssistantAction.CollectFeeProposal
        assertEquals("s1", feeProp.studentId)
        assertEquals(3000L, feeProp.amount)

        val attTurn = engine.parseLocalIntent("Ali aaj absent hai", hasImage = false, snapshot = snapshot)
        assertTrue(attTurn.action is com.example.data.ai.AssistantAction.MarkAttendanceProposal)
        assertEquals(
            com.example.data.local.AttendanceStatus.ABSENT,
            (attTurn.action as com.example.data.ai.AssistantAction.MarkAttendanceProposal).status
        )

        val paperTurn = engine.parseLocalIntent("ye paper check kar", hasImage = true, snapshot = snapshot, offlineReason = "No key")
        assertTrue(paperTurn.action is com.example.data.ai.AssistantAction.SaveExamMarksProposal)
    }
}
