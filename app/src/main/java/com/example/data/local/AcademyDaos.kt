package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AcademyProfileDao {
    @Query("SELECT * FROM academy_profile WHERE id = 'academy_profile' AND deleted = 0 LIMIT 1")
    fun observeProfile(): Flow<AcademyProfile?>

    @Query("SELECT * FROM academy_profile WHERE id = 'academy_profile' LIMIT 1")
    suspend fun getProfileOnce(): AcademyProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: AcademyProfile)

    @Query("SELECT * FROM academy_profile WHERE pendingSync = 1 LIMIT 1")
    suspend fun getPendingSync(): AcademyProfile?

    @Query("UPDATE academy_profile SET pendingSync = 0 WHERE id = 'academy_profile' AND updatedAt <= :syncedAt")
    suspend fun markSynced(syncedAt: Long)

    @Query("SELECT COUNT(*) FROM academy_profile WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface AcademyClassDao {
    @Query("SELECT * FROM classes WHERE deleted = 0 ORDER BY name ASC")
    fun observeAll(): Flow<List<AcademyClass>>

    @Query("SELECT * FROM classes WHERE deleted = 0 ORDER BY name ASC")
    suspend fun getAllOnce(): List<AcademyClass>

    @Query("SELECT * FROM classes WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: String): AcademyClass?

    @Query("SELECT * FROM classes WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getById(id: String): AcademyClass?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(academyClass: AcademyClass)

    @Query("SELECT * FROM classes WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<AcademyClass>

    @Query("UPDATE classes SET pendingSync = 0 WHERE id = :id AND updatedAt <= :syncedAt")
    suspend fun markSynced(id: String, syncedAt: Long)

    @Query("SELECT COUNT(*) FROM classes WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects WHERE deleted = 0 ORDER BY name ASC")
    fun observeAll(): Flow<List<Subject>>

    @Query("SELECT * FROM subjects WHERE classId = :classId AND deleted = 0 ORDER BY name ASC")
    fun observeByClass(classId: String): Flow<List<Subject>>

    @Query("SELECT * FROM subjects WHERE classId = :classId AND deleted = 0 ORDER BY name ASC")
    suspend fun getByClassOnce(classId: String): List<Subject>

    @Query("SELECT * FROM subjects WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: String): Subject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(subject: Subject)

    @Query("UPDATE subjects SET deleted = 1, updatedAt = :now, pendingSync = 1 WHERE classId = :classId AND deleted = 0")
    suspend fun softDeleteByClass(classId: String, now: Long)

    @Query("SELECT * FROM subjects WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<Subject>

    @Query("UPDATE subjects SET pendingSync = 0 WHERE id = :id AND updatedAt <= :syncedAt")
    suspend fun markSynced(id: String, syncedAt: Long)

    @Query("SELECT COUNT(*) FROM subjects WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetable_entries WHERE classId = :classId AND deleted = 0 ORDER BY dayOfWeek ASC, startTime ASC")
    fun observeByClass(classId: String): Flow<List<TimetableEntry>>

    @Query("SELECT * FROM timetable_entries WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: String): TimetableEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: TimetableEntry)

    @Query("UPDATE timetable_entries SET deleted = 1, updatedAt = :now, pendingSync = 1 WHERE classId = :classId AND deleted = 0")
    suspend fun softDeleteByClass(classId: String, now: Long)

    @Query("SELECT * FROM timetable_entries WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<TimetableEntry>

    @Query("UPDATE timetable_entries SET pendingSync = 0 WHERE id = :id AND updatedAt <= :syncedAt")
    suspend fun markSynced(id: String, syncedAt: Long)

    @Query("SELECT COUNT(*) FROM timetable_entries WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE deleted = 0 ORDER BY name ASC")
    fun observeAll(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE id = :id AND deleted = 0 LIMIT 1")
    fun observeById(id: String): Flow<Student?>

    @Query("SELECT * FROM students WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getById(id: String): Student?

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: String): Student?

    @Query("SELECT * FROM students WHERE classId = :classId AND status = 'ACTIVE' AND deleted = 0 ORDER BY rollNo ASC, name ASC")
    suspend fun getActiveByClass(classId: String): List<Student>

    @Query("SELECT * FROM students WHERE status = 'ACTIVE' AND deleted = 0 ORDER BY name ASC")
    suspend fun getAllActive(): List<Student>

    @Query("SELECT COUNT(*) FROM students WHERE classId = :classId AND deleted = 0")
    suspend fun countByClass(classId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(student: Student)

    @Query("SELECT * FROM students WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<Student>

    @Query("UPDATE students SET pendingSync = 0 WHERE id = :id AND updatedAt <= :syncedAt")
    suspend fun markSynced(id: String, syncedAt: Long)

    @Query("SELECT COUNT(*) FROM students WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface TeacherDao {
    @Query("SELECT * FROM teachers WHERE deleted = 0 ORDER BY name ASC")
    fun observeAll(): Flow<List<Teacher>>

    @Query("SELECT * FROM teachers WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: String): Teacher?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(teacher: Teacher)

    @Query("SELECT * FROM teachers WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<Teacher>

    @Query("UPDATE teachers SET pendingSync = 0 WHERE id = :id AND updatedAt <= :syncedAt")
    suspend fun markSynced(id: String, syncedAt: Long)

    @Query("SELECT COUNT(*) FROM teachers WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface SalaryPaymentDao {
    @Query("SELECT * FROM salary_payments WHERE deleted = 0 ORDER BY paidDate DESC")
    fun observeAll(): Flow<List<SalaryPayment>>

    @Query("SELECT * FROM salary_payments WHERE teacherId = :teacherId AND deleted = 0 ORDER BY paidDate DESC")
    fun observeByTeacher(teacherId: String): Flow<List<SalaryPayment>>

    @Query("SELECT * FROM salary_payments WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: String): SalaryPayment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(payment: SalaryPayment)

    @Query("UPDATE salary_payments SET deleted = 1, updatedAt = :now, pendingSync = 1 WHERE teacherId = :teacherId AND deleted = 0")
    suspend fun softDeleteByTeacher(teacherId: String, now: Long)

    @Query("SELECT * FROM salary_payments WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<SalaryPayment>

    @Query("UPDATE salary_payments SET pendingSync = 0 WHERE id = :id AND updatedAt <= :syncedAt")
    suspend fun markSynced(id: String, syncedAt: Long)

    @Query("SELECT COUNT(*) FROM salary_payments WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records WHERE deleted = 0 ORDER BY dateKey DESC")
    fun observeAll(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE classId = :classId AND dateKey = :dateKey AND deleted = 0")
    suspend fun getByClassAndDate(classId: String, dateKey: String): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE studentId = :studentId AND dateKey = :dateKey LIMIT 1")
    suspend fun getByStudentAndDate(studentId: String, dateKey: String): AttendanceRecord?

    @Query("SELECT * FROM attendance_records WHERE studentId = :studentId AND deleted = 0 ORDER BY dateKey DESC")
    fun observeByStudent(studentId: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: String): AttendanceRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<AttendanceRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: AttendanceRecord)

    @Query("UPDATE attendance_records SET deleted = 1, updatedAt = :now, pendingSync = 1 WHERE studentId = :studentId AND deleted = 0")
    suspend fun softDeleteByStudent(studentId: String, now: Long)

    @Query("SELECT * FROM attendance_records WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<AttendanceRecord>

    @Query("UPDATE attendance_records SET pendingSync = 0 WHERE id = :id AND updatedAt <= :syncedAt")
    suspend fun markSynced(id: String, syncedAt: Long)

    @Query("SELECT COUNT(*) FROM attendance_records WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface FeeChargeDao {
    @Query("SELECT * FROM fee_charges WHERE deleted = 0 ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<FeeCharge>>

    @Query("SELECT * FROM fee_charges WHERE studentId = :studentId AND deleted = 0 ORDER BY createdAt DESC, id DESC")
    fun observeByStudent(studentId: String): Flow<List<FeeCharge>>

    @Query("SELECT * FROM fee_charges WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getById(id: String): FeeCharge?

    @Query("SELECT * FROM fee_charges WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: String): FeeCharge?

    @Query("SELECT * FROM fee_charges WHERE studentId = :studentId AND ledgerType = :ledgerType AND monthKey = :monthKey AND deleted = 0 LIMIT 1")
    suspend fun findTuitionCharge(studentId: String, ledgerType: String, monthKey: String): FeeCharge?

    @Query("SELECT * FROM fee_charges WHERE studentId = :studentId AND ledgerType = 'PAPER_FEE' AND batchKey = :batchKey AND deleted = 0 LIMIT 1")
    suspend fun findPaperFeeCharge(studentId: String, batchKey: String): FeeCharge?

    @Query("SELECT * FROM fee_charges WHERE studentId = :studentId AND ledgerType = 'TUITION' AND status != 'PAID' AND deleted = 0 ORDER BY monthKey ASC, createdAt ASC")
    suspend fun getUnpaidTuitionCharges(studentId: String): List<FeeCharge>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(charge: FeeCharge)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(charges: List<FeeCharge>)

    @Query("UPDATE fee_charges SET deleted = 1, updatedAt = :now, pendingSync = 1 WHERE studentId = :studentId AND deleted = 0")
    suspend fun softDeleteByStudent(studentId: String, now: Long)

    @Query("SELECT * FROM fee_charges WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<FeeCharge>

    @Query("UPDATE fee_charges SET pendingSync = 0 WHERE id = :id AND updatedAt <= :syncedAt")
    suspend fun markSynced(id: String, syncedAt: Long)

    @Query("SELECT COUNT(*) FROM fee_charges WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface FeePaymentDao {
    @Query("SELECT * FROM fee_payments WHERE deleted = 0 ORDER BY paidDate DESC, id DESC")
    fun observeAll(): Flow<List<FeePayment>>

    @Query("SELECT * FROM fee_payments WHERE studentId = :studentId AND deleted = 0 ORDER BY paidDate DESC, id DESC")
    fun observeByStudent(studentId: String): Flow<List<FeePayment>>

    @Query("SELECT * FROM fee_payments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FeePayment?

    // Append-only: ignore conflicts if the payment id already exists!
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAppendOnly(payment: FeePayment)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllAppendOnly(payments: List<FeePayment>)

    @Query("SELECT * FROM fee_payments WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<FeePayment>

    @Query("UPDATE fee_payments SET pendingSync = 0 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("SELECT COUNT(*) FROM fee_payments WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface LedgerEntryDao {
    @Query("SELECT * FROM ledger_entries WHERE deleted = 0 ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: String): LedgerEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: LedgerEntry)

    @Query("UPDATE ledger_entries SET deleted = 1, updatedAt = :now, pendingSync = 1 WHERE linkedSalaryPaymentId = :salaryPaymentId AND deleted = 0")
    suspend fun softDeleteBySalaryPaymentId(salaryPaymentId: String, now: Long)

    @Query("SELECT * FROM ledger_entries WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<LedgerEntry>

    @Query("UPDATE ledger_entries SET pendingSync = 0 WHERE id = :id AND updatedAt <= :syncedAt")
    suspend fun markSynced(id: String, syncedAt: Long)

    @Query("SELECT COUNT(*) FROM ledger_entries WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams WHERE deleted = 0 ORDER BY examDate DESC, id DESC")
    fun observeAll(): Flow<List<Exam>>

    @Query("SELECT * FROM exams WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getById(id: String): Exam?

    @Query("SELECT * FROM exams WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: String): Exam?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exam: Exam)

    @Query("SELECT * FROM exams WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<Exam>

    @Query("UPDATE exams SET pendingSync = 0 WHERE id = :id AND updatedAt <= :syncedAt")
    suspend fun markSynced(id: String, syncedAt: Long)

    @Query("SELECT COUNT(*) FROM exams WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface ExamMarkDao {
    @Query("SELECT * FROM exam_marks WHERE deleted = 0")
    fun observeAll(): Flow<List<ExamMark>>

    @Query("SELECT * FROM exam_marks WHERE examId = :examId AND deleted = 0")
    fun observeByExam(examId: String): Flow<List<ExamMark>>

    @Query("SELECT * FROM exam_marks WHERE studentId = :studentId AND deleted = 0")
    fun observeByStudent(studentId: String): Flow<List<ExamMark>>

    @Query("SELECT * FROM exam_marks WHERE examId = :examId AND studentId = :studentId AND subjectId = :subjectId LIMIT 1")
    suspend fun findByKey(examId: String, studentId: String, subjectId: String): ExamMark?

    @Query("SELECT * FROM exam_marks WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: String): ExamMark?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mark: ExamMark)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(marks: List<ExamMark>)

    @Query("UPDATE exam_marks SET deleted = 1, updatedAt = :now, pendingSync = 1 WHERE examId = :examId AND deleted = 0")
    suspend fun softDeleteByExam(examId: String, now: Long)

    @Query("UPDATE exam_marks SET deleted = 1, updatedAt = :now, pendingSync = 1 WHERE studentId = :studentId AND deleted = 0")
    suspend fun softDeleteByStudent(studentId: String, now: Long)

    @Query("SELECT * FROM exam_marks WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<ExamMark>

    @Query("UPDATE exam_marks SET pendingSync = 0 WHERE id = :id AND updatedAt <= :syncedAt")
    suspend fun markSynced(id: String, syncedAt: Long)

    @Query("SELECT COUNT(*) FROM exam_marks WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface AcademyMemberDao {
    @Query("SELECT * FROM academy_members ORDER BY role ASC, name ASC")
    fun observeAll(): Flow<List<AcademyMember>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(members: List<AcademyMember>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: AcademyMember)

    @Query("DELETE FROM academy_members WHERE uid = :uid")
    suspend fun deleteByUid(uid: String)

    @Query("DELETE FROM academy_members")
    suspend fun clearAll()
}
