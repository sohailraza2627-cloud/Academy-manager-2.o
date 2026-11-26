package com.example.ui.screens.exams

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Grading
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AcademyClass
import com.example.data.local.Exam
import com.example.ui.components.*
import com.example.ui.viewmodel.DataLoadState
import com.example.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    examsState: DataLoadState<List<Exam>>,
    classes: List<AcademyClass>,
    onBack: () -> Unit,
    onExamClick: (Long) -> Unit,
    onSaveExam: (Exam) -> Unit,
    onDeleteExam: (Exam) -> Unit
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var deleteExamId by rememberSaveable { mutableStateOf<Long?>(null) }

    val classMap = remember(classes) { classes.associateBy { it.id } }
    val allExams = (examsState as? DataLoadState.Success)?.data ?: emptyList()
    val deleteExam = remember(deleteExamId, allExams) { allExams.find { it.id == deleteExamId } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exams_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("create_exam_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_exam))
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (examsState) {
                is DataLoadState.Loading -> LoadingStateView()
                is DataLoadState.Error -> ErrorStateView(examsState.message)
                is DataLoadState.Success -> {
                    if (examsState.data.isEmpty()) {
                        EmptyStateView(
                            title = stringResource(R.string.exams_empty),
                            icon = Icons.Default.Grading
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(examsState.data, key = { it.id }) { exam ->
                                GoogleCard(
                                    onClick = { onExamClick(exam.id) },
                                    contentPadding = PaddingValues(14.dp),
                                    modifier = Modifier.testTag("exam_card_${exam.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(exam.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${classMap[exam.classId]?.name ?: ""} · ${FormatUtils.formatDate(exam.examDate)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { deleteExamId = exam.id }) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = stringResource(R.string.delete),
                                                tint = MaterialTheme.colorScheme.error
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

    if (showCreateDialog) {
        CreateExamDialog(
            classes = classes,
            onDismiss = { showCreateDialog = false },
            onSave = {
                onSaveExam(it)
                showCreateDialog = false
            }
        )
    }

    if (deleteExam != null) {
        AlertDialog(
            onDismissRequest = { deleteExamId = null },
            title = { Text(stringResource(R.string.delete_exam_title)) },
            text = { Text(stringResource(R.string.delete_exam_warning, deleteExam.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteExam(deleteExam)
                        deleteExamId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteExamId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateExamDialog(
    classes: List<AcademyClass>,
    onDismiss: () -> Unit,
    onSave: (Exam) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var classId by rememberSaveable { mutableStateOf(classes.firstOrNull()?.id ?: 0L) }
    var examDate by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var classExpanded by remember { mutableStateOf(false) }
    var nameErr by rememberSaveable { mutableStateOf<String?>(null) }

    val requiredMsg = stringResource(R.string.field_required)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_exam)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (classes.isEmpty()) {
                    Text(stringResource(R.string.no_classes_prompt), color = MaterialTheme.colorScheme.error)
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; nameErr = null },
                        label = { Text(stringResource(R.string.exam_name)) },
                        isError = nameErr != null,
                        supportingText = nameErr?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val selectedClass = classes.find { it.id == classId }
                    ExposedDropdownMenuBox(
                        expanded = classExpanded,
                        onExpandedChange = { classExpanded = !classExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedClass?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.select_class)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = classExpanded,
                            onDismissRequest = { classExpanded = false }
                        ) {
                            classes.forEach { cls ->
                                DropdownMenuItem(
                                    text = { Text(cls.name) },
                                    onClick = {
                                        classId = cls.id
                                        classExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    DatePickerField(
                        label = stringResource(R.string.exam_date),
                        dateMillis = examDate,
                        onDateSelected = { examDate = it }
                    )
                }
            }
        },
        confirmButton = {
            if (classes.isNotEmpty()) {
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            nameErr = requiredMsg
                        } else if (classId != 0L) {
                            onSave(Exam(name = name.trim(), classId = classId, examDate = examDate))
                        }
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
