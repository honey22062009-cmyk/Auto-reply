package dev.veil.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.veil.app.domain.AccentPalette

internal fun accentLabel(palette: AccentPalette): String = when (palette) {
    AccentPalette.ROSE -> "Rose"
    AccentPalette.CORAL -> "Coral"
    AccentPalette.PLUM -> "Plum"
    AccentPalette.AMBER -> "Amber"
    AccentPalette.SILVER -> "Silver"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccentSelector(selected: AccentPalette, onSelect: (AccentPalette) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AccentPalette.entries.forEach { palette ->
            FilterChip(selected = selected == palette, onClick = { onSelect(palette) },
                label = { Text(accentLabel(palette)) },
                leadingIcon = { Box(Modifier.size(14.dp).background(veilColorScheme(false, palette).primary, CircleShape)) },
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "${accentLabel(palette)} accent" })
        }
    }
}

@Composable
internal fun CompanionSettings(enabled: Boolean, onEnabledChange: (Boolean) -> Unit, reducedMotion: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        MotePortrait(Modifier.size(64.dp), reducedMotion = reducedMotion)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Meet Mote", style = MaterialTheme.typography.titleSmall)
            Text("A quiet, optional guide to your next step. Tap the companion for help; it never sends a message.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange,
            modifier = Modifier.semantics { contentDescription = "Show Mote companion" })
    }
}
