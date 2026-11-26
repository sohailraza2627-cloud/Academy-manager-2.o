package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.local.AcademyProfile
import com.example.data.local.FeeLedgerType
import com.example.data.repository.ReceiptResult
import com.example.data.repository.StudentExamResult
import java.io.File
import java.io.FileOutputStream

object PdfAndCsvExporter {

    private fun exportsDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun generateReceiptPdf(
        context: Context,
        profile: AcademyProfile,
        receipt: ReceiptResult
    ): File {
        val doc = PdfDocument()
        val pageWidth = 595 // A4 width in pt
        val pageHeight = 460
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val bluePaint = Paint().apply {
            color = Color.parseColor("#1A73E8")
            isAntiAlias = true
        }
        val titlePaint = Paint().apply {
            color = Color.parseColor("#202124")
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#5F6368")
            textSize = 11f
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = Color.parseColor("#5F6368")
            textSize = 12f
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            color = Color.parseColor("#202124")
            textSize = 13f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#DADCE0")
            strokeWidth = 1f
        }

        // Top accent bar
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 8f, bluePaint)

        var y = 42f
        val left = 40f
        val right = pageWidth - 40f

        // Academy Logo if exists
        ImageStorage.loadBitmap(profile.logoPath)?.let { bmp ->
            val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, 44, 44, true)
            canvas.drawBitmap(scaled, left, 24f, null)
            canvas.drawText(profile.academyName, left + 56f, y, titlePaint)
            if (profile.phone.isNotBlank() || profile.address.isNotBlank()) {
                val info = listOf(profile.phone, profile.address).filter { it.isNotBlank() }.joinToString(" · ")
                canvas.drawText(info, left + 56f, y + 16f, subtitlePaint)
            }
        } ?: run {
            canvas.drawText(profile.academyName, left, y, titlePaint)
            if (profile.phone.isNotBlank() || profile.address.isNotBlank()) {
                val info = listOf(profile.phone, profile.address).filter { it.isNotBlank() }.joinToString(" · ")
                canvas.drawText(info, left, y + 16f, subtitlePaint)
            }
        }

        val ledgerBadge = when (receipt.ledgerType) {
            FeeLedgerType.PAPER_FEE -> "PAPER FEE RECEIPT"
            FeeLedgerType.TUITION -> "TUITION FEE RECEIPT"
            else -> "FEE RECEIPT"
        }
        val badgePaint = Paint(valuePaint).apply {
            color = Color.parseColor("#1A73E8")
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(ledgerBadge, right, 38f, badgePaint)
        val recNoPaint = Paint(subtitlePaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("No: ${receipt.receiptNumber}", right, 54f, recNoPaint)

        y = 84f
        canvas.drawLine(left, y, right, y, linePaint)
        y += 28f

        fun drawRow(label: String, value: String) {
            canvas.drawText(label, left, y, labelPaint)
            val rightVal = Paint(valuePaint).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText(value, right, y, rightVal)
            y += 26f
        }

        drawRow("Date", FormatUtils.formatDate(receipt.paidDate))
        drawRow("Student Name", receipt.studentName)
        drawRow("Father Name", receipt.fatherName)
        drawRow("Class / Roll No", "${receipt.className} · Roll #${receipt.rollNo}")
        drawRow("Fee Description", receipt.title)

        y += 6f
        canvas.drawLine(left, y, right, y, linePaint)
        y += 26f

        if (receipt.discountApplied > 0L) {
            drawRow("Discount Applied", FormatUtils.formatCurrency(receipt.discountApplied))
        }

        val paidHighlight = Paint(valuePaint).apply {
            color = Color.parseColor("#137333")
            textSize = 15f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Amount Paid", left, y, labelPaint)
        canvas.drawText(FormatUtils.formatCurrency(receipt.amountPaid), right, y, paidHighlight)
        y += 28f

        val remHighlight = Paint(valuePaint).apply {
            color = if (receipt.remainingBalance > 0L) Color.parseColor("#C5221F") else Color.parseColor("#137333")
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Remaining Balance", left, y, labelPaint)
        canvas.drawText(FormatUtils.formatCurrency(receipt.remainingBalance), right, y, remHighlight)

        y = pageHeight - 48f
        canvas.drawLine(left, y, right, y, linePaint)
        y += 20f
        canvas.drawText("Generated offline by ${profile.academyName}", left, y, subtitlePaint)
        val sigPaint = Paint(subtitlePaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Authorized Signature", right, y, sigPaint)

        doc.finishPage(page)
        val file = File(exportsDir(context), "Receipt_${receipt.receiptNumber}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun generateResultCardPdf(
        context: Context,
        profile: AcademyProfile,
        examName: String,
        examDate: Long,
        className: String,
        result: StudentExamResult
    ): File {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842 // A4
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val bluePaint = Paint().apply { color = Color.parseColor("#1A73E8") }
        val headerBgPaint = Paint().apply { color = Color.parseColor("#F1F3F4") }
        val titlePaint = Paint().apply {
            color = Color.parseColor("#202124")
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.parseColor("#202124")
            textSize = 12f
            isAntiAlias = true
        }
        val boldPaint = Paint(bodyPaint).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val mutedPaint = Paint().apply {
            color = Color.parseColor("#5F6368")
            textSize = 11f
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#DADCE0")
            strokeWidth = 1f
        }

        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 10f, bluePaint)
        val left = 44f
        val right = pageWidth - 44f
        var y = 48f

        canvas.drawText(profile.academyName, left, y, titlePaint)
        val tagPaint = Paint(boldPaint).apply {
            color = Color.parseColor("#1A73E8")
            textSize = 14f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("RESULT CARD", right, y, tagPaint)
        y += 18f
        canvas.drawText("$examName · ${FormatUtils.formatDate(examDate)}", left, y, mutedPaint)
        y += 22f
        canvas.drawLine(left, y, right, y, linePaint)
        y += 24f

        canvas.drawText("Student: ${result.student.name}", left, y, boldPaint)
        canvas.drawText("Class: $className", left + 300f, y, bodyPaint)
        y += 20f
        canvas.drawText("Father Name: ${result.student.fatherName}", left, y, bodyPaint)
        canvas.drawText("Roll No: ${result.student.rollNo}", left + 300f, y, bodyPaint)
        y += 28f

        // Table Header
        canvas.drawRect(left, y - 16f, right, y + 10f, headerBgPaint)
        canvas.drawText("Subject", left + 8f, y, boldPaint)
        canvas.drawText("Total", left + 260f, y, boldPaint)
        canvas.drawText("Obtained", left + 345f, y, boldPaint)
        canvas.drawText("Percent", left + 440f, y, boldPaint)
        y += 26f

        for (item in result.subjectMarks) {
            if (y > pageHeight - 140f) break
            canvas.drawText(item.subject.name, left + 8f, y, bodyPaint)
            canvas.drawText(item.total.toString(), left + 260f, y, bodyPaint)
            val obtStr = item.obtained?.let { FormatUtils.formatMarks(it) } ?: "-"
            canvas.drawText(obtStr, left + 345f, y, bodyPaint)
            val pctStr = item.obtained?.let {
                if (item.total > 0) FormatUtils.formatPercent((it / item.total) * 100.0) else "-"
            } ?: "-"
            canvas.drawText(pctStr, left + 440f, y, bodyPaint)
            y += 8f
            canvas.drawLine(left, y, right, y, linePaint)
            y += 20f
        }

        y += 12f
        canvas.drawRect(left, y - 16f, right, y + 58f, headerBgPaint)
        canvas.drawText(
            "Total Marks: ${FormatUtils.formatMarks(result.obtainedTotal)} / ${result.maxTotal}",
            left + 12f,
            y + 6f,
            boldPaint
        )
        canvas.drawText(
            "Percentage: ${String.format(java.util.Locale.US, "%.1f", result.percentage)}%",
            left + 280f,
            y + 6f,
            boldPaint
        )
        canvas.drawText("Grade: ${result.grade}", left + 12f, y + 34f, tagPaint.apply { textAlign = Paint.Align.LEFT })
        canvas.drawText("Class Rank: #${result.rank}", left + 280f, y + 34f, boldPaint)

        val footerY = pageHeight - 56f
        canvas.drawLine(left, footerY, left + 150f, footerY, linePaint)
        canvas.drawText("Principal / Teacher", left, footerY + 16f, mutedPaint)
        canvas.drawLine(right - 150f, footerY, right, footerY, linePaint)
        val parentPaint = Paint(mutedPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Parent Signature", right, footerY + 16f, parentPaint)

        doc.finishPage(page)
        val safeName = result.student.name.replace(Regex("[^A-Za-z0-9]"), "_")
        val file = File(exportsDir(context), "Result_${safeName}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun generateTableReportPdf(
        context: Context,
        profile: AcademyProfile,
        reportTitle: String,
        subtitle: String,
        headers: List<String>,
        rows: List<List<String>>,
        summaryLines: List<String>,
        fileNamePrefix: String
    ): File {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val left = 36f
        val right = pageWidth - 36f
        val usableWidth = right - left

        val bluePaint = Paint().apply { color = Color.parseColor("#1A73E8") }
        val headerBgPaint = Paint().apply { color = Color.parseColor("#F1F3F4") }
        val titlePaint = Paint().apply {
            color = Color.parseColor("#202124")
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#5F6368")
            textSize = 11f
            isAntiAlias = true
        }
        val cellPaint = Paint().apply {
            color = Color.parseColor("#202124")
            textSize = 10f
            isAntiAlias = true
        }
        val headerTextPaint = Paint(cellPaint).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#DADCE0")
            strokeWidth = 0.8f
        }

        val colCount = headers.size.coerceAtLeast(1)
        val colWidth = usableWidth / colCount

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas: Canvas = page.canvas

        fun drawPageHeader(): Float {
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 8f, bluePaint)
            canvas.drawText(profile.academyName, left, 34f, titlePaint)
            canvas.drawText("$reportTitle · $subtitle", left, 52f, subtitlePaint)
            var yPos = 70f
            if (pageNum == 1 && summaryLines.isNotEmpty()) {
                for (s in summaryLines) {
                    canvas.drawText(s, left, yPos, headerTextPaint)
                    yPos += 16f
                }
                yPos += 6f
            }
            canvas.drawRect(left, yPos - 13f, right, yPos + 7f, headerBgPaint)
            headers.forEachIndexed { i, h ->
                canvas.drawText(truncateText(h, 20), left + i * colWidth + 4f, yPos, headerTextPaint)
            }
            return yPos + 20f
        }

        var y = drawPageHeader()

        for (row in rows) {
            if (y > pageHeight - 48f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = drawPageHeader()
            }
            row.forEachIndexed { i, cell ->
                if (i < colCount) {
                    canvas.drawText(truncateText(cell, 22), left + i * colWidth + 4f, y, cellPaint)
                }
            }
            y += 6f
            canvas.drawLine(left, y, right, y, linePaint)
            y += 16f
        }

        doc.finishPage(page)
        val file = File(exportsDir(context), "${fileNamePrefix}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun generateCsvFile(
        context: Context,
        headers: List<String>,
        rows: List<List<String>>,
        fileNamePrefix: String
    ): File {
        val file = File(exportsDir(context), "${fileNamePrefix}_${System.currentTimeMillis()}.csv")
        val sb = StringBuilder()
        sb.appendLine(headers.joinToString(",") { escapeCsv(it) })
        for (row in rows) {
            sb.appendLine(row.joinToString(",") { escapeCsv(it) })
        }
        file.writeText(sb.toString())
        return file
    }

    private fun escapeCsv(value: String): String {
        val clean = value.replace("\"", "\"\"")
        return if (clean.contains(",") || clean.contains("\"") || clean.contains("\n")) {
            "\"$clean\""
        } else {
            clean
        }
    }

    private fun truncateText(text: String, maxChars: Int): String {
        return if (text.length <= maxChars) text else text.take(maxChars - 1) + "…"
    }
}
