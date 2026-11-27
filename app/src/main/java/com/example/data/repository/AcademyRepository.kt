package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.*
import com.example.util.FormatUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class ReceiptResult(
    val receiptNumber: String,
    val studentName: String,
    val fatherName: String,
    val rollNo: String,
    val className: String,
    val ledgerType: String,
    val title: String,
    val amountPaid: Long,
    val discountApplied: Long,
    val remainingBalance: Long,
    val paidDate: Long
)

data class StudentExamResult(
    val student: Student,
    val subjectMarks: List<SubjectMarkItem>,
    val obtainedTotal: Double,
    val maxTotal: Int,
    val percentage: Double,
    val grade: String,
    val rank: Int
)

data class SubjectMarkItem(
    val subject: Subject,
    val obtained: Double?,
    val total: Int
)

class AcademyRepository(
    private val db: AcademyDatabase,
    private val prefs: PreferencesRepository,
    private val onLocalMutation: () -> Unit = {}
) {

    private val profileDao = db.academyProfileDao()
    private val classDao = db.academyClassDao()
    private val subjectDao = db.subjectDao()
    private val timetableDao = db.timetableDao()
    private val studentDao = db.studentDao()
    private val teacherDao = db.teacherDao()
    private val salaryPaymentDao = db.salaryPaymentDao()
    private val attendanceDao = db.attendanceDao()
    private val feeChargeDao = db.feeChargeDao()
    private val feePaymentDao = db.feePaymentDao()
    private val ledgerEntryDao = db.ledgerEntryDao()
    private val examDao = db.examDao()
    private val examMarkDao = db.examMarkDao()
    private val memberDao = db.academyMemberDao()

    // Observable Flows (active deleted=0 records)
    val profileFlow: Flow<AcademyProfile> = profileDao.observeProfile().map { it ?: AcademyProfile() }
    val classesFlow: Flow<List<AcademyClass>> = classDao.observeAll()
    val subjectsFlow: Flow<List<Subject>> = subjectDao.observeAll()
    val studentsFlow: Flow<List<Student>> = studentDao.observeAll()
    val teachersFlow: Flow<List<Teacher>> = teacherDao.observeAll()
    val salaryPaymentsFlow: Flow<List<SalaryPayment>> = salaryPaymentDao.observeAll()
    val attendanceFlow: Flow<List<AttendanceRecord>> = attendanceDao.observeAll()
    val feeChargesFlow: Flow<List<FeeCharge>> = feeChargeDao.observeAll()
    val feePaymentsFlow: Flow<List<FeePayment>> = feePaymentDao.observeAll()
    val ledgerEntriesFlow: Flow<List<LedgerEntry>> = ledgerEntryDao.observeAll()
    val examsFlow: Flow<List<Exam>> = examDao.observeAll()
    val examMarksFlow: Flow<List<ExamMark>> = examMarkDao.observeAll()
    val membersFlow: Flow<List<AcademyMember>> = memberDao.observeAll()

    fun observeStudent(studentId: String): Flow<Student?> = studentDao.observeById(studentId)
    fun observeSubjectsByClass(classId: String): Flow<List<Subject>> = subjectDao.observeByClass(classId)
    fun observeTimetableByClass(classId: String): Flow<List<TimetableEntry>> = timetableDao.observeByClass(classId)
    fun observeStudentAttendance(studentId: String): Flow<List<AttendanceRecord>> = attendanceDao.observeByStudent(studentId)
    fun observeStudentFeeCharges(studentId: String): Flow<List<FeeCharge>> = feeChargeDao.observeByStudent(studentId)
    fun observeStudentFeePayments(studentId: String): Flow<List<FeePayment>> = feePaymentDao.observeByStudent(studentId)
    fun observeStudentExamMarks(studentId: String): Flow<List<ExamMark>> = examMarkDao.observeByStudent(studentId)
    fun observeExamMarks(examId: String): Flow<List<ExamMark>> = examMarkDao.observeByExam(examId)
    fun observeTeacherSalaries(teacherId: String): Flow<List<SalaryPayment>> = salaryPaymentDao.observeByTeacher(teacherId)

    // Profile
    suspend fun getOrCreateProfile(): AcademyProfile {
        return profileDao.getProfileOnce() ?: AcademyProfile(pendingSync = false).also { profileDao.upsert(it) }
    }

    suspend fun saveProfile(
        academyName: String,
        tagline: String,
        phone: String,
        address: String,
        logoPath: String?
    ) {
        val current = getOrCreateProfile()
        profileDao.upsert(
            current.copy(
                academyName = academyName.trim(),
                tagline = tagline.trim(),
                phone = phone.trim(),
                address = address.trim(),
                logoPath = logoPath,
                updatedAt = System.currentTimeMillis(),
                pendingSync = true
            )
        )
        onLocalMutation()
    }

    // Classes, Subjects, Timetable
    suspend fun saveClass(academyClass: AcademyClass): String {
        val id = academyClass.id.ifBlank { UUID.randomUUID().toString() }
        classDao.upsert(
            academyClass.copy(
                id = id,
                updatedAt = System.currentTimeMillis(),
                deleted = false,
                pendingSync = true
            )
        )
        onLocalMutation()
        return id
    }

    suspend fun deleteClass(academyClass: AcademyClass): Result<Unit> {
        val count = studentDao.countByClass(academyClass.id)
        if (count > 0) {
            return Result.failure(IllegalStateException("Class has $count student(s)"))
        }
        val now = System.currentTimeMillis()
        db.withTransaction {
            subjectDao.softDeleteByClass(academyClass.id, now)
            timetableDao.softDeleteByClass(academyClass.id, now)
            classDao.upsert(
                academyClass.copy(
                    deleted = true,
                    updatedAt = now,
                    pendingSync = true
                )
            )
        }
        onLocalMutation()
        return Result.success(Unit)
    }

    suspend fun saveSubject(subject: Subject): String {
        val id = subject.id.ifBlank { UUID.randomUUID().toString() }
        subjectDao.upsert(
            subject.copy(
                id = id,
                updatedAt = System.currentTimeMillis(),
                deleted = false,
                pendingSync = true
            )
        )
        onLocalMutation()
        return id
    }

    suspend fun deleteSubject(subject: Subject) {
        subjectDao.upsert(
            subject.copy(
                deleted = true,
                updatedAt = System.currentTimeMillis(),
                pendingSync = true
            )
        )
        onLocalMutation()
    }

    suspend fun saveTimetableEntry(entry: TimetableEntry): String {
        val id = entry.id.ifBlank { UUID.randomUUID().toString() }
        timetableDao.upsert(
            entry.copy(
                id = id,
                updatedAt = System.currentTimeMillis(),
                deleted = false,
                pendingSync = true
            )
        )
        onLocalMutation()
        return id
    }

    suspend fun deleteTimetableEntry(entry: TimetableEntry) {
        timetableDao.upsert(
            entry.copy(
                deleted = true,
                updatedAt = System.currentTimeMillis(),
                pendingSync = true
            )
        )
        onLocalMutation()
    }

    // Students
    suspend fun saveStudent(student: Student): String {
        val id = student.id.ifBlank { UUID.randomUUID().toString() }
        studentDao.upsert(
            student.copy(
                id = id,
                updatedAt = System.currentTimeMillis(),
                deleted = false,
                pendingSync = true
            )
        )
        onLocalMutation()
        return id
    }

    suspend fun setStudentStatus(student: Student, status: String) {
        val now = System.currentTimeMillis()
        studentDao.upsert(
            student.copy(
                status = status,
                leftDate = if (status == StudentStatus.LEFT) now else null,
                updatedAt = now,
                pendingSync = true
            )
        )
        onLocalMutation()
    }

    suspend fun deleteStudent(student: Student) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            // Soft delete student and associated attendance, unpaid/billed charges, and exam marks.
            // Append-only fee_payments are intentionally preserved.
            attendanceDao.softDeleteByStudent(student.id, now)
            feeChargeDao.softDeleteByStudent(student.id, now)
            examMarkDao.softDeleteByStudent(student.id, now)
            studentDao.upsert(
                student.copy(
                    deleted = true,
                    updatedAt = now,
                    pendingSync = true
                )
            )
        }
        onLocalMutation()
    }

    // Teachers & Salaries
    suspend fun saveTeacher(teacher: Teacher): String {
        val id = teacher.id.ifBlank { UUID.randomUUID().toString() }
        teacherDao.upsert(
            teacher.copy(
                id = id,
                updatedAt = System.currentTimeMillis(),
                deleted = false,
                pendingSync = true
            )
        )
        onLocalMutation()
        return id
    }

    suspend fun deleteTeacher(teacher: Teacher) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            salaryPaymentDao.softDeleteByTeacher(teacher.id, now)
            teacherDao.upsert(
                teacher.copy(
                    deleted = true,
                    updatedAt = now,
                    pendingSync = true
                )
            )
        }
        onLocalMutation()
    }

    suspend fun recordSalaryPayment(
        teacher: Teacher,
        monthKey: String,
        amount: Long,
        paidDate: Long,
        notes: String,
        alsoRecordExpense: Boolean
    ) {
        val now = System.currentTimeMillis()
        val paymentId = UUID.randomUUID().toString()
        db.withTransaction {
            salaryPaymentDao.upsert(
                SalaryPayment(
                    id = paymentId,
                    teacherId = teacher.id,
                    monthKey = monthKey,
                    amount = amount,
                    paidDate = paidDate,
                    notes = notes.trim(),
                    updatedAt = now,
                    deleted = false,
                    pendingSync = true
                )
            )
            if (alsoRecordExpense) {
                ledgerEntryDao.upsert(
                    LedgerEntry(
                        id = UUID.randomUUID().toString(),
                        type = LedgerType.EXPENSE,
                        category = "Salaries",
                        title = "Salary: ${teacher.name} (${FormatUtils.formatMonthKey(monthKey)})",
                        amount = amount,
                        date = paidDate,
                        monthKey = FormatUtils.toMonthKey(paidDate),
                        notes = notes.trim(),
                        linkedSalaryPaymentId = paymentId,
                        updatedAt = now,
                        deleted = false,
                        pendingSync = true
                    )
                )
            }
        }
        onLocalMutation()
    }

    suspend fun deleteSalaryPayment(payment: SalaryPayment) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            ledgerEntryDao.softDeleteBySalaryPaymentId(payment.id, now)
            salaryPaymentDao.upsert(
                payment.copy(
                    deleted = true,
                    updatedAt = now,
                    pendingSync = true
                )
            )
        }
        onLocalMutation()
    }

    // Attendance
    suspend fun getAttendanceDraftForClassAndDate(
        classId: String,
        dateKey: String
    ): List<Pair<Student, String>> {
        val activeStudents = studentDao.getActiveByClass(classId)
        val existing = attendanceDao.getByClassAndDate(classId, dateKey).associateBy { it.studentId }
        return activeStudents.map { student ->
            val status = existing[student.id]?.status ?: AttendanceStatus.PRESENT
            student to status
        }
    }

    suspend fun saveClassAttendance(
        classId: String,
        dateKey: String,
        statuses: Map<String, String>
    ) {
        val monthKey = dateKey.take(7)
        val now = System.currentTimeMillis()
        db.withTransaction {
            val records = statuses.map { (studentId, status) ->
                val existing = attendanceDao.getByStudentAndDate(studentId, dateKey)
                AttendanceRecord(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    studentId = studentId,
                    classId = classId,
                    dateKey = dateKey,
                    monthKey = monthKey,
                    status = status,
                    updatedAt = now,
                    deleted = false,
                    pendingSync = true
                )
            }
            if (records.isNotEmpty()) {
                attendanceDao.upsertAll(records)
            }
        }
        onLocalMutation()
    }

    // Fees: Monthly Tuition Generation (Never duplicates)
    suspend fun generateMonthlyTuitionFees(
        monthKey: String,
        classIdFilter: String? = null
    ): Pair<Int, Int> {
        var created = 0
        var skipped = 0
        val now = System.currentTimeMillis()
        db.withTransaction {
            val classes = classDao.getAllOnce().associateBy { it.id }
            val students = if (classIdFilter == null) {
                studentDao.getAllActive()
            } else {
                studentDao.getActiveByClass(classIdFilter)
            }
            val (_, monthEnd) = FormatUtils.monthRangeMillis(monthKey)
            val newCharges = mutableListOf<FeeCharge>()
            for (student in students) {
                val existing = feeChargeDao.findTuitionCharge(student.id, FeeLedgerType.TUITION, monthKey)
                if (existing != null) {
                    skipped++
                    continue
                }
                val cls = classes[student.classId] ?: continue
                val discount = student.monthlyDiscount.coerceIn(0L, cls.monthlyFee)
                val net = (cls.monthlyFee - discount).coerceAtLeast(0L)
                val status = if (net == 0L) FeeStatus.PAID else FeeStatus.UNPAID
                newCharges.add(
                    FeeCharge(
                        id = UUID.randomUUID().toString(),
                        studentId = student.id,
                        classId = student.classId,
                        ledgerType = FeeLedgerType.TUITION,
                        feeSubType = "Monthly Tuition",
                        title = "Tuition - ${FormatUtils.formatMonthKey(monthKey)}",
                        monthKey = monthKey,
                        baseAmount = cls.monthlyFee,
                        discountAmount = discount,
                        netAmount = net,
                        paidAmount = 0L,
                        status = status,
                        dueDate = monthEnd,
                        createdAt = now,
                        updatedAt = now,
                        deleted = false,
                        pendingSync = true
                    )
                )
                created++
            }
            if (newCharges.isNotEmpty()) {
                feeChargeDao.upsertAll(newCharges)
            }
        }
        if (created > 0) onLocalMutation()
        return created to skipped
    }

    // Fees: Paper Fee Batch Creation (Strictly Separate Ledger)
    suspend fun createPaperFeeCharges(
        classId: String,
        paperTitle: String,
        amountPerStudent: Long,
        dueDate: Long
    ): Pair<Int, Int> {
        val cleanTitle = paperTitle.trim()
        val batchKey = "${classId}::${cleanTitle.lowercase()}"
        var created = 0
        var skipped = 0
        val now = System.currentTimeMillis()
        db.withTransaction {
            val students = studentDao.getActiveByClass(classId)
            val newCharges = mutableListOf<FeeCharge>()
            for (student in students) {
                val existing = feeChargeDao.findPaperFeeCharge(student.id, batchKey)
                if (existing != null) {
                    skipped++
                    continue
                }
                newCharges.add(
                    FeeCharge(
                        id = UUID.randomUUID().toString(),
                        studentId = student.id,
                        classId = classId,
                        ledgerType = FeeLedgerType.PAPER_FEE,
                        feeSubType = cleanTitle,
                        title = cleanTitle,
                        monthKey = FormatUtils.toMonthKey(dueDate),
                        batchKey = batchKey,
                        baseAmount = amountPerStudent,
                        discountAmount = 0L,
                        netAmount = amountPerStudent,
                        paidAmount = 0L,
                        status = if (amountPerStudent == 0L) FeeStatus.PAID else FeeStatus.UNPAID,
                        dueDate = dueDate,
                        createdAt = now,
                        updatedAt = now,
                        deleted = false,
                        pendingSync = true
                    )
                )
                created++
            }
            if (newCharges.isNotEmpty()) {
                feeChargeDao.upsertAll(newCharges)
            }
        }
        if (created > 0) onLocalMutation()
        return created to skipped
    }

    // Fees: Admission / Other Custom Fee
    suspend fun createOtherFeeCharge(
        studentId: String,
        feeSubType: String,
        title: String,
        amount: Long,
        discount: Long,
        dueDate: Long
    ): String {
        val student = studentDao.getById(studentId) ?: throw IllegalArgumentException("Student not found")
        val clampedDiscount = discount.coerceIn(0L, amount)
        val net = (amount - clampedDiscount).coerceAtLeast(0L)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        feeChargeDao.upsert(
            FeeCharge(
                id = id,
                studentId = student.id,
                classId = student.classId,
                ledgerType = FeeLedgerType.OTHER,
                feeSubType = feeSubType.trim(),
                title = title.trim(),
                monthKey = FormatUtils.toMonthKey(dueDate),
                baseAmount = amount,
                discountAmount = clampedDiscount,
                netAmount = net,
                paidAmount = 0L,
                status = if (net == 0L) FeeStatus.PAID else FeeStatus.UNPAID,
                dueDate = dueDate,
                createdAt = now,
                updatedAt = now,
                deleted = false,
                pendingSync = true
            )
        )
        onLocalMutation()
        return id
    }

    // Fees: Collect Single Charge Payment (Append-Only FeePayment with device-prefixed receipt)
    suspend fun collectSingleChargePayment(
        chargeId: String,
        amountPaying: Long,
        discountAdded: Long,
        paidDate: Long,
        notes: String
    ): ReceiptResult {
        require(amountPaying > 0L) { "Payment amount must be positive" }
        require(discountAdded >= 0L) { "Discount cannot be negative" }

        val result = db.withTransaction {
            val charge = feeChargeDao.getById(chargeId) ?: throw IllegalStateException("Fee charge not found")
            val student = studentDao.getById(charge.studentId) ?: throw IllegalStateException("Student not found")
            val cls = classDao.getById(student.classId)

            val currentRemaining = charge.remainingAmount
            require(discountAdded <= currentRemaining) { "Discount cannot exceed remaining charge" }

            val newDiscount = charge.discountAmount + discountAdded
            val newNet = (charge.baseAmount - newDiscount).coerceAtLeast(0L)
            val remainingAfterDiscount = (newNet - charge.paidAmount).coerceAtLeast(0L)
            require(amountPaying <= remainingAfterDiscount) { "Overpayment not allowed" }

            val receiptNumber = prefs.allocateNextReceiptNumber(
                isPaperFee = charge.ledgerType == FeeLedgerType.PAPER_FEE
            )
            val now = System.currentTimeMillis()

            val newPaid = charge.paidAmount + amountPaying
            val newStatus = when {
                newPaid >= newNet -> FeeStatus.PAID
                newPaid > 0L -> FeeStatus.PARTIAL
                else -> FeeStatus.UNPAID
            }
            feeChargeDao.upsert(
                charge.copy(
                    discountAmount = newDiscount,
                    netAmount = newNet,
                    paidAmount = newPaid,
                    status = newStatus,
                    updatedAt = now,
                    pendingSync = true
                )
            )

            feePaymentDao.insertAppendOnly(
                FeePayment(
                    id = UUID.randomUUID().toString(),
                    chargeId = charge.id,
                    studentId = student.id,
                    ledgerType = charge.ledgerType,
                    receiptNumber = receiptNumber,
                    amountPaid = amountPaying,
                    discountApplied = discountAdded,
                    paidDate = paidDate,
                    monthKey = FormatUtils.toMonthKey(paidDate),
                    notes = notes.trim(),
                    updatedAt = now,
                    deleted = false,
                    pendingSync = true
                )
            )

            ReceiptResult(
                receiptNumber = receiptNumber,
                studentName = student.name,
                fatherName = student.fatherName,
                rollNo = student.rollNo,
                className = cls?.name ?: "",
                ledgerType = charge.ledgerType,
                title = charge.title,
                amountPaid = amountPaying,
                discountApplied = discountAdded,
                remainingBalance = (newNet - newPaid).coerceAtLeast(0L),
                paidDate = paidDate
            )
        }
        onLocalMutation()
        return result
    }

    // Fees: Collect Monthly Tuition with FIFO Arrears Settlement (Append-Only FeePayments)
    suspend fun collectTuitionPaymentFifo(
        studentId: String,
        targetChargeId: String?,
        amountPaying: Long,
        discountOnTarget: Long,
        paidDate: Long,
        notes: String
    ): ReceiptResult {
        require(amountPaying > 0L) { "Payment amount must be positive" }
        require(discountOnTarget >= 0L) { "Discount cannot be negative" }

        val result = db.withTransaction {
            val student = studentDao.getById(studentId) ?: throw IllegalStateException("Student not found")
            val cls = classDao.getById(student.classId)
            val unpaidCharges = feeChargeDao.getUnpaidTuitionCharges(studentId).toMutableList()
            require(unpaidCharges.isNotEmpty()) { "No unpaid tuition dues" }
            val now = System.currentTimeMillis()

            if (discountOnTarget > 0L) {
                val idx = if (targetChargeId != null) {
                    unpaidCharges.indexOfFirst { it.id == targetChargeId }
                } else {
                    unpaidCharges.lastIndex
                }
                val targetIdx = if (idx >= 0) idx else unpaidCharges.lastIndex
                val target = unpaidCharges[targetIdx]
                require(discountOnTarget <= target.remainingAmount) { "Discount cannot exceed remaining charge" }
                val newDiscount = target.discountAmount + discountOnTarget
                val newNet = (target.baseAmount - newDiscount).coerceAtLeast(0L)
                val newStatus = if (target.paidAmount >= newNet) FeeStatus.PAID else if (target.paidAmount > 0L) FeeStatus.PARTIAL else FeeStatus.UNPAID
                val updated = target.copy(
                    discountAmount = newDiscount,
                    netAmount = newNet,
                    status = newStatus,
                    updatedAt = now,
                    pendingSync = true
                )
                feeChargeDao.upsert(updated)
                unpaidCharges[targetIdx] = updated
            }

            val totalDue = unpaidCharges.sumOf { it.remainingAmount }
            require(amountPaying <= totalDue) { "Overpayment not allowed" }

            val receiptNumber = prefs.allocateNextReceiptNumber(isPaperFee = false)

            var remainingToAllocate = amountPaying
            val coveredTitles = mutableListOf<String>()
            val paymentMonthKey = FormatUtils.toMonthKey(paidDate)
            val paymentsToInsert = mutableListOf<FeePayment>()

            for (charge in unpaidCharges) {
                if (remainingToAllocate <= 0L) break
                val rem = charge.remainingAmount
                if (rem <= 0L) continue
                val payForThis = minOf(remainingToAllocate, rem)
                val newPaid = charge.paidAmount + payForThis
                val newStatus = if (newPaid >= charge.netAmount) FeeStatus.PAID else FeeStatus.PARTIAL
                feeChargeDao.upsert(
                    charge.copy(
                        paidAmount = newPaid,
                        status = newStatus,
                        updatedAt = now,
                        pendingSync = true
                    )
                )
                paymentsToInsert.add(
                    FeePayment(
                        id = UUID.randomUUID().toString(),
                        chargeId = charge.id,
                        studentId = studentId,
                        ledgerType = FeeLedgerType.TUITION,
                        receiptNumber = receiptNumber,
                        amountPaid = payForThis,
                        discountApplied = if (charge.id == targetChargeId) discountOnTarget else 0L,
                        paidDate = paidDate,
                        monthKey = paymentMonthKey,
                        notes = notes.trim(),
                        updatedAt = now,
                        deleted = false,
                        pendingSync = true
                    )
                )
                coveredTitles.add(charge.title)
                remainingToAllocate -= payForThis
            }

            feePaymentDao.insertAllAppendOnly(paymentsToInsert)

            ReceiptResult(
                receiptNumber = receiptNumber,
                studentName = student.name,
                fatherName = student.fatherName,
                rollNo = student.rollNo,
                className = cls?.name ?: "",
                ledgerType = FeeLedgerType.TUITION,
                title = coveredTitles.joinToString(", "),
                amountPaid = amountPaying,
                discountApplied = discountOnTarget,
                remainingBalance = (totalDue - amountPaying).coerceAtLeast(0L),
                paidDate = paidDate
            )
        }
        onLocalMutation()
        return result
    }

    suspend fun deleteFeeCharge(charge: FeeCharge) {
        feeChargeDao.upsert(
            charge.copy(
                deleted = true,
                updatedAt = System.currentTimeMillis(),
                pendingSync = true
            )
        )
        onLocalMutation()
    }

    // Ledger Entries
    suspend fun saveLedgerEntry(entry: LedgerEntry): String {
        val id = entry.id.ifBlank { UUID.randomUUID().toString() }
        ledgerEntryDao.upsert(
            entry.copy(
                id = id,
                updatedAt = System.currentTimeMillis(),
                deleted = false,
                pendingSync = true
            )
        )
        onLocalMutation()
        return id
    }

    suspend fun deleteLedgerEntry(entry: LedgerEntry) {
        ledgerEntryDao.upsert(
            entry.copy(
                deleted = true,
                updatedAt = System.currentTimeMillis(),
                pendingSync = true
            )
        )
        onLocalMutation()
    }

    // Exams & Marks
    suspend fun saveExam(exam: Exam): String {
        val id = exam.id.ifBlank { UUID.randomUUID().toString() }
        examDao.upsert(
            exam.copy(
                id = id,
                updatedAt = System.currentTimeMillis(),
                deleted = false,
                pendingSync = true
            )
        )
        onLocalMutation()
        return id
    }

    suspend fun deleteExam(exam: Exam) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            examMarkDao.softDeleteByExam(exam.id, now)
            examDao.upsert(
                exam.copy(
                    deleted = true,
                    updatedAt = now,
                    pendingSync = true
                )
            )
        }
        onLocalMutation()
    }

    suspend fun saveExamMarksForSubject(
        examId: String,
        subject: Subject,
        marksByStudent: Map<String, Double>
    ) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val records = marksByStudent.map { (studentId, marks) ->
                val existing = examMarkDao.findByKey(examId, studentId, subject.id)
                ExamMark(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    examId = examId,
                    studentId = studentId,
                    subjectId = subject.id,
                    marksObtained = marks.coerceIn(0.0, subject.totalMarks.toDouble()),
                    totalMarks = subject.totalMarks,
                    updatedAt = now,
                    deleted = false,
                    pendingSync = true
                )
            }
            if (records.isNotEmpty()) {
                examMarkDao.upsertAll(records)
            }
        }
        onLocalMutation()
    }

    fun computeExamResults(
        students: List<Student>,
        subjects: List<Subject>,
        marks: List<ExamMark>
    ): List<StudentExamResult> {
        if (students.isEmpty() || subjects.isEmpty()) return emptyList()
        val markLookup = marks.associateBy { it.studentId to it.subjectId }

        val raw = students.mapNotNull { student ->
            val subjectItems = subjects.map { subject ->
                val m = markLookup[student.id to subject.id]
                SubjectMarkItem(
                    subject = subject,
                    obtained = m?.marksObtained,
                    total = m?.totalMarks ?: subject.totalMarks
                )
            }
            val anyEntered = subjectItems.any { it.obtained != null }
            if (!anyEntered) null
            else {
                val obtainedSum = subjectItems.sumOf { it.obtained ?: 0.0 }
                val totalSum = subjectItems.sumOf { it.total }
                val pct = if (totalSum > 0) (obtainedSum / totalSum) * 100.0 else 0.0
                StudentExamResult(
                    student = student,
                    subjectMarks = subjectItems,
                    obtainedTotal = obtainedSum,
                    maxTotal = totalSum,
                    percentage = pct,
                    grade = FormatUtils.computeGrade(pct),
                    rank = 1
                )
            }
        }.sortedByDescending { it.obtainedTotal }

        var currentRank = 1
        return raw.mapIndexed { index, item ->
            if (index > 0 && item.obtainedTotal < raw[index - 1].obtainedTotal) {
                currentRank = index + 1
            }
            item.copy(rank = currentRank)
        }
    }
}
