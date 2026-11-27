package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AcademyManagerApp
import com.example.data.ai.*
import com.example.data.local.*
import com.example.data.repository.ReceiptResult
import com.example.util.FormatUtils
import com.example.util.ImageStorage
import com.example.util.WhatsAppMessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface ProposalExecutionState {
    data object Pending : ProposalExecutionState
    data object Executing : ProposalExecutionState
    data class Completed(val summary: String) : ProposalExecutionState
    data class Failed(val error: String) : ProposalExecutionState
}

data class AssistantChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val attachedBitmap: Bitmap? = null,
    val usedCloudAi: Boolean = false,
    val action: AssistantAction? = null,
    val actionState: ProposalExecutionState = ProposalExecutionState.Pending,
    val timestamp: Long = System.currentTimeMillis()
)

class AssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AcademyManagerApp
    private val repo get() = app.repository
    private val prefs get() = app.preferencesRepository
    private val networkMonitor get() = app.networkMonitor
    private val engine = AcademyAssistantEngine()

    private val _messages = MutableStateFlow<List<AssistantChatMessage>>(emptyList())
    val messages: StateFlow<List<AssistantChatMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _attachedUri = MutableStateFlow<Uri?>(null)
    private val _attachedBitmap = MutableStateFlow<Bitmap?>(null)
    val attachedBitmap: StateFlow<Bitmap?> = _attachedBitmap.asStateFlow()
    private val _attachedBase64 = MutableStateFlow<String?>(null)

    val userGeminiApiKey: StateFlow<String> = prefs.userGeminiApiKeyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val authSession: StateFlow<AuthSession> = prefs.authSessionFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AuthSession()
    )

    val students: StateFlow<List<Student>> = repo.studentsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val classes: StateFlow<List<AcademyClass>> = repo.classesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val exams: StateFlow<List<Exam>> = repo.examsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val subjects: StateFlow<List<Subject>> = repo.subjectsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val profile: StateFlow<AcademyProfile> = repo.profileFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AcademyProfile()
    )

    fun attachPaperPhoto(uri: Uri?) {
        if (uri == null) {
            clearAttachment()
            return
        }
        viewModelScope.launch {
            val encoded = withContext(Dispatchers.IO) {
                ImageStorage.encodeUriToBase64Jpeg(app, uri)
            }
            if (encoded != null) {
                _attachedUri.value = uri
                _attachedBase64.value = encoded.first
                _attachedBitmap.value = encoded.second
            }
        }
    }

    fun clearAttachment() {
        _attachedUri.value = null
        _attachedBase64.value = null
        _attachedBitmap.value = null
    }

    fun saveUserApiKey(apiKey: String) {
        viewModelScope.launch {
            prefs.setUserGeminiApiKey(apiKey)
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        clearAttachment()
    }

    fun sendMessage(rawPrompt: String) {
        val promptText = rawPrompt.trim()
        val imgBase64 = _attachedBase64.value
        val imgBitmap = _attachedBitmap.value
        if (promptText.isEmpty() && imgBase64 == null) return
        if (_isThinking.value) return

        val displayUserText = promptText.ifBlank { "Check this paper photo" }
        _messages.update {
            it + AssistantChatMessage(
                isUser = true,
                text = displayUserText,
                attachedBitmap = imgBitmap
            )
        }
        clearAttachment()
        _isThinking.value = true

        viewModelScope.launch {
            try {
                val snapshot = buildLiveSnapshot()
                val key = prefs.userGeminiApiKeyFlow.first()
                val response = engine.processPrompt(
                    prompt = displayUserText,
                    imageBase64Jpeg = imgBase64,
                    snapshot = snapshot,
                    userApiKey = key,
                    isOnline = networkMonitor.isOnline.value
                )
                _messages.update {
                    it + AssistantChatMessage(
                        isUser = false,
                        text = response.replyText,
                        usedCloudAi = response.usedCloudAi,
                        action = response.action,
                        actionState = ProposalExecutionState.Pending
                    )
                }
            } catch (e: Exception) {
                _messages.update {
                    it + AssistantChatMessage(
                        isUser = false,
                        text = "Unable to process request: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            } finally {
                _isThinking.value = false
            }
        }
    }

    fun executeFeeCollection(
        messageId: String,
        proposal: AssistantAction.CollectFeeProposal,
        amountToCollect: Long,
        onReceiptIssued: (ReceiptResult) -> Unit
    ) {
        val session = authSession.value
        if (!session.isAdmin) {
            updateActionState(messageId, ProposalExecutionState.Failed("Only Admin can record fee payments."))
            return
        }
        if (amountToCollect <= 0L || amountToCollect > proposal.maxDue) {
            updateActionState(
                messageId,
                ProposalExecutionState.Failed("Amount must be between Rs 1 and ${FormatUtils.formatCurrency(proposal.maxDue)}.")
            )
            return
        }

        updateActionState(messageId, ProposalExecutionState.Executing)
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val receipt = if (proposal.ledgerType == FeeLedgerType.TUITION) {
                    repo.collectTuitionPaymentFifo(
                        studentId = proposal.studentId,
                        targetChargeId = proposal.targetChargeId,
                        amountPaying = amountToCollect,
                        discountOnTarget = 0L,
                        paidDate = now,
                        notes = "Recorded via Academy Assistant"
                    )
                } else {
                    val chargeId = proposal.targetChargeId
                        ?: throw IllegalStateException("Charge not found")
                    repo.collectSingleChargePayment(
                        chargeId = chargeId,
                        amountPaying = amountToCollect,
                        discountAdded = 0L,
                        paidDate = now,
                        notes = "Recorded via Academy Assistant"
                    )
                }
                val summary = "Receipt ${receipt.receiptNumber} issued · Paid ${FormatUtils.formatCurrency(receipt.amountPaid)} · Balance ${FormatUtils.formatCurrency(receipt.remainingBalance)}"
                updateActionState(messageId, ProposalExecutionState.Completed(summary))
                onReceiptIssued(receipt)
            } catch (e: Exception) {
                updateActionState(messageId, ProposalExecutionState.Failed(e.message ?: "Failed to record payment"))
            }
        }
    }

    fun executeMarkAttendance(
        messageId: String,
        proposal: AssistantAction.MarkAttendanceProposal
    ) {
        updateActionState(messageId, ProposalExecutionState.Executing)
        viewModelScope.launch {
            try {
                val existingDraft = repo.getAttendanceDraftForClassAndDate(proposal.classId, proposal.dateKey)
                    .toMap()
                    .toMutableMap()
                val student = students.value.find { it.id == proposal.studentId }
                    ?: throw IllegalStateException("Student not found")
                existingDraft[student] = proposal.status
                val statusMap = existingDraft.entries.associate { it.key.id to it.value }
                repo.saveClassAttendance(proposal.classId, proposal.dateKey, statusMap)
                updateActionState(
                    messageId,
                    ProposalExecutionState.Completed(
                        "Attendance saved: ${proposal.studentName} marked ${proposal.status} (${FormatUtils.formatDateKey(proposal.dateKey)})"
                    )
                )
            } catch (e: Exception) {
                updateActionState(messageId, ProposalExecutionState.Failed(e.message ?: "Failed to save attendance"))
            }
        }
    }

    fun executeSaveExamMarks(
        messageId: String,
        studentId: String,
        studentName: String,
        examId: String,
        examName: String,
        subject: Subject,
        obtainedMarks: Double
    ) {
        if (!authSession.value.isAdmin) {
            updateActionState(messageId, ProposalExecutionState.Failed("Only Admin can save exam marks."))
            return
        }
        updateActionState(messageId, ProposalExecutionState.Executing)
        viewModelScope.launch {
            try {
                val clamped = obtainedMarks.coerceIn(0.0, subject.totalMarks.toDouble())
                repo.saveExamMarksForSubject(
                    examId = examId,
                    subject = subject,
                    marksByStudent = mapOf(studentId to clamped)
                )
                updateActionState(
                    messageId,
                    ProposalExecutionState.Completed(
                        "Saved ${FormatUtils.formatMarks(clamped)} / ${subject.totalMarks} for $studentName ($examName · ${subject.name})"
                    )
                )
            } catch (e: Exception) {
                updateActionState(messageId, ProposalExecutionState.Failed(e.message ?: "Failed to save marks"))
            }
        }
    }

    suspend fun buildWhatsAppTarget(proposal: AssistantAction.SendWhatsAppProposal): Pair<Student, ParentMessageRequest>? {
        val st = students.value.find { it.id == proposal.studentId } ?: return null
        val clsName = classes.value.find { it.id == st.classId }?.name ?: ""
        val charges = repo.observeStudentFeeCharges(st.id).first()
        val tuitionDue = charges.filter { it.ledgerType == FeeLedgerType.TUITION }.sumOf { it.remainingAmount }
        val paperDue = charges.filter { it.ledgerType == FeeLedgerType.PAPER_FEE }.sumOf { it.remainingAmount }
        val otherDue = charges.filter { it.ledgerType == FeeLedgerType.OTHER }.sumOf { it.remainingAmount }

        val academyName = profile.value.academyName
        val req = when (proposal.messageType) {
            WhatsAppMessageType.ATTENDANCE_ALERT -> ParentMessageRequest(
                type = WhatsAppMessageType.ATTENDANCE_ALERT,
                academyName = academyName,
                studentName = st.name,
                className = clsName,
                dateLabel = FormatUtils.formatDateKey(FormatUtils.currentDateKey()),
                attendanceStatus = AttendanceStatus.ABSENT
            )
            WhatsAppMessageType.EXAM_RESULT -> {
                val latestExam = exams.value.firstOrNull { it.classId == st.classId }
                ParentMessageRequest(
                    type = WhatsAppMessageType.EXAM_RESULT,
                    academyName = academyName,
                    studentName = st.name,
                    className = clsName,
                    subjectOrExamTitle = latestExam?.name ?: "Class Test",
                    dateLabel = FormatUtils.formatDate(latestExam?.examDate ?: System.currentTimeMillis()),
                    obtainedMarks = "-",
                    totalMarks = "-",
                    percentageLabel = "Report"
                )
            }
            WhatsAppMessageType.FEE_REMINDER -> ParentMessageRequest(
                type = WhatsAppMessageType.FEE_REMINDER,
                academyName = academyName,
                studentName = st.name,
                className = clsName,
                tuitionPending = tuitionDue,
                paperFeePending = paperDue,
                otherFeePending = otherDue
            )
        }
        return st to req
    }

    private fun updateActionState(messageId: String, state: ProposalExecutionState) {
        _messages.update { list ->
            list.map { msg ->
                if (msg.id == messageId) msg.copy(actionState = state) else msg
            }
        }
    }

    private suspend fun buildLiveSnapshot(): AcademyContextSnapshot {
        val prof = repo.profileFlow.first()
        val session = prefs.getAuthSessionOnce()
        val allClasses = repo.classesFlow.first()
        val classMap = allClasses.associateBy { it.id }
        val allSubjects = repo.subjectsFlow.first()
        val allExams = repo.examsFlow.first()
        val activeStudents = repo.studentsFlow.first().filter { it.status == StudentStatus.ACTIVE }
        val allCharges = repo.feeChargesFlow.first()
        val todayKey = FormatUtils.currentDateKey()
        val todayAttendance = repo.attendanceFlow.first().filter { it.dateKey == todayKey }

        val studentSnapshots = activeStudents.map { st ->
            val stCharges = allCharges.filter { it.studentId == st.id && it.remainingAmount > 0L }
            AcademyStudentSnapshot(
                student = st,
                className = classMap[st.classId]?.name ?: "",
                tuitionPending = stCharges.filter { it.ledgerType == FeeLedgerType.TUITION }.sumOf { it.remainingAmount },
                paperFeePending = stCharges.filter { it.ledgerType == FeeLedgerType.PAPER_FEE }.sumOf { it.remainingAmount },
                otherFeePending = stCharges.filter { it.ledgerType == FeeLedgerType.OTHER }.sumOf { it.remainingAmount },
                openCharges = stCharges
            )
        }

        return AcademyContextSnapshot(
            academyName = prof.academyName,
            isAdmin = session.isAdmin,
            todayDateKey = todayKey,
            classes = allClasses,
            subjects = allSubjects,
            exams = allExams,
            students = studentSnapshots,
            todayAbsentStudentIds = todayAttendance.filter { it.status == AttendanceStatus.ABSENT }.map { it.studentId }.toSet(),
            todayPresentCount = todayAttendance.count { it.status == AttendanceStatus.PRESENT },
            todayAbsentCount = todayAttendance.count { it.status == AttendanceStatus.ABSENT },
            todayLeaveCount = todayAttendance.count { it.status == AttendanceStatus.LEAVE }
        )
    }
}
