package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

object StudentStatus {
    const val ACTIVE = "ACTIVE"
    const val LEFT = "LEFT"
}

object AttendanceStatus {
    const val PRESENT = "PRESENT"
    const val ABSENT = "ABSENT"
    const val LEAVE = "LEAVE"
}

object FeeLedgerType {
    const val TUITION = "TUITION"
    const val PAPER_FEE = "PAPER_FEE"
    const val OTHER = "OTHER"
}

object FeeStatus {
    const val UNPAID = "UNPAID"
    const val PARTIAL = "PARTIAL"
    const val PAID = "PAID"
}

object LedgerType {
    const val INCOME = "INCOME"
    const val EXPENSE = "EXPENSE"
}

object UserRole {
    const val ADMIN = "ADMIN"
    const val TEACHER = "TEACHER"
}

@Entity(tableName = "academy_profile")
data class AcademyProfile(
    @PrimaryKey val id: String = PROFILE_ID,
    val academyName: String = "My Academy",
    val tagline: String = "",
    val phone: String = "",
    val address: String = "",
    val logoPath: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val pendingSync: Boolean = true
) {
    companion object {
        const val PROFILE_ID = "academy_profile"
    }
}

@Entity(tableName = "classes")
data class AcademyClass(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val monthlyFee: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val pendingSync: Boolean = true
)

@Entity(
    tableName = "subjects",
    indices = [Index("classId")]
)
data class Subject(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val classId: String,
    val name: String,
    val totalMarks: Int = 100,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val pendingSync: Boolean = true
)

@Entity(
    tableName = "timetable_entries",
    indices = [Index("classId")]
)
data class TimetableEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val classId: String,
    val dayOfWeek: Int, // 1=Mon .. 6=Sat
    val subjectName: String,
    val teacherName: String,
    val startTime: String,
    val endTime: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val pendingSync: Boolean = true
)

@Entity(
    tableName = "students",
    indices = [Index("classId")]
)
data class Student(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val fatherName: String,
    val phone: String,
    val address: String,
    val classId: String,
    val rollNo: String,
    val admissionDate: Long,
    val photoPath: String? = null,
    val guardianWhatsapp: String = "",
    val whatsappHistory: String = "",
    val monthlyDiscount: Long = 0L,
    val status: String = StudentStatus.ACTIVE,
    val leftDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val pendingSync: Boolean = true
)

@Entity(tableName = "teachers")
data class Teacher(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val subject: String,
    val phone: String,
    val salary: Long,
    val joiningDate: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val pendingSync: Boolean = true
)

@Entity(
    tableName = "salary_payments",
    indices = [Index("teacherId"), Index("monthKey")]
)
data class SalaryPayment(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val teacherId: String,
    val monthKey: String,
    val amount: Long,
    val paidDate: Long,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val pendingSync: Boolean = true
)

@Entity(
    tableName = "attendance_records",
    indices = [
        Index("studentId"),
        Index("classId"),
        Index("dateKey"),
        Index("monthKey")
    ]
)
data class AttendanceRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val studentId: String,
    val classId: String,
    val dateKey: String, // yyyy-MM-dd
    val monthKey: String, // yyyy-MM
    val status: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val pendingSync: Boolean = true
)

@Entity(
    tableName = "fee_charges",
    indices = [
        Index("studentId"),
        Index("classId"),
        Index("ledgerType"),
        Index("monthKey")
    ]
)
data class FeeCharge(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val studentId: String,
    val classId: String,
    val ledgerType: String, // TUITION, PAPER_FEE, OTHER
    val feeSubType: String,
    val title: String,
    val monthKey: String? = null, // yyyy-MM
    val batchKey: String? = null,
    val baseAmount: Long,
    val discountAmount: Long = 0L,
    val netAmount: Long,
    val paidAmount: Long = 0L,
    val status: String = FeeStatus.UNPAID,
    val dueDate: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val pendingSync: Boolean = true
) {
    val remainingAmount: Long
        get() = (netAmount - paidAmount).coerceAtLeast(0L)
}

/**
 * Append-only payment/receipt record. Never edited or overwritten.
 */
@Entity(
    tableName = "fee_payments",
    indices = [
        Index("chargeId"),
        Index("studentId"),
        Index("ledgerType"),
        Index("monthKey"),
        Index("receiptNumber")
    ]
)
data class FeePayment(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val chargeId: String,
    val studentId: String,
    val ledgerType: String, // TUITION, PAPER_FEE, OTHER
    val receiptNumber: String, // e.g. R-A-0001 for Tuition/Other, PF-A-0001 for Paper Fee
    val amountPaid: Long,
    val discountApplied: Long = 0L,
    val paidDate: Long,
    val monthKey: String, // yyyy-MM of payment date
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val pendingSync: Boolean = true
)

@Entity(tableName = "ledger_entries")
data class LedgerEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: String, // INCOME or EXPENSE
    val category: String,
    val title: String,
    val amount: Long,
    val date: Long,
    val monthKey: String,
    val notes: String = "",
    val linkedSalaryPaymentId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val pendingSync: Boolean = true
)

@Entity(
    tableName = "exams",
    indices = [Index("classId")]
)
data class Exam(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val classId: String,
    val examDate: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val pendingSync: Boolean = true
)

@Entity(
    tableName = "exam_marks",
    indices = [
        Index("examId"),
        Index("studentId"),
        Index("subjectId")
    ]
)
data class ExamMark(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val examId: String,
    val studentId: String,
    val subjectId: String,
    val marksObtained: Double,
    val totalMarks: Int,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val pendingSync: Boolean = true
)

@Entity(tableName = "academy_members")
data class AcademyMember(
    @PrimaryKey val uid: String,
    val email: String,
    val name: String,
    val role: String, // ADMIN or TEACHER
    val createdAt: Long = System.currentTimeMillis()
)
