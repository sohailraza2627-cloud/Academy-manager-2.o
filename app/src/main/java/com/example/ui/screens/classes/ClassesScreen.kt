package com.example.ui.screens.classes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AcademyClass
import com.example.data.local.Student
import com.example.data.local.StudentStatus
import com.example.data.local.Subject
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GoogleCard
import com.example.util.FormatUtils
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassesScreen(
    classes: List<AcademyClass>,
    students: List<Student>,
    subjects: List<Subject>,
    isAdmin: Boolean = true,
    onBack: () -> Unit,
    onClassClick: (String) -> Unit,
    onSaveClass: (AcademyClass) -> Unit,
    onDeleteClass: (AcademyClass) -> Unit
) {
    var showForm by rememberSaveable { mutableStateOf(false) }
    var editingClassId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteClassId by rememberSaveable { mutableStateOf<String?>(null) }

    val editingClass = remember(editingClassId, classes) { classes.find { it.id == editingClassId } }
    val deleteClass = remember(deleteClassId, classes) { classes.find { it.id == deleteClassId } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.classes_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = {
                        editingClassId = null
                        showForm = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_class_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_class))
                }
            }
        }
    ) { innerPadding ->
        if (classes.isEmpty()) {
            EmptyStateView(
                title = stringResource(R.string.classes_empty_title),
                subtitle = stringResource(R.string.classes_empty_subtitle),
                icon = Icons.Default.Class,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(classes, key = { it.id }) { cls ->
                    val studentCount = students.count { it.classId == cls.id && it.status == StudentStatus.ACTIVE }
                    val subjectCount = subjects.count { it.classId == cls.id }
                    GoogleCard(
                        onClick = { onClassClick(cls.id) },
                        contentPadding = PaddingValues(14.dp),
                        modifier = Modifier.testTag("class_card_${cls.id}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cls.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Monthly Fee: ${FormatUtils.formatCurrency(cls.monthlyFee)} · $studentCount Students · $subjectCount Subjects",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isAdmin) {
                                Row {
                                    IconButton(
                                        onClick = {
                                            editingClassId = cls.id
                                            showForm = true
                                        }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_class))
                                    }
                                    IconButton(onClick = { deleteClassId = cls.id }) {
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

    if (showForm) {
        ClassFormDialog(
            initial = editingClass,
            onDismiss = { showForm = false },
            onSave = {
                onSaveClass(it)
                showForm = false
            }
        )
    }

    if (deleteClass != null) {
        AlertDialog(
            onDismissRequest = { deleteClassId = null },
            title = { Text(stringResource(R.string.delete_class_title)) },
            text = { Text(stringResource(R.string.delete_class_warning)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteClass(deleteClass)
                        deleteClassId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteClassId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun ClassFormDialog(
    initial: AcademyClass?,
    onDismiss: () -> Unit,
    onSave: (AcademyClass) -> Unit
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var feeStr by rememberSaveable(initial?.id) { mutableStateOf(initial?.monthlyFee?.toString() ?: "") }
    var nameErr by rememberSaveable { mutableStateOf<String?>(null) }
    var feeErr by rememberSaveable { mutableStateOf<String?>(null) }

    val requiredMsg = stringResource(R.string.field_required)
    val invalidAmountMsg = stringResource(R.string.invalid_amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.add_class else R.string.edit_class)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameErr = null },
                    label = { Text(stringResource(R.string.class_name)) },
                    isError = nameErr != null,
                    supportingText = nameErr?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("class_name_input")
                )
                OutlinedTextField(
                    value = feeStr,
                    onValueChange = { feeStr = it; feeErr = null },
                    label = { Text(stringResource(R.string.class_monthly_fee)) },
                    isError = feeErr != null,
                    supportingText = feeErr?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("class_fee_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    var valid = true
                    if (name.isBlank()) { nameErr = requiredMsg; valid = false }
                    val fee = feeStr.trim().toLongOrNull()
                    if (fee == null || fee < 0L) { feeErr = invalidAmountMsg; valid = false }
                    if (valid && fee != null) {
                        onSave(
                            AcademyClass(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                monthlyFee = fee,
                                createdAt = initial?.createdAt ?: System.currentTimeMillis()
                            )
                        )
                    }
                },
                modifier = Modifier.testTag("save_class_btn")
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
