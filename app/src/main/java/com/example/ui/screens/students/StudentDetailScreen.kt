package com.example.ui.screens.students

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ai.AiTextResult
import com.example.data.ai.LocalTemplateAiEngine
import com.example.data.ai.ParentMessageRequest
import com.example.data.ai.StudentSummaryRequest
import com.example.data.local.*
import com.example.data.repository.ReceiptResult
import com.example.data.repository.StudentExamResult
import com.example.ui.components.*
import com.example.util.FormatUtils
import com.example.util.PdfAndCsvExporter
import com.example.util.WhatsAppHelper
import com.example.util.WhatsAppMessageType
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    student: Student?,
    classes: List<AcademyClass>,
    subjects: List<Subject>,
    allStudents: List<Student>,
    exams: List<Exam>,
    allExamMarks: List<ExamMark>,
    attendanceRecords: List<AttendanceRecord>,
    feeCharges: List<FeeCharge>,
    feePayments: List<FeePayment>,
    profile: AcademyProfile,
    isAdmin: Boolean = true,
    aiEnabled: Boolean = false,
    onSummarizeStudent: suspend (StudentSummaryRequest, Boolean) -> AiTextResult = { req, _ ->
        LocalTemplateAiEngine().summarizeStudent(req)
    },
    onSendWhatsAppRequest: (Student, ParentMessageRequest) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    onSaveStudent: (Student) -> Unit,
    onSetStatus: (Student, String) -> Unit,
    onDeleteStudent: (Student) -> Unit,
    computeExamResults: (List<Student>, List<Subject>, List<ExamMark>) -> List<StudentExamResult>
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var selectedMonthKey by rememberSaveable { mutableStateOf(FormatUtils.currentMonthKey()) }

    val classObj = remember(student, classes) { classes.find { it.id == student?.classId } }
    val className = classObj?.name ?: ""

    val tuitionPending = remember(feeCharges) {
        feeCharges.filter { it.ledgerType == FeeLedgerType.TUITION }.sumOf { it.remainingAmount }
    }
    val paperPending = remember(feeCharges) {
        feeCharges.filter { it.ledgerType == FeeLedgerType.PAPER_FEE }.sumOf { it.remainingAmount }
    }
    val otherPending = remember(feeCharges) {
        feeCharges.filter { it.ledgerType == FeeLedgerType.OTHER }.sumOf { it.remainingAmount }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(student?.name ?: stringResource(R.string.students_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("student_detail_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (student != null && isAdmin) {
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.testTag("student_detail_edit")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_student))
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("student_detail_delete")
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.delete_student),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (student == null) {
            LoadingStateView(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }

        val triggerFeeReminder = {
            onSendWhatsAppRequest(
                student,
                ParentMessageRequest(
                    type = WhatsAppMessageType.FEE_REMINDER,
                    academyName = profile.academyName,
                    studentName = student.name,
                    className = className,
                    tuitionPending = tuitionPending,
                    paperFeePending = paperPending,
                    otherFeePending = otherPending
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val tabs = listOf(
                stringResource(R.string.tab_profile),
                stringResource(R.string.tab_attendance),
                stringResource(R.string.tab_fees),
                stringResource(R.string.tab_results)
            )
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, maxLines = 1) },
                        modifier = Modifier.testTag("student_tab_$index")
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    val classSubjects = remember(subjects, student.classId) {
                        subjects.filter { it.classId == student.classId }
                    }
                    val classStudents = remember(allStudents, student.classId) {
                        allStudents.filter { it.classId == student.classId }
                    }
                    val recentExams = remember(exams, allExamMarks, classStudents, classSubjects, student.id) {
                        exams.filter { it.classId == student.classId }
                            .sortedByDescending { it.examDate }
                            .mapNotNull { exam ->
                                val marks = allExamMarks.filter { it.examId == exam.id }
                                val mine = computeExamResults(classStudents, classSubjects, marks)
                                    .find { it.student.id == student.id }
                                mine?.let {
                                    "${exam.name}: ${FormatUtils.formatMarks(it.obtainedTotal)}/${it.maxTotal} (${FormatUtils.formatPercent(it.percentage)})"
                                }
                            }
                    }
                    val summaryRequest = remember(student, className, attendanceRecords, recentExams, tuitionPending, paperPending, otherPending, profile.academyName) {
                        StudentSummaryRequest(
                            academyName = profile.academyName,
                            studentName = student.name,
                            className = className,
                            status = student.status,
                            presentDays = attendanceRecords.count { it.status == AttendanceStatus.PRESENT },
                            absentDays = attendanceRecords.count { it.status == AttendanceStatus.ABSENT },
                            leaveDays = attendanceRecords.count { it.status == AttendanceStatus.LEAVE },
                            recentExamsSummary = recentExams,
                            tuitionPending = tuitionPending,
                            paperFeePending = paperPending,
                            otherFeePending = otherPending
                        )
                    }
                    ProfileTab(
                        student = student,
                        className = className,
                        monthlyClassFee = classObj?.monthlyFee ?: 0L,
                        tuitionPending = tuitionPending,
                        paperPending = paperPending,
                        otherPending = otherPending,
                        summaryRequest = summaryRequest,
                        aiEnabled = aiEnabled,
                        onSummarizeStudent = onSummarizeStudent,
                        onSendFeeReminder = triggerFeeReminder,
                        isAdmin = isAdmin,
                        onToggleStatus = {
                            val next = if (student.status == StudentStatus.LEFT) StudentStatus.ACTIVE else StudentStatus.LEFT
                            onSetStatus(student, next)
                        },
                        onDeleteClick = { showDeleteConfirm = true }
                    )
                }

                1 -> AttendanceTab(
                    records = attendanceRecords,
                    monthKey = selectedMonthKey,
                    onMonthChange = { selectedMonthKey = it }
                )

                2 -> FeesTab(
                    student = student,
                    className = className,
                    charges = feeCharges,
                    payments = feePayments,
                    tuitionPending = tuitionPending,
                    paperPending = paperPending,
                    otherPending = otherPending,
                    onSendFeeReminder = triggerFeeReminder,
                    onShareReceipt = { receipt ->
                        val pdf = PdfAndCsvExporter.generateReceiptPdf(context, profile, receipt)
                        PdfAndCsvExporter.shareFile(
                            context,
                            pdf,
                            "application/pdf",
                            context.getString(R.string.share_pdf_receipt)
                        )
                    }
                )

                3 -> ResultsTab(
                    student = student,
                    className = className,
                    exams = exams.filter { it.classId == student.classId },
                    allStudents = allStudents,
                    subjects = subjects,
                    allExamMarks = allExamMarks,
                    computeExamResults = computeExamResults,
                    onSendWhatsAppResult = { exam, result ->
                        onSendWhatsAppRequest(
                            student,
                            ParentMessageRequest(
                                type = WhatsAppMessageType.EXAM_RESULT,
                                academyName = profile.academyName,
                                studentName = student.name,
                                className = className,
                                subjectOrExamTitle = exam.name,
                                dateLabel = FormatUtils.formatDate(exam.examDate),
                                obtainedMarks = FormatUtils.formatMarks(result.obtainedTotal),
                                totalMarks = result.maxTotal.toString(),
                                percentageLabel = FormatUtils.formatPercent(result.percentage),
                                grade = result.grade
                            )
                        )
                    },
                    onExportPdf = { exam, result ->
                        val pdf = PdfAndCsvExporter.generateResultCardPdf(
                            context = context,
                            profile = profile,
                            examName = exam.name,
                            examDate = exam.examDate,
                            className = className,
                            result = result
                        )
                        PdfAndCsvExporter.shareFile(
                            context,
                            pdf,
                            "application/pdf",
                            context.getString(R.string.export_result_card)
                        )
                    }
                )
            }
        }
    }

    if (showEditDialog && student != null) {
        StudentFormDialog(
            initialStudent = student,
            classes = classes,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                onSaveStudent(updated)
                showEditDialog = false
            }
        )
    }

    if (showDeleteConfirm && student != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_student_title)) },
            text = { Text(stringResource(R.string.delete_student_warning, student.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteStudent(student)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ProfileTab(
    student: Student,
    className: String,
    monthlyClassFee: Long,
    tuitionPending: Long,
    paperPending: Long,
    otherPending: Long,
    summaryRequest: StudentSummaryRequest,
    aiEnabled: Boolean,
    onSummarizeStudent: suspend (StudentSummaryRequest, Boolean) -> AiTextResult,
    onSendFeeReminder: () -> Unit,
    isAdmin: Boolean,
    onToggleStatus: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val localSummary = remember(summaryRequest) {
        (runBlocking { LocalTemplateAiEngine().summarizeStudent(summaryRequest) } as AiTextResult.Success).text
    }
    val historyEntries = remember(student.whatsappHistory) {
        WhatsAppHelper.parseHistoryEntries(student.whatsappHistory)
    }
    val totalPending = tuitionPending + paperPending + otherPending

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            GoogleCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ColoredInitialAvatar(
                        name = student.name,
                        photoPath = student.photoPath,
                        size = 68.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = student.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${stringResource(R.string.roll_prefix, student.rollNo)} · $className",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        StatusBadge(
                            text = stringResource(
                                if (student.status == StudentStatus.ACTIVE) R.string.active else R.string.left
                            ),
                            tone = if (student.status == StudentStatus.ACTIVE) BadgeTone.GREEN else BadgeTone.RED
                        )
                    }
                }
            }
        }

        item {
            GoogleCard {
                DetailRow("Father Name", student.fatherName)
                DetailRow("Phone", student.phone)
                DetailRow(
                    stringResource(R.string.guardian_whatsapp_short),
                    student.guardianWhatsapp.ifBlank { "—" }
                )
                if (historyEntries.isNotEmpty()) {
                    DetailRow(
                        stringResource(R.string.whatsapp_previous_numbers),
                        historyEntries.joinToString("\n")
                    )
                }
                DetailRow("Address", student.address.ifBlank { "—" })
                DetailRow("Admission Date", FormatUtils.formatDate(student.admissionDate))
                DetailRow("Monthly Class Fee", FormatUtils.formatCurrency(monthlyClassFee))
                DetailRow("Monthly Discount", FormatUtils.formatCurrency(student.monthlyDiscount))
                DetailRow(
                    "Net Monthly Tuition",
                    FormatUtils.formatCurrency((monthlyClassFee - student.monthlyDiscount).coerceAtLeast(0L))
                )
                if (student.leftDate != null) {
                    DetailRow("Left Date", FormatUtils.formatDate(student.leftDate))
                }
            }
        }

        item {
            FilledTonalButton(
                onClick = onSendFeeReminder,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("profile_whatsapp_fee_btn")
            ) {
                Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (totalPending > 0L) {
                        stringResource(R.string.wa_send_fee_reminder_amount, FormatUtils.formatCurrency(totalPending))
                    } else {
                        stringResource(R.string.wa_send_fee_reminder)
                    }
                )
            }
        }

        item {
            AcademySummaryCard(
                title = stringResource(R.string.student_snapshot_title),
                localSummaryText = localSummary,
                aiEnabled = aiEnabled,
                onGenerateAiSummary = { onSummarizeStudent(summaryRequest, true) }
            )
        }

        if (isAdmin) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onToggleStatus,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("toggle_student_left_btn")
                    ) {
                        Text(
                            stringResource(
                                if (student.status == StudentStatus.LEFT) R.string.reactivate_student
                                else R.string.mark_as_left
                            )
                        )
                    }
                    OutlinedButton(
                        onClick = onDeleteClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.delete_student))
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceTab(
    records: List<AttendanceRecord>,
    monthKey: String,
    onMonthChange: (String) -> Unit
) {
    val monthRecords = remember(records, monthKey) {
        records.filter { it.monthKey == monthKey }.sortedByDescending { it.dateKey }
    }
    val presentCount = monthRecords.count { it.status == AttendanceStatus.PRESENT }
    val absentCount = monthRecords.count { it.status == AttendanceStatus.ABSENT }
    val leaveCount = monthRecords.count { it.status == AttendanceStatus.LEAVE }
    val pct = if (monthRecords.isEmpty()) 0 else (presentCount * 100.0 / monthRecords.size).toInt()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonthSwitcher(monthKey = monthKey, onMonthChange = onMonthChange)
                StatusBadge(text = "$pct%", tone = if (pct >= 75) BadgeTone.GREEN else BadgeTone.AMBER)
            }
        }
        item {
            GoogleCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(presentCount.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.present), style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(absentCount.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.absent), style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(leaveCount.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.leave), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (monthRecords.isEmpty()) {
            item { EmptyStateView(title = stringResource(R.string.no_data)) }
        } else {
            items(monthRecords, key = { it.id }) { rec ->
                GoogleCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(FormatUtils.formatDateKey(rec.dateKey), style = MaterialTheme.typography.bodyMedium)
                        val (label, tone) = when (rec.status) {
                            AttendanceStatus.PRESENT -> stringResource(R.string.present) to BadgeTone.GREEN
                            AttendanceStatus.ABSENT -> stringResource(R.string.absent) to BadgeTone.RED
                            else -> stringResource(R.string.leave) to BadgeTone.AMBER
                        }
                        StatusBadge(text = label, tone = tone)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeesTab(
    student: Student,
    className: String,
    charges: List<FeeCharge>,
    payments: List<FeePayment>,
    tuitionPending: Long,
    paperPending: Long,
    otherPending: Long,
    onSendFeeReminder: () -> Unit,
    onShareReceipt: (ReceiptResult) -> Unit
) {
    val tuitionCharges = remember(charges) { charges.filter { it.ledgerType == FeeLedgerType.TUITION } }
    val paperCharges = remember(charges) { charges.filter { it.ledgerType == FeeLedgerType.PAPER_FEE } }
    val otherCharges = remember(charges) { charges.filter { it.ledgerType == FeeLedgerType.OTHER } }
    val chargeMap = remember(charges) { charges.associateBy { it.id } }

    val groupedReceipts = remember(payments) {
        payments.groupBy { it.receiptNumber }.values.toList()
    }
    val totalPending = tuitionPending + paperPending + otherPending

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            FilledTonalButton(
                onClick = onSendFeeReminder,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("fees_tab_whatsapp_btn")
            ) {
                Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (totalPending > 0L) {
                        stringResource(R.string.wa_send_fee_reminder_amount, FormatUtils.formatCurrency(totalPending))
                    } else {
                        stringResource(R.string.wa_send_fee_reminder)
                    }
                )
            }
        }

        item {
            SectionHeader(title = stringResource(R.string.tab_tuition))
            if (tuitionCharges.isEmpty()) {
                Text(stringResource(R.string.no_data), style = MaterialTheme.typography.bodySmall)
            }
        }
        items(tuitionCharges, key = { it.id }) { c -> StudentFeeChargeCard(c) }

        item {
            SectionHeader(title = stringResource(R.string.tab_paper_fee))
            if (paperCharges.isEmpty()) {
                Text(stringResource(R.string.no_data), style = MaterialTheme.typography.bodySmall)
            }
        }
        items(paperCharges, key = { it.id }) { c -> StudentFeeChargeCard(c) }

        item {
            SectionHeader(title = stringResource(R.string.tab_other_fees))
            if (otherCharges.isEmpty()) {
                Text(stringResource(R.string.no_data), style = MaterialTheme.typography.bodySmall)
            }
        }
        items(otherCharges, key = { it.id }) { c -> StudentFeeChargeCard(c) }

        item {
            SectionHeader(title = stringResource(R.string.receipt_history))
            if (groupedReceipts.isEmpty()) {
                Text(stringResource(R.string.no_receipts), style = MaterialTheme.typography.bodySmall)
            }
        }
        items(groupedReceipts, key = { it.first().receiptNumber }) { lines ->
            val first = lines.first()
            val totalPaid = lines.sumOf { it.amountPaid }
            val totalDiscount = lines.sumOf { it.discountApplied }
            val titles = lines.mapNotNull { chargeMap[it.chargeId]?.title }.distinct().joinToString(", ")
            val ledgerRemaining = charges
                .filter { it.ledgerType == first.ledgerType }
                .sumOf { it.remainingAmount }

            GoogleCard(contentPadding = PaddingValues(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${first.receiptNumber} · ${FormatUtils.formatCurrency(totalPaid)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$titles · ${FormatUtils.formatDate(first.paidDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            onShareReceipt(
                                ReceiptResult(
                                    receiptNumber = first.receiptNumber,
                                    studentName = student.name,
                                    fatherName = student.fatherName,
                                    rollNo = student.rollNo,
                                    className = className,
                                    ledgerType = first.ledgerType,
                                    title = titles.ifBlank { first.ledgerType },
                                    amountPaid = totalPaid,
                                    discountApplied = totalDiscount,
                                    remainingBalance = ledgerRemaining,
                                    paidDate = first.paidDate
                                )
                            )
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_pdf_receipt))
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentFeeChargeCard(charge: FeeCharge) {
    val tone = when (charge.status) {
        FeeStatus.PAID -> BadgeTone.GREEN
        FeeStatus.PARTIAL -> BadgeTone.AMBER
        else -> BadgeTone.RED
    }
    val statusText = when (charge.status) {
        FeeStatus.PAID -> stringResource(R.string.status_paid)
        FeeStatus.PARTIAL -> stringResource(R.string.status_partial)
        else -> stringResource(R.string.status_unpaid)
    }
    GoogleCard(contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(charge.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(
                    "Net ${FormatUtils.formatCurrency(charge.netAmount)} · Paid ${FormatUtils.formatCurrency(charge.paidAmount)} · Due ${FormatUtils.formatCurrency(charge.remainingAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusBadge(text = statusText, tone = tone)
        }
    }
}

@Composable
private fun ResultsTab(
    student: Student,
    className: String,
    exams: List<Exam>,
    allStudents: List<Student>,
    subjects: List<Subject>,
    allExamMarks: List<ExamMark>,
    computeExamResults: (List<Student>, List<Subject>, List<ExamMark>) -> List<StudentExamResult>,
    onSendWhatsAppResult: (Exam, StudentExamResult) -> Unit,
    onExportPdf: (Exam, StudentExamResult) -> Unit
) {
    val classSubjects = remember(subjects, student.classId) {
        subjects.filter { it.classId == student.classId }
    }
    val classStudents = remember(allStudents, student.classId) {
        allStudents.filter { it.classId == student.classId }
    }

    val examItems = remember(exams, allExamMarks, classStudents, classSubjects) {
        exams.mapNotNull { exam ->
            val marks = allExamMarks.filter { it.examId == exam.id }
            val ranked = computeExamResults(classStudents, classSubjects, marks)
            val mine = ranked.find { it.student.id == student.id }
            mine?.let { exam to it }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (examItems.isEmpty()) {
            item { EmptyStateView(title = stringResource(R.string.no_data)) }
        } else {
            items(examItems, key = { it.first.id }) { (exam, result) ->
                GoogleCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(exam.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${FormatUtils.formatDate(exam.examDate)} · $className",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(
                            text = "${stringResource(R.string.grade_label, result.grade)} · ${stringResource(R.string.rank_label, result.rank)}",
                            tone = if (result.grade == "F") BadgeTone.RED else BadgeTone.BLUE
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Total: ${FormatUtils.formatMarks(result.obtainedTotal)} / ${result.maxTotal} (${FormatUtils.formatPercent(result.percentage)})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onExportPdf(exam, result) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.export_pdf), maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = { onSendWhatsAppResult(exam, result) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("result_whatsapp_${exam.id}")
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.wa_short_label), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
