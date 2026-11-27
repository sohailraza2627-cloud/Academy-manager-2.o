package com.example.ui.screens.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
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
import com.example.data.local.*
import com.example.data.repository.StudentExamResult
import com.example.ui.components.*
import com.example.util.FormatUtils
import com.example.util.PdfAndCsvExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    profile: AcademyProfile,
    classes: List<AcademyClass>,
    subjects: List<Subject>,
    students: List<Student>,
    attendanceRecords: List<AttendanceRecord>,
    feeCharges: List<FeeCharge>,
    feePayments: List<FeePayment>,
    ledgerEntries: List<LedgerEntry>,
    exams: List<Exam>,
    examMarks: List<ExamMark>,
    computeExamResults: (List<Student>, List<Subject>, List<ExamMark>) -> List<StudentExamResult>,
    onBack: () -> Unit,
    onShowError: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedReportIndex by rememberSaveable { mutableIntStateOf(0) }
    var monthKey by rememberSaveable { mutableStateOf(FormatUtils.currentMonthKey()) }
    var selectedClassId by rememberSaveable { mutableStateOf(classes.firstOrNull()?.id ?: "") }
    var selectedExamId by rememberSaveable { mutableStateOf(exams.firstOrNull()?.id ?: "") }

    LaunchedEffect(classes) {
        if (selectedClassId.isBlank() && classes.isNotEmpty()) selectedClassId = classes.first().id
    }
    LaunchedEffect(exams) {
        if (selectedExamId.isBlank() && exams.isNotEmpty()) selectedExamId = exams.first().id
    }

    val studentMap = remember(students) { students.associateBy { it.id } }
    val classMap = remember(classes) { classes.associateBy { it.id } }

    val reportTitles = listOf(
        stringResource(R.string.report_attendance),
        stringResource(R.string.report_tuition),
        stringResource(R.string.report_paper_fee),
        stringResource(R.string.report_defaulters),
        stringResource(R.string.report_results),
        stringResource(R.string.report_ledger)
    )

    // Compute table data for selected report
    val reportData = remember(
        selectedReportIndex, monthKey, selectedClassId, selectedExamId,
        students, classes, subjects, attendanceRecords, feeCharges, feePayments, ledgerEntries, exams, examMarks
    ) {
        buildReportPayload(
            reportIndex = selectedReportIndex,
            monthKey = monthKey,
            selectedClassId = selectedClassId,
            selectedExamId = selectedExamId,
            studentMap = studentMap,
            classMap = classMap,
            students = students,
            subjects = subjects,
            attendanceRecords = attendanceRecords,
            feeCharges = feeCharges,
            feePayments = feePayments,
            ledgerEntries = ledgerEntries,
            exams = exams,
            examMarks = examMarks,
            computeExamResults = computeExamResults
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reports_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(reportTitles.indices.toList()) { idx ->
                    FilterChip(
                        selected = selectedReportIndex == idx,
                        onClick = { selectedReportIndex = idx },
                        label = { Text(reportTitles[idx]) },
                        modifier = Modifier.testTag("report_chip_$idx")
                    )
                }
            }

            // Contextual Filters
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedReportIndex in listOf(0, 1, 5)) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MonthSwitcher(monthKey = monthKey, onMonthChange = { monthKey = it })
                    }
                }
                if (selectedReportIndex == 0 && classes.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(classes, key = { it.id }) { cls ->
                            FilterChip(
                                selected = selectedClassId == cls.id,
                                onClick = { selectedClassId = cls.id },
                                label = { Text(cls.name) }
                            )
                        }
                    }
                }
                if (selectedReportIndex == 4 && exams.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(exams, key = { it.id }) { ex ->
                            FilterChip(
                                selected = selectedExamId == ex.id,
                                onClick = { selectedExamId = ex.id },
                                label = { Text("${ex.name} (${classMap[ex.classId]?.name ?: ""})") }
                            )
                        }
                    }
                }

                // Export Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            runCatching {
                                val file = PdfAndCsvExporter.generateTableReportPdf(
                                    context = context,
                                    profile = profile,
                                    reportTitle = reportTitles[selectedReportIndex],
                                    subtitle = reportData.subtitle,
                                    headers = reportData.headers,
                                    rows = reportData.rows,
                                    summaryLines = reportData.summaryLines,
                                    fileNamePrefix = reportData.filePrefix
                                )
                                PdfAndCsvExporter.shareFile(
                                    context,
                                    file,
                                    "application/pdf",
                                    reportTitles[selectedReportIndex]
                                )
                            }.onFailure {
                                onShowError(context.getString(R.string.export_failed))
                            }
                        },
                        enabled = reportData.rows.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("export_pdf_btn")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.export_pdf))
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val file = PdfAndCsvExporter.generateCsvFile(
                                    context = context,
                                    headers = reportData.headers,
                                    rows = reportData.rows,
                                    fileNamePrefix = reportData.filePrefix
                                )
                                PdfAndCsvExporter.shareFile(
                                    context,
                                    file,
                                    "text/csv",
                                    reportTitles[selectedReportIndex]
                                )
                            }.onFailure {
                                onShowError(context.getString(R.string.export_failed))
                            }
                        },
                        enabled = reportData.rows.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("export_csv_btn")
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.export_csv))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (reportData.rows.isEmpty()) {
                EmptyStateView(title = stringResource(R.string.no_data))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (reportData.summaryLines.isNotEmpty()) {
                        item {
                            GoogleCard(contentPadding = PaddingValues(14.dp)) {
                                reportData.summaryLines.forEach { line ->
                                    Text(line, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    items(reportData.rows) { row ->
                        GoogleCard(contentPadding = PaddingValues(12.dp)) {
                            row.forEachIndexed { idx, cell ->
                                val header = reportData.headers.getOrElse(idx) { "" }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(header, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(cell, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ReportPayload(
    val subtitle: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val summaryLines: List<String>,
    val filePrefix: String
)

private fun buildReportPayload(
    reportIndex: Int,
    monthKey: String,
    selectedClassId: String,
    selectedExamId: String,
    studentMap: Map<String, Student>,
    classMap: Map<String, AcademyClass>,
    students: List<Student>,
    subjects: List<Subject>,
    attendanceRecords: List<AttendanceRecord>,
    feeCharges: List<FeeCharge>,
    feePayments: List<FeePayment>,
    ledgerEntries: List<LedgerEntry>,
    exams: List<Exam>,
    examMarks: List<ExamMark>,
    computeExamResults: (List<Student>, List<Subject>, List<ExamMark>) -> List<StudentExamResult>
): ReportPayload {
    return when (reportIndex) {
        0 -> {
            // Attendance
            val clsName = classMap[selectedClassId]?.name ?: ""
            val classStudents = students.filter { it.classId == selectedClassId && it.status == StudentStatus.ACTIVE }
            val byStudent = attendanceRecords
                .filter { it.classId == selectedClassId && it.monthKey == monthKey }
                .groupBy { it.studentId }
            val rows = classStudents.map { st ->
                val recs = byStudent[st.id].orEmpty()
                val p = recs.count { it.status == AttendanceStatus.PRESENT }
                val a = recs.count { it.status == AttendanceStatus.ABSENT }
                val l = recs.count { it.status == AttendanceStatus.LEAVE }
                val pct = if (recs.isEmpty()) "0%" else "${(p * 100.0 / recs.size).toInt()}%"
                listOf(st.rollNo, st.name, p.toString(), a.toString(), l.toString(), pct)
            }
            ReportPayload(
                subtitle = "$clsName · ${FormatUtils.formatMonthKey(monthKey)}",
                headers = listOf("Roll", "Student", "Present", "Absent", "Leave", "%"),
                rows = rows,
                summaryLines = listOf("Class: $clsName · Month: ${FormatUtils.formatMonthKey(monthKey)}"),
                filePrefix = "Attendance_${monthKey}"
            )
        }
        1 -> {
            // Tuition Fees
            val charges = feeCharges.filter { it.ledgerType == FeeLedgerType.TUITION && it.monthKey == monthKey }
            val rows = charges.map { c ->
                val st = studentMap[c.studentId]
                listOf(
                    st?.name ?: "Student",
                    classMap[c.classId]?.name ?: "",
                    FormatUtils.formatCurrency(c.netAmount),
                    FormatUtils.formatCurrency(c.paidAmount),
                    FormatUtils.formatCurrency(c.remainingAmount),
                    c.status
                )
            }
            val billed = charges.sumOf { it.netAmount }
            val collected = charges.sumOf { it.paidAmount }
            val pending = charges.sumOf { it.remainingAmount }
            ReportPayload(
                subtitle = FormatUtils.formatMonthKey(monthKey),
                headers = listOf("Student", "Class", "Net", "Paid", "Due", "Status"),
                rows = rows,
                summaryLines = listOf(
                    "Tuition Billed: ${FormatUtils.formatCurrency(billed)} · Collected: ${FormatUtils.formatCurrency(collected)} · Pending: ${FormatUtils.formatCurrency(pending)}"
                ),
                filePrefix = "Tuition_Report_${monthKey}"
            )
        }
        2 -> {
            // Paper Fees (Separate!)
            val charges = feeCharges.filter { it.ledgerType == FeeLedgerType.PAPER_FEE }
            val rows = charges.map { c ->
                val st = studentMap[c.studentId]
                listOf(
                    st?.name ?: "Student",
                    classMap[c.classId]?.name ?: "",
                    c.title,
                    FormatUtils.formatCurrency(c.netAmount),
                    FormatUtils.formatCurrency(c.paidAmount),
                    FormatUtils.formatCurrency(c.remainingAmount)
                )
            }
            val billed = charges.sumOf { it.netAmount }
            val collected = charges.sumOf { it.paidAmount }
            val pending = charges.sumOf { it.remainingAmount }
            ReportPayload(
                subtitle = "Paper Fee Ledger",
                headers = listOf("Student", "Class", "Paper/Exam", "Fee", "Paid", "Due"),
                rows = rows,
                summaryLines = listOf(
                    "Paper Fee Billed: ${FormatUtils.formatCurrency(billed)} · Collected: ${FormatUtils.formatCurrency(collected)} · Pending: ${FormatUtils.formatCurrency(pending)}"
                ),
                filePrefix = "Paper_Fee_Report"
            )
        }
        3 -> {
            // Defaulters (Tuition & Paper separate rows)
            val unpaid = feeCharges.filter { it.remainingAmount > 0L }
            val rows = unpaid.map { c ->
                val st = studentMap[c.studentId]
                val ledgerLabel = if (c.ledgerType == FeeLedgerType.PAPER_FEE) "Paper Fee" else if (c.ledgerType == FeeLedgerType.TUITION) "Tuition" else "Other"
                listOf(
                    st?.name ?: "Student",
                    st?.phone ?: "",
                    ledgerLabel,
                    c.title,
                    FormatUtils.formatCurrency(c.remainingAmount)
                )
            }
            val tuitionDue = unpaid.filter { it.ledgerType == FeeLedgerType.TUITION }.sumOf { it.remainingAmount }
            val paperDue = unpaid.filter { it.ledgerType == FeeLedgerType.PAPER_FEE }.sumOf { it.remainingAmount }
            val otherDue = unpaid.filter { it.ledgerType == FeeLedgerType.OTHER }.sumOf { it.remainingAmount }
            ReportPayload(
                subtitle = "All Unpaid Dues",
                headers = listOf("Student", "Phone", "Ledger", "Title", "Due"),
                rows = rows,
                summaryLines = listOf(
                    "Tuition Due: ${FormatUtils.formatCurrency(tuitionDue)} · Paper Fee Due: ${FormatUtils.formatCurrency(paperDue)} · Other Due: ${FormatUtils.formatCurrency(otherDue)}"
                ),
                filePrefix = "Defaulters_Report"
            )
        }
        4 -> {
            // Exam Results
            val exam = exams.find { it.id == selectedExamId }
            if (exam == null) {
                ReportPayload("No Exam", emptyList(), emptyList(), emptyList(), "Results")
            } else {
                val clsStudents = students.filter { it.classId == exam.classId }
                val clsSubjects = subjects.filter { it.classId == exam.classId }
                val marks = examMarks.filter { it.examId == exam.id }
                val results = computeExamResults(clsStudents, clsSubjects, marks)
                val rows = results.map { r ->
                    listOf(
                        "#${r.rank}",
                        r.student.rollNo,
                        r.student.name,
                        "${FormatUtils.formatMarks(r.obtainedTotal)}/${r.maxTotal}",
                        FormatUtils.formatPercent(r.percentage),
                        r.grade
                    )
                }
                ReportPayload(
                    subtitle = "${exam.name} (${classMap[exam.classId]?.name ?: ""})",
                    headers = listOf("Rank", "Roll", "Student", "Marks", "%", "Grade"),
                    rows = rows,
                    summaryLines = listOf("Exam: ${exam.name} · Students Graded: ${results.size}"),
                    filePrefix = "Exam_Results_${exam.id}"
                )
            }
        }
        else -> {
            // Income & Expense Ledger
            val monthEntries = ledgerEntries.filter { it.monthKey == monthKey }
            val tuitionAndOther = feePayments.filter { it.monthKey == monthKey && it.ledgerType != FeeLedgerType.PAPER_FEE }.sumOf { it.amountPaid }
            val paperFee = feePayments.filter { it.monthKey == monthKey && it.ledgerType == FeeLedgerType.PAPER_FEE }.sumOf { it.amountPaid }
            val manualIncome = monthEntries.filter { it.type == LedgerType.INCOME }.sumOf { it.amount }
            val totalExp = monthEntries.filter { it.type == LedgerType.EXPENSE }.sumOf { it.amount }
            val rows = mutableListOf<List<String>>()
            if (tuitionAndOther > 0L) {
                rows.add(listOf(FormatUtils.formatMonthKey(monthKey), "INCOME", "Tuition & Other Fees", FormatUtils.formatCurrency(tuitionAndOther)))
            }
            if (paperFee > 0L) {
                rows.add(listOf(FormatUtils.formatMonthKey(monthKey), "INCOME", "Paper Fee Collections", FormatUtils.formatCurrency(paperFee)))
            }
            monthEntries.forEach { e ->
                rows.add(listOf(FormatUtils.formatDate(e.date), e.type, "${e.category}: ${e.title}", FormatUtils.formatCurrency(e.amount)))
            }
            val totalIn = tuitionAndOther + paperFee + manualIncome
            ReportPayload(
                subtitle = FormatUtils.formatMonthKey(monthKey),
                headers = listOf("Date", "Type", "Description", "Amount"),
                rows = rows,
                summaryLines = listOf(
                    "Total Income: ${FormatUtils.formatCurrency(totalIn)} · Total Expense: ${FormatUtils.formatCurrency(totalExp)} · Net: ${FormatUtils.formatCurrency(totalIn - totalExp)}"
                ),
                filePrefix = "Ledger_${monthKey}"
            )
        }
    }
}
