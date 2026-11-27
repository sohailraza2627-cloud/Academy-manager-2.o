package com.example.ui.screens.students

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AcademyClass
import com.example.data.local.Student
import com.example.data.local.StudentStatus
import com.example.ui.components.*
import com.example.ui.viewmodel.DataLoadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentsScreen(
    studentsState: DataLoadState<List<Student>>,
    classes: List<AcademyClass>,
    isAdmin: Boolean = true,
    onStudentClick: (String) -> Unit,
    onSaveStudent: (Student) -> Unit,
    onSetStudentStatus: (Student, String) -> Unit,
    onDeleteStudent: (Student) -> Unit,
    onNavigateToClasses: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedClassId by rememberSaveable { mutableStateOf<String?>(null) }
    var statusFilter by rememberSaveable { mutableStateOf("ALL") } // ALL, ACTIVE, LEFT

    var showAddEditDialog by rememberSaveable { mutableStateOf(false) }
    var editingStudentId by rememberSaveable { mutableStateOf<String?>(null) }
    var actionSheetStudentId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteConfirmStudentId by rememberSaveable { mutableStateOf<String?>(null) }

    val classMap = remember(classes) { classes.associateBy { it.id } }

    val allStudents = (studentsState as? DataLoadState.Success)?.data ?: emptyList()
    val editingStudent = remember(editingStudentId, allStudents) {
        allStudents.find { it.id == editingStudentId }
    }
    val actionSheetStudent = remember(actionSheetStudentId, allStudents) {
        allStudents.find { it.id == actionSheetStudentId }
    }
    val deleteConfirmStudent = remember(deleteConfirmStudentId, allStudents) {
        allStudents.find { it.id == deleteConfirmStudentId }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = {
                        editingStudentId = null
                        showAddEditDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_student_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_student))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SearchPillBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = stringResource(R.string.search_students_hint),
                    testTag = "students_search_bar"
                )

                // Class & status filter chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedClassId == null && statusFilter == "ALL",
                            onClick = {
                                selectedClassId = null
                                statusFilter = "ALL"
                            },
                            label = { Text(stringResource(R.string.all)) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == StudentStatus.ACTIVE,
                            onClick = {
                                statusFilter = if (statusFilter == StudentStatus.ACTIVE) "ALL" else StudentStatus.ACTIVE
                            },
                            label = { Text(stringResource(R.string.active)) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == StudentStatus.LEFT,
                            onClick = {
                                statusFilter = if (statusFilter == StudentStatus.LEFT) "ALL" else StudentStatus.LEFT
                            },
                            label = { Text(stringResource(R.string.left)) }
                        )
                    }
                    items(classes, key = { it.id }) { cls ->
                        FilterChip(
                            selected = selectedClassId == cls.id,
                            onClick = {
                                selectedClassId = if (selectedClassId == cls.id) null else cls.id
                            },
                            label = { Text(cls.name) }
                        )
                    }
                }
            }

            when (studentsState) {
                is DataLoadState.Loading -> LoadingStateView()
                is DataLoadState.Error -> ErrorStateView(studentsState.message)
                is DataLoadState.Success -> {
                    val filtered = remember(studentsState.data, searchQuery, selectedClassId, statusFilter) {
                        val q = searchQuery.trim().lowercase()
                        studentsState.data.filter { s ->
                            val matchesClass = selectedClassId == null || s.classId == selectedClassId
                            val matchesStatus = statusFilter == "ALL" || s.status == statusFilter
                            val matchesQuery = q.isEmpty() ||
                                s.name.lowercase().contains(q) ||
                                s.fatherName.lowercase().contains(q) ||
                                s.rollNo.lowercase().contains(q) ||
                                s.phone.lowercase().contains(q)
                            matchesClass && matchesStatus && matchesQuery
                        }
                    }

                    if (filtered.isEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyStateView(
                                title = stringResource(R.string.students_empty_title),
                                subtitle = if (classes.isEmpty()) {
                                    stringResource(R.string.no_classes_prompt)
                                } else {
                                    stringResource(R.string.students_empty_subtitle)
                                },
                                icon = Icons.Default.PeopleOutline
                            )
                            if (classes.isEmpty()) {
                                OutlinedButton(onClick = onNavigateToClasses) {
                                    Text(stringResource(R.string.classes_title))
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 88.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filtered, key = { it.id }) { student ->
                                val className = classMap[student.classId]?.name ?: ""
                                StudentListCard(
                                    student = student,
                                    className = className,
                                    showMoreAction = isAdmin,
                                    onClick = { onStudentClick(student.id) },
                                    onMoreClick = { actionSheetStudentId = student.id }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        StudentFormDialog(
            initialStudent = editingStudent,
            classes = classes,
            onDismiss = { showAddEditDialog = false },
            onSave = { student ->
                onSaveStudent(student)
                showAddEditDialog = false
            }
        )
    }

    // Quick Actions Bottom Sheet
    if (actionSheetStudent != null) {
        ModalBottomSheet(onDismissRequest = { actionSheetStudentId = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    ColoredInitialAvatar(
                        name = actionSheetStudent.name,
                        photoPath = actionSheetStudent.photoPath,
                        size = 44.dp
                    )
                    Column {
                        Text(
                            text = actionSheetStudent.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${classMap[actionSheetStudent.classId]?.name ?: ""} · ${stringResource(R.string.roll_prefix, actionSheetStudent.rollNo)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SheetActionRow(
                    icon = Icons.Default.Person,
                    label = stringResource(R.string.view_profile),
                    onClick = {
                        val id = actionSheetStudent.id
                        actionSheetStudentId = null
                        onStudentClick(id)
                    }
                )
                SheetActionRow(
                    icon = Icons.Default.Edit,
                    label = stringResource(R.string.edit_student),
                    onClick = {
                        editingStudentId = actionSheetStudent.id
                        actionSheetStudentId = null
                        showAddEditDialog = true
                    }
                )
                val isLeft = actionSheetStudent.status == StudentStatus.LEFT
                SheetActionRow(
                    icon = if (isLeft) Icons.Default.PersonAdd else Icons.Default.PersonOff,
                    label = stringResource(if (isLeft) R.string.reactivate_student else R.string.mark_as_left),
                    onClick = {
                        onSetStudentStatus(
                            actionSheetStudent,
                            if (isLeft) StudentStatus.ACTIVE else StudentStatus.LEFT
                        )
                        actionSheetStudentId = null
                    }
                )
                SheetActionRow(
                    icon = Icons.Default.DeleteOutline,
                    label = stringResource(R.string.delete_student),
                    isDestructive = true,
                    onClick = {
                        deleteConfirmStudentId = actionSheetStudent.id
                        actionSheetStudentId = null
                    }
                )
            }
        }
    }

    if (deleteConfirmStudent != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmStudentId = null },
            title = { Text(stringResource(R.string.delete_student_title)) },
            text = { Text(stringResource(R.string.delete_student_warning, deleteConfirmStudent.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteStudent(deleteConfirmStudent)
                        deleteConfirmStudentId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_student")
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmStudentId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun StudentListCard(
    student: Student,
    className: String,
    showMoreAction: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    GoogleCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("student_card_${student.id}"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ColoredInitialAvatar(
                name = student.name,
                photoPath = student.photoPath,
                size = 46.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = student.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (student.status == StudentStatus.LEFT) {
                        StatusBadge(text = stringResource(R.string.left), tone = BadgeTone.RED)
                    }
                }
                Text(
                    text = "${student.fatherName} · $className · ${stringResource(R.string.roll_prefix, student.rollNo)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = student.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showMoreAction) {
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("student_more_${student.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.student_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = tint
            )
        }
    }
}
