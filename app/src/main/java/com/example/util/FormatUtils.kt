package com.example.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object FormatUtils {
    private val currencyFormat: NumberFormat
        get() = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = 0
            isGroupingUsed = true
        }

    fun formatCurrency(amount: Long): String {
        return if (amount < 0) {
            "-Rs ${currencyFormat.format(-amount)}"
        } else {
            "Rs ${currencyFormat.format(amount)}"
        }
    }

    fun formatDate(epochMillis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
        return sdf.format(Date(epochMillis))
    }

    fun formatDateTime(epochMillis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
        return sdf.format(Date(epochMillis))
    }

    fun toDateKey(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date(epochMillis))
    }

    fun fromDateKey(dateKey: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    fun formatDateKey(dateKey: String): String {
        return formatDate(fromDateKey(dateKey))
    }

    fun toMonthKey(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        return sdf.format(Date(epochMillis))
    }

    fun currentMonthKey(): String = toMonthKey(System.currentTimeMillis())

    fun currentDateKey(): String = toDateKey(System.currentTimeMillis())

    fun formatMonthKey(monthKey: String): String {
        return try {
            val date = SimpleDateFormat("yyyy-MM", Locale.US).parse(monthKey)
            if (date != null) SimpleDateFormat("MMM yyyy", Locale.US).format(date) else monthKey
        } catch (_: Exception) {
            monthKey
        }
    }

    fun shiftMonthKey(monthKey: String, deltaMonths: Int): String {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(monthKey) ?: Date()
        cal.add(Calendar.MONTH, deltaMonths)
        return sdf.format(cal.time)
    }

    fun monthRangeMillis(monthKey: String): Pair<Long, Long> {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(monthKey) ?: Date()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis - 1
        return start to end
    }

    fun normalizeDevicePrefix(raw: String): String {
        val cleaned = raw.uppercase(Locale.US).filter { it.isLetterOrDigit() }.take(4)
        return cleaned.ifBlank { "A" }
    }

    fun formatReceiptNumber(prefix: String, devicePrefix: String, sequence: Int): String {
        val dev = normalizeDevicePrefix(devicePrefix)
        return "$prefix-$dev-${sequence.toString().padStart(4, '0')}"
    }

    fun computeGrade(percentage: Double): String {
        return when {
            percentage >= 90.0 -> "A+"
            percentage >= 80.0 -> "A"
            percentage >= 70.0 -> "B"
            percentage >= 60.0 -> "C"
            percentage >= 50.0 -> "D"
            else -> "F"
        }
    }

    fun formatMarks(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }

    fun formatPercent(value: Double): String {
        return "${value.roundToInt()}%"
    }

    fun initialsFor(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase(Locale.US)
            else -> "${parts.first().first()}${parts.last().first()}".uppercase(Locale.US)
        }
    }
}
