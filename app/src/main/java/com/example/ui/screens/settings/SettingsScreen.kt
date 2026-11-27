package com.example.ui.screens.settings

import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.local.AcademyMember
import com.example.data.local.AcademyProfile
import com.example.data.local.AuthSession
import com.example.data.local.UserRole
import com.example.data.sync.SyncStatus
import com.example.ui.components.BadgeTone
import com.example.ui.components.GoogleCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.components.SyncStatusBadge
import com.example.util.DatabaseBackupHelper
import com.example.util.FormatUtils
import com.example.util.ImageStorage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profile: AcademyProfile,
    authSession: AuthSession,
    syncStatus: SyncStatus,
    devicePrefix: String,
    hasPinSet: Boolean,
    members: List<AcademyMember>,
    isFirebaseConfigured: Boolean,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    aiAssistantEnabled: Boolean,
    onToggleAiAssistant: (Boolean) -> Unit,
    userGeminiApiKey: String,
    onSaveUserGeminiApiKey: (String) -> Unit,
    onSaveProfile: (name: String, tagline: String, phone: String, address: String, logoPath: String?) -> Unit,
    onChangePin: suspend (currentPin: String, newPin: String) -> Boolean,
    onSetDevicePrefix: (String) -> Unit,
    onInviteTeacher: suspend (name: String, email: String) -> Result<AcademyMember>,
    onSignOut: (CredentialManager) -> Unit,
    onDatabaseRestored: () -> Unit,
    onShowMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val isAdmin = authSession.isAdmin

    var academyName by rememberSaveable(profile.academyName) { mutableStateOf(profile.academyName) }
    var tagline by rememberSaveable(profile.tagline) { mutableStateOf(profile.tagline) }
    var phone by rememberSaveable(profile.phone) { mutableStateOf(profile.phone) }
    var address by rememberSaveable(profile.address) { mutableStateOf(profile.address) }
    var logoPath by rememberSaveable(profile.logoPath) { mutableStateOf(profile.logoPath) }
    var nameErr by rememberSaveable { mutableStateOf<String?>(null) }
    var prefixInput by rememberSaveable(devicePrefix) { mutableStateOf(devicePrefix) }
    var geminiKeyInput by rememberSaveable(userGeminiApiKey) { mutableStateOf(userGeminiApiKey) }
    var showGeminiKey by rememberSaveable { mutableStateOf(false) }

    var showChangePinDialog by rememberSaveable { mutableStateOf(false) }
    var showAddTeacherDialog by rememberSaveable { mutableStateOf(false) }
    var showSignOutConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingRestoreUriString by rememberSaveable { mutableStateOf<String?>(null) }

    val requiredMsg = stringResource(R.string.field_required)
    val backupOkMsg = stringResource(R.string.backup_success)
    val backupErrMsg = stringResource(R.string.backup_error)
    val restoreOkMsg = stringResource(R.string.restore_success)
    val restoreInvalidMsg = stringResource(R.string.restore_invalid_file)

    val logoBitmap = remember(logoPath) { ImageStorage.loadBitmap(logoPath) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val saved = ImageStorage.copyUriToInternalStorage(context, it, "academy_logo")
            if (saved != null) {
                logoPath = saved
            }
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { dest ->
            val res = DatabaseBackupHelper.exportDatabaseToUri(context, dest)
            onShowMessage(if (res.isSuccess) backupOkMsg else backupErrMsg)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { src ->
            pendingRestoreUriString = src.toString()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Account & Cloud Sync
            item { SectionHeader(title = stringResource(R.string.account_section)) }
            item {
                GoogleCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = authSession.name.ifBlank { profile.academyName },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (authSession.isOfflineLocalMode) {
                                    stringResource(R.string.auth_continue_offline)
                                } else {
                                    authSession.email
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SyncStatusBadge(status = syncStatus)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatusBadge(
                            text = stringResource(
                                if (isAdmin) R.string.role_admin else R.string.role_teacher
                            ),
                            tone = if (isAdmin) BadgeTone.GREEN else BadgeTone.BLUE
                        )
                        OutlinedButton(
                            onClick = { showSignOutConfirm = true },
                            modifier = Modifier.testTag("sign_out_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.sign_out))
                        }
                    }
                }
            }

            // Teacher Login Accounts (Admin only)
            if (isAdmin) {
                item { SectionHeader(title = stringResource(R.string.teacher_accounts_title)) }
                item {
                    GoogleCard {
                        Text(
                            text = stringResource(R.string.teacher_accounts_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))

                        val teacherMembers = remember(members) {
                            members.filter { it.role == UserRole.TEACHER }
                        }
                        if (teacherMembers.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_teacher_accounts),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                teacherMembers.forEach { member ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                member.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                member.email,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusBadge(
                                            text = stringResource(R.string.role_teacher),
                                            tone = BadgeTone.BLUE
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        val canManageCloudTeachers = isFirebaseConfigured && !authSession.isOfflineLocalMode
                        if (!canManageCloudTeachers) {
                            Text(
                                text = stringResource(R.string.auth_firebase_not_configured),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        OutlinedButton(
                            onClick = { showAddTeacherDialog = true },
                            enabled = canManageCloudTeachers,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("add_teacher_account_btn")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.add_teacher_account))
                        }
                    }
                }
            }

            // Academy Profile
            item { SectionHeader(title = stringResource(R.string.academy_profile_section)) }
            item {
                GoogleCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (logoBitmap != null) {
                                    Image(
                                        bitmap = logoBitmap.asImageBitmap(),
                                        contentDescription = academyName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.School,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                        if (isAdmin) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        logoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                ) {
                                    Text(stringResource(R.string.change_logo))
                                }
                                if (logoPath != null) {
                                    IconButton(onClick = { logoPath = null }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.remove_logo))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = academyName,
                        onValueChange = { if (isAdmin) { academyName = it; nameErr = null } },
                        readOnly = !isAdmin,
                        label = { Text(stringResource(R.string.academy_name_label)) },
                        isError = nameErr != null,
                        supportingText = nameErr?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_academy_name_input")
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tagline,
                        onValueChange = { if (isAdmin) tagline = it },
                        readOnly = !isAdmin,
                        label = { Text(stringResource(R.string.academy_tagline_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { if (isAdmin) phone = it },
                        readOnly = !isAdmin,
                        label = { Text(stringResource(R.string.academy_phone_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = address,
                        onValueChange = { if (isAdmin) address = it },
                        readOnly = !isAdmin,
                        label = { Text(stringResource(R.string.academy_address_label)) },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isAdmin) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (academyName.isBlank()) {
                                    nameErr = requiredMsg
                                } else {
                                    onSaveProfile(academyName, tagline, phone, address, logoPath)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("save_profile_btn")
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }

            // Device Receipt Prefix (Admin only)
            if (isAdmin) {
                item {
                    GoogleCard {
                        Text(
                            text = stringResource(R.string.device_receipt_prefix_label),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        val preview = FormatUtils.normalizeDevicePrefix(prefixInput)
                        Text(
                            text = stringResource(R.string.device_receipt_prefix_hint, preview),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = prefixInput,
                                onValueChange = { raw ->
                                    prefixInput = raw.filter { it.isLetterOrDigit() }.take(4).uppercase()
                                },
                                label = { Text(stringResource(R.string.device_receipt_prefix_label)) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("device_prefix_input")
                            )
                            Button(
                                onClick = { onSetDevicePrefix(prefixInput) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("save_device_prefix_btn")
                            ) {
                                Text(stringResource(R.string.save))
                            }
                        }
                    }
                }
            }

            item { SectionHeader(title = stringResource(R.string.appearance_section)) }
            item {
                GoogleCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.dark_mode_label),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                stringResource(R.string.dark_mode_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = onToggleDarkMode,
                            modifier = Modifier.testTag("dark_mode_switch")
                        )
                    }
                }
            }

            item { SectionHeader(title = stringResource(R.string.ai_section_title)) }
            item {
                val keySavedMsg = stringResource(R.string.ai_key_saved_msg)
                val keyClearedMsg = stringResource(R.string.ai_key_cleared_msg)
                GoogleCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    stringResource(R.string.ai_toggle_label),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.ai_toggle_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = aiAssistantEnabled,
                            onCheckedChange = onToggleAiAssistant,
                            modifier = Modifier.testTag("ai_assistant_switch")
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.ai_user_key_label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                        StatusBadge(
                            text = stringResource(
                                if (userGeminiApiKey.isNotBlank()) R.string.ai_key_active_badge
                                else R.string.ai_key_missing_badge
                            ),
                            tone = if (userGeminiApiKey.isNotBlank()) BadgeTone.GREEN else BadgeTone.NEUTRAL
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = geminiKeyInput,
                        onValueChange = { geminiKeyInput = it },
                        placeholder = { Text("AIza...") },
                        singleLine = true,
                        visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                Icon(
                                    imageVector = if (showGeminiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showGeminiKey) "Hide API Key" else "Show API Key"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_user_key_input")
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.ai_user_key_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val trimmed = geminiKeyInput.trim()
                                onSaveUserGeminiApiKey(trimmed)
                                onShowMessage(if (trimmed.isNotEmpty()) keySavedMsg else keyClearedMsg)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_save_key_btn")
                        ) {
                            Text(stringResource(R.string.ai_save_key))
                        }
                        if (userGeminiApiKey.isNotBlank() || geminiKeyInput.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    geminiKeyInput = ""
                                    onSaveUserGeminiApiKey("")
                                    onShowMessage(keyClearedMsg)
                                },
                                modifier = Modifier.testTag("ai_clear_key_btn")
                            ) {
                                Text(stringResource(R.string.ai_clear_key))
                            }
                        }
                    }
                }
            }

            item { SectionHeader(title = stringResource(R.string.security_section)) }
            item {
                GoogleCard(
                    onClick = { showChangePinDialog = true },
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.testTag("change_pin_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.LockReset, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(if (hasPinSet) R.string.change_pin else R.string.set_pin),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                stringResource(if (hasPinSet) R.string.change_pin_subtitle else R.string.set_pin_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (isAdmin) {
                item { SectionHeader(title = stringResource(R.string.backup_section)) }
                item {
                    GoogleCard(contentPadding = PaddingValues(0.dp)) {
                        Surface(
                            onClick = {
                                val fileName = "academy_manager_backup_${FormatUtils.currentDateKey()}.db"
                                backupLauncher.launch(fileName)
                            },
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("backup_db_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.backup_database), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                    Text(stringResource(R.string.backup_database_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Surface(
                            onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("restore_db_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.restore_database), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                    Text(stringResource(R.string.restore_database_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTeacherDialog) {
        InviteTeacherAccountDialog(
            onDismiss = { showAddTeacherDialog = false },
            onInvite = onInviteTeacher,
            onSuccess = { member ->
                showAddTeacherDialog = false
                onShowMessage(context.getString(R.string.teacher_invite_sent, member.email))
            }
        )
    }

    if (showChangePinDialog) {
        ChangePinDialog(
            hasPinSet = hasPinSet,
            onDismiss = { showChangePinDialog = false },
            onSubmit = { currentPin, newPin ->
                scope.launch {
                    val ok = onChangePin(currentPin, newPin)
                    if (ok) {
                        showChangePinDialog = false
                        onShowMessage(context.getString(R.string.pin_changed_success))
                    } else {
                        onShowMessage(context.getString(R.string.pin_wrong))
                    }
                }
            }
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text(stringResource(R.string.sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.sign_out_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirm = false
                        onSignOut(credentialManager)
                    }
                ) {
                    Text(stringResource(R.string.sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (pendingRestoreUriString != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreUriString = null },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = android.net.Uri.parse(pendingRestoreUriString)
                        pendingRestoreUriString = null
                        val res = DatabaseBackupHelper.restoreDatabaseFromUri(context, uri)
                        if (res.isSuccess) {
                            onShowMessage(restoreOkMsg)
                            onDatabaseRestored()
                        } else {
                            onShowMessage(restoreInvalidMsg)
                        }
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUriString = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun InviteTeacherAccountDialog(
    onDismiss: () -> Unit,
    onInvite: suspend (name: String, email: String) -> Result<AcademyMember>,
    onSuccess: (AcademyMember) -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var errorMsg by rememberSaveable { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    val requiredMsg = stringResource(R.string.field_required)
    val invalidEmailMsg = stringResource(R.string.auth_invalid_email)

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text(stringResource(R.string.add_teacher_account)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.invite_teacher_google_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMsg = null },
                    label = { Text(stringResource(R.string.teacher_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMsg = null },
                    label = { Text(stringResource(R.string.teacher_google_email_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AnimatedVisibility(visible = errorMsg != null) {
                    Text(
                        text = errorMsg.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanEmail = email.trim().lowercase()
                    when {
                        name.isBlank() -> errorMsg = requiredMsg
                        !Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() -> errorMsg = invalidEmailMsg
                        else -> {
                            isBusy = true
                            errorMsg = null
                            scope.launch {
                                val res = onInvite(name, cleanEmail)
                                isBusy = false
                                res.onSuccess(onSuccess)
                                res.onFailure { err ->
                                    errorMsg = err.localizedMessage ?: "Failed to invite teacher"
                                }
                            }
                        }
                    }
                },
                enabled = !isBusy
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(stringResource(R.string.invite_teacher_btn))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ChangePinDialog(
    hasPinSet: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (currentPin: String, newPin: String) -> Unit
) {
    var currentPin by rememberSaveable { mutableStateOf("") }
    var newPin by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }
    var errorMsg by rememberSaveable { mutableStateOf<String?>(null) }

    val mismatchMsg = stringResource(R.string.pin_mismatch)
    val lengthHintMsg = stringResource(R.string.pin_length_hint)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (hasPinSet) R.string.pin_change_title else R.string.set_pin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasPinSet) {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) currentPin = it; errorMsg = null },
                        label = { Text(stringResource(R.string.pin_current_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) newPin = it; errorMsg = null },
                    label = { Text(stringResource(R.string.pin_new_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it; errorMsg = null },
                    label = { Text(stringResource(R.string.pin_confirm_new_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(errorMsg.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        hasPinSet && currentPin.length !in 4..6 -> errorMsg = lengthHintMsg
                        newPin.length !in 4..6 -> errorMsg = lengthHintMsg
                        newPin != confirmPin -> errorMsg = mismatchMsg
                        else -> onSubmit(currentPin, newPin)
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
