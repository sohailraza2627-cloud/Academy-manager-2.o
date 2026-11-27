package com.example.ui.screens.assistant

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.ai.AssistantAction
import com.example.data.ai.ParentMessageRequest
import com.example.data.local.*
import com.example.data.repository.ReceiptResult
import com.example.ui.components.BadgeTone
import com.example.ui.components.GoogleCard
import com.example.ui.components.StatusBadge
import com.example.ui.viewmodel.AssistantChatMessage
import com.example.ui.viewmodel.AssistantViewModel
import com.example.ui.viewmodel.ProposalExecutionState
import com.example.util.FormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    assistantViewModel: AssistantViewModel = viewModel(),
    onOpenWhatsApp: (Student, ParentMessageRequest) -> Unit,
    onReceiptIssued: (ReceiptResult) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val messages by assistantViewModel.messages.collectAsStateWithLifecycle()
    val isThinking by assistantViewModel.isThinking.collectAsStateWithLifecycle()
    val attachedBitmap by assistantViewModel.attachedBitmap.collectAsStateWithLifecycle()
    val userGeminiApiKey by assistantViewModel.userGeminiApiKey.collectAsStateWithLifecycle()
    val authSession by assistantViewModel.authSession.collectAsStateWithLifecycle()
    val students by assistantViewModel.students.collectAsStateWithLifecycle()
    val exams by assistantViewModel.exams.collectAsStateWithLifecycle()
    val subjects by assistantViewModel.subjects.collectAsStateWithLifecycle()

    var inputText by rememberSaveable { mutableStateOf("") }
    var showApiKeyDialog by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val isImeVisible = WindowInsets.isImeVisible

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            assistantViewModel.attachPaperPhoto(uri)
        }
    }

    LaunchedEffect(messages.size, isThinking, isImeVisible) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.assistant_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (userGeminiApiKey.isNotBlank()) {
                                    stringResource(R.string.assistant_subtitle_key_set)
                                } else {
                                    stringResource(R.string.assistant_subtitle_local)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("assistant_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    AssistChip(
                        onClick = { showApiKeyDialog = true },
                        label = {
                            Text(
                                text = stringResource(
                                    if (userGeminiApiKey.isNotBlank()) R.string.assistant_key_chip_set
                                    else R.string.assistant_key_chip_add
                                ),
                                maxLines = 1
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("assistant_key_chip")
                    )
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = { assistantViewModel.clearChat() },
                            modifier = Modifier.testTag("assistant_clear_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.assistant_new_chat))
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Gemini-style Bottom Prompt Bar — anchored cleanly above navigation bar & soft keyboard
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.navigationBars
                                .union(WindowInsets.ime)
                                .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    AnimatedVisibility(visible = attachedBitmap != null) {
                        attachedBitmap?.let { bmp ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .fillMaxWidth()
                            ) {
                                Box {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = stringResource(R.string.assistant_attached_paper),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.assistant_attached_paper),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.assistant_attached_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { assistantViewModel.clearAttachment() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove attachment")
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("assistant_attach_photo_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = stringResource(R.string.assistant_attach_paper),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.assistant_input_placeholder),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("assistant_input_field")
                        )

                        FilledIconButton(
                            onClick = {
                                val text = inputText
                                inputText = ""
                                assistantViewModel.sendMessage(text)
                            },
                            enabled = !isThinking && (inputText.isNotBlank() || attachedBitmap != null),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("assistant_send_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.assistant_send)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            // Subtle key quota notice when user hasn't entered their own key yet
            AnimatedVisibility(visible = userGeminiApiKey.isBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.assistant_key_banner),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { showApiKeyDialog = true }) {
                            Text(stringResource(R.string.assistant_key_chip_add))
                        }
                    }
                }
            }

            if (messages.isEmpty()) {
                AssistantEmptyWelcome(
                    userName = authSession.name.ifBlank { "Teacher" },
                    onSuggestionClick = { suggestion ->
                        assistantViewModel.sendMessage(suggestion)
                    },
                    onPickPaperPhoto = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("assistant_chat_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        AssistantMessageBubble(
                            message = msg,
                            isAdmin = authSession.isAdmin,
                            students = students,
                            exams = exams,
                            subjects = subjects,
                            onConfirmFee = { proposal, amount ->
                                assistantViewModel.executeFeeCollection(
                                    messageId = msg.id,
                                    proposal = proposal,
                                    amountToCollect = amount,
                                    onReceiptIssued = onReceiptIssued
                                )
                            },
                            onConfirmAttendance = { proposal ->
                                assistantViewModel.executeMarkAttendance(msg.id, proposal)
                            },
                            onConfirmMarks = { studentId, studentName, examId, examName, subject, obtained ->
                                assistantViewModel.executeSaveExamMarks(
                                    messageId = msg.id,
                                    studentId = studentId,
                                    studentName = studentName,
                                    examId = examId,
                                    examName = examName,
                                    subject = subject,
                                    obtainedMarks = obtained
                                )
                            },
                            onTriggerWhatsApp = { proposal ->
                                scope.launch {
                                    assistantViewModel.buildWhatsAppTarget(proposal)?.let { (st, req) ->
                                        onOpenWhatsApp(st, req)
                                    }
                                }
                            }
                        )
                    }

                    if (isThinking) {
                        item(key = "thinking") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.assistant_thinking),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showApiKeyDialog) {
        UserGeminiKeyDialog(
            currentKey = userGeminiApiKey,
            onSave = { key ->
                assistantViewModel.saveUserApiKey(key)
                showApiKeyDialog = false
            },
            onDismiss = { showApiKeyDialog = false }
        )
    }
}

@Composable
private fun AssistantEmptyWelcome(
    userName: String,
    onSuggestionClick: (String) -> Unit,
    onPickPaperPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.assistant_greeting, userName),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.assistant_welcome_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionPromptCard(
                icon = Icons.Default.ReceiptLong,
                title = stringResource(R.string.assistant_sug_fee),
                onClick = { onSuggestionClick("Ali ki monthly fee aa gayi") }
            )
            SuggestionPromptCard(
                icon = Icons.Default.Grading,
                title = stringResource(R.string.assistant_sug_paper),
                onClick = onPickPaperPhoto
            )
            SuggestionPromptCard(
                icon = Icons.Default.FactCheck,
                title = stringResource(R.string.assistant_sug_attendance),
                onClick = { onSuggestionClick("Aaj absent students aur pending fees batao") }
            )
            SuggestionPromptCard(
                icon = Icons.Default.Chat,
                title = stringResource(R.string.assistant_sug_whatsapp),
                onClick = { onSuggestionClick("Pending fees summary dikhao") }
            )
        }
    }
}

@Composable
private fun SuggestionPromptCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    GoogleCard(onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ArrowOutward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AssistantMessageBubble(
    message: AssistantChatMessage,
    isAdmin: Boolean,
    students: List<Student>,
    exams: List<Exam>,
    subjects: List<Subject>,
    onConfirmFee: (AssistantAction.CollectFeeProposal, Long) -> Unit,
    onConfirmAttendance: (AssistantAction.MarkAttendanceProposal) -> Unit,
    onConfirmMarks: (String, String, String, String, Subject, Double) -> Unit,
    onTriggerWhatsApp: (AssistantAction.SendWhatsAppProposal) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        if (message.isUser) {
            Surface(
                shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    message.attachedBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = stringResource(R.string.assistant_attached_paper),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (message.usedCloudAi) "Gemini (Your Key)" else "Academy Helper",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    when (val action = message.action) {
                        is AssistantAction.CollectFeeProposal -> FeeProposalCard(
                            proposal = action,
                            state = message.actionState,
                            isAdmin = isAdmin,
                            onConfirm = { amt -> onConfirmFee(action, amt) }
                        )
                        is AssistantAction.MarkAttendanceProposal -> AttendanceProposalCard(
                            proposal = action,
                            state = message.actionState,
                            onConfirm = { onConfirmAttendance(action) }
                        )
                        is AssistantAction.SaveExamMarksProposal -> ExamMarksProposalCard(
                            proposal = action,
                            state = message.actionState,
                            isAdmin = isAdmin,
                            students = students,
                            exams = exams,
                            subjects = subjects,
                            onConfirm = onConfirmMarks
                        )
                        is AssistantAction.SendWhatsAppProposal -> {
                            GoogleCard(contentPadding = PaddingValues(12.dp)) {
                                Text(
                                    text = "${action.studentName} · Parent WhatsApp",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(6.dp))
                                FilledTonalButton(onClick = { onTriggerWhatsApp(action) }) {
                                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.wa_sheet_title))
                                }
                            }
                        }
                        null -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun FeeProposalCard(
    proposal: AssistantAction.CollectFeeProposal,
    state: ProposalExecutionState,
    isAdmin: Boolean,
    onConfirm: (Long) -> Unit
) {
    var amountText by rememberSaveable(proposal.studentId, proposal.amount) {
        mutableStateOf(proposal.amount.toString())
    }
    GoogleCard(contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${proposal.studentName} (${proposal.className})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${proposal.chargeTitle} · Due: ${FormatUtils.formatCurrency(proposal.maxDue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusBadge(
                text = if (proposal.ledgerType == FeeLedgerType.PAPER_FEE) "Paper Fee" else if (proposal.ledgerType == FeeLedgerType.TUITION) "Tuition" else "Other Fee",
                tone = BadgeTone.BLUE
            )
        }

        when (state) {
            is ProposalExecutionState.Completed -> {
                Spacer(Modifier.height(8.dp))
                StatusBadge(text = state.summary, tone = BadgeTone.GREEN)
            }
            else -> {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.amount_paying)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = state != ProposalExecutionState.Executing,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state is ProposalExecutionState.Failed) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onConfirm(amountText.toLongOrNull() ?: 0L) },
                    enabled = isAdmin && state != ProposalExecutionState.Executing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.assistant_confirm_receipt))
                }
            }
        }
    }
}

@Composable
private fun AttendanceProposalCard(
    proposal: AssistantAction.MarkAttendanceProposal,
    state: ProposalExecutionState,
    onConfirm: () -> Unit
) {
    GoogleCard(contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${proposal.studentName} (${proposal.className})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Date: ${FormatUtils.formatDateKey(proposal.dateKey)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusBadge(
                text = proposal.status,
                tone = when (proposal.status) {
                    AttendanceStatus.ABSENT -> BadgeTone.RED
                    AttendanceStatus.LEAVE -> BadgeTone.AMBER
                    else -> BadgeTone.GREEN
                }
            )
        }
        Spacer(Modifier.height(10.dp))
        when (state) {
            is ProposalExecutionState.Completed -> StatusBadge(text = state.summary, tone = BadgeTone.GREEN)
            else -> {
                if (state is ProposalExecutionState.Failed) {
                    Text(state.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(6.dp))
                }
                Button(
                    onClick = onConfirm,
                    enabled = state != ProposalExecutionState.Executing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.save_attendance))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamMarksProposalCard(
    proposal: AssistantAction.SaveExamMarksProposal,
    state: ProposalExecutionState,
    isAdmin: Boolean,
    students: List<Student>,
    exams: List<Exam>,
    subjects: List<Subject>,
    onConfirm: (String, String, String, String, Subject, Double) -> Unit
) {
    val activeStudents = remember(students) { students.filter { it.status == StudentStatus.ACTIVE } }
    var selectedExamId by rememberSaveable(proposal.examId) { mutableStateOf(proposal.examId ?: exams.firstOrNull()?.id.orEmpty()) }
    var selectedSubjectId by rememberSaveable(proposal.subjectId) { mutableStateOf(proposal.subjectId ?: subjects.firstOrNull()?.id.orEmpty()) }
    var selectedStudentId by rememberSaveable(proposal.studentId) { mutableStateOf(proposal.studentId ?: activeStudents.firstOrNull()?.id.orEmpty()) }
    var marksInput by rememberSaveable(proposal.obtainedMarks) {
        mutableStateOf(if (proposal.obtainedMarks > 0.0) FormatUtils.formatMarks(proposal.obtainedMarks) else "")
    }

    var examMenuOpen by remember { mutableStateOf(false) }
    var subjectMenuOpen by remember { mutableStateOf(false) }
    var studentMenuOpen by remember { mutableStateOf(false) }

    val selectedExam = exams.find { it.id == selectedExamId } ?: exams.firstOrNull()
    val filteredSubjects = remember(subjects, selectedExam) {
        if (selectedExam != null) subjects.filter { it.classId == selectedExam.classId }.ifEmpty { subjects } else subjects
    }
    val selectedSubject = filteredSubjects.find { it.id == selectedSubjectId } ?: filteredSubjects.firstOrNull()
    val filteredStudents = remember(activeStudents, selectedExam) {
        if (selectedExam != null) activeStudents.filter { it.classId == selectedExam.classId }.ifEmpty { activeStudents } else activeStudents
    }
    val selectedStudent = filteredStudents.find { it.id == selectedStudentId } ?: filteredStudents.firstOrNull()

    GoogleCard(contentPadding = PaddingValues(14.dp)) {
        Text(
            text = stringResource(R.string.assistant_paper_result_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (proposal.feedback.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = proposal.feedback,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))

        when (state) {
            is ProposalExecutionState.Completed -> StatusBadge(text = state.summary, tone = BadgeTone.GREEN)
            else -> {
                if (exams.isEmpty() || subjects.isEmpty() || activeStudents.isEmpty()) {
                    Text(
                        text = stringResource(R.string.assistant_need_exam_setup),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ExposedDropdownMenuBox(expanded = studentMenuOpen, onExpandedChange = { studentMenuOpen = it }) {
                        OutlinedTextField(
                            value = selectedStudent?.name ?: proposal.studentName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.nav_students)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(studentMenuOpen) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = studentMenuOpen, onDismissRequest = { studentMenuOpen = false }) {
                            filteredStudents.forEach { st ->
                                DropdownMenuItem(
                                    text = { Text("${st.name} (${st.rollNo})") },
                                    onClick = {
                                        selectedStudentId = st.id
                                        studentMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    ExposedDropdownMenuBox(expanded = examMenuOpen, onExpandedChange = { examMenuOpen = it }) {
                        OutlinedTextField(
                            value = selectedExam?.name ?: proposal.examName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.exams_title)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(examMenuOpen) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = examMenuOpen, onDismissRequest = { examMenuOpen = false }) {
                            exams.forEach { ex ->
                                DropdownMenuItem(
                                    text = { Text(ex.name) },
                                    onClick = {
                                        selectedExamId = ex.id
                                        examMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = subjectMenuOpen,
                            onExpandedChange = { subjectMenuOpen = it },
                            modifier = Modifier.weight(1.3f)
                        ) {
                            OutlinedTextField(
                                value = selectedSubject?.name ?: proposal.subjectName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.tab_subjects)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(subjectMenuOpen) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(expanded = subjectMenuOpen, onDismissRequest = { subjectMenuOpen = false }) {
                                filteredSubjects.forEach { sub ->
                                    DropdownMenuItem(
                                        text = { Text("${sub.name} (/${sub.totalMarks})") },
                                        onClick = {
                                            selectedSubjectId = sub.id
                                            subjectMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = marksInput,
                            onValueChange = { marksInput = it },
                            label = { Text("Marks / ${selectedSubject?.totalMarks ?: proposal.totalMarks}") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (state is ProposalExecutionState.Failed) {
                        Spacer(Modifier.height(6.dp))
                        Text(state.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val st = selectedStudent
                            val ex = selectedExam
                            val sub = selectedSubject
                            val marks = marksInput.toDoubleOrNull() ?: 0.0
                            if (st != null && ex != null && sub != null) {
                                onConfirm(st.id, st.name, ex.id, ex.name, sub, marks)
                            }
                        },
                        enabled = isAdmin && selectedStudent != null && selectedExam != null && selectedSubject != null && state != ProposalExecutionState.Executing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.assistant_save_marks_btn))
                    }
                }
            }
        }
    }
}

@Composable
private fun UserGeminiKeyDialog(
    currentKey: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyInput by rememberSaveable(currentKey) { mutableStateOf(currentKey) }
    var showKey by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_user_key_label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.ai_user_key_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    placeholder = { Text("AIza...") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showKey) "Hide API key" else "Show API key"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(keyInput.trim()) }) {
                Text(stringResource(R.string.ai_save_key))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (currentKey.isNotBlank()) {
                    TextButton(onClick = { onSave("") }) {
                        Text(stringResource(R.string.ai_clear_key))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
