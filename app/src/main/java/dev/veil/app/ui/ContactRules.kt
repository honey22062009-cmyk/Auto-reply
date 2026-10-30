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
    var sender by rememberSaveable(contact.id) { mutableStateOf(contact.sender) }
    var username by rememberSaveable(contact.id) { mutableStateOf(contact.instagramUsername) }
    var userId by rememberSaveable(contact.id) { mutableStateOf(contact.instagramUserId) }
    var phone by rememberSaveable(contact.id) { mutableStateOf(contact.whatsappPhone) }
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
    var advanced by rememberSaveable(contact.id) { mutableStateOf(false) }
    var consent by rememberSaveable(contact.id) { mutableStateOf(false) }
    val hints = dev.veil.app.data.ContactReferenceHints.sanitize(contact.app, username, userId, phone)
    val identityChanged = sender.trim() != contact.sender ||
        hints?.instagramUsername != contact.instagramUsername ||
        hints?.instagramUserId != contact.instagramUserId ||
        hints?.whatsappPhone != contact.whatsappPhone
    val eligible = contact.identityKey != null && contact.compatibilityVerified && !identityChanged
    val autoToSave = auto && eligible
    fun within(value: String, range: IntRange) = value.toIntOrNull()?.let { it in range } == true
    val valid = label.isNotBlank() && label.length <= 80 &&
        sender.isNotBlank() && sender.length <= 160 && hints != null &&
        within(cooldown, 0..86_400) && within(hour, 1..100) &&
        within(day, 1..1000) && within(conversation, 1..100) &&
        within(quietStart, 0..23) && within(quietEnd, 0..23) &&
        (!autoToSave || contact.autoEnabled || consent)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit ${contact.label}") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            OutlinedTextField(label, { label = it.take(80) }, label = {
                Text(if (contact.app == dev.veil.app.domain.MessagingApp.WHATSAPP) "Name saved on device" else "Person's display name")
            }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(sender, { sender = it.take(160) }, label = { Text("Exact notification sender") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            if (contact.app == dev.veil.app.domain.MessagingApp.WHATSAPP) {
                OutlinedTextField(phone, { phone = it.take(22) }, label = { Text("Phone number (optional)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = hints == null, modifier = Modifier.fillMaxWidth())
            } else {
                OutlinedTextField(username, { username = it.take(31) }, label = { Text("Instagram username") },
                    singleLine = true, isError = hints == null, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(userId, { userId = it.take(32) }, label = { Text("Numeric user ID (optional)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = hints == null, modifier = Modifier.fillMaxWidth())
            }
            if (identityChanged) {
                Text("Changing the sender or account details turns off auto-reply and requires a new notification binding.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Automatic replies", style = MaterialTheme.typography.titleSmall)
                    Text(if (eligible) "Only after your safety rules pass." else "Verify a notification and a consenting test reply first.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(autoToSave, { auto = it }, enabled = eligible,
                    modifier = Modifier.semantics { contentDescription = "Automatic replies for ${contact.label}" })
            }
            if (autoToSave && !contact.autoEnabled) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(consent, { consent = it }, modifier = Modifier.semantics {
                    contentDescription = "I authorize automatic replies to this contact"
                })
                Text("Allow suitable, verified automatic replies for this person.", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pause this person", Modifier.weight(1f))
                Switch(paused, { paused = it })
            }
            TextButton(onClick = { advanced = !advanced }) {
                Text(if (advanced) "Hide advanced rules" else "Advanced rules")
            }
            if (advanced) {
                NumberSetting("Cooldown (seconds)", cooldown, { cooldown = it }, "0–86400")
                NumberSetting("Maximum replies/hour", hour, { hour = it }, "1–100")
                NumberSetting("Maximum replies/day", day, { day = it }, "1–1000")
                NumberSetting("Maximum per conversation/day", conversation, { conversation = it }, "1–100")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Quiet hours", Modifier.weight(1f))
                    Switch(quiet, { quiet = it })
                }
                if (quiet) {
                    NumberSetting("Quiet hours start", quietStart, { quietStart = it }, "0–23, device local time")
                    NumberSetting("Quiet hours end", quietEnd, { quietEnd = it }, "0–23, can cross midnight")
                }
                OutlinedTextField(instruction, { instruction = it.take(1000) },
                    label = { Text("Writing-style notes") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                Text("Sensitive or uncertain messages always need review. Global caps also apply. Android dispatch does not prove receipt.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } },
        confirmButton = { TextButton(enabled = valid, onClick = {
            onSave(contact.copy(label = label.trim(), sender = sender.trim(),
                instagramUsername = hints!!.instagramUsername, instagramUserId = hints.instagramUserId,
                whatsappPhone = hints.whatsappPhone, autoEnabled = autoToSave, paused = paused,
                cooldownSeconds = cooldown.toInt(), maxPerHour = hour.toInt(), maxPerDay = day.toInt(),
                maxPerConversation = conversation.toInt(), quietEnabled = quiet, quietStartHour = quietStart.toInt(),
                quietEndHour = quietEnd.toInt(), instruction = instruction.trim()))
        }) { Text("Save") } },
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
