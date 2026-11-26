package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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

@Entity(tableName = "academy_profile")
data class AcademyProfile(
    @PrimaryKey val id: Int = 1,
    val academyName: String = "My Academy",
    val tagline: String = "",
    val phone: String = "",
    val address: String = "",
    val logoPath: String? = null,
    val nextTuitionReceiptSeq: Int = 1,
    val nextPaperReceiptSeq: Int = 1
)

@Entity(tableName = "classes")
data class AcademyClass(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val monthlyFee: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "subjects",
    foreignKeys = [
        ForeignKey(
            entity = AcademyClass::class,
            parentColumns = ["id"],
            childColumns = ["classId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("classId")]
)
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    val name: String,
    val totalMarks: Int = 100
)

@Entity(
    tableName = "timetable_entries",
    foreignKeys = [
        ForeignKey(
            entity = AcademyClass::class,
            parentColumns = ["id"],
            childColumns = ["classId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("classId")]
)
data class TimetableEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    val dayOfWeek: Int, // 1=Mon .. 6=Sat
    val subjectName: String,
    val teacherName: String,
    val startTime: String,
    val endTime: String
)

@Entity(
    tableName = "students",
    foreignKeys = [
        ForeignKey(
            entity = AcademyClass::class,
            parentColumns = ["id"],
            childColumns = ["classId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("classId")]
)
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fatherName: String,
    val phone: String,
    val address: String,
    val classId: Long,
    val rollNo: String,
    val admissionDate: Long,
    val photoPath: String? = null,
    val monthlyDiscount: Long = 0L,
    val status: String = StudentStatus.ACTIVE,
    val leftDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "teachers")
data class Teacher(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subject: String,
    val phone: String,
    val salary: Long,
    val joiningDate: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "salary_payments",
    foreignKeys = [
        ForeignKey(
            entity = Teacher::class,
            parentColumns = ["id"],
            childColumns = ["teacherId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("teacherId"), Index("monthKey")]
)
data class SalaryPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val teacherId: Long,
    val monthKey: String,
    val amount: Long,
    val paidDate: Long,
    val notes: String = ""
)

@Entity(
    tableName = "attendance_records",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["studentId", "dateKey"], unique = true),
        Index("classId"),
        Index("dateKey"),
        Index("monthKey")
    ]
)
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val classId: Long,
    val dateKey: String, // yyyy-MM-dd
    val monthKey: String, // yyyy-MM
    val status: String
)

@Entity(
    tableName = "fee_charges",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("studentId"),
        Index("classId"),
        Index("ledgerType"),
        Index("monthKey")
    ]
)
data class FeeCharge(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val classId: Long,
    val ledgerType: String, // TUITION, PAPER_FEE, OTHER
    val feeSubType: String, // Monthly Tuition, Exam/Paper title, Admission Fee, Custom
    val title: String,
    val monthKey: String? = null, // yyyy-MM
    val batchKey: String? = null, // unique batch key to prevent duplicate paper fee creation
    val baseAmount: Long,
    val discountAmount: Long = 0L,
    val netAmount: Long,
    val paidAmount: Long = 0L,
    val status: String = FeeStatus.UNPAID,
    val dueDate: Long,
    val createdAt: Long = System.currentTimeMillis()
) {
    val remainingAmount: Long
        get() = (netAmount - paidAmount).coerceAtLeast(0L)
}

@Entity(
    tableName = "fee_payments",
    foreignKeys = [
        ForeignKey(
            entity = FeeCharge::class,
            parentColumns = ["id"],
            childColumns = ["chargeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("chargeId"),
        Index("studentId"),
        Index("ledgerType"),
        Index("monthKey"),
        Index("receiptNumber")
    ]
)
data class FeePayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chargeId: Long,
    val studentId: Long,
    val ledgerType: String, // TUITION, PAPER_FEE, OTHER
    val receiptNumber: String, // R-0001 for Tuition/Other, PF-0001 for Paper Fee
    val amountPaid: Long,
    val discountApplied: Long = 0L,
    val paidDate: Long,
    val monthKey: String, // yyyy-MM of payment date
    val notes: String = ""
)

@Entity(tableName = "ledger_entries")
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // INCOME or EXPENSE
    val category: String,
    val title: String,
    val amount: Long,
    val date: Long,
    val monthKey: String,
    val notes: String = "",
    val linkedSalaryPaymentId: Long? = null
)

@Entity(
    tableName = "exams",
    foreignKeys = [
        ForeignKey(
            entity = AcademyClass::class,
            parentColumns = ["id"],
            childColumns = ["classId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("classId")]
)
data class Exam(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val classId: Long,
    val examDate: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "exam_marks",
    foreignKeys = [
        ForeignKey(
            entity = Exam::class,
            parentColumns = ["id"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["examId", "studentId", "subjectId"], unique = true),
        Index("studentId"),
        Index("subjectId")
    ]
)
data class ExamMark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examId: Long,
    val studentId: Long,
    val subjectId: Long,
    val marksObtained: Double,
    val totalMarks: Int
)
