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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.veil.app.VeilViewModel
import dev.veil.app.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun timeLabel(time: Long): String = DateTimeFormatter.ofPattern("MMM d · HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeScreen(state: VeilUiState, onPause: (Boolean) -> Unit, onLab: () -> Unit, onInbox: () -> Unit) {
    val prefs = state.preferences
    val status = when {
        prefs.capturePaused -> "Observation paused"
        prefs.enabledApps.isEmpty() -> "Choose your apps"
        !state.system.notificationAccess -> "Permission needed"
        !state.system.listenerConnected -> "Listener disconnected"
        else -> "Ready to observe"
    }
    ScreenBody {
        PageHeading("Your conversations.\nA little more breathing room.", "Private, on-device compatibility testing.")
        GlassPanel(intensity = prefs.glassIntensity, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Your companion")
                Icon(Icons.Outlined.BlurOn, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(status, style = MaterialTheme.typography.headlineMedium)
            Text("Automatic replies are off. Each test reply needs your review and confirmation.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            InfoRow(Icons.Outlined.PauseCircleOutline, "Pause everything", "Stops observation and blocks test replies.",
                trailing = { Switch(prefs.capturePaused, onPause, modifier = Modifier.semantics { contentDescription = "Pause everything" }) })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MessagingApp.entries.forEach { app -> StatusPill("${app.label} ${if (app in prefs.enabledApps) "on" else "off"}") }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            Metric("${state.messages.size}", "Recent notifications", Modifier.weight(1f))
            Metric("${state.messages.count { it.dispatchAcceptedAt != null }}", "Recent dispatches accepted", Modifier.weight(1f))
            Metric("${state.contacts.size}", "Priority contacts", Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Eyebrow("The next small step")
            Text("Know what works on your tablet.", style = MaterialTheme.typography.titleLarge)
            Text("The Compatibility Lab separates a detected notification, an available reply action, and a successful Android dispatch.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onLab, modifier = Modifier.heightIn(min = 50.dp)) {
                Icon(Icons.Outlined.Science, null, Modifier.size(20.dp)); Spacer(Modifier.width(9.dp)); Text("Open Compatibility Lab")
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
    val messages = state.messages.filter { it.status != ReplyStatus.DISMISSED }
    val clipboard = LocalClipboardManager.current
    ScreenList {
        item { PageHeading("A quiet place to review.", "A view of detected notifications, not a complete messaging inbox.") }
        item { StatusPill("Manual test mode · no automatic sends") }
        if (messages.isEmpty()) item { EmptyState(Icons.Outlined.ChatBubbleOutline, "Your first notification starts here.",
            "Enable an app and notification access, then ask a consenting contact to send a harmless message.", "Open Compatibility Lab", onLab) }
        items(messages, key = { it.observation.id }) { message ->
            NotificationCard(message, state,
                onCopy = { clipboard.setText(AnnotatedString(TEST_REPLY)) },
                onOpen = { viewModel.openConversation(message.observation.id) },
                onTest = { testMessage = message },
                onDismiss = { viewModel.dismissMessage(message.observation.id) },
                onReceipt = { receiptMessage = message })
        }
    }
    testMessage?.let { message -> TestReplyDialog(message, { testMessage = null }) { testMessage = null; viewModel.sendTest(message.observation.id) } }
    receiptMessage?.let { message -> ReceiptDialog({ receiptMessage = null }) { receiptMessage = null; viewModel.confirmReceipt(message.observation.id) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationCard(message: MessageRecord, state: VeilUiState, onCopy: () -> Unit, onOpen: () -> Unit,
    onTest: () -> Unit, onDismiss: () -> Unit, onReceipt: () -> Unit) {
    val observation = message.observation
    val isPriority = state.contacts.any { it.app == observation.app && it.sender == observation.sender }
    val canTest = PhaseOnePolicy.rejection(message, state.preferences) == null &&
        state.system.listenerConnected && state.system.notificationAccess
    GlassPanel(intensity = state.preferences.glassIntensity, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (observation.app == MessagingApp.WHATSAPP) Icons.Outlined.ChatBubbleOutline else Icons.Outlined.AlternateEmail, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(observation.sender ?: "Sender unavailable", style = MaterialTheme.typography.titleMedium)
                Text("${observation.app.label} · ${timeLabel(observation.postedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isPriority) Icon(Icons.Outlined.StarOutline, "Matches a priority label", tint = MaterialTheme.colorScheme.primary)
        }
        Text(observation.preview ?: "Message preview unavailable. Android or the messaging app may hide notification content.", style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(message.status.label, warning = message.status == ReplyStatus.FAILED)
            if (observation.isGroup) StatusPill("Group · copy only")
            if (!message.active && message.status != ReplyStatus.REMOVED) StatusPill("Action expired")
        }
        if (message.failureReason != null) Text(message.failureReason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
        Eyebrow("Fixed compatibility reply")
        Text(TEST_REPLY, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (canTest) Button(onClick = onTest, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Outlined.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("Review test reply") }
            OutlinedButton(onClick = onCopy, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("Copy test") }
            TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open ${observation.app.label}") }
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Dismiss") }
        }
        if (!canTest && message.dispatchAttemptedAt == null) {
            Text(when {
                state.preferences.capturePaused -> "Resume observation to test a fresh notification."
                observation.isGroup -> "Group replies are disabled in this prototype to avoid sending to an unintended audience."
                !state.system.listenerConnected -> "Reconnect notification access and receive a fresh test message."
                else -> "No usable active reply action. Copy the fixed test and send it manually in the app."
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (message.dispatchAcceptedAt != null) {
            Text("Android accepted the dispatch. Receipt has not been independently verified.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (message.receiptReportedAt == null) TextButton(onClick = onReceipt, modifier = Modifier.heightIn(min = 48.dp)) { Text("Record recipient confirmation") }
            else StatusPill("Receipt reported by you")
        }
    }
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
    ScreenBody {
        PageHeading("Your people, chosen by you.", "Save local contact labels. Automatic replies are not enabled in this version.")
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
                Text("Local label only · automatic replies off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            }
        }
        InfoRow(Icons.Outlined.Info, "Names can be ambiguous", "Two accounts may use the same notification name. This prototype never uses a name match to authorize sending.")
    }
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
