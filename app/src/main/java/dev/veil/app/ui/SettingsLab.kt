package dev.veil.app.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.veil.app.BuildConfig
import dev.veil.app.VeilViewModel
import dev.veil.app.domain.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(state: VeilUiState, viewModel: VeilViewModel, onLab: () -> Unit) {
    val preferences = state.preferences
    val notificationSettings = notificationSettingsAction()
    var deleting by rememberSaveable { mutableStateOf(false) }
    var glass by remember(preferences.glassIntensity) { mutableFloatStateOf(preferences.glassIntensity) }
    ScreenBody {
        PageHeading("Make room for your preferences.", "Your device. Your data. Your decisions.")
        SettingsSection("Appearance") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Appearance.entries.forEach { choice ->
                    FilterChip(selected = preferences.appearance == choice, onClick = { viewModel.setAppearance(choice) },
                        label = { Text(choice.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            InfoRow(Icons.Outlined.MotionPhotosOff, "Reduce motion", "Quiet transitions and immediate dock selection.",
                trailing = { Switch(preferences.reduceMotion, viewModel::setReduceMotion,
                    modifier = Modifier.semantics { contentDescription = "Reduce motion" }) })
            Text("Glass intensity", style = MaterialTheme.typography.titleSmall)
            Slider(value = glass, onValueChange = { glass = it }, onValueChangeFinished = { viewModel.setGlassIntensity(glass) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Glass intensity" })
            Text("Layered translucency, soft edges, and decorative light. ${if (Build.VERSION.SDK_INT >= 31) "Supported background artwork uses blur." else "This Android version uses a tonal fallback."} This is a glass approximation, not system-level backdrop sampling.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsSection("Notification access") {
            MessagingApp.entries.forEach { app -> PlatformToggle(app, app in preferences.enabledApps,
                { enabled -> viewModel.setPlatformEnabled(app, enabled) }, state.system) }
            StatusPill(if (state.system.notificationAccess) "Android access enabled" else "Android access disabled", warning = !state.system.notificationAccess)
            Text("Access is granted by Android. VEIL only inspects enabled platforms. Disabling an app here stops new observations; retained history stays until it expires or you delete it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = notificationSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Manage notification access") }
            TextButton(onClick = onLab, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open Compatibility Lab"); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(17.dp)) }
        }
        SettingsSection("Reply engine") {
            InfoRow(Icons.Outlined.PhoneAndroid, "Manual template · on device", "A fixed, recognizable compatibility test. No AI model or cloud provider is connected.")
            Text(TEST_REPLY, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Personalized generation and automatic replies belong to later milestones. Every send in this build requires your confirmation.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsSection("Privacy & storage") {
            InfoRow(Icons.Outlined.Lock, "Local and encrypted", "Stored names and message previews use a Keystore-backed key. The app has no network permission, analytics, or advertising.")
            Text("History retention", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1, 3, 7).forEach { days -> FilterChip(selected = preferences.retentionDays == days,
                    onClick = { viewModel.setRetentionDays(days) }, label = { Text("$days ${if (days == 1) "day" else "days"}") },
                    modifier = Modifier.heightIn(min = 48.dp)) }
            }
            Text("Expired data is removed when VEIL runs or processes notifications. Up to 500 observations and 1,000 activity events are retained. Saved contact labels remain until you remove them.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { deleting = true }, modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete notification history") }
        }
        SettingsSection("About VEIL") {
            Text("Version ${BuildConfig.VERSION_NAME} · Compatibility build", style = MaterialTheme.typography.titleSmall)
            Text("A standalone private Android application. No WhatsApp, Instagram, or ChatGPT account credentials are requested. Notification access cannot reveal your complete conversation history.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Compatibility with your installed messaging apps must be tested on this device. Android dispatch acceptance never proves that the recipient received a message.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete notification history?") },
        text = { Text("This removes retained messages and activity and clears live reply handles. Saved contact labels stay. New notifications can appear while observation is enabled; pause first if you want observation to stop.") },
        confirmButton = { TextButton(onClick = { deleting = false; viewModel.deleteHistory() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete history") } },
        dismissButton = { TextButton(onClick = { deleting = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } })
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Eyebrow(title)
        content()
        Spacer(Modifier.height(5.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompatibilityLab(state: VeilUiState, viewModel: VeilViewModel, onBack: () -> Unit, onExport: () -> Unit, onInbox: () -> Unit) {
    val notificationSettings = notificationSettingsAction()
    ScreenBody {
        PageHeading("Compatibility Lab", "Evidence from this device, one step at a time.",
            action = { IconButton(onClick = onBack) { Icon(Icons.Outlined.Close, "Close Compatibility Lab") } })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(if (state.system.notificationAccess) "Notification access enabled" else "Notification access disabled", warning = !state.system.notificationAccess)
            StatusPill(if (state.system.listenerConnected) "Listener connected" else "Listener disconnected", warning = !state.system.listenerConnected)
            if (state.preferences.capturePaused) StatusPill("Observation paused", warning = true)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = notificationSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Notification settings") }
            TextButton(onClick = viewModel::refreshSystemState, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Refresh checks") }
        }
        Text("Notification detected → reply action available → dispatch attempted → recipient receipt. These are separate outcomes. VEIL can inspect the first three; receipt must be checked with your contact.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MessagingApp.entries.forEach { app ->
            PlatformEvidence(app, state, onInbox)
        }
        Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Eyebrow("A controlled test")
            WorkflowStep("01", "Choose a consenting contact", "Use a one-to-one conversation and agree to exchange a harmless test. Group replies are disabled in this build.")
            WorkflowStep("02", "Receive a fresh message", "Enable the app in Settings and allow notification previews. Ask your contact to message while the conversation is not open in the messaging app.")
            WorkflowStep("03", "Review in Inbox", "Check the sender and available action. Review the fixed text and confirm consent before sending. If no action is available, use Copy test and open the app.")
            WorkflowStep("04", "Check receipt separately", "Ask your contact whether it arrived. Record their confirmation in Inbox. That becomes a user-reported result, never an independently verified receipt.")
            Button(onClick = onInbox, modifier = Modifier.heightIn(min = 50.dp)) { Text("Review notifications"); Spacer(Modifier.width(9.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(18.dp)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Eyebrow("Device profile")
            Text(state.system.device.model.ifBlank { "Device details unavailable" }, style = MaterialTheme.typography.titleLarge)
            EvidenceRow("Android", if (state.system.device.sdk > 0) "${state.system.device.androidVersion} · API ${state.system.device.sdk}" else "Not available")
            EvidenceRow("Memory", if (state.system.device.totalRamMb > 0) "${state.system.device.totalRamMb} MB total · ${state.system.device.availableRamMb} MB currently available" else "Not available")
            EvidenceRow("Low-memory device flag", if (state.system.device.lowRam) "Reported by Android" else "Not reported")
            Text("This is a runtime capability snapshot, not a performance benchmark. No on-device language model is bundled.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Eyebrow("Share the evidence")
            Text("Export a sanitized report", style = MaterialTheme.typography.titleLarge)
            Text("Includes device and app versions, capability flags, observation counts, and test timestamps. Excludes message text, sender names, contacts, and secrets.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onExport, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Save diagnostic report") }
        }
    }
}

@Composable
private fun PlatformEvidence(app: MessagingApp, state: VeilUiState, onInbox: () -> Unit) {
    val capability = state.system.apps.firstOrNull { it.app == app }
    val records = state.messages.filter { it.observation.app == app }
    val latest = records.maxByOrNull { it.observation.postedAt }
    val attempted = records.filter { it.dispatchAttemptedAt != null }.maxByOrNull { it.dispatchAttemptedAt!! }
    val acceptedAt = records.mapNotNull { it.dispatchAcceptedAt }.maxOrNull()
    val receiptAt = records.mapNotNull { it.receiptReportedAt }.maxOrNull()
    GlassPanel(modifier = Modifier.fillMaxWidth(), intensity = state.preferences.glassIntensity) {
        Text(app.label, style = MaterialTheme.typography.titleLarge)
        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        EvidenceRow("Application", if (capability?.installed == true) "Installed · ${capability.version ?: "version unknown"}" else "Not detected")
        EvidenceRow("Observation", if (app in state.preferences.enabledApps) "Enabled by you" else "Disabled — enable in Settings")
        EvidenceRow("Notifications detected", if (records.isEmpty()) "None retained yet" else "${records.size} in this view")
        EvidenceRow("Latest notification", latest?.let { timeLabel(it.observation.postedAt) } ?: "Not observed")
        EvidenceRow("Message preview", when { latest == null -> "Not observed"; latest.observation.preview.isNullOrBlank() -> "Unavailable"; else -> "Accessible in latest notification" })
        EvidenceRow("Sender identification", when { latest == null -> "Not observed"; latest.observation.sender.isNullOrBlank() -> "Unavailable"; else -> "Visible name available · identity not verified" })
        EvidenceRow("Reply action", when { latest == null -> "Not observed"; !latest.observation.hasReplyAction -> "No supported action detected"; !latest.active -> "Previously detected · no longer active"; else -> "Detected · rechecked before dispatch" })
        EvidenceRow("Test dispatch attempted", attempted?.dispatchAttemptedAt?.let(::timeLabel) ?: "Not attempted")
        EvidenceRow("Latest dispatch result", when {
            attempted == null -> "No test attempt"
            attempted.dispatchAcceptedAt != null -> "Accepted by Android · receipt unverified"
            attempted.status == ReplyStatus.FAILED -> "Failed · no automatic retry"
            else -> "Outcome unknown · do not repeat this test"
        })
        EvidenceRow("Last accepted dispatch", acceptedAt?.let(::timeLabel) ?: "None recorded")
        EvidenceRow("Recipient confirmation", receiptAt?.let { "User-reported · ${timeLabel(it)}" } ?: "Not reported")
        EvidenceRow("Independent receipt verification", "Unavailable")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
        Text(if (app == MessagingApp.INSTAGRAM)
            "Instagram may not expose a supported direct-reply action. Personal-account DM access is not provided by this app. Copy and open Instagram when no action is available."
            else "WhatsApp actions depend on its installed version, notification state, and preview settings. This targets the official personal Android app, not the WhatsApp Business Platform.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (records.isNotEmpty()) TextButton(onClick = onInbox, modifier = Modifier.heightIn(min = 48.dp)) { Text("Review ${app.label} notifications") }
    }
}

@Composable
private fun EvidenceRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WorkflowStep(number: String, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(17.dp), verticalAlignment = Alignment.Top) {
        Text(number, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
