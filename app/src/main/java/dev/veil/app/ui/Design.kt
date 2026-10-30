package dev.veil.app.ui

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import dev.veil.app.domain.Appearance
import dev.veil.app.domain.AccentPalette

private val LightColors = lightColorScheme(
    primary = Color(0xFF885563), onPrimary = Color.White,
    primaryContainer = Color(0xFFF2DDE3), onPrimaryContainer = Color(0xFF4C3039),
    secondary = Color(0xFF70626E), secondaryContainer = Color(0xFFECE4E9),
    background = Color(0xFFF7F4F1), onBackground = Color(0xFF29262B),
    surface = Color(0xFFFDFBF9), onSurface = Color(0xFF29262B),
    surfaceVariant = Color(0xFFEEE8E7), onSurfaceVariant = Color(0xFF665E63),
    outline = Color(0xFF897B81), outlineVariant = Color(0xFFDDD2D4),
    error = Color(0xFFA23845), errorContainer = Color(0xFFFFDADC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFDFB0BE), onPrimary = Color(0xFF452D36),
    primaryContainer = Color(0xFF533843), onPrimaryContainer = Color(0xFFF7DCE6),
    secondary = Color(0xFFCCBEC9), secondaryContainer = Color(0xFF423A44),
    background = Color(0xFF171619), onBackground = Color(0xFFF0E9EC),
    surface = Color(0xFF232126), onSurface = Color(0xFFF0E9EC),
    surfaceVariant = Color(0xFF302C32), onSurfaceVariant = Color(0xFFC5BAC1),
    outline = Color(0xFF9C8E98), outlineVariant = Color(0xFF494149),
    error = Color(0xFFFFB2BC), errorContainer = Color(0xFF5D2933),
)

/** Palette text pairs are checked against WCAG AA; surfaces stay neutral for readability. */
internal fun veilColorScheme(dark: Boolean, palette: AccentPalette): ColorScheme {
    val tones = when (palette) {
        AccentPalette.ROSE -> if (dark) listOf(0xFFDFB0BE, 0xFF452D36, 0xFF533843, 0xFFF7DCE6) else listOf(0xFF885563, 0xFFFFFFFF, 0xFFF2DDE3, 0xFF4C3039)
        AccentPalette.CORAL -> if (dark) listOf(0xFFE9B49F, 0xFF4B2D24, 0xFF623F33, 0xFFFFE1D5) else listOf(0xFF98523F, 0xFFFFFFFF, 0xFFF9E0D6, 0xFF603527)
        AccentPalette.PLUM -> if (dark) listOf(0xFFD1B6CC, 0xFF412E3E, 0xFF513D4F, 0xFFEFDDEB) else listOf(0xFF73566F, 0xFFFFFFFF, 0xFFECDDEB, 0xFF493246)
        AccentPalette.AMBER -> if (dark) listOf(0xFFDBC39C, 0xFF3E301C, 0xFF55452E, 0xFFEFE2C8) else listOf(0xFF785C32, 0xFFFFFFFF, 0xFFF1E3C9, 0xFF4F3B20)
        AccentPalette.SILVER -> if (dark) listOf(0xFFCBC5D2, 0xFF333039, 0xFF49454F, 0xFFE8E1EC) else listOf(0xFF62616A, 0xFFFFFFFF, 0xFFE5E2EB, 0xFF393842)
    }
    return (if (dark) DarkColors else LightColors).copy(
        primary = Color(tones[0]), onPrimary = Color(tones[1]),
        primaryContainer = Color(tones[2]), onPrimaryContainer = Color(tones[3]),
    )
}

@Composable
internal fun effectiveReducedMotion(preference: Boolean): Boolean {
    val context = rememberCoroutineScope().coroutineContext
    return preference || (context[MotionDurationScale]?.scaleFactor == 0f)
}

internal val LocalGlassIntensity = staticCompositionLocalOf { .65f }

@Composable
fun VeilTheme(appearance: Appearance = Appearance.SYSTEM, palette: AccentPalette = AccentPalette.ROSE, glassIntensity: Float = .65f, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        Appearance.DARK -> true
        Appearance.LIGHT -> false
        Appearance.SYSTEM -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalGlassIntensity provides glassIntensity.coerceIn(0f, 1f)) {
    MaterialTheme(
        colorScheme = veilColorScheme(dark, palette),
        typography = Typography(
            displaySmall = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Serif, fontSize = 38.sp, lineHeight = 44.sp),
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, lineHeight = 39.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.8).sp),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontSize = 27.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
        content = content,
    )
    }
}

/** Blurred decorative light, with a gradient fallback; this is not a scene backdrop sampler. */
@Composable
internal fun VeilBackdrop(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.background(colors.background)) {
        Canvas(Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(54.dp) else Modifier)) {
            drawCircle(Brush.radialGradient(listOf(colors.primary.copy(alpha = .12f), Color.Transparent),
                center = Offset(size.width * .98f, size.height * .1f), radius = size.width * .7f),
                radius = size.width * .7f, center = Offset(size.width * .98f, size.height * .1f))
            drawCircle(Brush.radialGradient(listOf(colors.secondary.copy(alpha = .075f), Color.Transparent),
                center = Offset(size.width * .1f, size.height * .9f), radius = size.width * .8f),
                radius = size.width * .8f, center = Offset(size.width * .1f, size.height * .9f))
        }
    }
}

@Composable
internal fun GlassPanel(modifier: Modifier = Modifier, intensity: Float = LocalGlassIntensity.current, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.shadow(10.dp, shape, ambientColor = colors.primary.copy(alpha = .07f), spotColor = colors.primary.copy(alpha = .08f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.surface.copy(alpha = .94f - intensity * .32f), colors.surfaceVariant.copy(alpha = .87f - intensity * .24f), colors.surface.copy(alpha = .90f - intensity * .20f))))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .34f), colors.outlineVariant.copy(alpha = .35f), Color.White.copy(alpha = .1f))), shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
    )
}

internal enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Outlined.Home),
    INBOX("inbox", "Inbox", Icons.Outlined.ChatBubbleOutline),
    PRIORITY("priority", "Priority", Icons.Outlined.StarOutline),
    ACTIVITY("activity", "Activity", Icons.Outlined.History),
    SETTINGS("settings", "Settings", Icons.Outlined.Tune),
}

@Composable
internal fun FloatingDock(selectedRoute: String?, reducedMotion: Boolean, intensity: Float,
    onSelect: (Destination) -> Unit, modifier: Modifier = Modifier, rail: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (rail) 32.dp else 36.dp)
    val fontScale = LocalDensity.current.fontScale
    val motionReduced = effectiveReducedMotion(reducedMotion)
    val defaultDockHeight = 51.dp + (15 * fontScale).dp
    val selectedIndex = Destination.entries.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    val surface = modifier.shadow(18.dp, shape, ambientColor = colors.primary.copy(alpha = .16f), spotColor = colors.primary.copy(alpha = .13f))
        .clip(shape)
        .background(Brush.linearGradient(listOf(colors.surface.copy(alpha = .91f - intensity * .31f), colors.surfaceVariant.copy(alpha = .86f - intensity * .31f), colors.surface.copy(alpha = .87f - intensity * .27f))))
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .76f), colors.outlineVariant.copy(alpha = .31f), Color.White.copy(alpha = .32f))), shape)
    if (rail) {
        Column(surface.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Destination.entries.forEach { item ->
                DockItem(item, item.route == selectedRoute, { onSelect(item) }, Modifier.width(78.dp).heightIn(min = defaultDockHeight)
                    .background(if (item.route == selectedRoute) colors.primaryContainer.copy(alpha = .56f) else Color.Transparent, RoundedCornerShape(27.dp)))
            }
        }
    } else {
        BoxWithConstraints(surface.padding(7.dp)) {
            val itemWidth = maxWidth / 5
            val showLabels = fontScale <= 1.4f
            val dockHeight = if (showLabels) defaultDockHeight else 62.dp
            val offset = animateDpAsState(itemWidth * selectedIndex, tween(if (motionReduced) 0 else 250), label = "dock highlight")
            if (Destination.entries.any { it.route == selectedRoute }) {
                Box(Modifier.offset { IntOffset(offset.value.roundToPx(), 0) }.width(itemWidth).height(dockHeight).clip(RoundedCornerShape(29.dp))
                    .background(Brush.verticalGradient(listOf(colors.primaryContainer.copy(alpha = .67f), colors.primaryContainer.copy(alpha = .26f))))
                    .border(1.dp, Color.White.copy(alpha = .55f), RoundedCornerShape(29.dp)))
            }
            Row { Destination.entries.forEach { item -> DockItem(item, item.route == selectedRoute, { onSelect(item) }, Modifier.weight(1f).heightIn(min = dockHeight), showLabel = showLabels) } }
        }
    }
}

@Composable
private fun DockItem(item: Destination, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.heightIn(min = 66.dp).clip(RoundedCornerShape(28.dp))
        .semantics { selected = isSelected; contentDescription = "${item.label} tab" }
        .clickable(role = Role.Tab, onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
        Icon(item.icon, contentDescription = null, tint = if (isSelected) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(23.dp))
        if (showLabel) Text(item.label, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected) colors.onSurface else colors.onSurfaceVariant)
    }
}

@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier) = Text(text.uppercase(), modifier,
    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)

@Composable
internal fun PageHeading(title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("V E I L", style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun StatusPill(text: String, warning: Boolean = false, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.clip(CircleShape).background(if (warning) colors.errorContainer.copy(alpha = .55f) else colors.primaryContainer.copy(alpha = .65f))
        .padding(horizontal = 11.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(5.dp).background(if (warning) colors.error else colors.primary, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (warning) colors.error else colors.onPrimaryContainer)
    }
}

@Composable
internal fun InfoRow(icon: ImageVector, title: String, detail: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
internal fun EmptyState(icon: ImageVector, title: String, detail: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}
