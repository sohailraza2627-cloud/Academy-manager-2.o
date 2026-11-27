package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.local.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

enum class SyncStatus {
    SYNCED,
    SYNCING,
    OFFLINE
}

object SyncCollections {
    const val USERS = "users"
    const val INVITES = "invites"
    const val ACADEMIES = "academies"
    const val MEMBERS = "members"
    const val CLASSES = "classes"
    const val SUBJECTS = "subjects"
    const val TIMETABLE = "timetable"
    const val STUDENTS = "students"
    const val TEACHERS = "teachers"
    const val SALARY_PAYMENTS = "salary_payments"
    const val ATTENDANCE = "attendance"
    const val FEE_CHARGES = "fee_charges"
    const val FEE_PAYMENTS = "fee_payments"
    const val EXAMS = "exams"
    const val MARKS = "marks"
    const val EXPENSES = "expenses"
}

class CloudSyncManager(
    private val context: Context,
    private val db: AcademyDatabase,
    private val prefs: PreferencesRepository,
    val networkMonitor: NetworkMonitor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pushMutex = Mutex()
    private val registrations = mutableListOf<ListenerRegistration>()
    private var activeAcademyId: String? = null

    private val _isSyncing = MutableStateFlow(false)

    private val pendingCountFlow: Flow<Int> = combine(
        listOf(
            db.academyProfileDao().observePendingCount(),
            db.academyClassDao().observePendingCount(),
            db.subjectDao().observePendingCount(),
            db.timetableDao().observePendingCount(),
            db.studentDao().observePendingCount(),
            db.teacherDao().observePendingCount(),
            db.salaryPaymentDao().observePendingCount(),
            db.attendanceDao().observePendingCount(),
            db.feeChargeDao().observePendingCount(),
            db.feePaymentDao().observePendingCount(),
            db.ledgerEntryDao().observePendingCount(),
            db.examDao().observePendingCount(),
            db.examMarkDao().observePendingCount()
        )
    ) { counts -> counts.sum() }

    val syncStatus: StateFlow<SyncStatus> = combine(
        networkMonitor.isOnline,
        _isSyncing,
        pendingCountFlow,
        prefs.authSessionFlow
    ) { online, syncing, pendingCount, session ->
        when {
            !online || session.isOfflineLocalMode -> SyncStatus.OFFLINE
            syncing || pendingCount > 0 -> SyncStatus.SYNCING
            else -> SyncStatus.SYNCED
        }
    }.stateIn(scope, SharingStarted.Eagerly, SyncStatus.OFFLINE)

    init {
        // Automatically push pending local edits whenever internet reconnects
        scope.launch {
            var wasOnline = networkMonitor.isOnline.value
            networkMonitor.isOnline.collect { online ->
                if (online && !wasOnline) {
                    val session = prefs.getAuthSessionOnce()
                    if (session.academyId.isNotBlank() && !session.isOfflineLocalMode) {
                        requestPush()
                    }
                }
                wasOnline = online
            }
        }
    }

    fun startListeners(academyId: String) {
        if (academyId.isBlank()) return
        if (activeAcademyId == academyId && registrations.isNotEmpty()) return
        stopListeners()

        val firestore = FirebaseClientProvider.getFirestoreOrNull(context) ?: return
        val auth = FirebaseClientProvider.getAuthOrNull(context)
        if (auth?.currentUser == null) return

        activeAcademyId = academyId
        val academyRef = firestore.collection(SyncCollections.ACADEMIES).document(academyId)

        // Academy profile doc
        registrations += academyRef.addSnapshotListener { doc, err ->
            if (err != null) {
                handleFirestoreError(err, OperationType.GET, "${SyncCollections.ACADEMIES}/$academyId")
                return@addSnapshotListener
            }
            if (doc != null && doc.exists()) {
                scope.launch { mergeProfile(doc) }
            }
        }

        fun listen(subCollection: String, onSnapshots: suspend (List<DocumentSnapshot>) -> Unit) {
            registrations += academyRef.collection(subCollection).addSnapshotListener { snap, err ->
                if (err != null) {
                    handleFirestoreError(
                        err,
                        OperationType.LIST,
                        "${SyncCollections.ACADEMIES}/$academyId/$subCollection"
                    )
                    return@addSnapshotListener
                }
                if (snap != null && !snap.isEmpty) {
                    scope.launch { onSnapshots(snap.documents) }
                }
            }
        }

        listen(SyncCollections.CLASSES) { docs -> mergeClasses(docs) }
        listen(SyncCollections.SUBJECTS) { docs -> mergeSubjects(docs) }
        listen(SyncCollections.TIMETABLE) { docs -> mergeTimetable(docs) }
        listen(SyncCollections.STUDENTS) { docs -> mergeStudents(docs) }
        listen(SyncCollections.TEACHERS) { docs -> mergeTeachers(docs) }
        listen(SyncCollections.SALARY_PAYMENTS) { docs -> mergeSalaryPayments(docs) }
        listen(SyncCollections.ATTENDANCE) { docs -> mergeAttendance(docs) }
        listen(SyncCollections.FEE_CHARGES) { docs -> mergeFeeCharges(docs) }
        listen(SyncCollections.FEE_PAYMENTS) { docs -> mergeFeePaymentsAppendOnly(docs) }
        listen(SyncCollections.EXAMS) { docs -> mergeExams(docs) }
        listen(SyncCollections.MARKS) { docs -> mergeExamMarks(docs) }
        listen(SyncCollections.EXPENSES) { docs -> mergeLedgerEntries(docs) }
        listen(SyncCollections.MEMBERS) { docs -> mergeMembers(docs) }
    }

    fun stopListeners() {
        registrations.forEach { runCatching { it.remove() } }
        registrations.clear()
        activeAcademyId = null
    }

    fun requestPush() {
        enqueueRetryWorker()
        if (networkMonitor.isOnline.value) {
            scope.launch {
                pushPendingChanges()
            }
        }
    }

    fun enqueueRetryWorker() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                SYNC_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enqueue SyncWorker", e)
        }
    }

    suspend fun pushPendingChanges(): Boolean {
        val session = prefs.getAuthSessionOnce()
        if (!session.isAuthenticated || session.isOfflineLocalMode || session.academyId.isBlank()) {
            return true
        }
        if (!networkMonitor.isOnline.value) return false

        val firestore = FirebaseClientProvider.getFirestoreOrNull(context) ?: return true
        val auth = FirebaseClientProvider.getAuthOrNull(context) ?: return true
        if (auth.currentUser == null) return false

        return pushMutex.withLock {
            _isSyncing.value = true
            try {
                val academyRef = firestore.collection(SyncCollections.ACADEMIES).document(session.academyId)
                val isAdmin = session.role == UserRole.ADMIN

                // Teachers can ONLY push attendance records (enforced both locally and in Firestore rules)
                val pendingAttendance = db.attendanceDao().getPendingSync()
                for (att in pendingAttendance) {
                    withTimeout(SYNC_TIMEOUT_MS) {
                        academyRef.collection(SyncCollections.ATTENDANCE).document(att.id)
                            .set(att.toFirestoreMap()).await()
                    }
                    db.attendanceDao().markSynced(att.id, att.updatedAt)
                }

                if (!isAdmin) {
                    return@withLock true
                }

                // Academy profile
                db.academyProfileDao().getPendingSync()?.let { prof ->
                    withTimeout(SYNC_TIMEOUT_MS) {
                        academyRef.set(prof.toFirestoreMap(), SetOptions.merge()).await()
                    }
                    db.academyProfileDao().markSynced(prof.updatedAt)
                }

                for (cls in db.academyClassDao().getPendingSync()) {
                    withTimeout(SYNC_TIMEOUT_MS) {
                        academyRef.collection(SyncCollections.CLASSES).document(cls.id)
                            .set(cls.toFirestoreMap()).await()
                    }
                    db.academyClassDao().markSynced(cls.id, cls.updatedAt)
                }

                for (sub in db.subjectDao().getPendingSync()) {
                    withTimeout(SYNC_TIMEOUT_MS) {
                        academyRef.collection(SyncCollections.SUBJECTS).document(sub.id)
                            .set(sub.toFirestoreMap()).await()
                    }
                    db.subjectDao().markSynced(sub.id, sub.updatedAt)
                }

                for (tt in db.timetableDao().getPendingSync()) {
                    withTimeout(SYNC_TIMEOUT_MS) {
                        academyRef.collection(SyncCollections.TIMETABLE).document(tt.id)
                            .set(tt.toFirestoreMap()).await()
                    }
                    db.timetableDao().markSynced(tt.id, tt.updatedAt)
                }

                for (st in db.studentDao().getPendingSync()) {
                    withTimeout(SYNC_TIMEOUT_MS) {
                        academyRef.collection(SyncCollections.STUDENTS).document(st.id)
                            .set(st.toFirestoreMap()).await()
                    }
                    db.studentDao().markSynced(st.id, st.updatedAt)
                }

                for (t in db.teacherDao().getPendingSync()) {
                    withTimeout(SYNC_TIMEOUT_MS) {
                        academyRef.collection(SyncCollections.TEACHERS).document(t.id)
                            .set(t.toFirestoreMap()).await()
                    }
                    db.teacherDao().markSynced(t.id, t.updatedAt)
                }

                for (sp in db.salaryPaymentDao().getPendingSync()) {
                    withTimeout(SYNC_TIMEOUT_MS) {
                        academyRef.collection(SyncCollections.SALARY_PAYMENTS).document(sp.id)
                            .set(sp.toFirestoreMap()).await()
                    }
                    db.salaryPaymentDao().markSynced(sp.id, sp.updatedAt)
                }

                for (charge in db.feeChargeDao().getPendingSync()) {
                    withTimeout(SYNC_TIMEOUT_MS) {
                        academyRef.collection(SyncCollections.FEE_CHARGES).document(charge.id)
                            .set(charge.toFirestoreMap()).await()
                    }
                    db.feeChargeDao().markSynced(charge.id, charge.updatedAt)
                }

                // Append-only fee_payments: create once if not already present, never overwrite
                for (pay in db.feePaymentDao().getPendingSync()) {
                    val docRef = academyRef.collection(SyncCollections.FEE_PAYMENTS).document(pay.id)
                    withTimeout(SYNC_TIMEOUT_MS) {
                        val existing = docRef.get().await()
                        if (!existing.exists()) {
                            docRef.set(pay.toFirestoreMap()).await()
                        }
                    }
                    db.feePaymentDao().markSynced(pay.id)
                }

                for (exam in db.examDao().getPendingSync()) {
                    withTimeout(SYNC_TIMEOUT_MS) {
                        academyRef.collection(SyncCollections.EXAMS).document(exam.id)
                            .set(exam.toFirestoreMap()).await()
                    }
                    db.examDao().markSynced(exam.id, exam.updatedAt)
                }

                for (mark in db.examMarkDao().getPendingSync()) {
                    withTimeout(SYNC_TIMEOUT_MS) {
                        academyRef.collection(SyncCollections.MARKS).document(mark.id)
                            .set(mark.toFirestoreMap()).await()
                    }
                    db.examMarkDao().markSynced(mark.id, mark.updatedAt)
                }

                for (entry in db.ledgerEntryDao().getPendingSync()) {
                    withTimeout(SYNC_TIMEOUT_MS) {
                        academyRef.collection(SyncCollections.EXPENSES).document(entry.id)
                            .set(entry.toFirestoreMap()).await()
                    }
                    db.ledgerEntryDao().markSynced(entry.id, entry.updatedAt)
                }

                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(
                    e,
                    OperationType.WRITE,
                    "${SyncCollections.ACADEMIES}/${session.academyId}"
                )
                Log.w(TAG, "Push pending changes failed (will retry)", e)
                false
            } finally {
                _isSyncing.value = false
            }
        }
    }

    // --- Merge Helpers (Last-Write-Wins by updatedAt; fee_payments append-only) ---

    private suspend fun mergeProfile(doc: DocumentSnapshot) {
        val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
        val name = doc.getString("academyName") ?: return
        db.withTransaction {
            val local = db.academyProfileDao().getProfileOnce()
            if (shouldApplyRemote(local?.updatedAt, remoteUpdatedAt)) {
                db.academyProfileDao().upsert(
                    AcademyProfile(
                        id = AcademyProfile.PROFILE_ID,
                        academyName = name,
                        tagline = doc.getString("tagline") ?: "",
                        phone = doc.getString("phone") ?: "",
                        address = doc.getString("address") ?: "",
                        logoPath = local?.logoPath,
                        updatedAt = remoteUpdatedAt,
                        deleted = false,
                        pendingSync = false
                    )
                )
            }
        }
    }

    private suspend fun mergeClasses(docs: List<DocumentSnapshot>) {
        db.withTransaction {
            val dao = db.academyClassDao()
            for (doc in docs) {
                val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                val local = dao.getByIdIncludingDeleted(doc.id)
                if (shouldApplyRemote(local?.updatedAt, remoteUpdatedAt)) {
                    dao.upsert(
                        AcademyClass(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            monthlyFee = doc.getLong("monthlyFee") ?: 0L,
                            createdAt = doc.getLong("createdAt") ?: remoteUpdatedAt,
                            updatedAt = remoteUpdatedAt,
                            deleted = doc.getBoolean("deleted") ?: false,
                            pendingSync = false
                        )
                    )
                }
            }
        }
    }

    private suspend fun mergeSubjects(docs: List<DocumentSnapshot>) {
        db.withTransaction {
            val dao = db.subjectDao()
            for (doc in docs) {
                val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                val local = dao.getByIdIncludingDeleted(doc.id)
                if (shouldApplyRemote(local?.updatedAt, remoteUpdatedAt)) {
                    dao.upsert(
                        Subject(
                            id = doc.id,
                            classId = doc.getString("classId") ?: "",
                            name = doc.getString("name") ?: "",
                            totalMarks = (doc.getLong("totalMarks") ?: 100L).toInt(),
                            updatedAt = remoteUpdatedAt,
                            deleted = doc.getBoolean("deleted") ?: false,
                            pendingSync = false
                        )
                    )
                }
            }
        }
    }

    private suspend fun mergeTimetable(docs: List<DocumentSnapshot>) {
        db.withTransaction {
            val dao = db.timetableDao()
            for (doc in docs) {
                val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                val local = dao.getByIdIncludingDeleted(doc.id)
                if (shouldApplyRemote(local?.updatedAt, remoteUpdatedAt)) {
                    dao.upsert(
                        TimetableEntry(
                            id = doc.id,
                            classId = doc.getString("classId") ?: "",
                            dayOfWeek = (doc.getLong("dayOfWeek") ?: 1L).toInt(),
                            subjectName = doc.getString("subjectName") ?: "",
                            teacherName = doc.getString("teacherName") ?: "",
                            startTime = doc.getString("startTime") ?: "",
                            endTime = doc.getString("endTime") ?: "",
                            updatedAt = remoteUpdatedAt,
                            deleted = doc.getBoolean("deleted") ?: false,
                            pendingSync = false
                        )
                    )
                }
            }
        }
    }

    private suspend fun mergeStudents(docs: List<DocumentSnapshot>) {
        db.withTransaction {
            val dao = db.studentDao()
            for (doc in docs) {
                val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                val local = dao.getByIdIncludingDeleted(doc.id)
                if (shouldApplyRemote(local?.updatedAt, remoteUpdatedAt)) {
                    dao.upsert(
                        Student(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            fatherName = doc.getString("fatherName") ?: "",
                            phone = doc.getString("phone") ?: "",
                            address = doc.getString("address") ?: "",
                            classId = doc.getString("classId") ?: "",
                            rollNo = doc.getString("rollNo") ?: "",
                            admissionDate = doc.getLong("admissionDate") ?: remoteUpdatedAt,
                            photoPath = local?.photoPath,
                            guardianWhatsapp = doc.getString("guardianWhatsapp") ?: "",
                            whatsappHistory = doc.getString("whatsappHistory") ?: "",
                            monthlyDiscount = doc.getLong("monthlyDiscount") ?: 0L,
                            status = doc.getString("status") ?: StudentStatus.ACTIVE,
                            leftDate = doc.getLong("leftDate"),
                            createdAt = doc.getLong("createdAt") ?: remoteUpdatedAt,
                            updatedAt = remoteUpdatedAt,
                            deleted = doc.getBoolean("deleted") ?: false,
                            pendingSync = false
                        )
                    )
                }
            }
        }
    }

    private suspend fun mergeTeachers(docs: List<DocumentSnapshot>) {
        db.withTransaction {
            val dao = db.teacherDao()
            for (doc in docs) {
                val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                val local = dao.getByIdIncludingDeleted(doc.id)
                if (shouldApplyRemote(local?.updatedAt, remoteUpdatedAt)) {
                    dao.upsert(
                        Teacher(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            subject = doc.getString("subject") ?: "",
                            phone = doc.getString("phone") ?: "",
                            salary = doc.getLong("salary") ?: 0L,
                            joiningDate = doc.getLong("joiningDate") ?: remoteUpdatedAt,
                            createdAt = doc.getLong("createdAt") ?: remoteUpdatedAt,
                            updatedAt = remoteUpdatedAt,
                            deleted = doc.getBoolean("deleted") ?: false,
                            pendingSync = false
                        )
                    )
                }
            }
        }
    }

    private suspend fun mergeSalaryPayments(docs: List<DocumentSnapshot>) {
        db.withTransaction {
            val dao = db.salaryPaymentDao()
            for (doc in docs) {
                val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                val local = dao.getByIdIncludingDeleted(doc.id)
                if (shouldApplyRemote(local?.updatedAt, remoteUpdatedAt)) {
                    dao.upsert(
                        SalaryPayment(
                            id = doc.id,
                            teacherId = doc.getString("teacherId") ?: "",
                            monthKey = doc.getString("monthKey") ?: "",
                            amount = doc.getLong("amount") ?: 0L,
                            paidDate = doc.getLong("paidDate") ?: remoteUpdatedAt,
                            notes = doc.getString("notes") ?: "",
                            updatedAt = remoteUpdatedAt,
                            deleted = doc.getBoolean("deleted") ?: false,
                            pendingSync = false
                        )
                    )
                }
            }
        }
    }

    private suspend fun mergeAttendance(docs: List<DocumentSnapshot>) {
        db.withTransaction {
            val dao = db.attendanceDao()
            for (doc in docs) {
                val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                val studentId = doc.getString("studentId") ?: continue
                val dateKey = doc.getString("dateKey") ?: continue
                val local = dao.getByIdIncludingDeleted(doc.id)
                    ?: dao.getByStudentAndDate(studentId, dateKey)
                if (shouldApplyRemote(local?.updatedAt, remoteUpdatedAt)) {
                    dao.upsert(
                        AttendanceRecord(
                            id = local?.id ?: doc.id,
                            studentId = studentId,
                            classId = doc.getString("classId") ?: "",
                            dateKey = dateKey,
                            monthKey = doc.getString("monthKey") ?: dateKey.take(7),
                            status = doc.getString("status") ?: AttendanceStatus.PRESENT,
                            updatedAt = remoteUpdatedAt,
                            deleted = doc.getBoolean("deleted") ?: false,
                            pendingSync = false
                        )
                    )
                }
            }
        }
    }

    private suspend fun mergeFeeCharges(docs: List<DocumentSnapshot>) {
        db.withTransaction {
            val dao = db.feeChargeDao()
            for (doc in docs) {
                val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                val local = dao.getByIdIncludingDeleted(doc.id)
                if (shouldApplyRemote(local?.updatedAt, remoteUpdatedAt)) {
                    dao.upsert(
                        FeeCharge(
                            id = doc.id,
                            studentId = doc.getString("studentId") ?: "",
                            classId = doc.getString("classId") ?: "",
                            ledgerType = doc.getString("ledgerType") ?: FeeLedgerType.TUITION,
                            feeSubType = doc.getString("feeSubType") ?: "",
                            title = doc.getString("title") ?: "",
                            monthKey = doc.getString("monthKey"),
                            batchKey = doc.getString("batchKey"),
                            baseAmount = doc.getLong("baseAmount") ?: 0L,
                            discountAmount = doc.getLong("discountAmount") ?: 0L,
                            netAmount = doc.getLong("netAmount") ?: 0L,
                            paidAmount = doc.getLong("paidAmount") ?: 0L,
                            status = doc.getString("status") ?: FeeStatus.UNPAID,
                            dueDate = doc.getLong("dueDate") ?: remoteUpdatedAt,
                            createdAt = doc.getLong("createdAt") ?: remoteUpdatedAt,
                            updatedAt = remoteUpdatedAt,
                            deleted = doc.getBoolean("deleted") ?: false,
                            pendingSync = false
                        )
                    )
                }
            }
        }
    }

    // Append-only: never overwrite existing local payment/receipt!
    private suspend fun mergeFeePaymentsAppendOnly(docs: List<DocumentSnapshot>) {
        db.withTransaction {
            val dao = db.feePaymentDao()
            val toInsert = mutableListOf<FeePayment>()
            for (doc in docs) {
                if (dao.getById(doc.id) != null) continue
                toInsert += FeePayment(
                    id = doc.id,
                    chargeId = doc.getString("chargeId") ?: "",
                    studentId = doc.getString("studentId") ?: "",
                    ledgerType = doc.getString("ledgerType") ?: FeeLedgerType.TUITION,
                    receiptNumber = doc.getString("receiptNumber") ?: "",
                    amountPaid = doc.getLong("amountPaid") ?: 0L,
                    discountApplied = doc.getLong("discountApplied") ?: 0L,
                    paidDate = doc.getLong("paidDate") ?: System.currentTimeMillis(),
                    monthKey = doc.getString("monthKey") ?: "",
                    notes = doc.getString("notes") ?: "",
                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                    deleted = false,
                    pendingSync = false
                )
            }
            if (toInsert.isNotEmpty()) {
                dao.insertAllAppendOnly(toInsert)
            }
        }
    }

    private suspend fun mergeExams(docs: List<DocumentSnapshot>) {
        db.withTransaction {
            val dao = db.examDao()
            for (doc in docs) {
                val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                val local = dao.getByIdIncludingDeleted(doc.id)
                if (shouldApplyRemote(local?.updatedAt, remoteUpdatedAt)) {
                    dao.upsert(
                        Exam(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            classId = doc.getString("classId") ?: "",
                            examDate = doc.getLong("examDate") ?: remoteUpdatedAt,
                            createdAt = doc.getLong("createdAt") ?: remoteUpdatedAt,
                            updatedAt = remoteUpdatedAt,
                            deleted = doc.getBoolean("deleted") ?: false,
                            pendingSync = false
                        )
                    )
                }
            }
        }
    }

    private suspend fun mergeExamMarks(docs: List<DocumentSnapshot>) {
        db.withTransaction {
            val dao = db.examMarkDao()
            for (doc in docs) {
                val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                val examId = doc.getString("examId") ?: continue
                val studentId = doc.getString("studentId") ?: continue
                val subjectId = doc.getString("subjectId") ?: continue
                val local = dao.getByIdIncludingDeleted(doc.id)
                    ?: dao.findByKey(examId, studentId, subjectId)
                if (shouldApplyRemote(local?.updatedAt, remoteUpdatedAt)) {
                    dao.upsert(
                        ExamMark(
                            id = local?.id ?: doc.id,
                            examId = examId,
                            studentId = studentId,
                            subjectId = subjectId,
                            marksObtained = doc.getDouble("marksObtained") ?: 0.0,
                            totalMarks = (doc.getLong("totalMarks") ?: 100L).toInt(),
                            updatedAt = remoteUpdatedAt,
                            deleted = doc.getBoolean("deleted") ?: false,
                            pendingSync = false
                        )
                    )
                }
            }
        }
    }

    private suspend fun mergeLedgerEntries(docs: List<DocumentSnapshot>) {
        db.withTransaction {
            val dao = db.ledgerEntryDao()
            for (doc in docs) {
                val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                val local = dao.getByIdIncludingDeleted(doc.id)
                if (shouldApplyRemote(local?.updatedAt, remoteUpdatedAt)) {
                    dao.upsert(
                        LedgerEntry(
                            id = doc.id,
                            type = doc.getString("type") ?: LedgerType.EXPENSE,
                            category = doc.getString("category") ?: "",
                            title = doc.getString("title") ?: "",
                            amount = doc.getLong("amount") ?: 0L,
                            date = doc.getLong("date") ?: remoteUpdatedAt,
                            monthKey = doc.getString("monthKey") ?: "",
                            notes = doc.getString("notes") ?: "",
                            linkedSalaryPaymentId = doc.getString("linkedSalaryPaymentId"),
                            updatedAt = remoteUpdatedAt,
                            deleted = doc.getBoolean("deleted") ?: false,
                            pendingSync = false
                        )
                    )
                }
            }
        }
    }

    private suspend fun mergeMembers(docs: List<DocumentSnapshot>) {
        val members = docs.mapNotNull { doc ->
            val email = doc.getString("email") ?: return@mapNotNull null
            AcademyMember(
                uid = doc.id,
                email = email,
                name = doc.getString("name") ?: email,
                role = doc.getString("role") ?: UserRole.TEACHER,
                createdAt = doc.getLong("createdAt") ?: 0L
            )
        }
        if (members.isNotEmpty()) {
            val memberDao = db.academyMemberDao()
            members.forEach { member ->
                memberDao.deleteByUid("invite:${member.email.trim().lowercase()}")
            }
            memberDao.upsertAll(members)
        }
    }

    companion object {
        private const val TAG = "CloudSyncManager"
        private const val SYNC_WORK_NAME = "academy_cloud_sync"
        private const val SYNC_TIMEOUT_MS = 15_000L

        fun shouldApplyRemote(localUpdatedAt: Long?, remoteUpdatedAt: Long): Boolean {
            return localUpdatedAt == null || remoteUpdatedAt > localUpdatedAt
        }
    }
}

private fun AcademyProfile.toFirestoreMap(): Map<String, Any> = mapOf(
    "academyName" to academyName,
    "tagline" to tagline,
    "phone" to phone,
    "address" to address,
    "updatedAt" to updatedAt
)

private fun AcademyClass.toFirestoreMap(): Map<String, Any> = mapOf(
    "id" to id,
    "name" to name,
    "monthlyFee" to monthlyFee,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "deleted" to deleted
)

private fun Subject.toFirestoreMap(): Map<String, Any> = mapOf(
    "id" to id,
    "classId" to classId,
    "name" to name,
    "totalMarks" to totalMarks,
    "updatedAt" to updatedAt,
    "deleted" to deleted
)

private fun TimetableEntry.toFirestoreMap(): Map<String, Any> = mapOf(
    "id" to id,
    "classId" to classId,
    "dayOfWeek" to dayOfWeek,
    "subjectName" to subjectName,
    "teacherName" to teacherName,
    "startTime" to startTime,
    "endTime" to endTime,
    "updatedAt" to updatedAt,
    "deleted" to deleted
)

private fun Student.toFirestoreMap(): Map<String, Any> = listOfNotNull(
    "id" to id,
    "name" to name,
    "fatherName" to fatherName,
    "phone" to phone,
    "address" to address,
    "classId" to classId,
    "rollNo" to rollNo,
    "admissionDate" to admissionDate,
    "guardianWhatsapp" to guardianWhatsapp,
    "whatsappHistory" to whatsappHistory,
    "monthlyDiscount" to monthlyDiscount,
    "status" to status,
    leftDate?.let { "leftDate" to it },
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "deleted" to deleted
).toMap()

private fun Teacher.toFirestoreMap(): Map<String, Any> = mapOf(
    "id" to id,
    "name" to name,
    "subject" to subject,
    "phone" to phone,
    "salary" to salary,
    "joiningDate" to joiningDate,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "deleted" to deleted
)

private fun SalaryPayment.toFirestoreMap(): Map<String, Any> = mapOf(
    "id" to id,
    "teacherId" to teacherId,
    "monthKey" to monthKey,
    "amount" to amount,
    "paidDate" to paidDate,
    "notes" to notes,
    "updatedAt" to updatedAt,
    "deleted" to deleted
)

private fun AttendanceRecord.toFirestoreMap(): Map<String, Any> = mapOf(
    "id" to id,
    "studentId" to studentId,
    "classId" to classId,
    "dateKey" to dateKey,
    "monthKey" to monthKey,
    "status" to status,
    "updatedAt" to updatedAt,
    "deleted" to deleted
)

private fun FeeCharge.toFirestoreMap(): Map<String, Any> = listOfNotNull(
    "id" to id,
    "studentId" to studentId,
    "classId" to classId,
    "ledgerType" to ledgerType,
    "feeSubType" to feeSubType,
    "title" to title,
    monthKey?.let { "monthKey" to it },
    batchKey?.let { "batchKey" to it },
    "baseAmount" to baseAmount,
    "discountAmount" to discountAmount,
    "netAmount" to netAmount,
    "paidAmount" to paidAmount,
    "status" to status,
    "dueDate" to dueDate,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "deleted" to deleted
).toMap()

private fun FeePayment.toFirestoreMap(): Map<String, Any> = mapOf(
    "id" to id,
    "chargeId" to chargeId,
    "studentId" to studentId,
    "ledgerType" to ledgerType,
    "receiptNumber" to receiptNumber,
    "amountPaid" to amountPaid,
    "discountApplied" to discountApplied,
    "paidDate" to paidDate,
    "monthKey" to monthKey,
    "notes" to notes,
    "updatedAt" to updatedAt
)

private fun LedgerEntry.toFirestoreMap(): Map<String, Any> = listOfNotNull(
    "id" to id,
    "type" to type,
    "category" to category,
    "title" to title,
    "amount" to amount,
    "date" to date,
    "monthKey" to monthKey,
    "notes" to notes,
    linkedSalaryPaymentId?.let { "linkedSalaryPaymentId" to it },
    "updatedAt" to updatedAt,
    "deleted" to deleted
).toMap()

private fun Exam.toFirestoreMap(): Map<String, Any> = mapOf(
    "id" to id,
    "name" to name,
    "classId" to classId,
    "examDate" to examDate,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "deleted" to deleted
)

private fun ExamMark.toFirestoreMap(): Map<String, Any> = mapOf(
    "id" to id,
    "examId" to examId,
    "studentId" to studentId,
    "subjectId" to subjectId,
    "marksObtained" to marksObtained,
    "totalMarks" to totalMarks,
    "updatedAt" to updatedAt,
    "deleted" to deleted
)
