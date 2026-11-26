package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AcademyProfileDao {
    @Query("SELECT * FROM academy_profile WHERE id = 1")
    fun observeProfile(): Flow<AcademyProfile?>

    @Query("SELECT * FROM academy_profile WHERE id = 1")
    suspend fun getProfileOnce(): AcademyProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: AcademyProfile)
}

@Dao
interface AcademyClassDao {
    @Query("SELECT * FROM classes ORDER BY name ASC")
    fun observeAll(): Flow<List<AcademyClass>>

    @Query("SELECT * FROM classes ORDER BY name ASC")
    suspend fun getAllOnce(): List<AcademyClass>

    @Query("SELECT * FROM classes WHERE id = :id")
    suspend fun getById(id: Long): AcademyClass?

    @Insert
    suspend fun insert(academyClass: AcademyClass): Long

    @Update
    suspend fun update(academyClass: AcademyClass)

    @Delete
    suspend fun delete(academyClass: AcademyClass)
}

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY name ASC")
    fun observeAll(): Flow<List<Subject>>

    @Query("SELECT * FROM subjects WHERE classId = :classId ORDER BY name ASC")
    fun observeByClass(classId: Long): Flow<List<Subject>>

    @Query("SELECT * FROM subjects WHERE classId = :classId ORDER BY name ASC")
    suspend fun getByClassOnce(classId: Long): List<Subject>

    @Insert
    suspend fun insert(subject: Subject): Long

    @Update
    suspend fun update(subject: Subject)

    @Delete
    suspend fun delete(subject: Subject)
}

@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetable_entries WHERE classId = :classId ORDER BY dayOfWeek ASC, startTime ASC")
    fun observeByClass(classId: Long): Flow<List<TimetableEntry>>

    @Insert
    suspend fun insert(entry: TimetableEntry): Long

    @Update
    suspend fun update(entry: TimetableEntry)

    @Delete
    suspend fun delete(entry: TimetableEntry)
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY name ASC")
    fun observeAll(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE id = :id")
    fun observeById(id: Long): Flow<Student?>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getById(id: Long): Student?

    @Query("SELECT * FROM students WHERE classId = :classId AND status = 'ACTIVE' ORDER BY rollNo ASC, name ASC")
    suspend fun getActiveByClass(classId: Long): List<Student>

    @Query("SELECT * FROM students WHERE status = 'ACTIVE' ORDER BY name ASC")
    suspend fun getAllActive(): List<Student>

    @Query("SELECT COUNT(*) FROM students WHERE classId = :classId")
    suspend fun countByClass(classId: Long): Int

    @Insert
    suspend fun insert(student: Student): Long

    @Update
    suspend fun update(student: Student)

    @Delete
    suspend fun delete(student: Student)
}

@Dao
interface TeacherDao {
    @Query("SELECT * FROM teachers ORDER BY name ASC")
    fun observeAll(): Flow<List<Teacher>>

    @Insert
    suspend fun insert(teacher: Teacher): Long

    @Update
    suspend fun update(teacher: Teacher)

    @Delete
    suspend fun delete(teacher: Teacher)
}

@Dao
interface SalaryPaymentDao {
    @Query("SELECT * FROM salary_payments ORDER BY paidDate DESC")
    fun observeAll(): Flow<List<SalaryPayment>>

    @Query("SELECT * FROM salary_payments WHERE teacherId = :teacherId ORDER BY paidDate DESC")
    fun observeByTeacher(teacherId: Long): Flow<List<SalaryPayment>>

    @Insert
    suspend fun insert(payment: SalaryPayment): Long

    @Delete
    suspend fun delete(payment: SalaryPayment)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records ORDER BY dateKey DESC")
    fun observeAll(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE dateKey = :dateKey")
    fun observeByDate(dateKey: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE classId = :classId AND dateKey = :dateKey")
    suspend fun getByClassAndDate(classId: Long, dateKey: String): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE studentId = :studentId ORDER BY dateKey DESC")
    fun observeByStudent(studentId: Long): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE classId = :classId AND monthKey = :monthKey")
    fun observeByClassAndMonth(classId: Long, monthKey: String): Flow<List<AttendanceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<AttendanceRecord>)

    @Query("DELETE FROM attendance_records WHERE classId = :classId AND dateKey = :dateKey")
    suspend fun deleteByClassAndDate(classId: Long, dateKey: String)
}

@Dao
interface FeeChargeDao {
    @Query("SELECT * FROM fee_charges ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<FeeCharge>>

    @Query("SELECT * FROM fee_charges WHERE studentId = :studentId ORDER BY createdAt DESC, id DESC")
    fun observeByStudent(studentId: Long): Flow<List<FeeCharge>>

    @Query("SELECT * FROM fee_charges WHERE id = :id")
    suspend fun getById(id: Long): FeeCharge?

    @Query("SELECT * FROM fee_charges WHERE studentId = :studentId AND ledgerType = :ledgerType AND monthKey = :monthKey LIMIT 1")
    suspend fun findTuitionCharge(studentId: Long, ledgerType: String, monthKey: String): FeeCharge?

    @Query("SELECT * FROM fee_charges WHERE studentId = :studentId AND ledgerType = 'PAPER_FEE' AND batchKey = :batchKey LIMIT 1")
    suspend fun findPaperFeeCharge(studentId: Long, batchKey: String): FeeCharge?

    @Query("SELECT * FROM fee_charges WHERE studentId = :studentId AND ledgerType = 'TUITION' AND status != 'PAID' ORDER BY monthKey ASC, id ASC")
    suspend fun getUnpaidTuitionCharges(studentId: Long): List<FeeCharge>

    @Insert
    suspend fun insert(charge: FeeCharge): Long

    @Insert
    suspend fun insertAll(charges: List<FeeCharge>)

    @Update
    suspend fun update(charge: FeeCharge)

    @Delete
    suspend fun delete(charge: FeeCharge)
}

@Dao
interface FeePaymentDao {
    @Query("SELECT * FROM fee_payments ORDER BY paidDate DESC, id DESC")
    fun observeAll(): Flow<List<FeePayment>>

    @Query("SELECT * FROM fee_payments WHERE studentId = :studentId ORDER BY paidDate DESC, id DESC")
    fun observeByStudent(studentId: Long): Flow<List<FeePayment>>

    @Query("SELECT * FROM fee_payments WHERE receiptNumber = :receiptNumber ORDER BY id ASC")
    suspend fun getByReceiptNumber(receiptNumber: String): List<FeePayment>

    @Insert
    suspend fun insert(payment: FeePayment): Long

    @Insert
    suspend fun insertAll(payments: List<FeePayment>)
}

@Dao
interface LedgerEntryDao {
    @Query("SELECT * FROM ledger_entries ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<LedgerEntry>>

    @Insert
    suspend fun insert(entry: LedgerEntry): Long

    @Update
    suspend fun update(entry: LedgerEntry)

    @Delete
    suspend fun delete(entry: LedgerEntry)

    @Query("DELETE FROM ledger_entries WHERE linkedSalaryPaymentId = :salaryPaymentId")
    suspend fun deleteBySalaryPaymentId(salaryPaymentId: Long)
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams ORDER BY examDate DESC, id DESC")
    fun observeAll(): Flow<List<Exam>>

    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun getById(id: Long): Exam?

    @Insert
    suspend fun insert(exam: Exam): Long

    @Update
    suspend fun update(exam: Exam)

    @Delete
    suspend fun delete(exam: Exam)
}

@Dao
interface ExamMarkDao {
    @Query("SELECT * FROM exam_marks")
    fun observeAll(): Flow<List<ExamMark>>

    @Query("SELECT * FROM exam_marks WHERE examId = :examId")
    fun observeByExam(examId: Long): Flow<List<ExamMark>>

    @Query("SELECT * FROM exam_marks WHERE studentId = :studentId")
    fun observeByStudent(studentId: Long): Flow<List<ExamMark>>

    @Query("SELECT * FROM exam_marks WHERE examId = :examId AND subjectId = :subjectId")
    suspend fun getByExamAndSubject(examId: Long, subjectId: Long): List<ExamMark>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(marks: List<ExamMark>)
}
