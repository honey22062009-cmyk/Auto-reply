package dev.veil.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.veil.app.domain.MessageRecord
import dev.veil.app.domain.PriorityContact

@Composable
internal fun ContactRuleDialog(contact: PriorityContact, onDismiss: () -> Unit, onSave: (PriorityContact) -> Unit) {
    var label by rememberSaveable(contact.id) { mutableStateOf(contact.label) }
    var auto by rememberSaveable(contact.id) { mutableStateOf(contact.autoEnabled) }
    var paused by rememberSaveable(contact.id) { mutableStateOf(contact.paused) }
    var cooldown by rememberSaveable(contact.id) { mutableStateOf(contact.cooldownSeconds.toString()) }
    var hour by rememberSaveable(contact.id) { mutableStateOf(contact.maxPerHour.toString()) }
    var day by rememberSaveable(contact.id) { mutableStateOf(contact.maxPerDay.toString()) }
    var conversation by rememberSaveable(contact.id) { mutableStateOf(contact.maxPerConversation.toString()) }
    var quiet by rememberSaveable(contact.id) { mutableStateOf(contact.quietEnabled) }
    var quietStart by rememberSaveable(contact.id) { mutableStateOf(contact.quietStartHour.toString()) }
    var quietEnd by rememberSaveable(contact.id) { mutableStateOf(contact.quietEndHour.toString()) }
    var instruction by rememberSaveable(contact.id) { mutableStateOf(contact.instruction) }
    var consent by rememberSaveable(contact.id) { mutableStateOf(false) }
    val eligible = contact.identityKey != null && contact.compatibilityVerified
    fun within(value: String, range: IntRange) = value.toIntOrNull()?.let { it in range } == true
    val valid = label.isNotBlank() && within(cooldown, 0..86_400) &&
        within(hour, 1..100) && within(day, 1..1000) &&
        within(conversation, 1..100) && within(quietStart, 0..23) &&
        within(quietEnd, 0..23) && (!auto || contact.autoEnabled || consent)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Rules for ${contact.label}") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(label, { if (it.length <= 80) label = it }, label = { Text("Local contact label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("${contact.app.label} · ${contact.sender}", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Automatic replies", style = MaterialTheme.typography.titleSmall)
                    Text(if (eligible) "Enables real replies under your safety rules." else "Bind stable metadata and complete a test with recipient confirmation first.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(auto, { auto = it }, enabled = eligible || auto,
                    modifier = Modifier.semantics { contentDescription = "Automatic replies for ${contact.label}" })
            }
            if (auto && !contact.autoEnabled) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(consent, { consent = it }, modifier = Modifier.semantics { contentDescription = "I authorize automatic replies to this contact" })
                Text("I authorize VEIL to send suitable replies to this contact when all rules permit.", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pause this contact", Modifier.weight(1f)); Switch(paused, { paused = it })
            }
            NumberSetting("Cooldown in seconds", cooldown, { cooldown = it }, "0–86400 seconds")
            NumberSetting("Maximum replies per hour", hour, { hour = it }, "1–100")
            NumberSetting("Maximum replies per day", day, { day = it }, "1–1000")
            NumberSetting("Maximum replies per conversation per day", conversation, { conversation = it }, "1–100")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quiet hours", Modifier.weight(1f)); Switch(quiet, { quiet = it })
            }
            if (quiet) {
                NumberSetting("Quiet hours start", quietStart, { quietStart = it }, "Hour 0–23 in the tablet's current time zone")
                NumberSetting("Quiet hours end", quietEnd, { quietEnd = it }, "Hour 0–23; a range can cross midnight")
            }
            OutlinedTextField(instruction, { if (it.length <= 1000) instruction = it }, label = { Text("Contact-specific style notes") },
                supportingText = { Text("Preferences only. These notes cannot override safety rules or supply unknown facts.") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
            Text("Sensitive or uncertain messages require review. Global safety caps of 10 automatic replies per hour and 30 per day also apply. Conversation limits reset at local midnight. Android dispatch does not prove receipt.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { TextButton(enabled = valid, onClick = {
            onSave(contact.copy(label = label.trim(), autoEnabled = auto, paused = paused,
                cooldownSeconds = cooldown.toInt(), maxPerHour = hour.toInt(), maxPerDay = day.toInt(),
                maxPerConversation = conversation.toInt(), quietEnabled = quiet, quietStartHour = quietStart.toInt(),
                quietEndHour = quietEnd.toInt(), instruction = instruction.trim()))
        }) { Text("Save contact rules") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun NumberSetting(label: String, value: String, onChange: (String) -> Unit, help: String) {
    OutlinedTextField(value, { text -> if (text.length <= 5 && text.all(Char::isDigit)) onChange(text) }, label = { Text(label) },
        supportingText = { Text(help) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth())
}

@Composable
internal fun ContactBindingDialog(contact: PriorityContact, records: List<MessageRecord>, onDismiss: () -> Unit, onBind: (MessageRecord) -> Unit) {
    var selected by remember(contact.id) { mutableStateOf<MessageRecord?>(null) }
    val candidates = records.filter { it.observation.app == contact.app && !it.observation.isGroup &&
        it.observation.identityKey != null && it.observation.sender?.equals(contact.sender, ignoreCase = true) == true }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Bind ${contact.label} to a notification") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Choose a notification you recognize from this contact. VEIL binds only stable exposed metadata; a visible name alone is insufficient.")
            if (candidates.isEmpty()) Text("No matching notification with stable identity metadata is retained. Ask this contact to send a harmless one-to-one test message. Some app versions do not expose suitable identity data; those conversations stay suggestion-only.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            candidates.take(20).forEach { message ->
                OutlinedCard(onClick = { selected = message }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected?.observation?.id == message.observation.id, onClick = { selected = message })
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(message.observation.sender ?: "Sender unavailable", style = MaterialTheme.typography.titleSmall)
                            Text(message.observation.preview.orEmpty().take(140), style = MaterialTheme.typography.bodyMedium)
                            Text(timeLabel(message.observation.postedAt), style = MaterialTheme.typography.bodySmall)
                            Text(if (message.dispatchKind == "TEST" && message.dispatchAcceptedAt != null && message.receiptReportedAt != null) "Accepted dispatch · receipt reported by you" else "Compatibility test still needed",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Text("Before enabling automation, run the fixed compatibility test and record your contact's receipt confirmation. Binding does not enable sending.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(enabled = selected != null, onClick = { selected?.let(onBind) }) { Text("Bind this notification") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
