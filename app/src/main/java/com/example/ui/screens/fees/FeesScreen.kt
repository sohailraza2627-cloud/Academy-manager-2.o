package com.example.ui.screens.fees

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.*
import com.example.data.repository.ReceiptResult
import com.example.ui.components.*
import com.example.ui.theme.StatusGreenText
import com.example.ui.theme.StatusRedText
import com.example.ui.viewmodel.DataLoadState
import com.example.util.FormatUtils

private sealed interface ActiveCollectTarget {
    data class Tuition(
        val student: Student,
        val currentCharge: FeeCharge,
        val arrears: Long
    ) : ActiveCollectTarget

    data class SingleCharge(
        val student: Student,
        val charge: FeeCharge
    ) : ActiveCollectTarget
}

@Composable
fun FeesScreen(
    initialTab: Int = 0,
    feeChargesState: DataLoadState<List<FeeCharge>>,
    students: List<Student>,
    classes: List<AcademyClass>,
    exams: List<Exam>,
    profile: AcademyProfile,
    onGenerateMonthlyTuition: (monthKey: String, classId: Long?, onDone: (Int, Int) -> Unit) -> Unit,
    onCreatePaperFeeBatch: (classId: Long, title: String, amount: Long, dueDate: Long, onDone: (Int, Int) -> Unit) -> Unit,
    onCreateOtherFee: (studentId: Long, subType: String, title: String, amount: Long, discount: Long, dueDate: Long) -> Unit,
    onCollectTuitionFifo: (studentId: Long, targetChargeId: Long?, amount: Long, discount: Long, paidDate: Long, notes: String, onSuccess: (ReceiptResult) -> Unit, onError: (String) -> Unit) -> Unit,
    onCollectSingleCharge: (chargeId: Long, amount: Long, discount: Long, paidDate: Long, notes: String, onSuccess: (ReceiptResult) -> Unit, onError: (String) -> Unit) -> Unit,
    onDeleteCharge: (FeeCharge) -> Unit,
    onShowMessage: (String) -> Unit
) {
    var selectedTab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab) }
    var selectedMonthKey by rememberSaveable { mutableStateOf(FormatUtils.currentMonthKey()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedClassId by rememberSaveable { mutableStateOf<Long?>(null) }
    var paperPendingOnly by rememberSaveable { mutableStateOf(true) }
    var defaulterLedgerTab by rememberSaveable { mutableIntStateOf(0) } // 0 Tuition, 1 Paper, 2 Other

    var showPaperFeeCreateDialog by rememberSaveable { mutableStateOf(false) }
    var showOtherFeeCreateDialog by rememberSaveable { mutableStateOf(false) }
    var activeCollectTarget by remember { mutableStateOf<ActiveCollectTarget?>(null) }
    var latestReceipt by remember { mutableStateOf<ReceiptResult?>(null) }

    val studentMap = remember(students) { students.associateBy { it.id } }
    val classMap = remember(classes) { classes.associateBy { it.id } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("fees_screen")
    ) {
        val tabs = listOf(
            stringResource(R.string.tab_tuition),
            stringResource(R.string.tab_paper_fee),
            stringResource(R.string.tab_other_fees),
            stringResource(R.string.tab_defaulters)
        )
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(label) },
                    modifier = Modifier.testTag("fees_tab_$idx")
                )
            }
        }

        when (feeChargesState) {
            is DataLoadState.Loading -> LoadingStateView()
            is DataLoadState.Error -> ErrorStateView(feeChargesState.message)
            is DataLoadState.Success -> {
                val allCharges = feeChargesState.data

                when (selectedTab) {
                    0 -> TuitionLedgerTab(
                        allCharges = allCharges,
                        students = students,
                        studentMap = studentMap,
                        classes = classes,
                        classMap = classMap,
                        monthKey = selectedMonthKey,
                        searchQuery = searchQuery,
                        selectedClassId = selectedClassId,
                        onMonthChange = { selectedMonthKey = it },
                        onSearchChange = { searchQuery = it },
                        onClassFilterChange = { selectedClassId = it },
                        onGenerateMonthlyFees = {
                            onGenerateMonthlyTuition(selectedMonthKey, selectedClassId) { created, skipped ->
                                // message handled by caller
                            }
                        },
                        onCollectClick = { student, charge, arrears ->
                            activeCollectTarget = ActiveCollectTarget.Tuition(student, charge, arrears)
                        }
                    )

                    1 -> PaperFeeLedgerTab(
                        allCharges = allCharges,
                        studentMap = studentMap,
                        classes = classes,
                        classMap = classMap,
                        searchQuery = searchQuery,
                        selectedClassId = selectedClassId,
                        pendingOnly = paperPendingOnly,
                        onSearchChange = { searchQuery = it },
                        onClassFilterChange = { selectedClassId = it },
                        onPendingToggle = { paperPendingOnly = !paperPendingOnly },
                        onCreatePaperFeesClick = { showPaperFeeCreateDialog = true },
                        onCollectClick = { student, charge ->
                            activeCollectTarget = ActiveCollectTarget.SingleCharge(student, charge)
                        }
                    )

                    2 -> OtherFeesLedgerTab(
                        allCharges = allCharges,
                        studentMap = studentMap,
                        classMap = classMap,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onAddOtherFeeClick = { showOtherFeeCreateDialog = true },
                        onCollectClick = { student, charge ->
                            activeCollectTarget = ActiveCollectTarget.SingleCharge(student, charge)
                        },
                        onDeleteCharge = onDeleteCharge
                    )

                    3 -> DefaultersTab(
                        allCharges = allCharges,
                        studentMap = studentMap,
                        classMap = classMap,
                        ledgerSegment = defaulterLedgerTab,
                        onSegmentChange = { defaulterLedgerTab = it },
                        onCollectTuition = { student, latestCharge, arrears ->
                            activeCollectTarget = ActiveCollectTarget.Tuition(student, latestCharge, arrears)
                        },
                        onCollectSingle = { student, charge ->
                            activeCollectTarget = ActiveCollectTarget.SingleCharge(student, charge)
                        }
                    )
                }
            }
        }
    }

    if (showPaperFeeCreateDialog) {
        CreatePaperFeeDialog(
            classes = classes,
            exams = exams,
            onDismiss = { showPaperFeeCreateDialog = false },
            onCreate = { classId, title, amount, dueDate ->
                onCreatePaperFeeBatch(classId, title, amount, dueDate) { _, _ -> }
                showPaperFeeCreateDialog = false
            }
        )
    }

    if (showOtherFeeCreateDialog) {
        CreateOtherFeeDialog(
            students = students.filter { it.status == StudentStatus.ACTIVE },
            classMap = classMap,
            onDismiss = { showOtherFeeCreateDialog = false },
            onCreate = { studentId, subType, title, amount, discount, dueDate ->
                onCreateOtherFee(studentId, subType, title, amount, discount, dueDate)
                showOtherFeeCreateDialog = false
            }
        )
    }

    when (val target = activeCollectTarget) {
        is ActiveCollectTarget.Tuition -> {
            FeeCollectSheet(
                student = target.student,
                className = classMap[target.student.classId]?.name ?: "",
                title = target.currentCharge.title,
                currentChargeDue = target.currentCharge.remainingAmount,
                arrearsDue = target.arrears,
                isTuitionWithArrears = true,
                onDismiss = { activeCollectTarget = null },
                onSubmit = { amount, discount, paidDate, notes ->
                    onCollectTuitionFifo(
                        target.student.id,
                        target.currentCharge.id,
                        amount,
                        discount,
                        paidDate,
                        notes,
                        { receipt ->
                            activeCollectTarget = null
                            latestReceipt = receipt
                        },
                        { err -> onShowMessage(err) }
                    )
                }
            )
        }
        is ActiveCollectTarget.SingleCharge -> {
            FeeCollectSheet(
                student = target.student,
                className = classMap[target.student.classId]?.name ?: "",
                title = target.charge.title,
                currentChargeDue = target.charge.remainingAmount,
                arrearsDue = 0L,
                isTuitionWithArrears = false,
                onDismiss = { activeCollectTarget = null },
                onSubmit = { amount, discount, paidDate, notes ->
                    onCollectSingleCharge(
                        target.charge.id,
                        amount,
                        discount,
                        paidDate,
                        notes,
                        { receipt ->
                            activeCollectTarget = null
                            latestReceipt = receipt
                        },
                        { err -> onShowMessage(err) }
                    )
                }
            )
        }
        null -> Unit
    }

    latestReceipt?.let { receipt ->
        ReceiptPreviewSheet(
            receipt = receipt,
            profile = profile,
            onDismiss = { latestReceipt = null }
        )
    }
}

@Composable
private fun TuitionLedgerTab(
    allCharges: List<FeeCharge>,
    students: List<Student>,
    studentMap: Map<Long, Student>,
    classes: List<AcademyClass>,
    classMap: Map<Long, AcademyClass>,
    monthKey: String,
    searchQuery: String,
    selectedClassId: Long?,
    onMonthChange: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onClassFilterChange: (Long?) -> Unit,
    onGenerateMonthlyFees: () -> Unit,
    onCollectClick: (Student, FeeCharge, Long) -> Unit
) {
    val tuitionCharges = remember(allCharges) {
        allCharges.filter { it.ledgerType == FeeLedgerType.TUITION }
    }
    val monthCharges = remember(tuitionCharges, monthKey, selectedClassId, searchQuery, studentMap) {
        val q = searchQuery.trim().lowercase()
        tuitionCharges.filter { c ->
            val st = studentMap[c.studentId]
            val matchesMonth = c.monthKey == monthKey
            val matchesClass = selectedClassId == null || c.classId == selectedClassId
            val matchesQuery = q.isEmpty() ||
                (st?.name?.lowercase()?.contains(q) == true) ||
                (st?.rollNo?.lowercase()?.contains(q) == true)
            matchesMonth && matchesClass && matchesQuery
        }
    }

    // Arrears per student strictly from earlier months (< monthKey)
    val arrearsByStudent = remember(tuitionCharges, monthKey) {
        tuitionCharges
            .filter { (it.monthKey ?: "") < monthKey && it.remainingAmount > 0L }
            .groupBy { it.studentId }
            .mapValues { (_, list) -> list.sumOf { it.remainingAmount } }
    }

    val totalBilled = monthCharges.sumOf { it.netAmount }
    val totalCollected = monthCharges.sumOf { it.paidAmount }
    val totalPending = monthCharges.sumOf { it.remainingAmount }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonthSwitcher(monthKey = monthKey, onMonthChange = onMonthChange)
                Button(
                    onClick = onGenerateMonthlyFees,
                    modifier = Modifier.testTag("generate_monthly_fees_btn")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.generate_monthly_fees), maxLines = 1)
                }
            }
        }

        item {
            LedgerSummaryCard(billed = totalBilled, collected = totalCollected, pending = totalPending)
        }

        item {
            SearchPillBar(
                query = searchQuery,
                onQueryChange = onSearchChange,
                placeholder = stringResource(R.string.search_students_hint)
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedClassId == null,
                        onClick = { onClassFilterChange(null) },
                        label = { Text(stringResource(R.string.all)) }
                    )
                }
                items(classes, key = { it.id }) { cls ->
                    FilterChip(
                        selected = selectedClassId == cls.id,
                        onClick = { onClassFilterChange(if (selectedClassId == cls.id) null else cls.id) },
                        label = { Text(cls.name) }
                    )
                }
            }
        }

        if (monthCharges.isEmpty()) {
            item {
                EmptyStateView(
                    title = stringResource(R.string.tuition_empty),
                    icon = Icons.Default.Payments
                )
            }
        } else {
            items(monthCharges, key = { it.id }) { charge ->
                val student = studentMap[charge.studentId] ?: return@items
                val arrears = arrearsByStudent[student.id] ?: 0L
                val totalPayable = charge.remainingAmount + arrears
                val statusLabel = when (charge.status) {
                    FeeStatus.PAID -> stringResource(R.string.status_paid)
                    FeeStatus.PARTIAL -> stringResource(R.string.status_partial)
                    else -> stringResource(R.string.status_unpaid)
                }
                val tone = when (charge.status) {
                    FeeStatus.PAID -> BadgeTone.GREEN
                    FeeStatus.PARTIAL -> BadgeTone.AMBER
                    else -> BadgeTone.RED
                }

                GoogleCard(contentPadding = PaddingValues(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ColoredInitialAvatar(student.name, student.photoPath, size = 40.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    student.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                StatusBadge(text = statusLabel, tone = tone)
                            }
                            Text(
                                "${classMap[student.classId]?.name ?: ""} · Net ${FormatUtils.formatCurrency(charge.netAmount)} · Paid ${FormatUtils.formatCurrency(charge.paidAmount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (arrears > 0L) {
                                Text(
                                    stringResource(R.string.arrears_label, FormatUtils.formatCurrency(arrears)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        if (totalPayable > 0L) {
                            FilledTonalButton(
                                onClick = { onCollectClick(student, charge, arrears) },
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .testTag("collect_tuition_${charge.id}")
                            ) {
                                Text(stringResource(R.string.collect))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaperFeeLedgerTab(
    allCharges: List<FeeCharge>,
    studentMap: Map<Long, Student>,
    classes: List<AcademyClass>,
    classMap: Map<Long, AcademyClass>,
    searchQuery: String,
    selectedClassId: Long?,
    pendingOnly: Boolean,
    onSearchChange: (String) -> Unit,
    onClassFilterChange: (Long?) -> Unit,
    onPendingToggle: () -> Unit,
    onCreatePaperFeesClick: () -> Unit,
    onCollectClick: (Student, FeeCharge) -> Unit
) {
    // Strictly ONLY PAPER_FEE ledger! Never mixed with tuition.
    val paperCharges = remember(allCharges) {
        allCharges.filter { it.ledgerType == FeeLedgerType.PAPER_FEE }
    }
    val totalBilled = paperCharges.sumOf { it.netAmount }
    val totalCollected = paperCharges.sumOf { it.paidAmount }
    val totalPending = paperCharges.sumOf { it.remainingAmount }

    val filtered = remember(paperCharges, searchQuery, selectedClassId, pendingOnly, studentMap) {
        val q = searchQuery.trim().lowercase()
        paperCharges.filter { c ->
            val st = studentMap[c.studentId]
            val matchesClass = selectedClassId == null || c.classId == selectedClassId
            val matchesPending = !pendingOnly || c.remainingAmount > 0L
            val matchesQuery = q.isEmpty() ||
                c.title.lowercase().contains(q) ||
                (st?.name?.lowercase()?.contains(q) == true) ||
                (st?.rollNo?.lowercase()?.contains(q) == true)
            matchesClass && matchesPending && matchesQuery
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Button(
                onClick = onCreatePaperFeesClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("create_paper_fees_btn")
            ) {
                Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.create_paper_fee_charges))
            }
        }

        item {
            LedgerSummaryCard(billed = totalBilled, collected = totalCollected, pending = totalPending)
        }

        item {
            SearchPillBar(
                query = searchQuery,
                onQueryChange = onSearchChange,
                placeholder = stringResource(R.string.search_students_hint)
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = pendingOnly,
                        onClick = onPendingToggle,
                        label = { Text(stringResource(R.string.pending_only)) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedClassId == null,
                        onClick = { onClassFilterChange(null) },
                        label = { Text(stringResource(R.string.all)) }
                    )
                }
                items(classes, key = { it.id }) { cls ->
                    FilterChip(
                        selected = selectedClassId == cls.id,
                        onClick = { onClassFilterChange(if (selectedClassId == cls.id) null else cls.id) },
                        label = { Text(cls.name) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateView(
                    title = stringResource(R.string.paper_fee_empty),
                    icon = Icons.Default.Description
                )
            }
        } else {
            items(filtered, key = { it.id }) { charge ->
                val student = studentMap[charge.studentId] ?: return@items
                val statusLabel = when (charge.status) {
                    FeeStatus.PAID -> stringResource(R.string.status_paid)
                    FeeStatus.PARTIAL -> stringResource(R.string.status_partial)
                    else -> stringResource(R.string.status_unpaid)
                }
                val tone = when (charge.status) {
                    FeeStatus.PAID -> BadgeTone.GREEN
                    FeeStatus.PARTIAL -> BadgeTone.AMBER
                    else -> BadgeTone.RED
                }
                GoogleCard(contentPadding = PaddingValues(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ColoredInitialAvatar(student.name, student.photoPath, size = 40.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    student.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                StatusBadge(text = statusLabel, tone = tone)
                            }
                            Text(
                                "${charge.title} · ${classMap[charge.classId]?.name ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Fee ${FormatUtils.formatCurrency(charge.netAmount)} · Due ${FormatUtils.formatCurrency(charge.remainingAmount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (charge.remainingAmount > 0L) {
                            FilledTonalButton(
                                onClick = { onCollectClick(student, charge) },
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .testTag("collect_paper_${charge.id}")
                            ) {
                                Text(stringResource(R.string.collect))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OtherFeesLedgerTab(
    allCharges: List<FeeCharge>,
    studentMap: Map<Long, Student>,
    classMap: Map<Long, AcademyClass>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onAddOtherFeeClick: () -> Unit,
    onCollectClick: (Student, FeeCharge) -> Unit,
    onDeleteCharge: (FeeCharge) -> Unit
) {
    val otherCharges = remember(allCharges, searchQuery, studentMap) {
        val q = searchQuery.trim().lowercase()
        allCharges.filter { c ->
            c.ledgerType == FeeLedgerType.OTHER && (
                q.isEmpty() ||
                    c.title.lowercase().contains(q) ||
                    (studentMap[c.studentId]?.name?.lowercase()?.contains(q) == true)
                )
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Button(
                onClick = onAddOtherFeeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("add_other_fee_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_custom_fee))
            }
        }
        item {
            SearchPillBar(
                query = searchQuery,
                onQueryChange = onSearchChange,
                placeholder = stringResource(R.string.search_students_hint)
            )
        }
        if (otherCharges.isEmpty()) {
            item {
                EmptyStateView(
                    title = stringResource(R.string.other_fee_empty),
                    icon = Icons.Default.Receipt
                )
            }
        } else {
            items(otherCharges, key = { it.id }) { charge ->
                val student = studentMap[charge.studentId] ?: return@items
                val tone = when (charge.status) {
                    FeeStatus.PAID -> BadgeTone.GREEN
                    FeeStatus.PARTIAL -> BadgeTone.AMBER
                    else -> BadgeTone.RED
                }
                val statusLabel = when (charge.status) {
                    FeeStatus.PAID -> stringResource(R.string.status_paid)
                    FeeStatus.PARTIAL -> stringResource(R.string.status_partial)
                    else -> stringResource(R.string.status_unpaid)
                }
                GoogleCard(contentPadding = PaddingValues(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ColoredInitialAvatar(student.name, student.photoPath, size = 40.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    student.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                StatusBadge(text = statusLabel, tone = tone)
                            }
                            Text(
                                "${charge.title} · ${classMap[charge.classId]?.name ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Net ${FormatUtils.formatCurrency(charge.netAmount)} · Due ${FormatUtils.formatCurrency(charge.remainingAmount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (charge.remainingAmount > 0L) {
                            FilledTonalButton(onClick = { onCollectClick(student, charge) }) {
                                Text(stringResource(R.string.collect))
                            }
                        } else {
                            IconButton(onClick = { onDeleteCharge(charge) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultersTab(
    allCharges: List<FeeCharge>,
    studentMap: Map<Long, Student>,
    classMap: Map<Long, AcademyClass>,
    ledgerSegment: Int,
    onSegmentChange: (Int) -> Unit,
    onCollectTuition: (Student, FeeCharge, Long) -> Unit,
    onCollectSingle: (Student, FeeCharge) -> Unit
) {
    val targetType = when (ledgerSegment) {
        0 -> FeeLedgerType.TUITION
        1 -> FeeLedgerType.PAPER_FEE
        else -> FeeLedgerType.OTHER
    }

    val grouped = remember(allCharges, targetType) {
        allCharges
            .filter { it.ledgerType == targetType && it.remainingAmount > 0L }
            .groupBy { it.studentId }
            .toList()
            .sortedByDescending { (_, list) -> list.sumOf { it.remainingAmount } }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val labels = listOf(
                    stringResource(R.string.tab_tuition),
                    stringResource(R.string.tab_paper_fee),
                    stringResource(R.string.tab_other_fees)
                )
                labels.forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = ledgerSegment == idx,
                        onClick = { onSegmentChange(idx) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = labels.size)
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }
        }

        if (grouped.isEmpty()) {
            item {
                EmptyStateView(
                    title = stringResource(R.string.defaulters_empty),
                    icon = Icons.Default.CheckCircleOutline
                )
            }
        } else {
            items(grouped, key = { it.first }) { (studentId, charges) ->
                val student = studentMap[studentId] ?: return@items
                val totalDue = charges.sumOf { it.remainingAmount }
                val sorted = charges.sortedBy { it.monthKey ?: "" }
                val latest = sorted.last()
                val olderArrears = totalDue - latest.remainingAmount

                GoogleCard(contentPadding = PaddingValues(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ColoredInitialAvatar(student.name, student.photoPath, size = 40.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${student.name} (${classMap[student.classId]?.name ?: ""})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "${charges.size} unpaid · ${student.phone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Due: ${FormatUtils.formatCurrency(totalDue)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                if (targetType == FeeLedgerType.TUITION) {
                                    onCollectTuition(student, latest, olderArrears)
                                } else {
                                    onCollectSingle(student, sorted.first())
                                }
                            }
                        ) {
                            Text(stringResource(R.string.collect))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerSummaryCard(billed: Long, collected: Long, pending: Long) {
    GoogleCard(contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(stringResource(R.string.billed_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(FormatUtils.formatCurrency(billed), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.collected_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(FormatUtils.formatCurrency(collected), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = StatusGreenText)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.pending_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(FormatUtils.formatCurrency(pending), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = StatusRedText)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePaperFeeDialog(
    classes: List<AcademyClass>,
    exams: List<Exam>,
    onDismiss: () -> Unit,
    onCreate: (classId: Long, title: String, amount: Long, dueDate: Long) -> Unit
) {
    var selectedClassId by rememberSaveable { mutableStateOf(classes.firstOrNull()?.id ?: 0L) }
    var paperTitle by rememberSaveable { mutableStateOf("") }
    var amountStr by rememberSaveable { mutableStateOf("") }
    var dueDate by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var classMenuExpanded by remember { mutableStateOf(false) }

    var titleError by rememberSaveable { mutableStateOf<String?>(null) }
    var amountError by rememberSaveable { mutableStateOf<String?>(null) }
    val requiredMsg = stringResource(R.string.field_required)
    val invalidAmountMsg = stringResource(R.string.invalid_amount)

    val classExams = remember(exams, selectedClassId) {
        exams.filter { it.classId == selectedClassId }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_paper_fee_charges)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (classes.isEmpty()) {
                    Text(stringResource(R.string.no_classes_prompt), color = MaterialTheme.colorScheme.error)
                } else {
                    val selectedClass = classes.find { it.id == selectedClassId }
                    ExposedDropdownMenuBox(
                        expanded = classMenuExpanded,
                        onExpandedChange = { classMenuExpanded = !classMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedClass?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.select_class)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = classMenuExpanded,
                            onDismissRequest = { classMenuExpanded = false }
                        ) {
                            classes.forEach { cls ->
                                DropdownMenuItem(
                                    text = { Text(cls.name) },
                                    onClick = {
                                        selectedClassId = cls.id
                                        classMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (classExams.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(classExams, key = { it.id }) { ex ->
                                AssistChip(
                                    onClick = { paperTitle = "${ex.name} Paper Fee"; titleError = null },
                                    label = { Text(ex.name) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = paperTitle,
                        onValueChange = { paperTitle = it; titleError = null },
                        label = { Text(stringResource(R.string.paper_fee_title_hint)) },
                        isError = titleError != null,
                        supportingText = titleError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("paper_fee_title_input")
                    )

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it; amountError = null },
                        label = { Text(stringResource(R.string.paper_fee_per_student)) },
                        isError = amountError != null,
                        supportingText = amountError?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("paper_fee_amount_input")
                    )

                    DatePickerField(
                        label = stringResource(R.string.select_date),
                        dateMillis = dueDate,
                        onDateSelected = { dueDate = it }
                    )
                }
            }
        },
        confirmButton = {
            if (classes.isNotEmpty()) {
                Button(
                    onClick = {
                        var valid = true
                        if (paperTitle.isBlank()) { titleError = requiredMsg; valid = false }
                        val amt = amountStr.trim().toLongOrNull()
                        if (amt == null || amt <= 0L) { amountError = invalidAmountMsg; valid = false }
                        if (valid && amt != null && selectedClassId != 0L) {
                            onCreate(selectedClassId, paperTitle.trim(), amt, dueDate)
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_paper_fees_btn")
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateOtherFeeDialog(
    students: List<Student>,
    classMap: Map<Long, AcademyClass>,
    onDismiss: () -> Unit,
    onCreate: (studentId: Long, subType: String, title: String, amount: Long, discount: Long, dueDate: Long) -> Unit
) {
    var selectedStudentId by rememberSaveable { mutableStateOf(students.firstOrNull()?.id ?: 0L) }
    var isAdmission by rememberSaveable { mutableStateOf(true) }
    var customTitle by rememberSaveable { mutableStateOf("") }
    var amountStr by rememberSaveable { mutableStateOf("") }
    var discountStr by rememberSaveable { mutableStateOf("") }
    var dueDate by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var studentMenuExpanded by remember { mutableStateOf(false) }

    var titleError by rememberSaveable { mutableStateOf<String?>(null) }
    var amountError by rememberSaveable { mutableStateOf<String?>(null) }
    val requiredMsg = stringResource(R.string.field_required)
    val invalidAmountMsg = stringResource(R.string.invalid_amount)
    val admissionLabel = stringResource(R.string.fee_type_admission)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_custom_fee)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (students.isEmpty()) {
                    Text(stringResource(R.string.students_empty_title), color = MaterialTheme.colorScheme.error)
                } else {
                    val selectedStudent = students.find { it.id == selectedStudentId }
                    ExposedDropdownMenuBox(
                        expanded = studentMenuExpanded,
                        onExpandedChange = { studentMenuExpanded = !studentMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedStudent?.let { "${it.name} (${classMap[it.classId]?.name ?: ""})" } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.select_student)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = studentMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = studentMenuExpanded,
                            onDismissRequest = { studentMenuExpanded = false }
                        ) {
                            students.forEach { st ->
                                DropdownMenuItem(
                                    text = { Text("${st.name} (${classMap[st.classId]?.name ?: ""})") },
                                    onClick = {
                                        selectedStudentId = st.id
                                        studentMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isAdmission,
                            onClick = { isAdmission = true },
                            label = { Text(stringResource(R.string.fee_type_admission)) }
                        )
                        FilterChip(
                            selected = !isAdmission,
                            onClick = { isAdmission = false },
                            label = { Text(stringResource(R.string.fee_type_custom)) }
                        )
                    }

                    if (!isAdmission) {
                        OutlinedTextField(
                            value = customTitle,
                            onValueChange = { customTitle = it; titleError = null },
                            label = { Text(stringResource(R.string.custom_fee_title)) },
                            isError = titleError != null,
                            supportingText = titleError?.let { { Text(it) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it; amountError = null },
                        label = { Text(stringResource(R.string.fee_amount)) },
                        isError = amountError != null,
                        supportingText = amountError?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = discountStr,
                        onValueChange = { discountStr = it },
                        label = { Text(stringResource(R.string.discount_amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    DatePickerField(
                        label = stringResource(R.string.select_date),
                        dateMillis = dueDate,
                        onDateSelected = { dueDate = it }
                    )
                }
            }
        },
        confirmButton = {
            if (students.isNotEmpty()) {
                Button(
                    onClick = {
                        var valid = true
                        val resolvedTitle = if (isAdmission) admissionLabel else customTitle.trim()
                        if (!isAdmission && customTitle.isBlank()) {
                            titleError = requiredMsg; valid = false
                        }
                        val amt = amountStr.trim().toLongOrNull()
                        val disc = if (discountStr.isBlank()) 0L else discountStr.trim().toLongOrNull()
                        if (amt == null || amt <= 0L || disc == null || disc < 0L || disc > amt) {
                            amountError = invalidAmountMsg; valid = false
                        }
                        if (valid && amt != null && disc != null && selectedStudentId != 0L) {
                            onCreate(
                                selectedStudentId,
                                if (isAdmission) "Admission Fee" else "Custom Fee",
                                resolvedTitle,
                                amt,
                                disc,
                                dueDate
                            )
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
