package com.example.ui.screens.teachers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.example.data.local.SalaryPayment
import com.example.data.local.Teacher
import com.example.ui.components.*
import com.example.ui.theme.StatusGreenText
import com.example.ui.viewmodel.DataLoadState
import com.example.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachersScreen(
    teachersState: DataLoadState<List<Teacher>>,
    salaryPayments: List<SalaryPayment>,
    onBack: () -> Unit,
    onSaveTeacher: (Teacher) -> Unit,
    onDeleteTeacher: (Teacher) -> Unit,
    onRecordSalary: (Teacher, String, Long, Long, String, Boolean) -> Unit,
    onDeleteSalary: (SalaryPayment) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showTeacherForm by rememberSaveable { mutableStateOf(false) }
    var editingTeacherId by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailTeacherId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteTeacherId by rememberSaveable { mutableStateOf<Long?>(null) }
    var paySalaryTeacherId by rememberSaveable { mutableStateOf<Long?>(null) }

    val allTeachers = (teachersState as? DataLoadState.Success)?.data ?: emptyList()
    val editingTeacher = remember(editingTeacherId, allTeachers) { allTeachers.find { it.id == editingTeacherId } }
    val detailTeacher = remember(detailTeacherId, allTeachers) { allTeachers.find { it.id == detailTeacherId } }
    val deleteTeacher = remember(deleteTeacherId, allTeachers) { allTeachers.find { it.id == deleteTeacherId } }
    val paySalaryTeacher = remember(paySalaryTeacherId, allTeachers) { allTeachers.find { it.id == paySalaryTeacherId } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.teachers_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTeacherId = null
                    showTeacherForm = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_teacher_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_teacher))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                SearchPillBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = stringResource(R.string.search_teachers_hint)
                )
            }

            when (teachersState) {
                is DataLoadState.Loading -> LoadingStateView()
                is DataLoadState.Error -> ErrorStateView(teachersState.message)
                is DataLoadState.Success -> {
                    val filtered = remember(teachersState.data, searchQuery) {
                        val q = searchQuery.trim().lowercase()
                        if (q.isEmpty()) teachersState.data
                        else teachersState.data.filter {
                            it.name.lowercase().contains(q) || it.subject.lowercase().contains(q)
                        }
                    }

                    if (filtered.isEmpty()) {
                        EmptyStateView(
                            title = stringResource(R.string.teachers_empty_title),
                            subtitle = stringResource(R.string.teachers_empty_subtitle),
                            icon = Icons.Default.Badge
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered, key = { it.id }) { teacher ->
                                GoogleCard(
                                    onClick = { detailTeacherId = teacher.id },
                                    contentPadding = PaddingValues(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        ColoredInitialAvatar(name = teacher.name, size = 44.dp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                teacher.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                "${teacher.subject} · ${teacher.phone}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "Salary: ${FormatUtils.formatCurrency(teacher.salary)} · Joined ${FormatUtils.formatDate(teacher.joiningDate)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        FilledTonalButton(
                                            onClick = { paySalaryTeacherId = teacher.id },
                                            modifier = Modifier.heightIn(min = 44.dp)
                                        ) {
                                            Text(stringResource(R.string.record_salary_payment))
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

    if (showTeacherForm) {
        TeacherFormDialog(
            initial = editingTeacher,
            onDismiss = { showTeacherForm = false },
            onSave = {
                onSaveTeacher(it)
                showTeacherForm = false
            }
        )
    }

    if (detailTeacher != null) {
        val teacherSalaries = remember(salaryPayments, detailTeacher.id) {
            salaryPayments.filter { it.teacherId == detailTeacher.id }
        }
        ModalBottomSheet(onDismissRequest = { detailTeacherId = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ColoredInitialAvatar(detailTeacher.name, size = 48.dp)
                        Column {
                            Text(detailTeacher.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${detailTeacher.subject} · ${FormatUtils.formatCurrency(detailTeacher.salary)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row {
                        IconButton(onClick = {
                            editingTeacherId = detailTeacher.id
                            detailTeacherId = null
                            showTeacherForm = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_teacher))
                        }
                        IconButton(onClick = {
                            deleteTeacherId = detailTeacher.id
                            detailTeacherId = null
                        }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Button(
                    onClick = {
                        paySalaryTeacherId = detailTeacher.id
                        detailTeacherId = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.record_salary_payment))
                }

                SectionHeader(title = stringResource(R.string.salary_records))
                if (teacherSalaries.isEmpty()) {
                    Text(
                        stringResource(R.string.no_salary_records),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(teacherSalaries, key = { it.id }) { pay ->
                            GoogleCard(contentPadding = PaddingValues(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "${FormatUtils.formatMonthKey(pay.monthKey)} · ${FormatUtils.formatCurrency(pay.amount)}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = StatusGreenText
                                        )
                                        Text(
                                            FormatUtils.formatDate(pay.paidDate),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onDeleteSalary(pay) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.delete))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (paySalaryTeacher != null) {
        PaySalaryDialog(
            teacher = paySalaryTeacher,
            onDismiss = { paySalaryTeacherId = null },
            onConfirm = { monthKey, amount, paidDate, notes, alsoExpense ->
                onRecordSalary(paySalaryTeacher, monthKey, amount, paidDate, notes, alsoExpense)
                paySalaryTeacherId = null
            }
        )
    }

    if (deleteTeacher != null) {
        AlertDialog(
            onDismissRequest = { deleteTeacherId = null },
            title = { Text(stringResource(R.string.delete_teacher_title)) },
            text = { Text(stringResource(R.string.delete_teacher_warning, deleteTeacher.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTeacher(deleteTeacher)
                        deleteTeacherId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTeacherId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun TeacherFormDialog(
    initial: Teacher?,
    onDismiss: () -> Unit,
    onSave: (Teacher) -> Unit
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var subject by rememberSaveable(initial?.id) { mutableStateOf(initial?.subject ?: "") }
    var phone by rememberSaveable(initial?.id) { mutableStateOf(initial?.phone ?: "") }
    var salaryStr by rememberSaveable(initial?.id) { mutableStateOf(initial?.salary?.toString() ?: "") }
    var joiningDate by rememberSaveable(initial?.id) { mutableStateOf(initial?.joiningDate ?: System.currentTimeMillis()) }

    var nameErr by rememberSaveable { mutableStateOf<String?>(null) }
    var subjectErr by rememberSaveable { mutableStateOf<String?>(null) }
    var phoneErr by rememberSaveable { mutableStateOf<String?>(null) }
    var salaryErr by rememberSaveable { mutableStateOf<String?>(null) }

    val requiredMsg = stringResource(R.string.field_required)
    val invalidAmountMsg = stringResource(R.string.invalid_amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.add_teacher else R.string.edit_teacher)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameErr = null },
                    label = { Text(stringResource(R.string.teacher_name)) },
                    isError = nameErr != null,
                    supportingText = nameErr?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it; subjectErr = null },
                    label = { Text(stringResource(R.string.subject_taught)) },
                    isError = subjectErr != null,
                    supportingText = subjectErr?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; phoneErr = null },
                    label = { Text(stringResource(R.string.phone_number)) },
                    isError = phoneErr != null,
                    supportingText = phoneErr?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = salaryStr,
                    onValueChange = { salaryStr = it; salaryErr = null },
                    label = { Text(stringResource(R.string.monthly_salary)) },
                    isError = salaryErr != null,
                    supportingText = salaryErr?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(
                    label = stringResource(R.string.joining_date),
                    dateMillis = joiningDate,
                    onDateSelected = { joiningDate = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    var valid = true
                    if (name.isBlank()) { nameErr = requiredMsg; valid = false }
                    if (subject.isBlank()) { subjectErr = requiredMsg; valid = false }
                    if (phone.isBlank()) { phoneErr = requiredMsg; valid = false }
                    val sal = salaryStr.trim().toLongOrNull()
                    if (sal == null || sal < 0L) { salaryErr = invalidAmountMsg; valid = false }
                    if (valid && sal != null) {
                        onSave(
                            Teacher(
                                id = initial?.id ?: 0L,
                                name = name.trim(),
                                subject = subject.trim(),
                                phone = phone.trim(),
                                salary = sal,
                                joiningDate = joiningDate,
                                createdAt = initial?.createdAt ?: System.currentTimeMillis()
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

@Composable
private fun PaySalaryDialog(
    teacher: Teacher,
    onDismiss: () -> Unit,
    onConfirm: (monthKey: String, amount: Long, paidDate: Long, notes: String, alsoExpense: Boolean) -> Unit
) {
    var monthKey by rememberSaveable { mutableStateOf(FormatUtils.currentMonthKey()) }
    var amountStr by rememberSaveable(teacher.id) { mutableStateOf(teacher.salary.toString()) }
    var paidDate by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var alsoRecordExpense by rememberSaveable { mutableStateOf(true) }
    var amountErr by rememberSaveable { mutableStateOf<String?>(null) }

    val invalidAmountMsg = stringResource(R.string.invalid_amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stringResource(R.string.record_salary_payment)} · ${teacher.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MonthSwitcher(
                    monthKey = monthKey,
                    onMonthChange = { monthKey = it },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it; amountErr = null },
                    label = { Text(stringResource(R.string.fee_amount)) },
                    isError = amountErr != null,
                    supportingText = amountErr?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(
                    label = stringResource(R.string.select_date),
                    dateMillis = paidDate,
                    onDateSelected = { paidDate = it }
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = alsoRecordExpense,
                        onCheckedChange = { alsoRecordExpense = it }
                    )
                    Text(
                        text = stringResource(R.string.also_record_expense),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.trim().toLongOrNull()
                    if (amt == null || amt <= 0L) {
                        amountErr = invalidAmountMsg
                    } else {
                        onConfirm(monthKey, amt, paidDate, notes, alsoRecordExpense)
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
