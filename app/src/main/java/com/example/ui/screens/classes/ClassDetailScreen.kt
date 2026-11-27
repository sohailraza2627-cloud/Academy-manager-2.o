package com.example.ui.screens.classes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ai.AiTextResult
import com.example.data.ai.ClassSummaryRequest
import com.example.data.ai.LocalTemplateAiEngine
import com.example.data.local.*
import com.example.data.repository.StudentExamResult
import com.example.ui.components.AcademySummaryCard
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GoogleCard
import com.example.util.FormatUtils
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailScreen(
    academyClass: AcademyClass?,
    subjects: List<Subject>,
    timetable: List<TimetableEntry>,
    classStudents: List<Student> = emptyList(),
    attendanceRecords: List<AttendanceRecord> = emptyList(),
    feeCharges: List<FeeCharge> = emptyList(),
    exams: List<Exam> = emptyList(),
    examMarks: List<ExamMark> = emptyList(),
    profile: AcademyProfile = AcademyProfile(),
    aiEnabled: Boolean = false,
    computeExamResults: (List<Student>, List<Subject>, List<ExamMark>) -> List<StudentExamResult> = { _, _, _ -> emptyList() },
    onSummarizeClass: suspend (ClassSummaryRequest, Boolean) -> AiTextResult = { req, _ ->
        LocalTemplateAiEngine().summarizeClass(req)
    },
    isAdmin: Boolean = true,
    onBack: () -> Unit,
    onSaveSubject: (Subject) -> Unit,
    onDeleteSubject: (Subject) -> Unit,
    onSaveTimetable: (TimetableEntry) -> Unit,
    onDeleteTimetable: (TimetableEntry) -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedDay by rememberSaveable { mutableIntStateOf(1) } // 1=Mon..6=Sat
    var showAddSubject by rememberSaveable { mutableStateOf(false) }
    var showAddTimetable by rememberSaveable { mutableStateOf(false) }

    val dayLabels = listOf(
        1 to stringResource(R.string.day_mon),
        2 to stringResource(R.string.day_tue),
        3 to stringResource(R.string.day_wed),
        4 to stringResource(R.string.day_thu),
        5 to stringResource(R.string.day_fri),
        6 to stringResource(R.string.day_sat)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(academyClass?.name ?: stringResource(R.string.classes_title))
                        if (academyClass != null) {
                            Text(
                                "Fee: ${FormatUtils.formatCurrency(academyClass.monthlyFee)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            if (isAdmin && academyClass != null) {
                FloatingActionButton(
                    onClick = {
                        if (selectedTab == 0) showAddSubject = true else showAddTimetable = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tab_subjects)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.tab_timetable)) }
                )
            }

            if (selectedTab == 0) {
                val activeCount = remember(classStudents) { classStudents.count { it.status == StudentStatus.ACTIVE } }
                val leftCount = remember(classStudents) { classStudents.count { it.status == StudentStatus.LEFT } }
                val thirtyDaysAgo = remember { System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L }
                val cutoffKey = remember(thirtyDaysAgo) { FormatUtils.toDateKey(thirtyDaysAgo) }
                val recentAtt = remember(attendanceRecords, cutoffKey) {
                    attendanceRecords.filter { it.dateKey >= cutoffKey }
                }
                val latestExam = remember(exams) { exams.maxByOrNull { it.examDate } }
                val latestAvg = remember(latestExam, classStudents, subjects, examMarks) {
                    if (latestExam == null) null
                    else {
                        val marks = examMarks.filter { it.examId == latestExam.id }
                        val ranked = computeExamResults(classStudents, subjects, marks)
                        if (ranked.isEmpty()) null else ranked.map { it.percentage }.average()
                    }
                }
                val summaryRequest = remember(academyClass, activeCount, leftCount, recentAtt, feeCharges, latestExam, latestAvg, profile.academyName) {
                    ClassSummaryRequest(
                        academyName = profile.academyName,
                        className = academyClass?.name ?: "",
                        activeStudentsCount = activeCount,
                        leftStudentsCount = leftCount,
                        presentCountLast30Days = recentAtt.count { it.status == AttendanceStatus.PRESENT },
                        absentCountLast30Days = recentAtt.count { it.status == AttendanceStatus.ABSENT },
                        leaveCountLast30Days = recentAtt.count { it.status == AttendanceStatus.LEAVE },
                        tuitionPendingTotal = feeCharges.filter { it.ledgerType == FeeLedgerType.TUITION }.sumOf { it.remainingAmount },
                        paperFeePendingTotal = feeCharges.filter { it.ledgerType == FeeLedgerType.PAPER_FEE }.sumOf { it.remainingAmount },
                        latestExamTitle = latestExam?.name,
                        latestExamAvgPercent = latestAvg
                    )
                }
                val localSummary = remember(summaryRequest) {
                    (runBlocking { LocalTemplateAiEngine().summarizeClass(summaryRequest) } as AiTextResult.Success).text
                }

                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (academyClass != null && classStudents.isNotEmpty()) {
                        item {
                            AcademySummaryCard(
                                title = stringResource(R.string.class_overview_title),
                                localSummaryText = localSummary,
                                aiEnabled = aiEnabled,
                                onGenerateAiSummary = { onSummarizeClass(summaryRequest, true) }
                            )
                        }
                    }
                    if (subjects.isEmpty()) {
                        item {
                            EmptyStateView(
                                title = stringResource(R.string.subjects_empty),
                                icon = Icons.Default.Book
                            )
                        }
                    } else {
                        items(subjects, key = { it.id }) { subject ->
                            GoogleCard(contentPadding = PaddingValues(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(subject.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                        Text(
                                            "Total Marks: ${subject.totalMarks}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isAdmin) {
                                        IconButton(onClick = { onDeleteSubject(subject) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dayLabels, key = { it.first }) { (dayInt, label) ->
                        FilterChip(
                            selected = selectedDay == dayInt,
                            onClick = { selectedDay = dayInt },
                            label = { Text(label) }
                        )
                    }
                }

                val dayEntries = remember(timetable, selectedDay) {
                    timetable.filter { it.dayOfWeek == selectedDay }
                }
                if (dayEntries.isEmpty()) {
                    EmptyStateView(
                        title = stringResource(R.string.timetable_empty),
                        icon = Icons.Default.Schedule
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(dayEntries, key = { it.id }) { entry ->
                            GoogleCard(contentPadding = PaddingValues(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "${entry.startTime} – ${entry.endTime}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(entry.subjectName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                        if (entry.teacherName.isNotBlank()) {
                                            Text(
                                                entry.teacherName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (isAdmin) {
                                        IconButton(onClick = { onDeleteTimetable(entry) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSubject && academyClass != null) {
        AddSubjectDialog(
            classId = academyClass.id,
            onDismiss = { showAddSubject = false },
            onSave = {
                onSaveSubject(it)
                showAddSubject = false
            }
        )
    }

    if (showAddTimetable && academyClass != null) {
        AddTimetableDialog(
            classId = academyClass.id,
            dayOfWeek = selectedDay,
            subjects = subjects,
            onDismiss = { showAddTimetable = false },
            onSave = {
                onSaveTimetable(it)
                showAddTimetable = false
            }
        )
    }
}

@Composable
private fun AddSubjectDialog(
    classId: String,
    onDismiss: () -> Unit,
    onSave: (Subject) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var marksStr by rememberSaveable { mutableStateOf("100") }
    var nameErr by rememberSaveable { mutableStateOf<String?>(null) }
    var marksErr by rememberSaveable { mutableStateOf<String?>(null) }

    val requiredMsg = stringResource(R.string.field_required)
    val invalidAmountMsg = stringResource(R.string.invalid_amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_subject)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameErr = null },
                    label = { Text(stringResource(R.string.subject_name)) },
                    isError = nameErr != null,
                    supportingText = nameErr?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = marksStr,
                    onValueChange = { marksStr = it; marksErr = null },
                    label = { Text(stringResource(R.string.total_marks)) },
                    isError = marksErr != null,
                    supportingText = marksErr?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    var valid = true
                    if (name.isBlank()) { nameErr = requiredMsg; valid = false }
                    val marks = marksStr.trim().toIntOrNull()
                    if (marks == null || marks <= 0) { marksErr = invalidAmountMsg; valid = false }
                    if (valid && marks != null) {
                        onSave(Subject(classId = classId, name = name.trim(), totalMarks = marks))
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun AddTimetableDialog(
    classId: String,
    dayOfWeek: Int,
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSave: (TimetableEntry) -> Unit
) {
    var subjectName by rememberSaveable { mutableStateOf(subjects.firstOrNull()?.name ?: "") }
    var teacherName by rememberSaveable { mutableStateOf("") }
    var startTime by rememberSaveable { mutableStateOf("09:00 AM") }
    var endTime by rememberSaveable { mutableStateOf("09:45 AM") }
    var subjectErr by rememberSaveable { mutableStateOf<String?>(null) }

    val requiredMsg = stringResource(R.string.field_required)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_timetable_slot)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (subjects.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(subjects, key = { it.id }) { sub ->
                            AssistChip(
                                onClick = { subjectName = sub.name; subjectErr = null },
                                label = { Text(sub.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = subjectName,
                    onValueChange = { subjectName = it; subjectErr = null },
                    label = { Text(stringResource(R.string.subject_name)) },
                    isError = subjectErr != null,
                    supportingText = subjectErr?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = teacherName,
                    onValueChange = { teacherName = it },
                    label = { Text(stringResource(R.string.teacher_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text(stringResource(R.string.start_time)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text(stringResource(R.string.end_time)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (subjectName.isBlank() || startTime.isBlank() || endTime.isBlank()) {
                        subjectErr = requiredMsg
                    } else {
                        onSave(
                            TimetableEntry(
                                classId = classId,
                                dayOfWeek = dayOfWeek,
                                subjectName = subjectName.trim(),
                                teacherName = teacherName.trim(),
                                startTime = startTime.trim(),
                                endTime = endTime.trim()
                            )
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
