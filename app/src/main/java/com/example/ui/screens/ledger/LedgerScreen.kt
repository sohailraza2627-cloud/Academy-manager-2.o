package com.example.ui.screens.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.data.local.*
import com.example.ui.components.*
import com.example.ui.theme.StatusGreenText
import com.example.ui.theme.StatusRedText
import com.example.ui.viewmodel.DataLoadState
import com.example.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    ledgerState: DataLoadState<List<LedgerEntry>>,
    feePayments: List<FeePayment>,
    isAdmin: Boolean = true,
    onBack: () -> Unit,
    onSaveEntry: (LedgerEntry) -> Unit,
    onDeleteEntry: (LedgerEntry) -> Unit
) {
    var monthKey by rememberSaveable { mutableStateOf(FormatUtils.currentMonthKey()) }
    var filterType by rememberSaveable { mutableStateOf("ALL") } // ALL, INCOME, EXPENSE
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    val monthFeeIncome = remember(feePayments, monthKey) {
        feePayments.filter { it.monthKey == monthKey && it.ledgerType != FeeLedgerType.PAPER_FEE }
            .sumOf { it.amountPaid }
    }
    val monthPaperIncome = remember(feePayments, monthKey) {
        feePayments.filter { it.monthKey == monthKey && it.ledgerType == FeeLedgerType.PAPER_FEE }
            .sumOf { it.amountPaid }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ledger_title)) },
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
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_ledger_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_ledger_entry))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (ledgerState) {
                is DataLoadState.Loading -> LoadingStateView()
                is DataLoadState.Error -> ErrorStateView(ledgerState.message)
                is DataLoadState.Success -> {
                    val monthEntries = remember(ledgerState.data, monthKey) {
                        ledgerState.data.filter { it.monthKey == monthKey }
                    }
                    val manualIncome = monthEntries.filter { it.type == LedgerType.INCOME }.sumOf { it.amount }
                    val totalExpenses = monthEntries.filter { it.type == LedgerType.EXPENSE }.sumOf { it.amount }
                    val totalIncome = monthFeeIncome + monthPaperIncome + manualIncome
                    val net = totalIncome - totalExpenses

                    val filteredEntries = remember(monthEntries, filterType) {
                        when (filterType) {
                            LedgerType.INCOME -> monthEntries.filter { it.type == LedgerType.INCOME }
                            LedgerType.EXPENSE -> monthEntries.filter { it.type == LedgerType.EXPENSE }
                            else -> monthEntries
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                MonthSwitcher(monthKey = monthKey, onMonthChange = { monthKey = it })
                            }
                        }

                        item {
                            GoogleCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(stringResource(R.string.income), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(FormatUtils.formatCurrency(totalIncome), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = StatusGreenText)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(stringResource(R.string.expense), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(FormatUtils.formatCurrency(totalExpenses), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = StatusRedText)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(stringResource(R.string.net_balance), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            FormatUtils.formatCurrency(net),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (net >= 0L) StatusGreenText else StatusRedText
                                        )
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Text(
                                    "${stringResource(R.string.fee_collections_summary)}: Tuition/Other ${FormatUtils.formatCurrency(monthFeeIncome)} · Paper Fee ${FormatUtils.formatCurrency(monthPaperIncome)} · Manual ${FormatUtils.formatCurrency(manualIncome)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = filterType == "ALL",
                                    onClick = { filterType = "ALL" },
                                    label = { Text(stringResource(R.string.all)) }
                                )
                                FilterChip(
                                    selected = filterType == LedgerType.INCOME,
                                    onClick = { filterType = LedgerType.INCOME },
                                    label = { Text(stringResource(R.string.income)) }
                                )
                                FilterChip(
                                    selected = filterType == LedgerType.EXPENSE,
                                    onClick = { filterType = LedgerType.EXPENSE },
                                    label = { Text(stringResource(R.string.expense)) }
                                )
                            }
                        }

                        if (filteredEntries.isEmpty()) {
                            item {
                                EmptyStateView(
                                    title = stringResource(R.string.ledger_empty),
                                    icon = Icons.Default.AccountBalanceWallet
                                )
                            }
                        } else {
                            items(filteredEntries, key = { it.id }) { entry ->
                                val isIncome = entry.type == LedgerType.INCOME
                                GoogleCard(contentPadding = PaddingValues(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                                StatusBadge(
                                                    text = entry.category,
                                                    tone = if (isIncome) BadgeTone.GREEN else BadgeTone.RED
                                                )
                                            }
                                            Text(
                                                FormatUtils.formatDate(entry.date),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (entry.notes.isNotBlank()) {
                                                Text(entry.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = (if (isIncome) "+" else "-") + FormatUtils.formatCurrency(entry.amount),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isIncome) StatusGreenText else StatusRedText
                                            )
                                            if (isAdmin) {
                                                IconButton(onClick = { onDeleteEntry(entry) }) {
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
            }
        }
    }

    if (showAddDialog) {
        AddLedgerEntryDialog(
            onDismiss = { showAddDialog = false },
            onSave = {
                onSaveEntry(it)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddLedgerEntryDialog(
    onDismiss: () -> Unit,
    onSave: (LedgerEntry) -> Unit
) {
    var type by rememberSaveable { mutableStateOf(LedgerType.EXPENSE) }
    val expenseCategories = listOf("Rent", "Salaries", "Electricity & Bills", "Supplies", "Maintenance", "Other")
    val incomeCategories = listOf("Other Income", "Books & Notes", "Donation", "Misc")
    val categories = if (type == LedgerType.EXPENSE) expenseCategories else incomeCategories

    var category by rememberSaveable { mutableStateOf(expenseCategories.first()) }
    var title by rememberSaveable { mutableStateOf("") }
    var amountStr by rememberSaveable { mutableStateOf("") }
    var dateMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var notes by rememberSaveable { mutableStateOf("") }

    var titleErr by rememberSaveable { mutableStateOf<String?>(null) }
    var amountErr by rememberSaveable { mutableStateOf<String?>(null) }
    val requiredMsg = stringResource(R.string.field_required)
    val invalidAmountMsg = stringResource(R.string.invalid_amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_ledger_entry)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == LedgerType.EXPENSE,
                        onClick = {
                            type = LedgerType.EXPENSE
                            category = expenseCategories.first()
                        },
                        label = { Text(stringResource(R.string.expense)) }
                    )
                    FilterChip(
                        selected = type == LedgerType.INCOME,
                        onClick = {
                            type = LedgerType.INCOME
                            category = incomeCategories.first()
                        },
                        label = { Text(stringResource(R.string.income)) }
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; titleErr = null },
                    label = { Text(stringResource(R.string.entry_title)) },
                    isError = titleErr != null,
                    supportingText = titleErr?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                    dateMillis = dateMillis,
                    onDateSelected = { dateMillis = it }
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    var valid = true
                    if (title.isBlank()) { titleErr = requiredMsg; valid = false }
                    val amt = amountStr.trim().toLongOrNull()
                    if (amt == null || amt <= 0L) { amountErr = invalidAmountMsg; valid = false }
                    if (valid && amt != null) {
                        onSave(
                            LedgerEntry(
                                type = type,
                                category = category,
                                title = title.trim(),
                                amount = amt,
                                date = dateMillis,
                                monthKey = FormatUtils.toMonthKey(dateMillis),
                                notes = notes.trim()
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
