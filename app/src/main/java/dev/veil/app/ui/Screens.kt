package dev.veil.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.veil.app.VeilViewModel
import dev.veil.app.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun timeLabel(time: Long): String = DateTimeFormatter.ofPattern("MMM d · HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeScreen(state: VeilUiState, onPause: (Boolean) -> Unit, onLab: () -> Unit, onInbox: () -> Unit, onAi: () -> Unit) {
    val prefs = state.preferences
    val status = when {
        prefs.capturePaused -> "Observation paused"
        prefs.enabledApps.isEmpty() -> "Choose your apps"
        !state.system.notificationAccess -> "Permission needed"
        !state.system.listenerConnected -> "Listener disconnected"
        prefs.automationEnabled -> "Priority automation enabled"
        else -> "Ready with suggestions"
    }
    val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    ScreenBody {
        PageHeading("Your conversations.\nA little more breathing room.", "A calm command center for your private messages.")
        GlassPanel(intensity = prefs.glassIntensity, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Your workspace")
                Icon(Icons.Outlined.BlurOn, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(status, style = MaterialTheme.typography.headlineMedium)
            Text(if (prefs.automationEnabled) "Only approved priority rules may send. Anything sensitive stays for your review." else "Suggestions are ready. Automatic replies remain off until you choose and verify a contact.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            InfoRow(Icons.Outlined.PauseCircleOutline, "Pause everything", "Stops observing messages and prevents new replies.",
                trailing = { Switch(prefs.capturePaused, onPause, modifier = Modifier.semantics { contentDescription = "Pause everything" }) })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MessagingApp.entries.forEach { app -> StatusPill("${app.label} ${if (app in prefs.enabledApps) "on" else "off"}") }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 480.dp || LocalDensity.current.fontScale >= 1.6f) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Metric("${state.messages.count { it.suggestion != null && it.dispatchAttemptedAt == null }}", "Drafts to review")
                    Metric("${state.messages.count { (it.dispatchAcceptedAt ?: 0) >= today }}", "Accepted today")
                    Metric("${state.contacts.size}", "Priority contacts")
                }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                Metric("${state.messages.count { it.suggestion != null && it.dispatchAttemptedAt == null }}", "Drafts to review", Modifier.weight(1f))
                Metric("${state.messages.count { (it.dispatchAcceptedAt ?: 0) >= today }}", "Accepted today", Modifier.weight(1f))
                Metric("${state.contacts.size}", "Priority contacts", Modifier.weight(1f))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Eyebrow("Quick actions")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = onInbox, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Review inbox")
                }
                OutlinedButton(onClick = onLab, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Science, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Check compatibility")
                }
                TextButton(onClick = onAi, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Reply style")
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Outlined.ArrowForward, null, Modifier.size(17.dp))
                }
            }
        }
        state.messages.firstOrNull()?.let { latest ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Eyebrow("Last detected")
                Text(latest.observation.sender ?: "Sender unavailable", style = MaterialTheme.typography.titleLarge)
                Text("${latest.observation.app.label} · ${timeLabel(latest.observation.postedAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onInbox, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text("Review in Inbox"); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(16.dp)) }
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(19.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(
                colors.surface.copy(alpha = .87f),
                colors.primaryContainer.copy(alpha = .57f),
                colors.secondaryContainer.copy(alpha = .55f)
            )))
            .border(1.dp, colors.primary.copy(alpha = .14f), shape)
            .padding(horizontal = 17.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = colors.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable
internal fun InboxScreen(state: VeilUiState, viewModel: VeilViewModel, onLab: () -> Unit) {
    var testMessage by remember { mutableStateOf<MessageRecord?>(null) }
    var receiptMessage by remember { mutableStateOf<MessageRecord?>(null) }
    var reviewing by remember { mutableStateOf<Pair<MessageRecord, String>?>(null) }
    val messages = state.messages.filter { it.status != ReplyStatus.DISMISSED }
    val clipboard = LocalClipboardManager.current
    ScreenList {
        item { PageHeading("A quiet place to review.", "Detected notifications and drafts, not your complete messaging inbox.") }
        item { StatusPill(if (state.preferences.automationEnabled) "Priority rules enabled · other replies need review" else "Suggestions first · automatic sending off") }
        if (messages.isEmpty()) item { EmptyState(Icons.Outlined.ChatBubbleOutline, "Your first notification starts here.",
            "Enable an app and notification access, then ask a consenting contact to send a harmless message.", "Open Compatibility Lab", onLab) }
        items(messages, key = { it.observation.id }) { message ->
            NotificationCard(message, state,
                onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
                onOpen = { viewModel.openConversation(message.observation.id) },
                onTest = { testMessage = message },
                onGenerate = { viewModel.generateSuggestion(message.observation.id) },
                onReview = { text -> reviewing = message to text },
                onDismiss = { viewModel.dismissMessage(message.observation.id) },
                onReceipt = { receiptMessage = message })
        }
    }
    testMessage?.let { message -> TestReplyDialog(message, { testMessage = null }) { testMessage = null; viewModel.sendTest(message.observation.id) } }
    receiptMessage?.let { message -> ReceiptDialog({ receiptMessage = null }) { receiptMessage = null; viewModel.confirmReceipt(message.observation.id) } }
    reviewing?.let { (message, text) -> ReplyReviewDialog(message, text, { reviewing = null }) {
        reviewing = null; viewModel.sendReply(message.observation.id, text)
    } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationCard(message: MessageRecord, state: VeilUiState, onCopy: (String) -> Unit, onOpen: () -> Unit,
    onTest: () -> Unit, onGenerate: () -> Unit, onReview: (String) -> Unit, onDismiss: () -> Unit, onReceipt: () -> Unit) {
    val observation = message.observation
    val isPriority = state.contacts.any { it.app == observation.app && it.identityKey != null && it.identityKey == observation.identityKey }
    val canTest = PhaseOnePolicy.rejection(message, state.preferences) == null &&
        state.system.listenerConnected && state.system.notificationAccess
    val drafting = message.generationState in setOf("GENERATING", "RUNNING")
    var draft by rememberSaveable(observation.id, message.suggestion) { mutableStateOf(message.suggestion.orEmpty()) }
    val canSend = message.active && observation.hasReplyAction && !observation.isGroup &&
        !state.preferences.capturePaused && observation.app in state.preferences.enabledApps &&
        state.system.listenerConnected && state.system.notificationAccess && message.dispatchAttemptedAt == null &&
        !observation.sender.isNullOrBlank() && !observation.preview.isNullOrBlank() && draft.isNotBlank() && draft.length <= 1000
    GlassPanel(intensity = state.preferences.glassIntensity, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (observation.app == MessagingApp.WHATSAPP) Icons.Outlined.ChatBubbleOutline else Icons.Outlined.AlternateEmail, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(observation.sender ?: "Sender unavailable", style = MaterialTheme.typography.titleMedium)
                Text("${observation.app.label} · ${timeLabel(observation.postedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isPriority) Icon(Icons.Outlined.StarOutline, "Bound priority contact", tint = MaterialTheme.colorScheme.primary)
        }
        Text(observation.preview ?: "Message preview unavailable. Android or the messaging app may hide notification content.", style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(when {
                message.dispatchAttemptedAt != null -> message.status.label
                drafting -> "Drafting a reply"
                message.suggestion != null -> "Awaiting your review"
                observation.hasReplyAction -> "Reply action available"
                else -> "Copy / open app"
            }, warning = message.status == ReplyStatus.FAILED)
            if (message.dispatchKind == "AUTO") StatusPill("Automatic reply")
            if (observation.isGroup) StatusPill("Group · review and copy")
            if (!message.active) StatusPill("Action expired")
        }
        message.failureReason?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        message.generationReason?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
        if (message.dispatchAttemptedAt == null) {
            if (drafting) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick = onGenerate, enabled = !drafting && !observation.preview.isNullOrBlank() && !state.preferences.capturePaused,
                modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (draft.isBlank()) "Suggest a reply" else "Regenerate draft")
            }
            if (draft.isNotBlank()) {
                OutlinedTextField(draft, { if (it.length <= 1000) draft = it }, label = { Text("Your reply draft") }, supportingText = { Text("${draft.length} / 1,000 characters") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 8)
                Text(if (message.engine?.contains("OPENAI", ignoreCase = true) == true) "Generated with OpenAI · review facts and tone before sending."
                    else "Template draft · a deterministic on-device suggestion, not a language model.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { onReview(draft.trim()) }, enabled = canSend, modifier = Modifier.heightIn(min = 48.dp)) { Text("Review & send") }
                    OutlinedButton(onClick = { onCopy(draft) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Copy reply") }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (canTest) OutlinedButton(onClick = onTest, modifier = Modifier.heightIn(min = 48.dp)) { Text("Review test reply") }
                TextButton(onClick = { onCopy(TEST_REPLY) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Copy test") }
                TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open ${observation.app.label}") }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Dismiss") }
            }
            if (!canSend) Text("Sending needs an active supported action and a visible one-to-one sender. You can copy a draft and verify the recipient in the messaging app.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (message.dispatchAcceptedAt != null) {
            Text("Android accepted the dispatch. Receipt has not been independently verified.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (message.receiptReportedAt == null) TextButton(onClick = onReceipt, modifier = Modifier.heightIn(min = 48.dp)) { Text("Record recipient confirmation") }
            else StatusPill("Receipt reported by you")
        }
    }
}

@Composable
internal fun ReplyReviewDialog(message: MessageRecord, text: String, onDismiss: () -> Unit, onSend: () -> Unit) {
    var reviewed by rememberSaveable(message.observation.id, text) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Send this reply?") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${message.observation.app.label} · ${message.observation.sender ?: "Unknown sender"}", fontWeight = FontWeight.SemiBold)
            Text(text)
            Text("This sends a real message through the current notification action. Android acceptance is not proof of delivery.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(reviewed, { reviewed = it }, modifier = Modifier.semantics { contentDescription = "I reviewed the recipient and reply text" })
                Text("I checked the recipient and the complete reply.")
            }
        } },
        confirmButton = { TextButton(onClick = onSend, enabled = reviewed, modifier = Modifier.heightIn(min = 48.dp)) { Text("Send reply") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } })
}

@Composable
internal fun TestReplyDialog(message: MessageRecord, onDismiss: () -> Unit, onSend: () -> Unit) {
    var consent by rememberSaveable(message.observation.id) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Outlined.Send, null) },
        title = { Text("Send one test reply?") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${message.observation.app.label} · ${message.observation.sender ?: "Unknown sender"}", fontWeight = FontWeight.SemiBold)
            Text(TEST_REPLY)
            Text("This sends a real message through this notification’s reply action. Android dispatch is not proof of delivery.", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(consent, { consent = it }, modifier = Modifier.semantics { contentDescription = "This contact agreed to receive a test reply" })
                Text("This is my consenting test contact.", style = MaterialTheme.typography.bodyMedium)
            }
        } },
        confirmButton = { TextButton(onClick = onSend, enabled = consent, modifier = Modifier.heightIn(min = 48.dp)) { Text("Send test") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } })
}

@Composable
internal fun ReceiptDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Did your contact confirm receipt?") },
        text = { Text("Only record this after checking with your contact or independently viewing their device. VEIL cannot inspect delivery receipts. This will be marked “Receipt reported by you”.") },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) { Text("Yes, I checked") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Not yet") } })
}

@Composable
internal fun PriorityScreen(state: VeilUiState, viewModel: VeilViewModel) {
    var adding by remember { mutableStateOf<MessagingApp?>(null) }
    var removing by remember { mutableStateOf<PriorityContact?>(null) }
    var editing by remember { mutableStateOf<PriorityContact?>(null) }
    var binding by remember { mutableStateOf<PriorityContact?>(null) }
    ScreenBody {
        PageHeading("Priority people", "Choose who VEIL can reply to. Automatic replies start off.")
        if (!state.system.notificationAccess) {
            Text("Notification access is off. You can save people now, then verify them after setup.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 840.dp && LocalDensity.current.fontScale < 1.5f
            val whatsapp: @Composable () -> Unit = {
                PriorityPlatformSection(MessagingApp.WHATSAPP,
                    state.contacts.filter { it.app == MessagingApp.WHATSAPP },
                    state.preferences.automationEnabled, onAdd = { adding = MessagingApp.WHATSAPP },
                    onEdit = { editing = it }, onBind = { binding = it }, onRemove = { removing = it },
                    onPause = { c, paused -> viewModel.saveContact(c.copy(paused = paused)) })
            }
            val instagram: @Composable () -> Unit = {
                PriorityPlatformSection(MessagingApp.INSTAGRAM,
                    state.contacts.filter { it.app == MessagingApp.INSTAGRAM },
                    state.preferences.automationEnabled, onAdd = { adding = MessagingApp.INSTAGRAM },
                    onEdit = { editing = it }, onBind = { binding = it }, onRemove = { removing = it },
                    onPause = { c, paused -> viewModel.saveContact(c.copy(paused = paused)) })
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f)) { whatsapp() }
                    Column(Modifier.weight(1f)) { instagram() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    whatsapp()
                    instagram()
                }
            }
        }
        Text("Names, phone numbers and Instagram IDs help you recognize people. VEIL can only auto-reply after you bind the actual notification identity and confirm a test. Unsupported replies stay manual.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    editing?.let { contact -> ContactRuleDialog(contact, onDismiss = { editing = null }, onSave = {
        editing = null; viewModel.saveContact(it)
    }) }
    binding?.let { contact -> ContactBindingDialog(contact, state.messages, onDismiss = { binding = null }, onBind = { message ->
        binding = null; viewModel.bindContact(contact.id, message.observation.id)
    }) }
    adding?.let { app ->
        AddContactDialog(app, onDismiss = { adding = null }) { label, platform, sender, username, userId, number ->
            viewModel.addContact(label, platform, sender, username, userId, number)
            adding = null
        }
    }
    removing?.let { contact ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove ${contact.label}?") },
            text = { Text("This removes the local VEIL rule. It does not remove anyone from ${contact.app.label}.") },
            confirmButton = { TextButton(onClick = { viewModel.removeContact(contact.id); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PriorityPlatformSection(
    app: MessagingApp,
    contacts: List<PriorityContact>,
    globalAutomation: Boolean,
    onAdd: () -> Unit,
    onEdit: (PriorityContact) -> Unit,
    onBind: (PriorityContact) -> Unit,
    onRemove: (PriorityContact) -> Unit,
    onPause: (PriorityContact, Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (app == MessagingApp.WHATSAPP) Icons.Outlined.ChatBubbleOutline else Icons.Outlined.AlternateEmail,
                contentDescription = null, tint = colors.primary, modifier = Modifier.size(23.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleLarge)
                Text("${contacts.size} saved · ${if (app == MessagingApp.WHATSAPP) "Saved names and phone numbers" else "Usernames and account IDs"}",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            OutlinedButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Add ${app.label} person" }) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("Add")
            }
        }
        if (contacts.isEmpty()) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Text(if (app == MessagingApp.WHATSAPP) "No WhatsApp people yet." else "No Instagram people yet.",
                    modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant)
            }
        }
        contacts.forEach { contact ->
            OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)) {
                Column(Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(
                        colors.surface.copy(alpha = .89f),
                        colors.primaryContainer.copy(alpha = .42f),
                        colors.secondaryContainer.copy(alpha = .37f))))
                    .padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(42.dp).background(colors.primaryContainer.copy(alpha = .5f), CircleShape),
                            contentAlignment = Alignment.Center) {
                            Text(contact.label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium,
                                color = colors.primary)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(contact.label, style = MaterialTheme.typography.titleMedium)
                            val detail = if (app == MessagingApp.WHATSAPP) {
                                contact.whatsappPhone.ifBlank { "Number not added" }
                            } else {
                                "@${contact.instagramUsername.ifBlank { contact.sender }}${contact.instagramUserId.takeIf { it.isNotBlank() }?.let { " · ID ${it.takeLast(4).padStart(it.length, '•')}" } ?: ""}"
                            }
                            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                                maxLines = 2)
                        }
                        IconButton(onClick = { onRemove(contact) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove ${contact.label}")
                        }
                    }
                    val ready = contact.identityKey != null && contact.compatibilityVerified
                    val active = ready && contact.autoEnabled && globalAutomation && !contact.paused
                    Text(when {
                        contact.paused -> "Paused"
                        active -> "Auto-reply enabled"
                        !ready -> "Suggestions only · identity not verified"
                        contact.autoEnabled -> "Rule ready · global automation off"
                        else -> "Auto-reply off"
                    }, color = if (active) colors.primary else colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = { onEdit(contact) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit") }
                        TextButton(onClick = { onBind(contact) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (ready) "Recheck identity" else "Verify identity")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Pause", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            Switch(contact.paused, { onPause(contact, it) },
                                modifier = Modifier.semantics { contentDescription = "Pause ${contact.label}" })
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AddContactDialog(
    app: MessagingApp,
    onDismiss: () -> Unit,
    onAdd: (String, MessagingApp, String, String, String, String) -> Unit,
) {
    var name by rememberSaveable(app) { mutableStateOf("") }
    var notificationSender by rememberSaveable(app) { mutableStateOf("") }
    var username by rememberSaveable(app) { mutableStateOf("") }
    var userId by rememberSaveable(app) { mutableStateOf("") }
    var phone by rememberSaveable(app) { mutableStateOf("") }
    val hints = dev.veil.app.data.ContactReferenceHints.sanitize(app, username, userId, phone)
    val effectiveSender = notificationSender.trim().ifBlank { name.trim() }
    val valid = name.isNotBlank() && name.length <= 80 && effectiveSender.isNotBlank() &&
        hints != null && (app != MessagingApp.INSTAGRAM || hints.instagramUsername.isNotBlank())
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (app == MessagingApp.WHATSAPP) "Add WhatsApp person" else "Add Instagram person") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(80) },
                    label = { Text(if (app == MessagingApp.WHATSAPP) "Name saved on device" else "Person's display name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (app == MessagingApp.WHATSAPP) {
                    OutlinedTextField(phone, { phone = it.take(22) },
                        label = { Text("WhatsApp phone number (optional)") },
                        supportingText = { Text("Include country code, e.g. +91. Only a reference, not verified by Android.") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(), isError = hints == null)
                } else {
                    OutlinedTextField(username, { username = it.removePrefix("@").take(30) },
                        label = { Text("Instagram username") }, prefix = { Text("@") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), isError = hints == null)
                    OutlinedTextField(userId, { userId = it.take(32) },
                        label = { Text("Numeric Instagram user ID (optional)") },
                        supportingText = { Text("Saved for your reference. Instagram notifications usually do not expose this ID.") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), isError = hints == null)
                }
                OutlinedTextField(notificationSender, { notificationSender = it.take(160) },
                    label = { Text("Notification sender (if different)") },
                    supportingText = { Text("Use the exact sender shown in VEIL Inbox. Leave blank if it matches the name above.") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Auto-reply stays off until you verify this exact conversation.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = {
            onAdd(name.trim(), app, effectiveSender, hints!!.instagramUsername, hints.instagramUserId, hints.whatsappPhone)
        }) { Text("Save person") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun ActivityScreen(state: VeilUiState, onLab: () -> Unit) {
    ScreenList {
        item { PageHeading("A clear record.", "What VEIL observed, attempted, and could actually verify.") }
        if (state.activity.isEmpty()) item { EmptyState(Icons.Outlined.History, "No activity recorded yet.",
            "Notification detection and your controlled tests will appear here. Activity never treats Android dispatch as delivery.", "Start a compatibility test", onLab) }
        items(state.activity, key = { it.id }) { event ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.padding(top = 7.dp).size(7.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .65f), CircleShape))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(event.title, style = MaterialTheme.typography.titleSmall)
                    Text(event.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(listOfNotNull(event.app?.label, timeLabel(event.at)).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(7.dp)); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                }
            }
        }
    }
}
