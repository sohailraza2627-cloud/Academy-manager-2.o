package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AcademyManagerApp
import com.example.data.local.*
import com.example.data.repository.StudentExamResult
import com.example.util.FormatUtils
import com.example.util.ImageStorage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface DataLoadState<out T> {
    data object Loading : DataLoadState<Nothing>
    data class Success<T>(val data: T) : DataLoadState<T>
    data class Error(val message: String) : DataLoadState<Nothing>
}

class AcademicsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AcademyManagerApp
    private val repo get() = app.repository

    val classes: StateFlow<List<AcademyClass>> = repo.classesFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val subjects: StateFlow<List<Subject>> = repo.subjectsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val studentsState: StateFlow<DataLoadState<List<Student>>> = repo.studentsFlow
        .map<List<Student>, DataLoadState<List<Student>>> { DataLoadState.Success(it) }
        .catch { emit(DataLoadState.Error(it.message ?: "Failed to load students")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DataLoadState.Loading)

    val students: StateFlow<List<Student>> = repo.studentsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val teachersState: StateFlow<DataLoadState<List<Teacher>>> = repo.teachersFlow
        .map<List<Teacher>, DataLoadState<List<Teacher>>> { DataLoadState.Success(it) }
        .catch { emit(DataLoadState.Error(it.message ?: "Failed to load teachers")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DataLoadState.Loading)

    val teachers: StateFlow<List<Teacher>> = repo.teachersFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val salaryPayments: StateFlow<List<SalaryPayment>> = repo.salaryPaymentsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val attendanceRecords: StateFlow<List<AttendanceRecord>> = repo.attendanceFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val examsState: StateFlow<DataLoadState<List<Exam>>> = repo.examsFlow
        .map<List<Exam>, DataLoadState<List<Exam>>> { DataLoadState.Success(it) }
        .catch { emit(DataLoadState.Error(it.message ?: "Failed to load exams")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DataLoadState.Loading)

    val exams: StateFlow<List<Exam>> = repo.examsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val examMarks: StateFlow<List<ExamMark>> = repo.examMarksFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    // Attendance Editor State
    private val _selectedAttendanceClassId = MutableStateFlow<Long?>(null)
    val selectedAttendanceClassId: StateFlow<Long?> = _selectedAttendanceClassId.asStateFlow()

    private val _selectedAttendanceDateKey = MutableStateFlow(FormatUtils.currentDateKey())
    val selectedAttendanceDateKey: StateFlow<String> = _selectedAttendanceDateKey.asStateFlow()

    private val _attendanceDraft = MutableStateFlow<List<Pair<Student, String>>>(emptyList())
    val attendanceDraft: StateFlow<List<Pair<Student, String>>> = _attendanceDraft.asStateFlow()

    private val _attendanceLoading = MutableStateFlow(false)
    val attendanceLoading: StateFlow<Boolean> = _attendanceLoading.asStateFlow()

    init {
        viewModelScope.launch {
            classes.collect { list ->
                if (_selectedAttendanceClassId.value == null && list.isNotEmpty()) {
                    selectAttendanceClass(list.first().id)
                }
            }
        }
    }

    fun selectAttendanceClass(classId: Long) {
        _selectedAttendanceClassId.value = classId
        loadAttendanceDraft()
    }

    fun selectAttendanceDate(dateKey: String) {
        _selectedAttendanceDateKey.value = dateKey
        loadAttendanceDraft()
    }

    fun loadAttendanceDraft() {
        val classId = _selectedAttendanceClassId.value ?: return
        val dateKey = _selectedAttendanceDateKey.value
        viewModelScope.launch {
            _attendanceLoading.value = true
            _attendanceDraft.value = repo.getAttendanceDraftForClassAndDate(classId, dateKey)
            _attendanceLoading.value = false
        }
    }

    fun setStudentAttendanceStatus(studentId: Long, status: String) {
        _attendanceDraft.value = _attendanceDraft.value.map { (student, current) ->
            if (student.id == studentId) student to status else student to current
        }
    }

    fun toggleStudentAttendanceStatus(studentId: Long) {
        _attendanceDraft.value = _attendanceDraft.value.map { (student, current) ->
            if (student.id == studentId) {
                val next = when (current) {
                    AttendanceStatus.PRESENT -> AttendanceStatus.ABSENT
                    AttendanceStatus.ABSENT -> AttendanceStatus.LEAVE
                    else -> AttendanceStatus.PRESENT
                }
                student to next
            } else {
                student to current
            }
        }
    }

    fun markAllPresent() {
        _attendanceDraft.value = _attendanceDraft.value.map { (student, _) ->
            student to AttendanceStatus.PRESENT
        }
    }

    fun saveAttendance(onComplete: (String) -> Unit) {
        val classId = _selectedAttendanceClassId.value ?: return
        val dateKey = _selectedAttendanceDateKey.value
        val map = _attendanceDraft.value.associate { it.first.id to it.second }
        viewModelScope.launch {
            repo.saveClassAttendance(classId, dateKey, map)
            onComplete(FormatUtils.formatDateKey(dateKey))
        }
    }

    // Student operations
    fun observeStudent(studentId: Long): Flow<Student?> = repo.observeStudent(studentId)
    fun observeStudentAttendance(studentId: Long): Flow<List<AttendanceRecord>> = repo.observeStudentAttendance(studentId)
    fun observeStudentExamMarks(studentId: Long): Flow<List<ExamMark>> = repo.observeStudentExamMarks(studentId)

    fun saveStudent(student: Student, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.saveStudent(student)
            onDone()
        }
    }

    fun setStudentStatus(student: Student, status: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.setStudentStatus(student, status)
            loadAttendanceDraft()
            onDone()
        }
    }

    fun deleteStudent(student: Student, onDone: () -> Unit) {
        viewModelScope.launch {
            ImageStorage.deleteFileIfExists(student.photoPath)
            repo.deleteStudent(student)
            loadAttendanceDraft()
            onDone()
        }
    }

    // Class, Subject, Timetable operations
    fun observeSubjectsByClass(classId: Long): Flow<List<Subject>> = repo.observeSubjectsByClass(classId)
    fun observeTimetableByClass(classId: Long): Flow<List<TimetableEntry>> = repo.observeTimetableByClass(classId)

    fun saveClass(academyClass: AcademyClass, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.saveClass(academyClass)
            onDone()
        }
    }

    fun deleteClass(academyClass: AcademyClass, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(repo.deleteClass(academyClass))
        }
    }

    fun saveSubject(subject: Subject, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.saveSubject(subject)
            onDone()
        }
    }

    fun deleteSubject(subject: Subject) {
        viewModelScope.launch { repo.deleteSubject(subject) }
    }

    fun saveTimetableEntry(entry: TimetableEntry, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.saveTimetableEntry(entry)
            onDone()
        }
    }

    fun deleteTimetableEntry(entry: TimetableEntry) {
        viewModelScope.launch { repo.deleteTimetableEntry(entry) }
    }

    // Teacher & Salary operations
    fun observeTeacherSalaries(teacherId: Long): Flow<List<SalaryPayment>> = repo.observeTeacherSalaries(teacherId)

    fun saveTeacher(teacher: Teacher, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.saveTeacher(teacher)
            onDone()
        }
    }

    fun deleteTeacher(teacher: Teacher, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.deleteTeacher(teacher)
            onDone()
        }
    }

    fun recordSalaryPayment(
        teacher: Teacher,
        monthKey: String,
        amount: Long,
        paidDate: Long,
        notes: String,
        alsoRecordExpense: Boolean,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            repo.recordSalaryPayment(teacher, monthKey, amount, paidDate, notes, alsoRecordExpense)
            onDone()
        }
    }

    fun deleteSalaryPayment(payment: SalaryPayment) {
        viewModelScope.launch { repo.deleteSalaryPayment(payment) }
    }

    // Exam operations
    fun saveExam(exam: Exam, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.saveExam(exam)
            onDone()
        }
    }

    fun deleteExam(exam: Exam, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.deleteExam(exam)
            onDone()
        }
    }

    fun saveExamMarksForSubject(
        examId: Long,
        subject: Subject,
        marksByStudent: Map<Long, Double>,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            repo.saveExamMarksForSubject(examId, subject, marksByStudent)
            onDone()
        }
    }

    fun computeExamResults(
        classStudents: List<Student>,
        classSubjects: List<Subject>,
        marks: List<ExamMark>
    ): List<StudentExamResult> = repo.computeExamResults(classStudents, classSubjects, marks)
}
