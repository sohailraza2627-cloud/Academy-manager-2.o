package com.example.ui.navigation

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.AcademyManagerApp
import com.example.R
import com.example.data.ai.ParentMessageRequest
import com.example.data.local.Student
import com.example.data.local.StudentStatus
import com.example.ui.components.LoadingStateView
import com.example.ui.components.ParentWhatsAppSheet
import com.example.ui.components.SyncStatusBadge
import com.example.util.FormatUtils
import com.example.util.WhatsAppMessageType
import com.example.ui.screens.assistant.AssistantScreen
import com.example.ui.screens.attendance.AttendanceScreen
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.classes.ClassDetailScreen
import com.example.ui.screens.classes.ClassesScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.exams.ExamDetailScreen
import com.example.ui.screens.exams.ExamsScreen
import com.example.ui.screens.fees.FeesScreen
import com.example.ui.screens.ledger.LedgerScreen
import com.example.ui.screens.more.MoreScreen
import com.example.ui.screens.pin.PinScreen
import com.example.ui.screens.reports.ReportsScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.students.StudentDetailScreen
import com.example.ui.screens.students.StudentsScreen
import com.example.ui.screens.teachers.TeachersScreen
import com.example.ui.viewmodel.*
import kotlinx.coroutines.launch

object AcademyRoutes {
    const val HOME = "home"
    const val STUDENTS = "students"
    const val ATTENDANCE = "attendance"
    const val FEES = "fees?tab={tab}"
    const val MORE = "more"

    const val STUDENT_DETAIL = "student_detail/{studentId}"
    const val TEACHERS = "teachers"
    const val CLASSES = "classes"
    const val CLASS_DETAIL = "class_detail/{classId}"
    const val LEDGER = "ledger"
    const val EXAMS = "exams"
    const val EXAM_DETAIL = "exam_detail/{examId}"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
    const val ASSISTANT = "assistant"

    fun fees(tab: Int = 0) = "fees?tab=$tab"
    fun studentDetail(id: String) = "student_detail/$id"
    fun classDetail(id: String) = "class_detail/$id"
    fun examDetail(id: String) = "exam_detail/$id"
}

private data class BottomNavItem(
    val route: String,
    val matchPrefix: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun AcademyRoot(
    appLockViewModel: AppLockViewModel,
    academicsViewModel: AcademicsViewModel,
    financeViewModel: FinanceViewModel
) {
    val gateState by appLockViewModel.gateState.collectAsStateWithLifecycle()
    val authSession by appLockViewModel.authSession.collectAsStateWithLifecycle()
    val pendingGoogleUser by appLockViewModel.pendingGoogleUser.collectAsStateWithLifecycle()
    val profile by appLockViewModel.academyProfile.collectAsStateWithLifecycle()
    val isDarkMode by appLockViewModel.isDarkMode.collectAsStateWithLifecycle()

    when (gateState) {
        PinGateState.Loading -> {
            Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingStateView()
                }
            }
        }
        PinGateState.NeedsAuth -> {
            AuthScreen(
                isFirebaseConfigured = appLockViewModel.isFirebaseConfigured,
                pendingGoogleUser = pendingGoogleUser,
                onGoogleIdToken = appLockViewModel::signInWithGoogleIdToken,
                onCreateAcademyWithGoogle = appLockViewModel::completeAdminAcademyCreation,
                onCancelGoogleSetup = appLockViewModel::cancelPendingGoogleSetup,
                onContinueOfflineLocal = appLockViewModel::continueOfflineLocalMode
            )
        }
        PinGateState.NeedsPinSetup -> {
            PinScreen(
                isSetupMode = true,
                profile = profile,
                onCreatePin = { pin -> appLockViewModel.createInitialPin(pin) },
                onVerifyPin = { false }
            )
        }
        PinGateState.Locked -> {
            PinScreen(
                isSetupMode = false,
                profile = profile,
                onCreatePin = {},
                onVerifyPin = { pin -> appLockViewModel.verifyAndUnlock(pin) }
            )
        }
        PinGateState.Unlocked -> {
            key(authSession.uid) {
                UnlockedAcademyShell(
                    appLockViewModel = appLockViewModel,
                    academicsViewModel = academicsViewModel,
                    financeViewModel = financeViewModel,
                    isDarkMode = isDarkMode
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnlockedAcademyShell(
    appLockViewModel: AppLockViewModel,
    academicsViewModel: AcademicsViewModel,
    financeViewModel: FinanceViewModel,
    isDarkMode: Boolean
) {
    val context = LocalContext.current
    val app = context.applicationContext as AcademyManagerApp
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun showMessage(msg: String) {
        scope.launch {
            snackbarHostState.showSnackbar(msg)
        }
    }

    val profile by appLockViewModel.academyProfile.collectAsStateWithLifecycle()
    val authSession by appLockViewModel.authSession.collectAsStateWithLifecycle()
    val syncStatus by appLockViewModel.syncStatus.collectAsStateWithLifecycle()
    val devicePrefix by appLockViewModel.devicePrefix.collectAsStateWithLifecycle()
    val hasPinSet by appLockViewModel.hasPinSet.collectAsStateWithLifecycle()
    val aiAssistantEnabled by appLockViewModel.aiAssistantEnabled.collectAsStateWithLifecycle()
    val userGeminiApiKey by appLockViewModel.userGeminiApiKey.collectAsStateWithLifecycle()
    val members by appLockViewModel.members.collectAsStateWithLifecycle()
    val isAdmin = authSession.isAdmin

    var activeWhatsAppTarget by remember { mutableStateOf<Pair<Student, ParentMessageRequest>?>(null) }

    val classes by academicsViewModel.classes.collectAsStateWithLifecycle()
    val subjects by academicsViewModel.subjects.collectAsStateWithLifecycle()
    val studentsState by academicsViewModel.studentsState.collectAsStateWithLifecycle()
    val students by academicsViewModel.students.collectAsStateWithLifecycle()
    val teachersState by academicsViewModel.teachersState.collectAsStateWithLifecycle()
    val salaryPayments by academicsViewModel.salaryPayments.collectAsStateWithLifecycle()
    val attendanceRecords by academicsViewModel.attendanceRecords.collectAsStateWithLifecycle()
    val examsState by academicsViewModel.examsState.collectAsStateWithLifecycle()
    val exams by academicsViewModel.exams.collectAsStateWithLifecycle()
    val examMarks by academicsViewModel.examMarks.collectAsStateWithLifecycle()

    val selectedAttClassId by academicsViewModel.selectedAttendanceClassId.collectAsStateWithLifecycle()
    val selectedAttDateKey by academicsViewModel.selectedAttendanceDateKey.collectAsStateWithLifecycle()
    val attDraft by academicsViewModel.attendanceDraft.collectAsStateWithLifecycle()
    val attLoading by academicsViewModel.attendanceLoading.collectAsStateWithLifecycle()

    val feeChargesState by financeViewModel.feeChargesState.collectAsStateWithLifecycle()
    val feeCharges by financeViewModel.feeCharges.collectAsStateWithLifecycle()
    val feePayments by financeViewModel.feePayments.collectAsStateWithLifecycle()
    val ledgerState by financeViewModel.ledgerEntriesState.collectAsStateWithLifecycle()
    val ledgerEntries by financeViewModel.ledgerEntries.collectAsStateWithLifecycle()

    val navItems = listOf(
        BottomNavItem(AcademyRoutes.HOME, AcademyRoutes.HOME, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
        BottomNavItem(AcademyRoutes.STUDENTS, AcademyRoutes.STUDENTS, R.string.nav_students, Icons.Filled.People, Icons.Outlined.People, "nav_students"),
        BottomNavItem(AcademyRoutes.ATTENDANCE, AcademyRoutes.ATTENDANCE, R.string.nav_attendance, Icons.Filled.FactCheck, Icons.Outlined.FactCheck, "nav_attendance"),
        BottomNavItem(AcademyRoutes.fees(0), "fees", R.string.nav_fees, Icons.Filled.Payments, Icons.Outlined.Payments, "nav_fees"),
        BottomNavItem(AcademyRoutes.MORE, AcademyRoutes.MORE, R.string.nav_more, Icons.Filled.Menu, Icons.Outlined.Menu, "nav_more")
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AcademyRoutes.HOME
    val showBottomBar = navItems.any { currentRoute.startsWith(it.matchPrefix) }

    fun navigateTopTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = if (showBottomBar) {
                WindowInsets.safeDrawing.exclude(WindowInsets.ime)
            } else {
                WindowInsets(0, 0, 0, 0)
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (showBottomBar) {
                    val topTitle = when {
                        currentRoute.startsWith(AcademyRoutes.HOME) -> stringResource(R.string.dashboard_title)
                        currentRoute.startsWith(AcademyRoutes.STUDENTS) -> stringResource(R.string.students_title)
                        currentRoute.startsWith(AcademyRoutes.ATTENDANCE) -> stringResource(R.string.attendance_title)
                        currentRoute.startsWith("fees") -> stringResource(R.string.fees_title)
                        else -> stringResource(R.string.more_title)
                    }
                    TopAppBar(
                        title = { Text(topTitle) },
                        actions = {
                            IconButton(
                                onClick = { navController.navigate(AcademyRoutes.ASSISTANT) },
                                modifier = Modifier.testTag("topbar_assistant_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = stringResource(R.string.assistant_title),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            SyncStatusBadge(
                                status = syncStatus,
                                onClick = { appLockViewModel.triggerManualSync() },
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            },
            bottomBar = {
                if (showBottomBar && !useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        navItems.forEach { item ->
                            val selected = currentRoute.startsWith(item.matchPrefix)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes), maxLines = 1) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                if (showBottomBar && useNavRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        navItems.forEach { item ->
                            val selected = currentRoute.startsWith(item.matchPrefix)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes), maxLines = 1) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = AcademyRoutes.HOME,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                    ) {
                        composable(AcademyRoutes.HOME) {
                            DashboardScreen(
                                profile = profile,
                                isAdmin = isAdmin,
                                students = students,
                                attendanceRecords = attendanceRecords,
                                feeCharges = feeCharges,
                                feePayments = feePayments,
                                ledgerEntries = ledgerEntries,
                                onNavigateToStudents = { navigateTopTab(AcademyRoutes.STUDENTS) },
                                onNavigateToAttendance = { navigateTopTab(AcademyRoutes.ATTENDANCE) },
                                onNavigateToFees = { navigateTopTab(AcademyRoutes.fees(0)) },
                                onNavigateToPaperFee = { navigateTopTab(AcademyRoutes.fees(1)) },
                                onNavigateToLedger = { navController.navigate(AcademyRoutes.LEDGER) },
                                onNavigateToReports = { navController.navigate(AcademyRoutes.REPORTS) },
                                onOpenAssistant = { navController.navigate(AcademyRoutes.ASSISTANT) }
                            )
                        }

                        composable(AcademyRoutes.STUDENTS) {
                            StudentsScreen(
                                studentsState = studentsState,
                                classes = classes,
                                isAdmin = isAdmin,
                                onStudentClick = { id -> navController.navigate(AcademyRoutes.studentDetail(id)) },
                                onSaveStudent = { st ->
                                    academicsViewModel.saveStudent(st) {
                                        showMessage(context.getString(R.string.student_saved))
                                    }
                                },
                                onSetStudentStatus = { st, status ->
                                    academicsViewModel.setStudentStatus(st, status) {
                                        val msgRes = if (status == StudentStatus.LEFT) R.string.student_marked_left else R.string.student_reactivated
                                        showMessage(context.getString(msgRes))
                                    }
                                },
                                onDeleteStudent = { st ->
                                    academicsViewModel.deleteStudent(st) {
                                        showMessage(context.getString(R.string.student_deleted))
                                    }
                                },
                                onNavigateToClasses = { navController.navigate(AcademyRoutes.CLASSES) }
                            )
                        }

                        composable(
                            route = AcademyRoutes.STUDENT_DETAIL,
                            arguments = listOf(navArgument("studentId") { type = NavType.StringType })
                        ) { entry ->
                            val studentId = entry.arguments?.getString("studentId") ?: ""
                            val student by academicsViewModel.observeStudent(studentId).collectAsStateWithLifecycle(initialValue = null)
                            val stAttendance by academicsViewModel.observeStudentAttendance(studentId).collectAsStateWithLifecycle(initialValue = emptyList())
                            val stCharges by financeViewModel.observeStudentFeeCharges(studentId).collectAsStateWithLifecycle(initialValue = emptyList())
                            val stPayments by financeViewModel.observeStudentFeePayments(studentId).collectAsStateWithLifecycle(initialValue = emptyList())

                            StudentDetailScreen(
                                student = student,
                                classes = classes,
                                subjects = subjects,
                                allStudents = students,
                                exams = exams,
                                allExamMarks = examMarks,
                                attendanceRecords = stAttendance,
                                feeCharges = stCharges,
                                feePayments = stPayments,
                                profile = profile,
                                isAdmin = isAdmin,
                                aiEnabled = aiAssistantEnabled,
                                onSummarizeStudent = academicsViewModel::summarizeStudent,
                                onSendWhatsAppRequest = { st, req -> activeWhatsAppTarget = st to req },
                                onBack = { navController.popBackStack() },
                                onSaveStudent = { st ->
                                    academicsViewModel.saveStudent(st) {
                                        showMessage(context.getString(R.string.student_saved))
                                    }
                                },
                                onSetStatus = { st, status ->
                                    academicsViewModel.setStudentStatus(st, status) {
                                        val msgRes = if (status == StudentStatus.LEFT) R.string.student_marked_left else R.string.student_reactivated
                                        showMessage(context.getString(msgRes))
                                    }
                                },
                                onDeleteStudent = { st ->
                                    academicsViewModel.deleteStudent(st) {
                                        showMessage(context.getString(R.string.student_deleted))
                                    }
                                },
                                computeExamResults = academicsViewModel::computeExamResults
                            )
                        }

                        composable(AcademyRoutes.ATTENDANCE) {
                            AttendanceScreen(
                                classes = classes,
                                students = students,
                                allAttendanceRecords = attendanceRecords,
                                selectedClassId = selectedAttClassId,
                                selectedDateKey = selectedAttDateKey,
                                draftList = attDraft,
                                isLoading = attLoading,
                                onSelectClass = academicsViewModel::selectAttendanceClass,
                                onSelectDate = academicsViewModel::selectAttendanceDate,
                                onSetStatus = academicsViewModel::setStudentAttendanceStatus,
                                onToggleStatus = academicsViewModel::toggleStudentAttendanceStatus,
                                onMarkAllPresent = academicsViewModel::markAllPresent,
                                onSaveAttendance = {
                                    academicsViewModel.saveAttendance { formattedDate ->
                                        showMessage(context.getString(R.string.attendance_saved, formattedDate))
                                    }
                                },
                                onSendAttendanceWhatsApp = { student, className, dateLabel, status ->
                                    activeWhatsAppTarget = student to ParentMessageRequest(
                                        type = WhatsAppMessageType.ATTENDANCE_ALERT,
                                        academyName = profile.academyName,
                                        studentName = student.name,
                                        className = className,
                                        dateLabel = dateLabel,
                                        attendanceStatus = status
                                    )
                                }
                            )
                        }

                        composable(
                            route = AcademyRoutes.FEES,
                            arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })
                        ) { entry ->
                            val initialTab = entry.arguments?.getInt("tab") ?: 0
                            val classMap = remember(classes) { classes.associateBy { it.id } }
                            FeesScreen(
                                initialTab = initialTab,
                                isAdmin = isAdmin,
                                feeChargesState = feeChargesState,
                                students = students,
                                classes = classes,
                                exams = exams,
                                profile = profile,
                                onGenerateMonthlyTuition = { mKey, cId, _ ->
                                    financeViewModel.generateMonthlyTuitionFees(mKey, cId) { created, skipped ->
                                        showMessage(context.getString(R.string.fees_generated_result, created, skipped))
                                    }
                                },
                                onCreatePaperFeeBatch = { cId, title, amt, due, _ ->
                                    financeViewModel.createPaperFeeCharges(cId, title, amt, due) { created, skipped ->
                                        showMessage(context.getString(R.string.paper_fees_created_result, created, skipped))
                                    }
                                },
                                onCreateOtherFee = { sId, subType, title, amt, disc, due ->
                                    financeViewModel.createOtherFeeCharge(sId, subType, title, amt, disc, due) {
                                        showMessage(context.getString(R.string.save))
                                    }
                                },
                                onCollectTuitionFifo = financeViewModel::collectTuitionPaymentFifo,
                                onCollectSingleCharge = financeViewModel::collectSingleChargePayment,
                                onDeleteCharge = financeViewModel::deleteFeeCharge,
                                onShowMessage = ::showMessage,
                                onSendFeeWhatsApp = { student, tDue, pDue, oDue ->
                                    activeWhatsAppTarget = student to ParentMessageRequest(
                                        type = WhatsAppMessageType.FEE_REMINDER,
                                        academyName = profile.academyName,
                                        studentName = student.name,
                                        className = classMap[student.classId]?.name ?: "",
                                        tuitionPending = tDue,
                                        paperFeePending = pDue,
                                        otherFeePending = oDue
                                    )
                                }
                            )
                        }

                        composable(AcademyRoutes.MORE) {
                            MoreScreen(
                                onOpenTeachers = { navController.navigate(AcademyRoutes.TEACHERS) },
                                onOpenClasses = { navController.navigate(AcademyRoutes.CLASSES) },
                                onOpenExams = { navController.navigate(AcademyRoutes.EXAMS) },
                                onOpenLedger = { navController.navigate(AcademyRoutes.LEDGER) },
                                onOpenReports = { navController.navigate(AcademyRoutes.REPORTS) },
                                onOpenSettings = { navController.navigate(AcademyRoutes.SETTINGS) },
                                onOpenAssistant = { navController.navigate(AcademyRoutes.ASSISTANT) }
                            )
                        }

                        composable(AcademyRoutes.TEACHERS) {
                            TeachersScreen(
                                teachersState = teachersState,
                                salaryPayments = salaryPayments,
                                isAdmin = isAdmin,
                                onBack = { navController.popBackStack() },
                                onSaveTeacher = { academicsViewModel.saveTeacher(it) },
                                onDeleteTeacher = { academicsViewModel.deleteTeacher(it) },
                                onRecordSalary = { teacher, mKey, amt, paidDate, notes, alsoExp ->
                                    academicsViewModel.recordSalaryPayment(
                                        teacher, mKey, amt, paidDate, notes, alsoExp
                                    ) {
                                        showMessage(context.getString(R.string.salary_paid_msg))
                                    }
                                },
                                onDeleteSalary = { academicsViewModel.deleteSalaryPayment(it) }
                            )
                        }

                        composable(AcademyRoutes.CLASSES) {
                            ClassesScreen(
                                classes = classes,
                                students = students,
                                subjects = subjects,
                                isAdmin = isAdmin,
                                onBack = { navController.popBackStack() },
                                onClassClick = { id -> navController.navigate(AcademyRoutes.classDetail(id)) },
                                onSaveClass = { academicsViewModel.saveClass(it) },
                                onDeleteClass = { cls ->
                                    academicsViewModel.deleteClass(cls) { result ->
                                        result.onFailure {
                                            val count = students.count { s -> s.classId == cls.id }
                                            showMessage(context.getString(R.string.delete_class_has_students, count))
                                        }
                                    }
                                }
                            )
                        }

                        composable(
                            route = AcademyRoutes.CLASS_DETAIL,
                            arguments = listOf(navArgument("classId") { type = NavType.StringType })
                        ) { entry ->
                            val classId = entry.arguments?.getString("classId") ?: ""
                            val cls = classes.find { it.id == classId }
                            val clsSubjects by academicsViewModel.observeSubjectsByClass(classId)
                                .collectAsStateWithLifecycle(initialValue = emptyList())
                            val clsTimetable by academicsViewModel.observeTimetableByClass(classId)
                                .collectAsStateWithLifecycle(initialValue = emptyList())

                            val clsStudents = remember(students, classId) {
                                students.filter { it.classId == classId }
                            }
                            val clsAttendance = remember(attendanceRecords, classId) {
                                attendanceRecords.filter { it.classId == classId }
                            }
                            val clsCharges = remember(feeCharges, classId) {
                                feeCharges.filter { it.classId == classId }
                            }
                            val clsExams = remember(exams, classId) {
                                exams.filter { it.classId == classId }
                            }

                            ClassDetailScreen(
                                academyClass = cls,
                                subjects = clsSubjects,
                                timetable = clsTimetable,
                                classStudents = clsStudents,
                                attendanceRecords = clsAttendance,
                                feeCharges = clsCharges,
                                exams = clsExams,
                                examMarks = examMarks,
                                profile = profile,
                                aiEnabled = aiAssistantEnabled,
                                computeExamResults = academicsViewModel::computeExamResults,
                                onSummarizeClass = academicsViewModel::summarizeClass,
                                isAdmin = isAdmin,
                                onBack = { navController.popBackStack() },
                                onSaveSubject = { academicsViewModel.saveSubject(it) },
                                onDeleteSubject = { academicsViewModel.deleteSubject(it) },
                                onSaveTimetable = { academicsViewModel.saveTimetableEntry(it) },
                                onDeleteTimetable = { academicsViewModel.deleteTimetableEntry(it) }
                            )
                        }

                        composable(AcademyRoutes.LEDGER) {
                            LedgerScreen(
                                ledgerState = ledgerState,
                                feePayments = feePayments,
                                isAdmin = isAdmin,
                                onBack = { navController.popBackStack() },
                                onSaveEntry = { financeViewModel.saveLedgerEntry(it) },
                                onDeleteEntry = { financeViewModel.deleteLedgerEntry(it) }
                            )
                        }

                        composable(AcademyRoutes.EXAMS) {
                            ExamsScreen(
                                examsState = examsState,
                                classes = classes,
                                isAdmin = isAdmin,
                                onBack = { navController.popBackStack() },
                                onExamClick = { id -> navController.navigate(AcademyRoutes.examDetail(id)) },
                                onSaveExam = { academicsViewModel.saveExam(it) },
                                onDeleteExam = { academicsViewModel.deleteExam(it) }
                            )
                        }

                        composable(
                            route = AcademyRoutes.EXAM_DETAIL,
                            arguments = listOf(navArgument("examId") { type = NavType.StringType })
                        ) { entry ->
                            val examId = entry.arguments?.getString("examId") ?: ""
                            val exam = exams.find { it.id == examId }
                            val cls = classes.find { it.id == exam?.classId }
                            val clsStudents = remember(students, exam) {
                                students.filter { it.classId == exam?.classId && it.status == StudentStatus.ACTIVE }
                                    .sortedBy { it.rollNo }
                            }
                            val clsSubjects = remember(subjects, exam) {
                                subjects.filter { it.classId == exam?.classId }
                            }
                            val marks by app.repository.observeExamMarks(examId)
                                .collectAsStateWithLifecycle(initialValue = emptyList())

                            ExamDetailScreen(
                                exam = exam,
                                academyClass = cls,
                                classStudents = clsStudents,
                                classSubjects = clsSubjects,
                                examMarks = marks,
                                profile = profile,
                                isAdmin = isAdmin,
                                computeExamResults = academicsViewModel::computeExamResults,
                                onBack = { navController.popBackStack() },
                                onSaveMarks = { eId, subject, map ->
                                    academicsViewModel.saveExamMarksForSubject(eId, subject, map) {
                                        showMessage(context.getString(R.string.marks_saved))
                                    }
                                },
                                onSendExamWhatsApp = { student, ex, className, result ->
                                    activeWhatsAppTarget = student to ParentMessageRequest(
                                        type = WhatsAppMessageType.EXAM_RESULT,
                                        academyName = profile.academyName,
                                        studentName = student.name,
                                        className = className,
                                        subjectOrExamTitle = ex.name,
                                        dateLabel = FormatUtils.formatDate(ex.examDate),
                                        obtainedMarks = FormatUtils.formatMarks(result.obtainedTotal),
                                        totalMarks = result.maxTotal.toString(),
                                        percentageLabel = FormatUtils.formatPercent(result.percentage),
                                        grade = result.grade
                                    )
                                }
                            )
                        }

                        composable(AcademyRoutes.REPORTS) {
                            ReportsScreen(
                                profile = profile,
                                classes = classes,
                                subjects = subjects,
                                students = students,
                                attendanceRecords = attendanceRecords,
                                feeCharges = feeCharges,
                                feePayments = feePayments,
                                ledgerEntries = ledgerEntries,
                                exams = exams,
                                examMarks = examMarks,
                                computeExamResults = academicsViewModel::computeExamResults,
                                onBack = { navController.popBackStack() },
                                onShowError = ::showMessage
                            )
                        }

                        composable(AcademyRoutes.SETTINGS) {
                            SettingsScreen(
                                profile = profile,
                                authSession = authSession,
                                syncStatus = syncStatus,
                                devicePrefix = devicePrefix,
                                hasPinSet = hasPinSet,
                                members = members,
                                isFirebaseConfigured = appLockViewModel.isFirebaseConfigured,
                                isDarkMode = isDarkMode,
                                onToggleDarkMode = appLockViewModel::setDarkMode,
                                aiAssistantEnabled = aiAssistantEnabled,
                                onToggleAiAssistant = appLockViewModel::setAiAssistantEnabled,
                                userGeminiApiKey = userGeminiApiKey,
                                onSaveUserGeminiApiKey = appLockViewModel::saveUserGeminiApiKey,
                                onSaveProfile = { name, tag, phone, addr, logo ->
                                    appLockViewModel.saveProfile(name, tag, phone, addr, logo) {
                                        showMessage(context.getString(R.string.profile_saved))
                                    }
                                },
                                onChangePin = appLockViewModel::setOrChangePin,
                                onSetDevicePrefix = { newPrefix ->
                                    appLockViewModel.setDevicePrefix(newPrefix) {
                                        showMessage(context.getString(R.string.prefix_saved))
                                    }
                                },
                                onInviteTeacher = appLockViewModel::inviteTeacherByEmail,
                                onSignOut = appLockViewModel::signOut,
                                onDatabaseRestored = {
                                    (context as? Activity)?.recreate()
                                },
                                onShowMessage = ::showMessage,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(AcademyRoutes.ASSISTANT) {
                            AssistantScreen(
                                onOpenWhatsApp = { st, req -> activeWhatsAppTarget = st to req },
                                onReceiptIssued = { r ->
                                    showMessage("${r.receiptNumber}: ${FormatUtils.formatCurrency(r.amountPaid)} saved")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    activeWhatsAppTarget?.let { (student, request) ->
        ParentWhatsAppSheet(
            student = student,
            request = request,
            aiEnabled = aiAssistantEnabled,
            initialDraft = academicsViewModel.buildLocalParentMessage(request),
            onDraftWithAi = academicsViewModel::draftParentMessageWithAi,
            onDismiss = { activeWhatsAppTarget = null },
            onSendResult = { res ->
                res.onFailure { err ->
                    showMessage(err.localizedMessage ?: context.getString(R.string.error_generic))
                }
            }
        )
    }
}
