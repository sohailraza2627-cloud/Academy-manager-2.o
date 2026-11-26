package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.local.AcademyDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DatabaseBackupHelper {

    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun exportDatabaseToUri(context: Context, destinationUri: Uri): Result<Unit> {
        return try {
            val db = AcademyDatabase.getInstance(context)
            db.checkpointWal()
            val dbFile = context.getDatabasePath(AcademyDatabase.DB_NAME)
            if (!dbFile.exists()) {
                return Result.failure(IllegalStateException("Database file not found"))
            }
            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                FileInputStream(dbFile).use { input ->
                    input.copyTo(out)
                }
                out.flush()
            } ?: return Result.failure(IllegalStateException("Cannot open destination"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun restoreDatabaseFromUri(context: Context, sourceUri: Uri): Result<Unit> {
        return try {
            val tempFile = File(context.cacheDir, "restore_verify.db")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { out ->
                    input.copyTo(out)
                }
            } ?: return Result.failure(IllegalStateException("Cannot open backup file"))

            if (tempFile.length() < 100) {
                tempFile.delete()
                return Result.failure(IllegalArgumentException("Invalid SQLite file"))
            }

            val header = ByteArray(16)
            FileInputStream(tempFile).use { it.read(header) }
            if (!header.contentEquals(SQLITE_MAGIC)) {
                tempFile.delete()
                return Result.failure(IllegalArgumentException("Invalid SQLite header"))
            }

            AcademyDatabase.closeAndResetInstance()

            val dbFile = context.getDatabasePath(AcademyDatabase.DB_NAME)
            val walFile = File("${dbFile.absolutePath}-wal")
            val shmFile = File("${dbFile.absolutePath}-shm")
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            dbFile.parentFile?.mkdirs()
            tempFile.copyTo(dbFile, overwrite = true)
            tempFile.delete()

            // Re-open to verify integrity
            AcademyDatabase.getInstance(context)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
