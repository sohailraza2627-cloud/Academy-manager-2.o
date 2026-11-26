package com.example

import com.example.util.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class AcademyLogicUnitTest {

    @Test
    fun `currency formats as Rs with comma grouping`() {
        assertEquals("Rs 12,500", FormatUtils.formatCurrency(12500L))
        assertEquals("Rs 0", FormatUtils.formatCurrency(0L))
        assertEquals("-Rs 500", FormatUtils.formatCurrency(-500L))
    }

    @Test
    fun `grade boundaries match specification`() {
        assertEquals("A+", FormatUtils.computeGrade(90.0))
        assertEquals("A", FormatUtils.computeGrade(80.0))
        assertEquals("B", FormatUtils.computeGrade(70.0))
        assertEquals("C", FormatUtils.computeGrade(60.0))
        assertEquals("D", FormatUtils.computeGrade(50.0))
        assertEquals("F", FormatUtils.computeGrade(49.9))
    }

    @Test
    fun `receipt numbers and month keys format accurately`() {
        assertEquals("R-0001", FormatUtils.formatReceiptNumber("R", 1))
        assertEquals("PF-0042", FormatUtils.formatReceiptNumber("PF", 42))
        assertEquals("2026-12", FormatUtils.shiftMonthKey("2026-11", 1))
        assertEquals("2026-10", FormatUtils.shiftMonthKey("2026-11", -1))
        assertEquals("AR", FormatUtils.initialsFor("Ali Raza"))
    }
}
