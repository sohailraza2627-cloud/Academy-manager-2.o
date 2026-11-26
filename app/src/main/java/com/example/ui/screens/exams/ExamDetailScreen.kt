package com.example.ui.screens.exams

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.*
import com.example.data.repository.StudentExamResult
import com.example.ui.components.*
import com.example.util.FormatUtils
import com.example.util.PdfAndCsvExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamDetailScreen(
    exam: Exam?,
    academyClass: AcademyClass?,
    classStudents: List<Student>,
    classSubjects: List<Subject>,
    examMarks: List<ExamMark>,
    profile: AcademyProfile,
    computeExamResults: (List<Student>, List<Subject>, List<ExamMark>) -> List<StudentExamResult>,
    onBack: () -> Unit,
    onSaveMarks: (examId: Long, subject: Subject, marksByStudent: Map<Long, Double>) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedSubjectId by rememberSaveable { mutableStateOf(classSubjects.firstOrNull()?.id ?: 0L) }
    var subjectExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(classSubjects) {
        if (selectedSubjectId == 0L && classSubjects.isNotEmpty()) {
            selectedSubjectId = classSubjects.first().id
        }
    }

    val selectedSubject = remember(classSubjects, selectedSubjectId) {
        classSubjects.find { it.id == selectedSubjectId } ?: classSubjects.firstOrNull()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(exam?.name ?: stringResource(R.string.exams_title))
                        if (academyClass != null && exam != null) {
                            Text(
                                "${academyClass.name} · ${FormatUtils.formatDate(exam.examDate)}",
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
        }
    ) { innerPadding ->
        if (exam == null) {
            LoadingStateView(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.enter_marks)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.results_and_ranks)) }
                )
            }

            if (selectedTab == 0) {
                if (classSubjects.isEmpty()) {
                    EmptyStateView(title = stringResource(R.string.no_subjects_for_exam))
                } else if (classStudents.isEmpty()) {
                    EmptyStateView(title = stringResource(R.string.attendance_empty_students))
                } else if (selectedSubject != null) {
                    val existingMap = remember(examMarks, selectedSubject.id) {
                        examMarks.filter { it.subjectId == selectedSubject.id }
                            .associate { it.studentId to FormatUtils.formatMarks(it.marksObtained) }
                    }
                    val draftInputs = remember(selectedSubject.id, existingMap, classStudents) {
                        mutableStateMapOf<Long, String>().apply {
                            classStudents.forEach { st ->
                                put(st.id, existingMap[st.id] ?: "")
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = subjectExpanded,
                            onExpandedChange = { subjectExpanded = !subjectExpanded }
                        ) {
                            OutlinedTextField(
                                value = "${selectedSubject.name} (Max ${selectedSubject.totalMarks})",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.select_subject)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = subjectExpanded,
                                onDismissRequest = { subjectExpanded = false }
                            ) {
                                classSubjects.forEach { sub ->
                                    DropdownMenuItem(
                                        text = { Text("${sub.name} (${sub.totalMarks})") },
                                        onClick = {
                                            selectedSubjectId = sub.id
                                            subjectExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(classStudents, key = { it.id }) { student ->
                                val txt = draftInputs[student.id] ?: ""
                                val parsed = txt.trim().toDoubleOrNull()
                                val invalid = txt.isNotBlank() && (parsed == null || parsed < 0.0 || parsed > selectedSubject.totalMarks)
                                GoogleCard(contentPadding = PaddingValues(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        ColoredInitialAvatar(student.name, student.photoPath, size = 38.dp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(student.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                            Text(stringResource(R.string.roll_prefix, student.rollNo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        OutlinedTextField(
                                            value = txt,
                                            onValueChange = { draftInputs[student.id] = it },
                                            label = { Text("/ ${selectedSubject.totalMarks}") },
                                            isError = invalid,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.width(108.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val validMap = mutableMapOf<Long, Double>()
                                classStudents.forEach { st ->
                                    val value = draftInputs[st.id]?.trim()?.toDoubleOrNull()
                                    if (value != null && value in 0.0..selectedSubject.totalMarks.toDouble()) {
                                        validMap[st.id] = value
                                    }
                                }
                                onSaveMarks(exam.id, selectedSubject, validMap)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("save_marks_btn")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            } else {
                // Results & Ranks
                val results = remember(classStudents, classSubjects, examMarks) {
                    computeExamResults(classStudents, classSubjects, examMarks)
                }
                if (results.isEmpty()) {
                    EmptyStateView(title = stringResource(R.string.no_data))
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(results, key = { it.student.id }) { res ->
                            GoogleCard(contentPadding = PaddingValues(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ColoredInitialAvatar(res.student.name, res.student.photoPath, size = 42.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(res.student.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            StatusBadge(
                                                text = stringResource(R.string.rank_label, res.rank),
                                                tone = BadgeTone.BLUE
                                            )
                                            StatusBadge(
                                                text = res.grade,
                                                tone = if (res.grade == "F") BadgeTone.RED else BadgeTone.GREEN
                                            )
                                        }
                                        Text(
                                            "Marks: ${FormatUtils.formatMarks(res.obtainedTotal)} / ${res.maxTotal} (${FormatUtils.formatPercent(res.percentage)})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val pdf = PdfAndCsvExporter.generateResultCardPdf(
                                                context = context,
                                                profile = profile,
                                                examName = exam.name,
                                                examDate = exam.examDate,
                                                className = academyClass?.name ?: "",
                                                result = res
                                            )
                                            PdfAndCsvExporter.shareFile(
                                                context,
                                                pdf,
                                                "application/pdf",
                                                context.getString(R.string.export_result_card)
                                            )
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.PictureAsPdf,
                                            contentDescription = stringResource(R.string.export_result_card),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
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
