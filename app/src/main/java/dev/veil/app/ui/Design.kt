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
import androidx.compose.ui.graphics.luminance
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
    primary = Color(0xFFA23F39), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0D9), onPrimaryContainer = Color(0xFF652B29),
    secondary = Color(0xFF915435), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE9D4), onSecondaryContainer = Color(0xFF5A361F),
    tertiary = Color(0xFF956A35), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBC6), onTertiaryContainer = Color(0xFF573B1F),
    background = Color(0xFFFFF9F5), onBackground = Color(0xFF302A29),
    surface = Color(0xFFFFFEFC), onSurface = Color(0xFF302A29),
    surfaceVariant = Color(0xFFF6EBE4), onSurfaceVariant = Color(0xFF675A55),
    outline = Color(0xFF88746C), outlineVariant = Color(0xFFE2D0C6),
    error = Color(0xFFA63340), errorContainer = Color(0xFFFFDDE1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB7AA), onPrimary = Color(0xFF552820),
    primaryContainer = Color(0xFF69372F), onPrimaryContainer = Color(0xFFFFE3DA),
    secondary = Color(0xFFF2C29D), onSecondary = Color(0xFF4A2E1D),
    secondaryContainer = Color(0xFF5A3D2B), onSecondaryContainer = Color(0xFFFFE7D3),
    tertiary = Color(0xFFE8C795), onTertiary = Color(0xFF4D341E),
    tertiaryContainer = Color(0xFF59442C), onTertiaryContainer = Color(0xFFFFECCB),
    background = Color(0xFF1F1A1B), onBackground = Color(0xFFFFF1EA),
    surface = Color(0xFF2B2323), onSurface = Color(0xFFFFF1EA),
    surfaceVariant = Color(0xFF3D3030), onSurfaceVariant = Color(0xFFDFC9BE),
    outline = Color(0xFFC2A89B), outlineVariant = Color(0xFF5A4541),
    error = Color(0xFFFFB3BF), errorContainer = Color(0xFF682A36),
)

/** Warm, editorial color systems. Avoid the conventional neon purple/blue AI aesthetic. */
internal fun veilColorScheme(dark: Boolean, palette: AccentPalette): ColorScheme {
    val tones = when (palette) {
        AccentPalette.ROSE -> if (dark) listOf(0xFFFFB4BF, 0xFF55282F, 0xFF67333C, 0xFFFFE3E8)
            else listOf(0xFFA13E57, 0xFFFFFFFF, 0xFFFFDEE5, 0xFF692839)
        AccentPalette.CORAL -> if (dark) listOf(0xFFFFB39F, 0xFF54281F, 0xFF683B2F, 0xFFFFE4D9)
            else listOf(0xFFA24A34, 0xFFFFFFFF, 0xFFFFE1D7, 0xFF6F3527)
        // Preserve previously stored enum names without introducing purple-tinted UI.
        AccentPalette.PLUM -> if (dark) listOf(0xFFFFBBC0, 0xFF57282F, 0xFF68343B, 0xFFFFE1E5)
            else listOf(0xFF893B43, 0xFFFFFFFF, 0xFFFCE0E1, 0xFF622A32)
        AccentPalette.AMBER -> if (dark) listOf(0xFFE8CA98, 0xFF46311C, 0xFF59442B, 0xFFFFECCB)
            else listOf(0xFF7A542A, 0xFFFFFFFF, 0xFFFFEDCB, 0xFF543A1F)
        AccentPalette.SILVER -> if (dark) listOf(0xFFD9C9BE, 0xFF40312B, 0xFF584841, 0xFFFFE9DB)
            else listOf(0xFF69564C, 0xFFFFFFFF, 0xFFEFE5DF, 0xFF483A33)
        AccentPalette.SUNSET -> if (dark) listOf(0xFFFFB7AA, 0xFF552820, 0xFF69372F, 0xFFFFE3DA)
            else listOf(0xFFA23F39, 0xFFFFFFFF, 0xFFFFE0D9, 0xFF652B29)
        AccentPalette.BLOSSOM -> if (dark) listOf(0xFFFFB7C6, 0xFF582932, 0xFF703744, 0xFFFFDFE7)
            else listOf(0xFFA3415F, 0xFFFFFFFF, 0xFFFFDFE8, 0xFF69293E)
        AccentPalette.CHAMPAGNE -> if (dark) listOf(0xFFE8CA98, 0xFF46311C, 0xFF59442B, 0xFFFFECCB)
            else listOf(0xFF7A542A, 0xFFFFFFFF, 0xFFFFEDCB, 0xFF543A1F)
    }
    val base = if (dark) DarkColors else LightColors
    val second = when (palette) {
        AccentPalette.BLOSSOM, AccentPalette.ROSE -> if (dark) Color(0xFFF4BFA0) else Color(0xFF935039)
        AccentPalette.CHAMPAGNE, AccentPalette.AMBER -> if (dark) Color(0xFFFFC4A2) else Color(0xFF995638)
        else -> base.secondary
    }
    val secondContainer = when (palette) {
        AccentPalette.BLOSSOM, AccentPalette.ROSE -> if (dark) Color(0xFF5C3D32) else Color(0xFFFFE8D8)
        AccentPalette.CHAMPAGNE, AccentPalette.AMBER -> if (dark) Color(0xFF5F3B2D) else Color(0xFFFFE1D5)
        else -> base.secondaryContainer
    }
    return base.copy(
        primary = Color(tones[0]), onPrimary = Color(tones[1]),
        primaryContainer = Color(tones[2]), onPrimaryContainer = Color(tones[3]),
        secondary = second, onSecondary = if (dark) Color(0xFF4A2E1D) else Color.White,
        secondaryContainer = secondContainer,
        onSecondaryContainer = if (dark) Color(0xFFFFE7D3) else Color(0xFF58341F),
        tertiary = if (dark) Color(0xFFE8C795) else Color(0xFF956A35),
    )
}

@Composable
internal fun effectiveReducedMotion(preference: Boolean): Boolean {
    val context = rememberCoroutineScope().coroutineContext
    return preference || (context[MotionDurationScale]?.scaleFactor == 0f)
}

internal val LocalGlassIntensity = staticCompositionLocalOf { .65f }

@Composable
fun VeilTheme(appearance: Appearance = Appearance.SYSTEM, palette: AccentPalette = AccentPalette.SUNSET, glassIntensity: Float = .65f, content: @Composable () -> Unit) {
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
        Canvas(Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(46.dp) else Modifier)) {
            val wide = size.width.coerceAtLeast(1f)
            val glowA = Offset(size.width * .86f, size.height * .06f)
            val glowB = Offset(size.width * .05f, size.height * .76f)
            val glowC = Offset(size.width * .63f, size.height * .95f)
            drawCircle(Brush.radialGradient(listOf(colors.primary.copy(alpha = .19f), Color.Transparent),
                center = glowA, radius = wide * .76f), radius = wide * .76f, center = glowA)
            drawCircle(Brush.radialGradient(listOf(colors.secondary.copy(alpha = .14f), Color.Transparent),
                center = glowB, radius = wide * .82f), radius = wide * .82f, center = glowB)
            drawCircle(Brush.radialGradient(listOf(colors.tertiary.copy(alpha = .08f), Color.Transparent),
                center = glowC, radius = wide * .52f), radius = wide * .52f, center = glowC)
        }
    }
}

@Composable
internal fun GlassPanel(modifier: Modifier = Modifier, intensity: Float = LocalGlassIntensity.current, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val dark = colors.background.luminance() < .4f
    Column(
        modifier.shadow(12.dp, shape, ambientColor = colors.primary.copy(alpha = .12f), spotColor = colors.secondary.copy(alpha = .12f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(
                colors.surface.copy(alpha = .98f - intensity * .10f),
                colors.primaryContainer.copy(alpha = if (dark) .28f else .46f),
                colors.secondaryContainer.copy(alpha = if (dark) .27f else .41f),
                colors.surface.copy(alpha = .97f - intensity * .08f)
            )))
            .border(1.dp, Brush.linearGradient(listOf(
                Color.White.copy(alpha = if (dark) .20f else .92f),
                colors.primary.copy(alpha = .24f), colors.secondary.copy(alpha = .22f),
                Color.White.copy(alpha = if (dark) .09f else .48f)
            )), shape)
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
    val dark = colors.background.luminance() < .4f
    val surface = modifier.shadow(18.dp, shape, ambientColor = colors.primary.copy(alpha = .20f), spotColor = colors.secondary.copy(alpha = .17f))
        .clip(shape)
        .background(Brush.linearGradient(listOf(
            colors.surface.copy(alpha = .96f - intensity * .13f),
            colors.primaryContainer.copy(alpha = if (dark) .26f else .48f),
            colors.secondaryContainer.copy(alpha = if (dark) .25f else .47f),
            colors.surface.copy(alpha = .97f - intensity * .10f)
        )))
        .border(1.dp, Brush.linearGradient(listOf(
            Color.White.copy(alpha = if (dark) .29f else .96f),
            colors.primary.copy(alpha = .29f), colors.secondary.copy(alpha = .31f)
        )), shape)
    if (rail) {
        Column(surface.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Destination.entries.forEach { item ->
                DockItem(item, item.route == selectedRoute, { onSelect(item) }, Modifier.width(78.dp).heightIn(min = defaultDockHeight)
                    .then(if (item.route == selectedRoute) Modifier.background(
                        Brush.linearGradient(listOf(colors.primaryContainer, colors.secondaryContainer.copy(alpha = .82f))),
                        RoundedCornerShape(27.dp)) else Modifier))
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
                    .background(Brush.linearGradient(listOf(colors.primaryContainer.copy(alpha = .93f),
                        colors.secondaryContainer.copy(alpha = .85f))))
                    .border(1.dp, colors.primary.copy(alpha = .20f), RoundedCornerShape(29.dp)))
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
            Text("V E I L", style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.primary)
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
