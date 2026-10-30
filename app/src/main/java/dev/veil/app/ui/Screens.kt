package dev.veil.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
        PageHeading("Your conversations.\nA little more breathing room.", "A quieter space for your messages and replies.")
        GlassPanel(intensity = prefs.glassIntensity, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Your companion")
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var adding by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<PriorityContact?>(null) }
    var editing by remember { mutableStateOf<PriorityContact?>(null) }
    var binding by remember { mutableStateOf<PriorityContact?>(null) }
    ScreenBody {
        PageHeading("Your people, chosen by you.", "Explicit contact rules, with a separate opt-in for automatic replies.")
        Button(onClick = { adding = true }, modifier = Modifier.heightIn(min = 50.dp)) { Icon(Icons.Outlined.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Add priority contact") }
        if (state.contacts.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Eyebrow("A deliberate circle")
                Text("Frequency never makes someone a priority.", style = MaterialTheme.typography.titleLarge)
                Text("You decide who belongs here. A saved name is a local label, not a verified account identity or permission to send messages.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.contacts.forEach { contact ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(contact.label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(contact.label, style = MaterialTheme.typography.titleMedium)
                        Text("${contact.app.label} · ${contact.sender}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { removing = contact }) { Icon(Icons.Outlined.DeleteOutline, "Remove ${contact.label}") }
                }
                StatusPill(when {
                    contact.paused -> "Contact paused"
                    contact.autoEnabled && state.preferences.automationEnabled -> "Automatic rule enabled"
                    contact.autoEnabled -> "Rule enabled · global automation off"
                    else -> "Automatic replies off"
                })
                Text(when {
                    contact.identityKey == null -> "No stable notification identity bound. A visible name alone cannot authorize automation."
                    !contact.compatibilityVerified -> "Identity bound; complete a reply test and record recipient confirmation before automation."
                    else -> "Bound notification identity · compatibility receipt reported by you"
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { editing = contact }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Contact rules") }
                    TextButton(onClick = { binding = contact }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Bind notification") }
                }
                InfoRow(Icons.Outlined.PauseCircleOutline, "Pause this contact", "Blocks this contact's automatic replies.",
                    trailing = { Switch(contact.paused, { viewModel.saveContact(contact.copy(paused = it)) },
                        modifier = Modifier.semantics { contentDescription = "Pause ${contact.label}" }) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            }
        }
        InfoRow(Icons.Outlined.Info, "Names can be ambiguous", "Two accounts may use the same notification name. Automation requires stable exposed identity metadata, a test confirmed by you, and your explicit rule; unavailable metadata means suggestions only.")
    }
    editing?.let { contact -> ContactRuleDialog(contact, onDismiss = { editing = null }, onSave = {
        editing = null; viewModel.saveContact(it)
    }) }
    binding?.let { contact -> ContactBindingDialog(contact, state.messages, onDismiss = { binding = null }, onBind = { message ->
        binding = null; viewModel.bindContact(contact.id, message.observation.id)
    }) }
    if (adding) AddContactDialog(onDismiss = { adding = false }) { label, app, sender -> viewModel.addContact(label, app, sender); adding = false }
    removing?.let { contact -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove ${contact.label}?") },
        text = { Text("This deletes the local priority label. It does not change the contact in ${contact.app.label}.") },
        confirmButton = { TextButton(onClick = { viewModel.removeContact(contact.id); removing = null }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddContactDialog(onDismiss: () -> Unit, onAdd: (String, MessagingApp, String) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    var sender by rememberSaveable { mutableStateOf("") }
    var app by rememberSaveable { mutableStateOf(MessagingApp.WHATSAPP) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("A place for your people") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(label, { if (it.length <= 80) label = it }, label = { Text("Local contact label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MessagingApp.entries.forEach { item -> FilterChip(selected = item == app, onClick = { app = item }, label = { Text(item.label) }) } }
            OutlinedTextField(sender, { if (it.length <= 120) sender = it }, label = { Text("Exact notification sender") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Use the exact name visible in Inbox. This saves a label only; it never enables automatic replies.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = { onAdd(label.trim(), app, sender.trim()) }, enabled = label.isNotBlank() && sender.isNotBlank()) { Text("Save contact") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } })
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
