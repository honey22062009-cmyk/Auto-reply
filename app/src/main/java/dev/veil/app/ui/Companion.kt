package dev.veil.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.veil.app.domain.VeilUiState

internal data class CompanionTip(val title: String, val detail: String, val route: String)

/** Intentional, local guidance. It does not read message text or make AI/provider calls. */
internal fun companionTips(state: VeilUiState): List<CompanionTip> = buildList {
    when {
        !state.system.notificationAccess -> add(CompanionTip("Connect notification access",
            "The Compatibility Lab explains the permission and checks what Android makes available.", "lab"))
        state.preferences.capturePaused -> add(CompanionTip("You're taking a pause",
            "Observation is paused. Review the controls on Home whenever you want to resume.", "home"))
        state.preferences.enabledApps.isEmpty() -> add(CompanionTip("Choose your messaging apps",
            "Enable only the platforms you want VEIL to observe in Settings.", "settings"))
        state.messages.isEmpty() -> add(CompanionTip("Start with one harmless message",
            "Use the Lab's step-by-step check with a consenting test contact. Capabilities vary by app version.", "lab"))
        else -> add(CompanionTip("Give your Inbox a moment",
            "Review the latest notifications and available reply options. Notification access is only a partial view.", "inbox"))
    }
    add(CompanionTip("Make VEIL sound like you",
        "Choose your reply engine and explicitly supplied writing preferences. You stay in control of what leaves the device.", "ai"))
    add(CompanionTip("Be deliberate with priority contacts",
        "Check the platform, sender, and per-contact controls. A displayed name alone is not proof of identity.", "priority"))
    add(CompanionTip("Make yourself at home",
        "Pick a color, tune the glass, or hide me. Reduced motion keeps the experience quiet.", "settings"))
}

/** A quiet, optional guide. Its only animation is a short response to the owner's tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompanionDock(
    state: VeilUiState,
    onNavigate: (String) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.preferences.companionEnabled) return
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    val intensity = LocalGlassIntensity.current
    Box(modifier.size(72.dp)
        .shadow(12.dp, shape, ambientColor = colors.primary.copy(alpha = .13f), spotColor = colors.primary.copy(alpha = .17f))
        .clip(shape)
        .background(Brush.linearGradient(listOf(colors.surface.copy(alpha = .98f - intensity * .15f), colors.primaryContainer.copy(alpha = .94f - intensity * .18f))))
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .6f), colors.outlineVariant.copy(alpha = .35f))), shape)
        .semantics { contentDescription = "Mote, your VEIL guide" }
        .clickable(role = Role.Button, onClickLabel = "Open guide") { open = true },
        contentAlignment = Alignment.Center) {
        MotePortrait(Modifier.size(60.dp), awake = open, reducedMotion = state.preferences.reduceMotion)
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false },
            containerColor = colors.surface, tonalElevation = 0.dp) {
            Column(Modifier.fillMaxWidth().widthIn(max = 760.dp).align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    MotePortrait(Modifier.size(72.dp), awake = true, reducedMotion = state.preferences.reduceMotion)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("A little guidance from Mote", style = MaterialTheme.typography.titleLarge)
                        Text("Your on-device companion", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { open = false }) { Icon(Icons.Outlined.Close, "Close Mote guide") }
                }
                Text("I help you find the next useful step. I don't send messages, read your conversations, or contact an AI provider.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                companionTips(state).forEachIndexed { index, tip ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (index == 0) Eyebrow("A good place to start")
                        Text(tip.title, style = MaterialTheme.typography.titleSmall)
                        Text(tip.detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        TextButton(onClick = { open = false; onNavigate(tip.route) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(when (tip.route) { "lab" -> "Open Compatibility Lab"; "ai" -> "Open reply settings"; "priority" -> "Open Priority"; "inbox" -> "Open Inbox"; "home" -> "Open Home"; else -> "Open Settings" })
                            Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(18.dp))
                        }
                        if (index < companionTips(state).lastIndex) HorizontalDivider(color = colors.outlineVariant.copy(alpha = .5f))
                    }
                }
                TextButton(onClick = { open = false; onHide() }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Hide Mote · bring back in Settings")
                }
            }
        }
    }
}

/** Original vector artwork: pearl wings, a small face, and restrained highlights. No image SDK. */
@Composable
internal fun MotePortrait(modifier: Modifier = Modifier, awake: Boolean = false, reducedMotion: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val amount by animateFloatAsState(if (awake) 1f else 0f,
        animationSpec = tween(if (effectiveReducedMotion(reducedMotion)) 0 else 240), label = "Mote greeting")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val lift = h * .04f * amount
        val left = Path().apply {
            moveTo(w * .48f, h * .46f)
            cubicTo(w * .27f, h * (.15f - .03f * amount), w * .06f, h * .28f - lift, w * .13f, h * .48f)
            cubicTo(w * .05f, h * .67f, w * .32f, h * .82f, w * .47f, h * .61f)
            close()
        }
        val right = Path().apply {
            moveTo(w * .52f, h * .46f)
            cubicTo(w * .73f, h * (.15f - .03f * amount), w * .94f, h * .28f - lift, w * .87f, h * .48f)
            cubicTo(w * .95f, h * .67f, w * .68f, h * .82f, w * .53f, h * .61f)
            close()
        }
        val wing = Brush.linearGradient(listOf(colors.primaryContainer.copy(alpha = .98f), colors.surface.copy(alpha = .96f), colors.primary.copy(alpha = .3f)))
        listOf(left, right).forEach { path ->
            drawPath(path, wing)
            drawPath(path, colors.primary.copy(alpha = .45f), style = Stroke(width = w * .012f))
        }
        drawOval(Brush.verticalGradient(listOf(colors.surface, colors.primaryContainer)),
            topLeft = Offset(w * .35f, h * .35f), size = Size(w * .30f, h * .38f))
        drawOval(colors.primary.copy(alpha = .45f), Offset(w * .35f, h * .35f), Size(w * .30f, h * .38f), style = Stroke(w * .012f))
        val eyes = colors.onSurface
        drawCircle(eyes, w * .025f, Offset(w * .445f, h * .49f))
        drawCircle(eyes, w * .025f, Offset(w * .555f, h * .49f))
        drawCircle(colors.surface, w * .009f, Offset(w * .439f, h * .482f))
        drawCircle(colors.surface, w * .009f, Offset(w * .549f, h * .482f))
        val smile = Path().apply { moveTo(w * .47f, h * .57f); quadraticTo(w * .5f, h * .60f, w * .53f, h * .57f) }
        drawPath(smile, eyes.copy(alpha = .65f), style = Stroke(w * .012f, cap = StrokeCap.Round))
        val antenna = Path().apply {
            moveTo(w * .44f, h * .36f); quadraticTo(w * .4f, h * .2f, w * .33f, h * .22f)
            moveTo(w * .56f, h * .36f); quadraticTo(w * .6f, h * .2f, w * .67f, h * .22f)
        }
        drawPath(antenna, colors.primary.copy(alpha = .75f), style = Stroke(w * .015f, cap = StrokeCap.Round))
        drawCircle(colors.primary, w * .025f, Offset(w * .33f, h * .22f))
        drawCircle(colors.primary, w * .025f, Offset(w * .67f, h * .22f))
        drawOval(Color.White.copy(alpha = .6f), Offset(w * .18f, h * .31f - lift), Size(w * .12f, h * .05f))
    }
}
