package dev.veil.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.veil.app.VeilViewModel
import dev.veil.app.ai.*
import dev.veil.app.domain.VeilUiState

@Composable
internal fun AiStudioScreen(state: VeilUiState, viewModel: VeilViewModel, onBack: () -> Unit) {
    AiStudioContent(state, viewModel::saveAiConfiguration, viewModel::saveWritingStyle,
        viewModel::saveApiKey, viewModel::deleteApiKey, viewModel::previewReply, onBack)
}

/** Callback boundary lets UI tests exercise consent and editing without invoking a provider. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiStudioContent(
    state: VeilUiState,
    onSaveConfiguration: (AiConfiguration) -> Unit,
    onSaveStyle: (WritingStyle) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onDeleteApiKey: () -> Unit,
    onPreview: (String) -> Unit,
    onBack: () -> Unit,
) {
    val saved = state.preferences.ai
    val savedStyle = state.preferences.style
    var provider by rememberSaveable(saved.provider) { mutableStateOf(saved.provider) }
    var model by rememberSaveable(saved.model) { mutableStateOf(saved.model) }
    var tokens by rememberSaveable(saved.maxOutputTokens) { mutableStateOf(saved.maxOutputTokens.toString()) }
    var consent by rememberSaveable(saved.cloudConsent) { mutableStateOf(saved.cloudConsent) }
    var includeContext by rememberSaveable(saved.includeContext) { mutableStateOf(saved.includeContext) }
    // A credential is never written to saved instance state or populated from encrypted storage.
    var apiKey by remember { mutableStateOf("") }
    var language by rememberSaveable(savedStyle.language) { mutableStateOf(savedStyle.language) }
    var warmth by rememberSaveable(savedStyle.warmth) { mutableIntStateOf(savedStyle.warmth) }
    var humor by rememberSaveable(savedStyle.humor) { mutableIntStateOf(savedStyle.humor) }
    var emoji by rememberSaveable(savedStyle.emoji) { mutableIntStateOf(savedStyle.emoji) }
    var length by rememberSaveable(savedStyle.maxCharacters) { mutableStateOf(savedStyle.maxCharacters.toString()) }
    var preferred by rememberSaveable(savedStyle.preferredPhrases) { mutableStateOf(savedStyle.preferredPhrases) }
    var avoid by rememberSaveable(savedStyle.avoidPhrases) { mutableStateOf(savedStyle.avoidPhrases) }
    var examples by rememberSaveable(savedStyle.approvedExamples) { mutableStateOf(savedStyle.approvedExamples.joinToString("\n")) }
    var incoming by rememberSaveable { mutableStateOf("") }
    val tokenCount = tokens.toIntOrNull()
    val characterCount = length.toIntOrNull()
    val examplesList = examples.lines().map(String::trim).filter(String::isNotEmpty)
    val configValid = provider == AiProvider.TEMPLATE ||
        (model.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,79}")) && tokenCount != null && tokenCount in 128..1024)
    val styleValid = characterCount != null && characterCount in 40..1000 && preferred.length <= 512 &&
        avoid.length <= 512 && examplesList.size <= 5 && examplesList.all { it.length <= 500 }
    val effectiveModel = model.trim().takeIf { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,79}")) } ?: AiConfiguration().model
    val configuration = AiConfiguration(provider, effectiveModel, (tokenCount ?: 256).coerceIn(128, 1024),
        cloudConsent = provider == AiProvider.OPENAI && consent,
        includeContext = provider == AiProvider.OPENAI && includeContext)
    val writingStyle = WritingStyle(language, warmth, humor, emoji, characterCount ?: 240,
        preferred.trim(), avoid.trim(), examplesList)
    val unsaved = configuration != saved || writingStyle != savedStyle
    val cloudReady = saved.provider != AiProvider.OPENAI || (saved.cloudConsent && state.hasApiKey)
    val context = LocalContext.current

    ScreenBody {
        PageHeading("A voice that feels like you.", "Choose a reply engine, shape your style, and preview before you send.",
            action = { IconButton(onClick = onBack) { Icon(Icons.Outlined.Close, "Close AI Studio") } })
        GlassPanel(Modifier.fillMaxWidth()) {
            Eyebrow("Reply engine")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                FilterChip(provider == AiProvider.TEMPLATE, { provider = AiProvider.TEMPLATE },
                    label = { Text("On-device templates") }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(provider == AiProvider.OPENAI, { provider = AiProvider.OPENAI },
                    label = { Text("OpenAI API") }, modifier = Modifier.heightIn(min = 48.dp))
            }
            Text(if (provider == AiProvider.TEMPLATE)
                "Simple, deterministic suggestions without a network connection. This engine is a template system, not an on-device language model."
                else "Optional cloud generation using your own OpenAI API key. Selected message content leaves this device only after you enable cloud generation.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (provider == AiProvider.OPENAI) {
                OutlinedTextField(model, { model = it.take(80) }, label = { Text("OpenAI model") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), isError = !model.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,79}")))
                OutlinedTextField(tokens, { tokens = it.filter(Char::isDigit).take(4) },
                    label = { Text("Output token limit (128–1024)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), isError = tokenCount == null || tokenCount !in 128..1024)
                ConsentRow(consent, { consent = it }, "Allow OpenAI cloud generation",
                    "I allow selected message text, optional retained context, and supplied style examples to be sent to OpenAI.")
                ConsentRow(includeContext, { includeContext = it }, "Include limited conversation context",
                    "When available, include a small retained notification-derived context window. VEIL does not have the full conversation.")
                Text("OpenAI API usage is billed separately. A ChatGPT subscription is not an API key. Model access and pricing depend on your API account; VEIL does not promise free generation or estimate costs from unverified rates.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { onSaveConfiguration(configuration) }, enabled = configValid,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Save AI settings") }
        }
        if (provider == AiProvider.OPENAI) GlassPanel(Modifier.fillMaxWidth()) {
            Eyebrow("Your API key")
            StatusPill(if (state.hasApiKey) "Key stored securely" else "No API key stored", warning = !state.hasApiKey)
            Text("The key uses Android Keystore-backed encryption and is never displayed again. This field stays blank after saving.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(apiKey, { apiKey = it.take(512) }, label = { Text("OpenAI API key") },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "OpenAI API key" })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { val value = apiKey.trim(); apiKey = ""; onSaveApiKey(value) },
                    enabled = apiKey.trim().length in 12..512 && apiKey.trim().all { it.code in 33..126 }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Save key") }
                TextButton(onClick = { apiKey = ""; onDeleteApiKey() }, enabled = state.hasApiKey,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete stored key") }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Eyebrow("Writing style")
            Text("A few thoughtful preferences.", style = MaterialTheme.typography.titleLarge)
            Text("Your examples guide drafts; they do not teach VEIL your complete personality. Review a preview whenever you change your style.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ReplyLanguage.entries.forEach { choice ->
                    FilterChip(language == choice, { language = choice },
                        label = { Text(when (choice) { ReplyLanguage.ENGLISH -> "English"; ReplyLanguage.HINDI -> "Hindi"; ReplyLanguage.HINGLISH -> "Hinglish" }) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            StyleSlider("Warmth", warmth) { warmth = it }
            StyleSlider("Humor", humor) { humor = it }
            StyleSlider("Emoji frequency", emoji) { emoji = it }
            OutlinedTextField(length, { length = it.filter(Char::isDigit).take(4) }, label = { Text("Maximum reply characters (40–1000)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                isError = characterCount == null || characterCount !in 40..1000, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(preferred, { preferred = it.take(512) }, label = { Text("Preferred phrases") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
            OutlinedTextField(avoid, { avoid = it.take(512) }, label = { Text("Phrases to avoid") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
            OutlinedTextField(examples, { examples = it.take(2505) }, label = { Text("Approved writing examples") },
                supportingText = { Text("Up to 5 examples, one per line, 500 characters each. Supply only text you choose to use.") },
                modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 7,
                isError = examplesList.size > 5 || examplesList.any { it.length > 500 })
            Text("Style preferences and examples are stored locally. If you enable the OpenAI engine, up to five approved examples and selected preferences are included in your generation request.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onSaveStyle(writingStyle) }, enabled = styleValid,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Save writing style") }
        }
        GlassPanel(Modifier.fillMaxWidth()) {
            Eyebrow("Try your voice")
            Text("Preview a reply", style = MaterialTheme.typography.titleLarge)
            Text("Previews use your saved settings and never send a message. Enter a harmless sample without passwords, verification codes, or private information.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(incoming, { incoming = it.take(2000) }, label = { Text("A sample incoming message") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
            if (unsaved) Text("Save your AI settings and writing style before previewing.",
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            if (!cloudReady) Text("To use the saved OpenAI engine, store your key and enable cloud generation first.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onPreview(incoming.trim()) },
                enabled = incoming.isNotBlank() && !state.previewBusy && !unsaved && cloudReady,
                modifier = Modifier.heightIn(min = 48.dp)) {
                if (state.previewBusy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
                Text(if (state.previewBusy) "Generating preview…" else "Preview reply")
            }
            state.previewDraft?.let { draft ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                Text("Draft preview", style = MaterialTheme.typography.labelLarge)
                Text(draft, style = MaterialTheme.typography.bodyLarge)
                Text("Review facts and tone yourself. Generating a preview does not authorize automatic replies.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("VEIL draft preview", draft))
                }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Copy preview") }
            }
        }
    }
}

@Composable
private fun StyleSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text("$value%", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
        Slider(value / 100f, { onChange((it * 100).toInt().coerceIn(0, 100)) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
    }
}

@Composable
private fun ConsentRow(checked: Boolean, onChange: (Boolean) -> Unit, description: String, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked, onChange, Modifier.semantics { contentDescription = description })
        Column(Modifier.weight(1f).padding(top = 7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(description, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
