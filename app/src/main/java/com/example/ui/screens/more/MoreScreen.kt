package com.example.ui.screens.more

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.GoogleCard
import com.example.ui.components.SectionHeader

@Composable
fun MoreScreen(
    onOpenTeachers: () -> Unit,
    onOpenClasses: () -> Unit,
    onOpenExams: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("more_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(title = stringResource(R.string.more_academics))
        }
        item {
            GoogleCard(contentPadding = PaddingValues(0.dp)) {
                MoreRowItem(
                    icon = Icons.Default.Badge,
                    title = stringResource(R.string.teachers_title),
                    subtitle = stringResource(R.string.salary_records),
                    onClick = onOpenTeachers,
                    testTag = "more_teachers"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                MoreRowItem(
                    icon = Icons.Default.Class,
                    title = stringResource(R.string.classes_title),
                    subtitle = "${stringResource(R.string.tab_subjects)} & ${stringResource(R.string.tab_timetable)}",
                    onClick = onOpenClasses,
                    testTag = "more_classes"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                MoreRowItem(
                    icon = Icons.Default.Grading,
                    title = stringResource(R.string.exams_title),
                    subtitle = "${stringResource(R.string.enter_marks)} & ${stringResource(R.string.results_and_ranks)}",
                    onClick = onOpenExams,
                    testTag = "more_exams"
                )
            }
        }

        item {
            SectionHeader(title = stringResource(R.string.more_finance))
        }
        item {
            GoogleCard(contentPadding = PaddingValues(0.dp)) {
                MoreRowItem(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = stringResource(R.string.ledger_title),
                    subtitle = "${stringResource(R.string.income)} & ${stringResource(R.string.expense)}",
                    onClick = onOpenLedger,
                    testTag = "more_ledger"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                MoreRowItem(
                    icon = Icons.Default.Assessment,
                    title = stringResource(R.string.reports_title),
                    subtitle = "${stringResource(R.string.export_pdf)} & ${stringResource(R.string.export_csv)}",
                    onClick = onOpenReports,
                    testTag = "more_reports"
                )
            }
        }

        item {
            SectionHeader(title = stringResource(R.string.more_system))
        }
        item {
            GoogleCard(contentPadding = PaddingValues(0.dp)) {
                MoreRowItem(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.settings_title),
                    subtitle = "${stringResource(R.string.academy_profile_section)}, ${stringResource(R.string.change_pin)}, ${stringResource(R.string.backup_section)}",
                    onClick = onOpenSettings,
                    testTag = "more_settings"
                )
            }
        }
    }
}

@Composable
private fun MoreRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
