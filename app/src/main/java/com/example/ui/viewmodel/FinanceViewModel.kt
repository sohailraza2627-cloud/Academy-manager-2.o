package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AcademyManagerApp
import com.example.data.local.*
import com.example.data.repository.ReceiptResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AcademyManagerApp
    private val repo get() = app.repository

    val feeChargesState: StateFlow<DataLoadState<List<FeeCharge>>> = repo.feeChargesFlow
        .map<List<FeeCharge>, DataLoadState<List<FeeCharge>>> { DataLoadState.Success(it) }
        .catch { emit(DataLoadState.Error(it.message ?: "Failed to load fees")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DataLoadState.Loading)

    val feeCharges: StateFlow<List<FeeCharge>> = repo.feeChargesFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val feePayments: StateFlow<List<FeePayment>> = repo.feePaymentsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val ledgerEntriesState: StateFlow<DataLoadState<List<LedgerEntry>>> = repo.ledgerEntriesFlow
        .map<List<LedgerEntry>, DataLoadState<List<LedgerEntry>>> { DataLoadState.Success(it) }
        .catch { emit(DataLoadState.Error(it.message ?: "Failed to load ledger")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DataLoadState.Loading)

    val ledgerEntries: StateFlow<List<LedgerEntry>> = repo.ledgerEntriesFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun observeStudentFeeCharges(studentId: String): Flow<List<FeeCharge>> =
        repo.observeStudentFeeCharges(studentId)

    fun observeStudentFeePayments(studentId: String): Flow<List<FeePayment>> =
        repo.observeStudentFeePayments(studentId)

    fun generateMonthlyTuitionFees(
        monthKey: String,
        classIdFilter: String? = null,
        onResult: (Int, Int) -> Unit
    ) {
        viewModelScope.launch {
            val (created, skipped) = repo.generateMonthlyTuitionFees(monthKey, classIdFilter)
            onResult(created, skipped)
        }
    }

    fun createPaperFeeCharges(
        classId: String,
        paperTitle: String,
        amountPerStudent: Long,
        dueDate: Long,
        onResult: (Int, Int) -> Unit
    ) {
        viewModelScope.launch {
            val (created, skipped) = repo.createPaperFeeCharges(classId, paperTitle, amountPerStudent, dueDate)
            onResult(created, skipped)
        }
    }

    fun createOtherFeeCharge(
        studentId: String,
        feeSubType: String,
        title: String,
        amount: Long,
        discount: Long,
        dueDate: Long,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            repo.createOtherFeeCharge(studentId, feeSubType, title, amount, discount, dueDate)
            onDone()
        }
    }

    fun collectSingleChargePayment(
        chargeId: String,
        amountPaying: Long,
        discountAdded: Long,
        paidDate: Long,
        notes: String,
        onSuccess: (ReceiptResult) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val receipt = repo.collectSingleChargePayment(
                    chargeId = chargeId,
                    amountPaying = amountPaying,
                    discountAdded = discountAdded,
                    paidDate = paidDate,
                    notes = notes
                )
                onSuccess(receipt)
            } catch (e: Exception) {
                onError(e.message ?: "Payment failed")
            }
        }
    }

    fun collectTuitionPaymentFifo(
        studentId: String,
        targetChargeId: String?,
        amountPaying: Long,
        discountOnTarget: Long,
        paidDate: Long,
        notes: String,
        onSuccess: (ReceiptResult) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val receipt = repo.collectTuitionPaymentFifo(
                    studentId = studentId,
                    targetChargeId = targetChargeId,
                    amountPaying = amountPaying,
                    discountOnTarget = discountOnTarget,
                    paidDate = paidDate,
                    notes = notes
                )
                onSuccess(receipt)
            } catch (e: Exception) {
                onError(e.message ?: "Payment failed")
            }
        }
    }

    fun deleteFeeCharge(charge: FeeCharge) {
        viewModelScope.launch { repo.deleteFeeCharge(charge) }
    }

    fun saveLedgerEntry(entry: LedgerEntry, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.saveLedgerEntry(entry)
            onDone()
        }
    }

    fun deleteLedgerEntry(entry: LedgerEntry) {
        viewModelScope.launch { repo.deleteLedgerEntry(entry) }
    }
}
