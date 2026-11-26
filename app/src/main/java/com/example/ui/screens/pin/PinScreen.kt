package com.example.ui.screens.pin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AcademyProfile
import com.example.util.ImageStorage
import kotlinx.coroutines.launch

@Composable
fun PinScreen(
    isSetupMode: Boolean,
    profile: AcademyProfile,
    onCreatePin: suspend (String) -> Unit,
    onVerifyPin: suspend (String) -> Boolean
) {
    val scope = rememberCoroutineScope()
    var enteredPin by rememberSaveable { mutableStateOf("") }
    var firstPin by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMsg by rememberSaveable { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val logoBitmap = remember(profile.logoPath) { ImageStorage.loadBitmap(profile.logoPath) }

    val mismatchText = stringResource(R.string.pin_mismatch)
    val wrongPinText = stringResource(R.string.pin_wrong)

    fun submitCurrentPin() {
        if (enteredPin.length !in 4..6 || isProcessing) return
        if (isSetupMode) {
            if (firstPin == null) {
                firstPin = enteredPin
                enteredPin = ""
                errorMsg = null
            } else {
                if (firstPin == enteredPin) {
                    isProcessing = true
                    scope.launch {
                        onCreatePin(enteredPin)
                        isProcessing = false
                    }
                } else {
                    errorMsg = mismatchText
                    firstPin = null
                    enteredPin = ""
                }
            }
        } else {
            isProcessing = true
            scope.launch {
                val ok = onVerifyPin(enteredPin)
                isProcessing = false
                if (!ok) {
                    errorMsg = wrongPinText
                    enteredPin = ""
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.widthIn(max = 400.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (logoBitmap != null) {
                                Image(
                                    bitmap = logoBitmap.asImageBitmap(),
                                    contentDescription = profile.academyName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = if (isSetupMode) Icons.Default.School else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = when {
                            isSetupMode && firstPin == null -> stringResource(R.string.pin_create_title)
                            isSetupMode -> stringResource(R.string.pin_confirm_title)
                            else -> stringResource(R.string.pin_enter_title)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = when {
                            isSetupMode && firstPin == null -> stringResource(R.string.pin_create_subtitle)
                            isSetupMode -> stringResource(R.string.pin_confirm_subtitle)
                            else -> stringResource(R.string.pin_enter_subtitle, profile.academyName)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // PIN Dots Indicator (4-6 digits)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 6) {
                            val filled = i < enteredPin.length
                            val isRequiredSlot = i < 4
                            Box(
                                modifier = Modifier
                                    .size(if (filled) 16.dp else 14.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            filled -> MaterialTheme.colorScheme.primary
                                            isRequiredSlot -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                        }
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.pin_length_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AnimatedVisibility(visible = errorMsg != null) {
                        Text(
                            text = errorMsg.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Tactile Keypad
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "DEL")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        rows.forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                row.forEach { key ->
                                    when (key) {
                                        "C" -> PinKeyButton(
                                            label = "C",
                                            testTag = "pin_key_clear",
                                            isSecondary = true,
                                            onClick = {
                                                enteredPin = ""
                                                errorMsg = null
                                            }
                                        )
                                        "DEL" -> Surface(
                                            onClick = {
                                                if (enteredPin.isNotEmpty()) {
                                                    enteredPin = enteredPin.dropLast(1)
                                                }
                                            },
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .size(68.dp)
                                                .testTag("pin_key_backspace")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                    contentDescription = stringResource(R.string.pin_backspace),
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                        else -> PinKeyButton(
                                            label = key,
                                            testTag = "pin_key_$key",
                                            onClick = {
                                                if (enteredPin.length < 6) {
                                                    enteredPin += key
                                                    errorMsg = null
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { submitCurrentPin() },
                        enabled = enteredPin.length in 4..6 && !isProcessing,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("pin_submit_button")
                    ) {
                        Text(
                            text = if (isSetupMode) stringResource(R.string.pin_continue)
                            else stringResource(R.string.pin_unlock),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKeyButton(
    label: String,
    testTag: String,
    isSecondary: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSecondary) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .size(68.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
