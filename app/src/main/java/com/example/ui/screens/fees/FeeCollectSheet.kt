package com.example.ui.screens.fees

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.Student
import com.example.ui.components.DatePickerField
import com.example.ui.components.GoogleCard
import com.example.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeeCollectSheet(
    student: Student,
    className: String,
    title: String,
    currentChargeDue: Long,
    arrearsDue: Long = 0L,
    isTuitionWithArrears: Boolean = false,
    onDismiss: () -> Unit,
    onSubmit: (amountPaying: Long, discountAdded: Long, paidDate: Long, notes: String) -> Unit
) {
    val totalMaxDue = currentChargeDue + arrearsDue
    var amountStr by rememberSaveable(student.id, title) { mutableStateOf(totalMaxDue.toString()) }
    var discountStr by rememberSaveable(student.id, title) { mutableStateOf("") }
    var paidDate by rememberSaveable(student.id, title) { mutableStateOf(System.currentTimeMillis()) }
    var notes by rememberSaveable(student.id, title) { mutableStateOf("") }
    var errorMsg by rememberSaveable { mutableStateOf<String?>(null) }

    val invalidAmountMsg = stringResource(R.string.invalid_amount)
    val discountExceedsMsg = stringResource(R.string.discount_exceeds_error)

    val discountVal = discountStr.trim().toLongOrNull() ?: 0L
    val effectiveMax = (totalMaxDue - discountVal).coerceAtLeast(0L)
    val overpaymentMsg = stringResource(R.string.overpayment_error, FormatUtils.formatCurrency(effectiveMax))

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.collect_payment),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            GoogleCard(contentPadding = PaddingValues(14.dp)) {
                Text(
                    text = "${student.name} · $className",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (isTuitionWithArrears && arrearsDue > 0L) {
                    Text(
                        text = stringResource(R.string.arrears_label, FormatUtils.formatCurrency(arrearsDue)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.current_month_label, FormatUtils.formatCurrency(currentChargeDue)),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = stringResource(R.string.total_payable_label, FormatUtils.formatCurrency(totalMaxDue)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it; errorMsg = null },
                label = { Text(stringResource(R.string.amount_paying)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("collect_amount_input")
            )

            OutlinedTextField(
                value = discountStr,
                onValueChange = { discountStr = it; errorMsg = null },
                label = { Text(stringResource(R.string.discount_amount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("collect_discount_input")
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

            if (errorMsg != null) {
                Text(
                    text = errorMsg.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        val amt = amountStr.trim().toLongOrNull()
                        val disc = if (discountStr.isBlank()) 0L else discountStr.trim().toLongOrNull()
                        when {
                            disc == null || disc < 0L -> errorMsg = invalidAmountMsg
                            disc > currentChargeDue -> errorMsg = discountExceedsMsg
                            amt == null || amt <= 0L -> errorMsg = invalidAmountMsg
                            amt > (totalMaxDue - disc) -> errorMsg = overpaymentMsg
                            else -> onSubmit(amt, disc, paidDate, notes)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("confirm_collect_payment_btn")
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
