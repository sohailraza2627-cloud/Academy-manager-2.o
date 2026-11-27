package com.example.ui.screens.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.*
import com.example.ui.components.BadgeTone
import com.example.ui.components.GoogleCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.StatusGreenText
import com.example.ui.theme.StatusRedText
import com.example.util.FormatUtils
import com.example.util.ImageStorage

@Composable
fun DashboardScreen(
    profile: AcademyProfile,
    isAdmin: Boolean = true,
    students: List<Student>,
    attendanceRecords: List<AttendanceRecord>,
    feeCharges: List<FeeCharge>,
    feePayments: List<FeePayment>,
    ledgerEntries: List<LedgerEntry>,
    onNavigateToStudents: () -> Unit,
    onNavigateToAttendance: () -> Unit,
    onNavigateToFees: () -> Unit,
    onNavigateToPaperFee: () -> Unit,
    onNavigateToLedger: () -> Unit,
    onNavigateToReports: () -> Unit,
    onOpenAssistant: () -> Unit = {}
) {
    val todayKey = remember { FormatUtils.currentDateKey() }
    val currentMonthKey = remember { FormatUtils.currentMonthKey() }
    val logoBitmap = remember(profile.logoPath) { ImageStorage.loadBitmap(profile.logoPath) }

    // Metrics
    val activeStudents = remember(students) { students.count { it.status == StudentStatus.ACTIVE } }
    val leftStudents = remember(students) { students.count { it.status == StudentStatus.LEFT } }

    val todayRecords = remember(attendanceRecords, todayKey) {
        attendanceRecords.filter { it.dateKey == todayKey }
    }
    val todayPresent = remember(todayRecords) {
        todayRecords.count { it.status == AttendanceStatus.PRESENT }
    }
    val todayAttPct = remember(todayRecords, todayPresent) {
        if (todayRecords.isEmpty()) null else (todayPresent * 100.0 / todayRecords.size).toInt()
    }

    // Separate Tuition & Paper Fee totals!
    val tuitionCollectedThisMonth = remember(feePayments, currentMonthKey) {
        feePayments.filter {
            it.monthKey == currentMonthKey && it.ledgerType != FeeLedgerType.PAPER_FEE
        }.sumOf { it.amountPaid }
    }
    val paperFeeCollectedThisMonth = remember(feePayments, currentMonthKey) {
        feePayments.filter {
            it.monthKey == currentMonthKey && it.ledgerType == FeeLedgerType.PAPER_FEE
        }.sumOf { it.amountPaid }
    }

    val tuitionPendingTotal = remember(feeCharges) {
        feeCharges.filter { it.ledgerType != FeeLedgerType.PAPER_FEE }.sumOf { it.remainingAmount }
    }
    val paperPendingTotal = remember(feeCharges) {
        feeCharges.filter { it.ledgerType == FeeLedgerType.PAPER_FEE }.sumOf { it.remainingAmount }
    }
    val totalPending = tuitionPendingTotal + paperPendingTotal

    val manualIncomeThisMonth = remember(ledgerEntries, currentMonthKey) {
        ledgerEntries.filter { it.monthKey == currentMonthKey && it.type == LedgerType.INCOME }
            .sumOf { it.amount }
    }
    val expenseThisMonth = remember(ledgerEntries, currentMonthKey) {
        ledgerEntries.filter { it.monthKey == currentMonthKey && it.type == LedgerType.EXPENSE }
            .sumOf { it.amount }
    }
    val totalIncomeThisMonth = tuitionCollectedThisMonth + paperFeeCollectedThisMonth + manualIncomeThisMonth
    val netThisMonth = totalIncomeThisMonth - expenseThisMonth

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GoogleCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (logoBitmap != null) {
                                Image(
                                    bitmap = logoBitmap.asImageBitmap(),
                                    contentDescription = profile.academyName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.academyName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = FormatUtils.formatDate(System.currentTimeMillis()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!isAdmin) {
                        StatusBadge(
                            text = stringResource(R.string.role_teacher),
                            tone = BadgeTone.BLUE
                        )
                    }
                }
            }
        }

        // Gemini-style Academy Assistant Launcher Card
        item {
            Surface(
                onClick = onOpenAssistant,
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .testTag("dashboard_assistant_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = stringResource(R.string.assistant_title),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.assistant_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.assistant_card_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowOutward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // 2-column metric grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DashboardMetricCard(
                        title = stringResource(R.string.metric_total_students),
                        value = activeStudents.toString(),
                        subtitle = stringResource(R.string.metric_active_students_sub, activeStudents, leftStudents),
                        icon = Icons.Default.People,
                        onClick = onNavigateToStudents,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card_total_students")
                    )
                    DashboardMetricCard(
                        title = stringResource(R.string.metric_today_attendance),
                        value = todayAttPct?.let { "$it%" } ?: "—",
                        subtitle = if (todayRecords.isEmpty()) {
                            stringResource(R.string.metric_not_marked)
                        } else {
                            stringResource(R.string.metric_today_attendance_sub, todayPresent, todayRecords.size)
                        },
                        icon = Icons.Default.FactCheck,
                        onClick = onNavigateToAttendance,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card_today_attendance")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DashboardMetricCard(
                        title = stringResource(R.string.metric_tuition_collected),
                        value = FormatUtils.formatCurrency(tuitionCollectedThisMonth),
                        subtitle = FormatUtils.formatMonthKey(currentMonthKey),
                        icon = Icons.Default.Payments,
                        valueColor = StatusGreenText,
                        onClick = onNavigateToFees,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card_tuition_collected")
                    )
                    DashboardMetricCard(
                        title = stringResource(R.string.metric_paper_collected),
                        value = FormatUtils.formatCurrency(paperFeeCollectedThisMonth),
                        subtitle = FormatUtils.formatMonthKey(currentMonthKey),
                        icon = Icons.Default.Description,
                        onClick = onNavigateToPaperFee,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card_paper_collected")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DashboardMetricCard(
                        title = stringResource(R.string.metric_total_pending),
                        value = FormatUtils.formatCurrency(totalPending),
                        subtitle = stringResource(
                            R.string.metric_pending_breakdown,
                            FormatUtils.formatCurrency(tuitionPendingTotal),
                            FormatUtils.formatCurrency(paperPendingTotal)
                        ),
                        icon = Icons.Default.PendingActions,
                        valueColor = if (totalPending > 0L) StatusRedText else MaterialTheme.colorScheme.onSurface,
                        onClick = onNavigateToFees,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card_total_pending")
                    )
                    DashboardMetricCard(
                        title = stringResource(R.string.metric_income_vs_expense),
                        value = FormatUtils.formatCurrency(netThisMonth),
                        subtitle = stringResource(
                            R.string.metric_income_expense_sub,
                            FormatUtils.formatCurrency(totalIncomeThisMonth),
                            FormatUtils.formatCurrency(expenseThisMonth)
                        ),
                        icon = Icons.Default.AccountBalanceWallet,
                        valueColor = if (netThisMonth >= 0L) StatusGreenText else StatusRedText,
                        onClick = onNavigateToLedger,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card_income_expense")
                    )
                }
            }
        }

        item {
            SectionHeader(title = stringResource(R.string.quick_actions))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    label = stringResource(R.string.action_mark_attendance),
                    icon = Icons.Default.HowToReg,
                    onClick = onNavigateToAttendance,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("qa_mark_attendance")
                )
                if (isAdmin) {
                    QuickActionTile(
                        label = stringResource(R.string.action_add_student),
                        icon = Icons.Default.PersonAdd,
                        onClick = onNavigateToStudents,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("qa_add_student")
                    )
                    QuickActionTile(
                        label = stringResource(R.string.action_collect_fee),
                        icon = Icons.Default.ReceiptLong,
                        onClick = onNavigateToFees,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("qa_collect_fee")
                    )
                }
                QuickActionTile(
                    label = stringResource(R.string.action_reports),
                    icon = Icons.Default.Assessment,
                    onClick = onNavigateToReports,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("qa_reports")
                )
            }
        }
    }
}

@Composable
private fun DashboardMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    GoogleCard(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GoogleCard(
        onClick = onClick,
        modifier = modifier.heightIn(min = 84.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
