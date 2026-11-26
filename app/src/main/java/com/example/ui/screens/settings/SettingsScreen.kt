package com.example.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AcademyProfile
import com.example.ui.components.GoogleCard
import com.example.ui.components.SectionHeader
import com.example.util.DatabaseBackupHelper
import com.example.util.FormatUtils
import com.example.util.ImageStorage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profile: AcademyProfile,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onSaveProfile: (name: String, tagline: String, phone: String, address: String, logoPath: String?) -> Unit,
    onChangePin: suspend (currentPin: String, newPin: String) -> Boolean,
    onDatabaseRestored: () -> Unit,
    onShowMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var academyName by rememberSaveable(profile.academyName) { mutableStateOf(profile.academyName) }
    var tagline by rememberSaveable(profile.tagline) { mutableStateOf(profile.tagline) }
    var phone by rememberSaveable(profile.phone) { mutableStateOf(profile.phone) }
    var address by rememberSaveable(profile.address) { mutableStateOf(profile.address) }
    var logoPath by rememberSaveable(profile.logoPath) { mutableStateOf(profile.logoPath) }
    var nameErr by rememberSaveable { mutableStateOf<String?>(null) }

    var showChangePinDialog by rememberSaveable { mutableStateOf(false) }
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

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = academyName,
                        onValueChange = { academyName = it; nameErr = null },
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
                        onValueChange = { tagline = it },
                        label = { Text(stringResource(R.string.academy_tagline_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(stringResource(R.string.academy_phone_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(R.string.academy_address_label)) },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                            Text(stringResource(R.string.change_pin), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.change_pin_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

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

    if (showChangePinDialog) {
        ChangePinDialog(
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
private fun ChangePinDialog(
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
        title = { Text(stringResource(R.string.pin_change_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) currentPin = it; errorMsg = null },
                    label = { Text(stringResource(R.string.pin_current_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                        currentPin.length !in 4..6 || newPin.length !in 4..6 -> errorMsg = lengthHintMsg
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
