package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.*
import com.example.util.FormatUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

class AcademyRepository(private val db: AcademyDatabase) {

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

    // Observable Flows
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

    fun observeStudent(studentId: Long): Flow<Student?> = studentDao.observeById(studentId)
    fun observeSubjectsByClass(classId: Long): Flow<List<Subject>> = subjectDao.observeByClass(classId)
    fun observeTimetableByClass(classId: Long): Flow<List<TimetableEntry>> = timetableDao.observeByClass(classId)
    fun observeStudentAttendance(studentId: Long): Flow<List<AttendanceRecord>> = attendanceDao.observeByStudent(studentId)
    fun observeStudentFeeCharges(studentId: Long): Flow<List<FeeCharge>> = feeChargeDao.observeByStudent(studentId)
    fun observeStudentFeePayments(studentId: Long): Flow<List<FeePayment>> = feePaymentDao.observeByStudent(studentId)
    fun observeStudentExamMarks(studentId: Long): Flow<List<ExamMark>> = examMarkDao.observeByStudent(studentId)
    fun observeExamMarks(examId: Long): Flow<List<ExamMark>> = examMarkDao.observeByExam(examId)
    fun observeTeacherSalaries(teacherId: Long): Flow<List<SalaryPayment>> = salaryPaymentDao.observeByTeacher(teacherId)

    // Profile
    suspend fun getOrCreateProfile(): AcademyProfile {
        return profileDao.getProfileOnce() ?: AcademyProfile().also { profileDao.upsert(it) }
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
                logoPath = logoPath
            )
        )
    }

    // Classes, Subjects, Timetable
    suspend fun saveClass(academyClass: AcademyClass): Long {
        return if (academyClass.id == 0L) classDao.insert(academyClass)
        else {
            classDao.update(academyClass)
            academyClass.id
        }
    }

    suspend fun deleteClass(academyClass: AcademyClass): Result<Unit> {
        val count = studentDao.countByClass(academyClass.id)
        if (count > 0) {
            return Result.failure(IllegalStateException("Class has $count student(s)"))
        }
        classDao.delete(academyClass)
        return Result.success(Unit)
    }

    suspend fun saveSubject(subject: Subject): Long {
        return if (subject.id == 0L) subjectDao.insert(subject)
        else {
            subjectDao.update(subject)
            subject.id
        }
    }

    suspend fun deleteSubject(subject: Subject) = subjectDao.delete(subject)

    suspend fun saveTimetableEntry(entry: TimetableEntry): Long {
        return if (entry.id == 0L) timetableDao.insert(entry)
        else {
            timetableDao.update(entry)
            entry.id
        }
    }

    suspend fun deleteTimetableEntry(entry: TimetableEntry) = timetableDao.delete(entry)

    // Students
    suspend fun saveStudent(student: Student): Long {
        return if (student.id == 0L) studentDao.insert(student)
        else {
            studentDao.update(student)
            student.id
        }
    }

    suspend fun setStudentStatus(student: Student, status: String) {
        val updated = student.copy(
            status = status,
            leftDate = if (status == StudentStatus.LEFT) System.currentTimeMillis() else null
        )
        studentDao.update(updated)
    }

    suspend fun deleteStudent(student: Student) = studentDao.delete(student)

    // Teachers & Salaries
    suspend fun saveTeacher(teacher: Teacher): Long {
        return if (teacher.id == 0L) teacherDao.insert(teacher)
        else {
            teacherDao.update(teacher)
            teacher.id
        }
    }

    suspend fun deleteTeacher(teacher: Teacher) = teacherDao.delete(teacher)

    suspend fun recordSalaryPayment(
        teacher: Teacher,
        monthKey: String,
        amount: Long,
        paidDate: Long,
        notes: String,
        alsoRecordExpense: Boolean
    ) {
        db.withTransaction {
            val paymentId = salaryPaymentDao.insert(
                SalaryPayment(
                    teacherId = teacher.id,
                    monthKey = monthKey,
                    amount = amount,
                    paidDate = paidDate,
                    notes = notes.trim()
                )
            )
            if (alsoRecordExpense) {
                ledgerEntryDao.insert(
                    LedgerEntry(
                        type = LedgerType.EXPENSE,
                        category = "Salaries",
                        title = "Salary: ${teacher.name} (${FormatUtils.formatMonthKey(monthKey)})",
                        amount = amount,
                        date = paidDate,
                        monthKey = FormatUtils.toMonthKey(paidDate),
                        notes = notes.trim(),
                        linkedSalaryPaymentId = paymentId
                    )
                )
            }
        }
    }

    suspend fun deleteSalaryPayment(payment: SalaryPayment) {
        db.withTransaction {
            ledgerEntryDao.deleteBySalaryPaymentId(payment.id)
            salaryPaymentDao.delete(payment)
        }
    }

    // Attendance
    suspend fun getAttendanceDraftForClassAndDate(
        classId: Long,
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
        classId: Long,
        dateKey: String,
        statuses: Map<Long, String>
    ) {
        val monthKey = dateKey.take(7)
        db.withTransaction {
            val records = statuses.map { (studentId, status) ->
                AttendanceRecord(
                    studentId = studentId,
                    classId = classId,
                    dateKey = dateKey,
                    monthKey = monthKey,
                    status = status
                )
            }
            attendanceDao.deleteByClassAndDate(classId, dateKey)
            if (records.isNotEmpty()) {
                attendanceDao.upsertAll(records)
            }
        }
    }

    // Fees: Monthly Tuition Generation (Never duplicates)
    suspend fun generateMonthlyTuitionFees(
        monthKey: String,
        classIdFilter: Long? = null
    ): Pair<Int, Int> {
        var created = 0
        var skipped = 0
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
                        dueDate = monthEnd
                    )
                )
                created++
            }
            if (newCharges.isNotEmpty()) {
                feeChargeDao.insertAll(newCharges)
            }
        }
        return created to skipped
    }

    // Fees: Paper Fee Batch Creation (Strictly Separate Ledger)
    suspend fun createPaperFeeCharges(
        classId: Long,
        paperTitle: String,
        amountPerStudent: Long,
        dueDate: Long
    ): Pair<Int, Int> {
        val cleanTitle = paperTitle.trim()
        val batchKey = "${classId}::${cleanTitle.lowercase()}"
        var created = 0
        var skipped = 0
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
                        dueDate = dueDate
                    )
                )
                created++
            }
            if (newCharges.isNotEmpty()) {
                feeChargeDao.insertAll(newCharges)
            }
        }
        return created to skipped
    }

    // Fees: Admission / Other Custom Fee
    suspend fun createOtherFeeCharge(
        studentId: Long,
        feeSubType: String,
        title: String,
        amount: Long,
        discount: Long,
        dueDate: Long
    ): Long {
        val student = studentDao.getById(studentId) ?: throw IllegalArgumentException("Student not found")
        val clampedDiscount = discount.coerceIn(0L, amount)
        val net = (amount - clampedDiscount).coerceAtLeast(0L)
        return feeChargeDao.insert(
            FeeCharge(
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
                dueDate = dueDate
            )
        )
    }

    // Fees: Collect Single Charge Payment (Paper Fee / Other / Specific Charge)
    suspend fun collectSingleChargePayment(
        chargeId: Long,
        amountPaying: Long,
        discountAdded: Long,
        paidDate: Long,
        notes: String
    ): ReceiptResult {
        require(amountPaying > 0L) { "Payment amount must be positive" }
        require(discountAdded >= 0L) { "Discount cannot be negative" }

        return db.withTransaction {
            val charge = feeChargeDao.getById(chargeId) ?: throw IllegalStateException("Fee charge not found")
            val student = studentDao.getById(charge.studentId) ?: throw IllegalStateException("Student not found")
            val cls = classDao.getById(student.classId)

            val currentRemaining = charge.remainingAmount
            require(discountAdded <= currentRemaining) { "Discount cannot exceed remaining charge" }

            val newDiscount = charge.discountAmount + discountAdded
            val newNet = (charge.baseAmount - newDiscount).coerceAtLeast(0L)
            val remainingAfterDiscount = (newNet - charge.paidAmount).coerceAtLeast(0L)
            require(amountPaying <= remainingAfterDiscount) { "Overpayment not allowed" }

            val profile = getOrCreateProfile()
            val receiptNumber: String
            val updatedProfile: AcademyProfile
            if (charge.ledgerType == FeeLedgerType.PAPER_FEE) {
                receiptNumber = FormatUtils.formatReceiptNumber("PF", profile.nextPaperReceiptSeq)
                updatedProfile = profile.copy(nextPaperReceiptSeq = profile.nextPaperReceiptSeq + 1)
            } else {
                receiptNumber = FormatUtils.formatReceiptNumber("R", profile.nextTuitionReceiptSeq)
                updatedProfile = profile.copy(nextTuitionReceiptSeq = profile.nextTuitionReceiptSeq + 1)
            }
            profileDao.upsert(updatedProfile)

            val newPaid = charge.paidAmount + amountPaying
            val newStatus = when {
                newPaid >= newNet -> FeeStatus.PAID
                newPaid > 0L -> FeeStatus.PARTIAL
                else -> FeeStatus.UNPAID
            }
            feeChargeDao.update(
                charge.copy(
                    discountAmount = newDiscount,
                    netAmount = newNet,
                    paidAmount = newPaid,
                    status = newStatus
                )
            )

            feePaymentDao.insert(
                FeePayment(
                    chargeId = charge.id,
                    studentId = student.id,
                    ledgerType = charge.ledgerType,
                    receiptNumber = receiptNumber,
                    amountPaid = amountPaying,
                    discountApplied = discountAdded,
                    paidDate = paidDate,
                    monthKey = FormatUtils.toMonthKey(paidDate),
                    notes = notes.trim()
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
    }

    // Fees: Collect Monthly Tuition with FIFO Arrears Settlement
    suspend fun collectTuitionPaymentFifo(
        studentId: Long,
        targetChargeId: Long?,
        amountPaying: Long,
        discountOnTarget: Long,
        paidDate: Long,
        notes: String
    ): ReceiptResult {
        require(amountPaying > 0L) { "Payment amount must be positive" }
        require(discountOnTarget >= 0L) { "Discount cannot be negative" }

        return db.withTransaction {
            val student = studentDao.getById(studentId) ?: throw IllegalStateException("Student not found")
            val cls = classDao.getById(student.classId)
            var unpaidCharges = feeChargeDao.getUnpaidTuitionCharges(studentId).toMutableList()
            require(unpaidCharges.isNotEmpty()) { "No unpaid tuition dues" }

            // Apply discount to target charge (or latest unpaid charge)
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
                val updated = target.copy(discountAmount = newDiscount, netAmount = newNet, status = newStatus)
                feeChargeDao.update(updated)
                unpaidCharges[targetIdx] = updated
            }

            val totalDue = unpaidCharges.sumOf { it.remainingAmount }
            require(amountPaying <= totalDue) { "Overpayment not allowed" }

            val profile = getOrCreateProfile()
            val receiptNumber = FormatUtils.formatReceiptNumber("R", profile.nextTuitionReceiptSeq)
            profileDao.upsert(profile.copy(nextTuitionReceiptSeq = profile.nextTuitionReceiptSeq + 1))

            var remainingToAllocate = amountPaying
            val coveredTitles = mutableListOf<String>()
            val paymentMonthKey = FormatUtils.toMonthKey(paidDate)
            val paymentsToInsert = mutableListOf<FeePayment>()

            // Allocate oldest arrears first (FIFO)
            for (charge in unpaidCharges) {
                if (remainingToAllocate <= 0L) break
                val rem = charge.remainingAmount
                if (rem <= 0L) continue
                val payForThis = minOf(remainingToAllocate, rem)
                val newPaid = charge.paidAmount + payForThis
                val newStatus = if (newPaid >= charge.netAmount) FeeStatus.PAID else FeeStatus.PARTIAL
                feeChargeDao.update(charge.copy(paidAmount = newPaid, status = newStatus))
                paymentsToInsert.add(
                    FeePayment(
                        chargeId = charge.id,
                        studentId = studentId,
                        ledgerType = FeeLedgerType.TUITION,
                        receiptNumber = receiptNumber,
                        amountPaid = payForThis,
                        discountApplied = if (charge.id == targetChargeId) discountOnTarget else 0L,
                        paidDate = paidDate,
                        monthKey = paymentMonthKey,
                        notes = notes.trim()
                    )
                )
                coveredTitles.add(charge.title)
                remainingToAllocate -= payForThis
            }

            feePaymentDao.insertAll(paymentsToInsert)

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
    }

    suspend fun deleteFeeCharge(charge: FeeCharge) = feeChargeDao.delete(charge)

    // Ledger Entries
    suspend fun saveLedgerEntry(entry: LedgerEntry): Long {
        return if (entry.id == 0L) ledgerEntryDao.insert(entry)
        else {
            ledgerEntryDao.update(entry)
            entry.id
        }
    }

    suspend fun deleteLedgerEntry(entry: LedgerEntry) = ledgerEntryDao.delete(entry)

    // Exams & Marks
    suspend fun saveExam(exam: Exam): Long {
        return if (exam.id == 0L) examDao.insert(exam)
        else {
            examDao.update(exam)
            exam.id
        }
    }

    suspend fun deleteExam(exam: Exam) = examDao.delete(exam)

    suspend fun saveExamMarksForSubject(
        examId: Long,
        subject: Subject,
        marksByStudent: Map<Long, Double>
    ) {
        val records = marksByStudent.map { (studentId, marks) ->
            ExamMark(
                examId = examId,
                studentId = studentId,
                subjectId = subject.id,
                marksObtained = marks.coerceIn(0.0, subject.totalMarks.toDouble()),
                totalMarks = subject.totalMarks
            )
        }
        if (records.isNotEmpty()) {
            examMarkDao.upsertAll(records)
        }
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
