package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AcademyProfile::class,
        AcademyClass::class,
        Subject::class,
        TimetableEntry::class,
        Student::class,
        Teacher::class,
        SalaryPayment::class,
        AttendanceRecord::class,
        FeeCharge::class,
        FeePayment::class,
        LedgerEntry::class,
        Exam::class,
        ExamMark::class,
        AcademyMember::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AcademyDatabase : RoomDatabase() {
    abstract fun academyProfileDao(): AcademyProfileDao
    abstract fun academyClassDao(): AcademyClassDao
    abstract fun subjectDao(): SubjectDao
    abstract fun timetableDao(): TimetableDao
    abstract fun studentDao(): StudentDao
    abstract fun teacherDao(): TeacherDao
    abstract fun salaryPaymentDao(): SalaryPaymentDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun feeChargeDao(): FeeChargeDao
    abstract fun feePaymentDao(): FeePaymentDao
    abstract fun ledgerEntryDao(): LedgerEntryDao
    abstract fun examDao(): ExamDao
    abstract fun examMarkDao(): ExamMarkDao
    abstract fun academyMemberDao(): AcademyMemberDao

    fun checkpointWal() {
        openHelper.writableDatabase.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)")).use {
            while (it.moveToNext()) { /* consume */ }
        }
    }

    companion object {
        const val DB_NAME = "academy_manager.db"

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE students ADD COLUMN guardianWhatsapp TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE students ADD COLUMN whatsappHistory TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: AcademyDatabase? = null

        fun getInstance(context: Context): AcademyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun closeAndResetInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        private fun buildDatabase(appContext: Context): AcademyDatabase {
            return Room.databaseBuilder(
                appContext,
                AcademyDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
