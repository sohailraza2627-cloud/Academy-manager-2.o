package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ai.AiTextResult
import com.example.data.ai.ParentMessageRequest
import com.example.data.local.Student
import com.example.util.WhatsAppHelper
import com.example.util.WhatsAppMessageType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentWhatsAppSheet(
    student: Student,
    request: ParentMessageRequest,
    aiEnabled: Boolean,
    initialDraft: String,
    onDraftWithAi: suspend (ParentMessageRequest) -> AiTextResult,
    onDismiss: () -> Unit,
    onSendResult: (Result<Unit>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var messageText by remember(student.id, initialDraft) { mutableStateOf(initialDraft) }
    var isAiLoading by remember { mutableStateOf(false) }
    var aiNote by remember { mutableStateOf<String?>(null) }

    val normalizedNumber = remember(student.guardianWhatsapp) {
        WhatsAppHelper.normalizeWhatsAppNumber(student.guardianWhatsapp)
    }

    val topicLabel = when (request.type) {
        WhatsAppMessageType.ATTENDANCE_ALERT -> stringResource(R.string.wa_type_attendance)
        WhatsAppMessageType.EXAM_RESULT -> stringResource(R.string.wa_type_result)
        WhatsAppMessageType.FEE_REMINDER -> stringResource(R.string.wa_type_fee)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.wa_sheet_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${student.name} · $topicLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                StatusBadge(
                    text = normalizedNumber ?: stringResource(R.string.wa_no_number_badge),
                    tone = if (normalizedNumber != null) BadgeTone.GREEN else BadgeTone.RED
                )
            }

            if (normalizedNumber == null) {
                GoogleCard(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.wa_missing_number_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text(stringResource(R.string.wa_message_label)) },
                minLines = 5,
                maxLines = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wa_message_input")
            )

            AnimatedVisibility(visible = aiNote != null) {
                Text(
                    text = aiNote.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (aiEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            isAiLoading = true
                            aiNote = null
                            scope.launch {
                                when (val res = onDraftWithAi(request)) {
                                    is AiTextResult.Success -> {
                                        messageText = res.text
                                        aiNote = if (res.usedCloudAi) {
                                            context.getString(R.string.ai_draft_ready)
                                        } else {
                                            context.getString(R.string.ai_local_template_used)
                                        }
                                    }
                                    is AiTextResult.FallbackError -> {
                                        messageText = res.fallbackText
                                        aiNote = res.message
                                    }
                                }
                                isAiLoading = false
                            }
                        },
                        enabled = !isAiLoading,
                        modifier = Modifier.testTag("wa_ai_draft_btn")
                    ) {
                        if (isAiLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ai_drafting))
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ai_polish_draft))
                        }
                    }

                    TextButton(
                        onClick = {
                            messageText = initialDraft
                            aiNote = null
                        },
                        enabled = !isAiLoading
                    ) {
                        Text(stringResource(R.string.reset_template))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        val res = WhatsAppHelper.openWhatsAppChat(
                            context = context,
                            rawPhoneNumber = student.guardianWhatsapp,
                            message = messageText.trim()
                        )
                        if (res.isSuccess) {
                            onDismiss()
                        }
                        onSendResult(res)
                    },
                    enabled = messageText.isNotBlank() && !isAiLoading,
                    modifier = Modifier
                        .weight(1.4f)
                        .heightIn(min = 48.dp)
                        .testTag("wa_send_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.wa_send_button))
                }
            }
        }
    }
}

@Composable
fun AcademySummaryCard(
    title: String,
    localSummaryText: String,
    aiEnabled: Boolean,
    onGenerateAiSummary: suspend () -> AiTextResult,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var summaryText by remember(localSummaryText) { mutableStateOf(localSummaryText) }
    var isCloudSummary by remember(localSummaryText) { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember(localSummaryText) { mutableStateOf<String?>(null) }

    GoogleCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (isCloudSummary) {
                    Text(
                        text = stringResource(R.string.ai_summary_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (aiEnabled) {
                FilledTonalButton(
                    onClick = {
                        isLoading = true
                        statusMessage = null
                        scope.launch {
                            when (val res = onGenerateAiSummary()) {
                                is AiTextResult.Success -> {
                                    summaryText = res.text
                                    isCloudSummary = res.usedCloudAi
                                }
                                is AiTextResult.FallbackError -> {
                                    summaryText = res.fallbackText
                                    isCloudSummary = false
                                    statusMessage = res.message
                                }
                            }
                            isLoading = false
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.testTag("ai_summary_btn")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(stringResource(R.string.ai_summarize))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = summaryText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AnimatedVisibility(visible = statusMessage != null) {
            Column {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = statusMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
