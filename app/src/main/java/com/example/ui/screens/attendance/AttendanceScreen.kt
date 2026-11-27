package com.example.ui.screens.attendance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    classes: List<AcademyClass>,
    students: List<Student>,
    allAttendanceRecords: List<AttendanceRecord>,
    selectedClassId: String?,
    selectedDateKey: String,
    draftList: List<Pair<Student, String>>,
    isLoading: Boolean,
    onSelectClass: (String) -> Unit,
    onSelectDate: (String) -> Unit,
    onSetStatus: (String, String) -> Unit,
    onToggleStatus: (String) -> Unit,
    onMarkAllPresent: () -> Unit,
    onSaveAttendance: () -> Unit,
    onSendAttendanceWhatsApp: (Student, String, String, String) -> Unit = { _, _, _, _ -> }
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var reportMonthKey by rememberSaveable { mutableStateOf(FormatUtils.currentMonthKey()) }
    var classDropdownExpanded by remember { mutableStateOf(false) }

    val selectedClass = remember(classes, selectedClassId) {
        classes.find { it.id == selectedClassId } ?: classes.firstOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("attendance_screen")
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.tab_daily_mark)) },
                modifier = Modifier.testTag("att_tab_daily")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.tab_monthly_report)) },
                modifier = Modifier.testTag("att_tab_monthly")
            )
        }

        if (classes.isEmpty()) {
            EmptyStateView(
                title = stringResource(R.string.classes_empty_title),
                subtitle = stringResource(R.string.no_classes_prompt),
                icon = Icons.Default.EventAvailable
            )
            return
        }

        // Class selector common to both tabs
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = classDropdownExpanded,
                onExpandedChange = { classDropdownExpanded = !classDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedClass?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.select_class)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                        .testTag("att_class_dropdown")
                )
                ExposedDropdownMenu(
                    expanded = classDropdownExpanded,
                    onDismissRequest = { classDropdownExpanded = false }
                ) {
                    classes.forEach { cls ->
                        DropdownMenuItem(
                            text = { Text(cls.name) },
                            onClick = {
                                onSelectClass(cls.id)
                                classDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        if (selectedTab == 0) {
            // Daily Marking
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DatePickerField(
                    label = stringResource(R.string.select_date),
                    dateMillis = FormatUtils.fromDateKey(selectedDateKey),
                    onDateSelected = { millis -> onSelectDate(FormatUtils.toDateKey(millis)) }
                )

                val presentCount = draftList.count { it.second == AttendanceStatus.PRESENT }
                val absentCount = draftList.count { it.second == AttendanceStatus.ABSENT }
                val leaveCount = draftList.count { it.second == AttendanceStatus.LEAVE }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusBadge("P: $presentCount", BadgeTone.GREEN)
                        StatusBadge("A: $absentCount", BadgeTone.RED)
                        StatusBadge("L: $leaveCount", BadgeTone.AMBER)
                    }
                    TextButton(
                        onClick = onMarkAllPresent,
                        enabled = draftList.isNotEmpty(),
                        modifier = Modifier.testTag("mark_all_present_btn")
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.mark_all_present))
                    }
                }

                if (isLoading) {
                    LoadingStateView(modifier = Modifier.weight(1f))
                } else if (draftList.isEmpty()) {
                    EmptyStateView(
                        title = stringResource(R.string.attendance_empty_students),
                        icon = Icons.Default.EventAvailable,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(draftList, key = { it.first.id }) { (student, status) ->
                            AttendanceStudentRow(
                                student = student,
                                status = status,
                                onToggle = { onToggleStatus(student.id) },
                                onSelectStatus = { onSetStatus(student.id, it) },
                                onWhatsAppClick = {
                                    onSendAttendanceWhatsApp(
                                        student,
                                        selectedClass?.name ?: "",
                                        FormatUtils.formatDateKey(selectedDateKey),
                                        status
                                    )
                                }
                            )
                        }
                    }

                    Button(
                        onClick = onSaveAttendance,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .heightIn(min = 52.dp)
                            .testTag("save_attendance_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.save_attendance))
                    }
                }
            }
        } else {
            // Monthly Report
            val activeClassStudents = remember(students, selectedClass) {
                students.filter { it.classId == selectedClass?.id && it.status == StudentStatus.ACTIVE }
                    .sortedBy { it.rollNo }
            }
            val classMonthRecords = remember(allAttendanceRecords, selectedClass, reportMonthKey) {
                allAttendanceRecords.filter {
                    it.classId == selectedClass?.id && it.monthKey == reportMonthKey
                }.groupBy { it.studentId }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MonthSwitcher(
                    monthKey = reportMonthKey,
                    onMonthChange = { reportMonthKey = it },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                if (activeClassStudents.isEmpty()) {
                    EmptyStateView(title = stringResource(R.string.attendance_empty_students))
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(activeClassStudents, key = { it.id }) { student ->
                            val recs = classMonthRecords[student.id].orEmpty()
                            val p = recs.count { it.status == AttendanceStatus.PRESENT }
                            val a = recs.count { it.status == AttendanceStatus.ABSENT }
                            val l = recs.count { it.status == AttendanceStatus.LEAVE }
                            val total = recs.size
                            val pct = if (total == 0) 0 else (p * 100.0 / total).toInt()

                            GoogleCard(contentPadding = PaddingValues(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ColoredInitialAvatar(
                                        name = student.name,
                                        photoPath = student.photoPath,
                                        size = 40.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = student.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "P: $p · A: $a · L: $l · ${stringResource(R.string.working_days, total)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusBadge(
                                        text = if (total == 0) "—" else "$pct%",
                                        tone = when {
                                            total == 0 -> BadgeTone.NEUTRAL
                                            pct >= 75 -> BadgeTone.GREEN
                                            pct >= 50 -> BadgeTone.AMBER
                                            else -> BadgeTone.RED
                                        }
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

@Composable
private fun AttendanceStudentRow(
    student: Student,
    status: String,
    onToggle: () -> Unit,
    onSelectStatus: (String) -> Unit,
    onWhatsAppClick: () -> Unit
) {
    GoogleCard(
        onClick = onToggle,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = Modifier.testTag("att_row_${student.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ColoredInitialAvatar(
                name = student.name,
                photoPath = student.photoPath,
                size = 40.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.roll_prefix, student.rollNo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AttendancePillButton(
                    label = stringResource(R.string.present_short),
                    selected = status == AttendanceStatus.PRESENT,
                    tone = BadgeTone.GREEN,
                    onClick = { onSelectStatus(AttendanceStatus.PRESENT) },
                    testTag = "att_p_${student.id}"
                )
                AttendancePillButton(
                    label = stringResource(R.string.absent_short),
                    selected = status == AttendanceStatus.ABSENT,
                    tone = BadgeTone.RED,
                    onClick = { onSelectStatus(AttendanceStatus.ABSENT) },
                    testTag = "att_a_${student.id}"
                )
                AttendancePillButton(
                    label = stringResource(R.string.leave_short),
                    selected = status == AttendanceStatus.LEAVE,
                    tone = BadgeTone.AMBER,
                    onClick = { onSelectStatus(AttendanceStatus.LEAVE) },
                    testTag = "att_l_${student.id}"
                )
                IconButton(
                    onClick = onWhatsAppClick,
                    modifier = Modifier.testTag("att_wa_${student.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = stringResource(R.string.wa_send_attendance_alert),
                        tint = when (status) {
                            AttendanceStatus.ABSENT -> MaterialTheme.colorScheme.error
                            AttendanceStatus.LEAVE -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AttendancePillButton(
    label: String,
    selected: Boolean,
    tone: BadgeTone,
    onClick: () -> Unit,
    testTag: String
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (bg, fg) = if (selected) {
        when (tone) {
            BadgeTone.GREEN -> if (isDark) StatusGreenDarkBg to StatusGreenDarkText else StatusGreenBg to StatusGreenText
            BadgeTone.RED -> if (isDark) StatusRedDarkBg to StatusRedDarkText else StatusRedBg to StatusRedText
            BadgeTone.AMBER -> if (isDark) StatusAmberDarkBg to StatusAmberDarkText else StatusAmberBg to StatusAmberText
            else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        }
    } else {
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = if (selected) BorderStroke(1.dp, fg.copy(alpha = 0.5f)) else null,
        modifier = Modifier
            .defaultMinSize(minWidth = 42.dp, minHeight = 42.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = fg
            )
        }
    }
}
