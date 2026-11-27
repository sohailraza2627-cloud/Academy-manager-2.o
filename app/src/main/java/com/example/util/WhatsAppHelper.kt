package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

enum class WhatsAppMessageType {
    ATTENDANCE_ALERT,
    EXAM_RESULT,
    FEE_REMINDER
}

sealed class WhatsAppLaunchException(message: String) : Exception(message) {
    class MissingOrInvalidNumber(message: String) : WhatsAppLaunchException(message)
    class NotInstalled(message: String) : WhatsAppLaunchException(message)
    class LaunchFailed(message: String) : WhatsAppLaunchException(message)
}

object WhatsAppHelper {

    /**
     * Normalizes a guardian WhatsApp number into clean E.164 format (e.g. "+923001234567").
     * Supports Pakistan local formats ("03xx...", "3xx...", "0092...", "92...") as well as
     * international "+<country><number>" formats. Returns null when blank or invalid.
     */
    fun normalizeWhatsAppNumber(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val compact = trimmed.replace(Regex("[\\s\\-().]"), "")
        if (compact.isEmpty()) return null

        val digitsOnly: String
        when {
            compact.startsWith("+") -> {
                val afterPlus = compact.drop(1)
                if (afterPlus.isEmpty() || !afterPlus.all(Char::isDigit)) return null
                digitsOnly = afterPlus
            }
            compact.startsWith("00") -> {
                val afterDoubleZero = compact.drop(2)
                if (afterDoubleZero.isEmpty() || !afterDoubleZero.all(Char::isDigit)) return null
                digitsOnly = afterDoubleZero
            }
            compact.startsWith("03") -> {
                if (!compact.all(Char::isDigit)) return null
                if (compact.length != 11) return null
                digitsOnly = "92" + compact.drop(1)
            }
            compact.startsWith("0") -> {
                if (!compact.all(Char::isDigit)) return null
                if (compact.length !in 10..11) return null
                digitsOnly = "92" + compact.drop(1)
            }
            compact.startsWith("3") && compact.length == 10 && compact.all(Char::isDigit) -> {
                digitsOnly = "92$compact"
            }
            compact.all(Char::isDigit) -> {
                digitsOnly = compact
            }
            else -> return null
        }

        if (digitsOnly.length !in 10..15) return null
        if (digitsOnly.startsWith("92") && digitsOnly.length !in 11..12) return null
        return "+$digitsOnly"
    }

    /**
     * Returns digits only without '+' for wa.me URLs, or null if invalid.
     */
    fun toWaMeDigits(raw: String): String? {
        return normalizeWhatsAppNumber(raw)?.removePrefix("+")
    }

    /**
     * Appends an old number with timestamp into history without duplicating the latest entry.
     */
    fun appendHistory(existingHistory: String, previousNumber: String, dateMillis: Long = System.currentTimeMillis()): String {
        val cleanOld = previousNumber.trim()
        if (cleanOld.isEmpty()) return existingHistory
        val dateLabel = FormatUtils.formatDate(dateMillis)
        val entry = "$cleanOld ($dateLabel)"
        val existingEntries = existingHistory.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (existingEntries.firstOrNull() == entry) return existingHistory
        return (listOf(entry) + existingEntries).take(10).joinToString("\n")
    }

    fun parseHistoryEntries(history: String): List<String> {
        return history.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun buildAttendanceMessage(
        academyName: String,
        studentName: String,
        className: String,
        dateLabel: String,
        status: String
    ): String {
        val statusReadable = when (status.uppercase()) {
            "ABSENT" -> "Absent"
            "LEAVE" -> "on Leave"
            else -> "Present"
        }
        val classPart = if (className.isNotBlank()) " ($className)" else ""
        return buildString {
            append("Assalam-o-Alaikum,")
            append("\n\n")
            append("*${academyName.ifBlank { "Alhamd Science Evening Academy" }}* Attendance Alert:")
            append("\n")
            append("Student: *$studentName*$classPart")
            append("\n")
            append("Date: $dateLabel")
            append("\n")
            append("Status: *$statusReadable*")
            if (status.equals("ABSENT", ignoreCase = true)) {
                append("\n\nPlease inform the academy if there is any emergency. Thank you.")
            }
        }
    }

    fun buildExamResultMessage(
        academyName: String,
        studentName: String,
        subjectOrExamTitle: String,
        testDateLabel: String,
        obtainedMarks: String,
        totalMarks: String,
        percentageLabel: String,
        grade: String? = null
    ): String {
        return buildString {
            append("Assalam-o-Alaikum,")
            append("\n\n")
            append("*${academyName.ifBlank { "Alhamd Science Evening Academy" }}* Test Result:")
            append("\n")
            append("Student: *$studentName*")
            append("\n")
            append("Test / Subject: $subjectOrExamTitle")
            append("\n")
            append("Date: $testDateLabel")
            append("\n")
            append("Marks: *$obtainedMarks / $totalMarks* ($percentageLabel)")
            if (!grade.isNullOrBlank()) {
                append("\nGrade: $grade")
            }
            append("\n\nThank you for supporting your child's progress.")
        }
    }

    fun buildFeeReminderMessage(
        academyName: String,
        studentName: String,
        className: String,
        tuitionPending: Long,
        paperFeePending: Long,
        otherFeePending: Long = 0L
    ): String {
        val totalPending = (tuitionPending + paperFeePending + otherFeePending).coerceAtLeast(0L)
        val classPart = if (className.isNotBlank()) " ($className)" else ""
        return buildString {
            append("Assalam-o-Alaikum,")
            append("\n\n")
            append("*${academyName.ifBlank { "Alhamd Science Evening Academy" }}* Fee Reminder:")
            append("\n")
            append("Student: *$studentName*$classPart")
            append("\n")
            append("Monthly Tuition Pending: ${FormatUtils.formatCurrency(tuitionPending)}")
            append("\n")
            append("Paper / Test Fee Pending: ${FormatUtils.formatCurrency(paperFeePending)}")
            if (otherFeePending > 0L) {
                append("\nOther Fee Pending: ${FormatUtils.formatCurrency(otherFeePending)}")
            }
            append("\n")
            append("*Total Pending Balance: ${FormatUtils.formatCurrency(totalPending)}*")
            append("\n\nKindly clear the pending dues at your earliest convenience. JazakAllah.")
        }
    }

    fun findInstalledWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        val candidates = listOf("com.whatsapp", "com.whatsapp.w4b")
        return candidates.firstOrNull { pkg -> isPackageInstalled(pm, pkg) }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens an individual WhatsApp chat with a single guardian via Android Intent.
     * Never sends bulk/broadcast messages.
     */
    fun openWhatsAppChat(
        context: Context,
        rawPhoneNumber: String,
        message: String
    ): Result<Unit> {
        val digits = toWaMeDigits(rawPhoneNumber)
            ?: return Result.failure(
                WhatsAppLaunchException.MissingOrInvalidNumber(
                    "Guardian WhatsApp number is missing or invalid. Please update the student's profile first."
                )
            )

        val whatsappPkg = findInstalledWhatsAppPackage(context)
            ?: return Result.failure(
                WhatsAppLaunchException.NotInstalled(
                    "WhatsApp is not installed on this device."
                )
            )

        val uri = Uri.parse("https://wa.me/$digits?text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(whatsappPkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            Result.success(Unit)
        } catch (_: ActivityNotFoundException) {
            Result.failure(
                WhatsAppLaunchException.NotInstalled("WhatsApp could not be opened on this device.")
            )
        } catch (e: Exception) {
            Result.failure(
                WhatsAppLaunchException.LaunchFailed(
                    e.localizedMessage ?: "Failed to open WhatsApp."
                )
            )
        }
    }
}
