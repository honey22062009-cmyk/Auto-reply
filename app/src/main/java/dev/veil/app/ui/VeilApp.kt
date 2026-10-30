package dev.veil.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.veil.app.VeilViewModel
import dev.veil.app.domain.*

@Composable
fun VeilApp(viewModel: VeilViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
    val setupSnackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice, state.preferences.onboarded) {
        if (!state.preferences.onboarded && state.loaded) state.notice?.let {
            setupSnackbar.showSnackbar(it)
            viewModel.clearNotice()
        }
    }
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshSystemState() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    VeilTheme(state.preferences.appearance, state.preferences.accent, state.preferences.glassIntensity) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                VeilBackdrop(Modifier.matchParentSize())
                when {
                    !state.loaded -> Column(Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (state.notice == null) CircularProgressIndicator()
                        else Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.error)
                        Text(state.notice ?: "Opening your private workspace…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    !state.preferences.onboarded -> VeilOnboarding(state.system, viewModel::finishOnboarding)
                    else -> MainShell(state, viewModel)
                }
                if (state.loaded && !state.preferences.onboarded) SnackbarHost(setupSnackbar,
                    Modifier.align(Alignment.BottomCenter).safeDrawingPadding().imePadding().padding(16.dp))
            }
        }
    }
}

@Composable
private fun MainShell(state: VeilUiState, viewModel: VeilViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val selectedRoute = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(viewModel::exportDiagnostics) }
    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() }
    }
    val goLab = { nav.navigate("lab") { launchSingleTop = true } }
    val goInbox = { nav.navigate("inbox") { launchSingleTop = true } }
    val goAi = { nav.navigate("ai") { launchSingleTop = true } }
    val reducedMotion = effectiveReducedMotion(state.preferences.reduceMotion)
    val exportSettings = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(viewModel::exportSettings) }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        val rail = maxWidth >= 1000.dp && LocalDensity.current.fontScale < 1.6f
        Row(Modifier.fillMaxSize()) {
            if (rail) {
                Box(Modifier.width(128.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    FloatingDock(selectedRoute, state.preferences.reduceMotion, state.preferences.glassIntensity,
                        onSelect = { destination -> nav.navigate(destination.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        } }, rail = true)
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                NavHost(nav, startDestination = "home", modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn(tween(if (reducedMotion) 0 else 180)) },
                    exitTransition = { fadeOut(tween(if (reducedMotion) 0 else 120)) }) {
                    composable("home") { HomeScreen(state, viewModel::setCapturePaused, goLab, goInbox, goAi) }
                    composable("inbox") { InboxScreen(state, viewModel, goLab) }
                    composable("priority") { PriorityScreen(state, viewModel) }
                    composable("activity") { ActivityScreen(state, goLab) }
                    composable("settings") { SettingsScreen(state, viewModel, goLab, goAi, onExportSettings = { exportSettings.launch("veil-settings.json") }) }
                    composable("ai") { AiStudioScreen(state, viewModel, onBack = { nav.popBackStack() }) }
                    composable("lab") { CompatibilityLab(state, viewModel, onBack = { nav.popBackStack() }, onExport = { export.launch("veil-diagnostics.json") }, onInbox = goInbox) }
                }
            }
        }
        if (!rail) {
            FloatingDock(selectedRoute, state.preferences.reduceMotion, state.preferences.glassIntensity,
                onSelect = { destination -> nav.navigate(destination.route) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true; restoreState = true
                } }, modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 12.dp).widthIn(max = 560.dp).fillMaxWidth())
        }
        if (WindowInsets.ime.getBottom(LocalDensity.current) == 0) CompanionDock(state, onNavigate = { route -> nav.navigate(route) { launchSingleTop = true } },
            onHide = { viewModel.setCompanionEnabled(false) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = if (rail) 24.dp else 112.dp))
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(start = if (rail) 128.dp else 0.dp, bottom = if (rail) 16.dp else 102.dp))
    }
}

@Composable
internal fun ScreenBody(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 920.dp).fillMaxWidth().fillMaxHeight(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 142.dp)) {
            item { Column(verticalArrangement = Arrangement.spacedBy(27.dp), content = content) }
        }
    }
}

@Composable
internal fun ScreenList(content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 920.dp).fillMaxWidth().fillMaxHeight(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 142.dp),
            verticalArrangement = Arrangement.spacedBy(27.dp), content = content)
    }
}

@Composable
internal fun notificationSettingsAction(): () -> Unit {
    val context = LocalContext.current
    return {
        try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        catch (_: ActivityNotFoundException) { Toast.makeText(context, "Open Android Settings → Notification access → VEIL.", Toast.LENGTH_LONG).show() }
        catch (_: SecurityException) { Toast.makeText(context, "Notification settings are restricted on this device.", Toast.LENGTH_LONG).show() }
    }
}

@Composable
internal fun VeilOnboarding(system: SystemSnapshot, onFinish: (Set<MessagingApp>) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var whatsapp by rememberSaveable { mutableStateOf(false) }
    var instagram by rememberSaveable { mutableStateOf(false) }
    val notificationSettings = notificationSettingsAction()
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenBody {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("V E I L", style = MaterialTheme.typography.titleMedium)
                Eyebrow("${step + 1} / 3")
            }
            AnimatedContent(step, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "onboarding") { current ->
                Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
                    when (current) {
                        0 -> {
                            Spacer(Modifier.height(14.dp))
                            Text("A little space.\nFor your conversations.", style = MaterialTheme.typography.displaySmall)
                            Text("A private messaging companion, built around your control.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            GlassPanel {
                                Eyebrow("Start with certainty")
                                Text("Start with a clear boundary", style = MaterialTheme.typography.titleLarge)
                                Text("First, discover what your installed messaging apps actually share with Android.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                InfoRow(Icons.Outlined.NotificationsNone, "Notification-based", "VEIL sees accessible notifications, not your full inbox.")
                                InfoRow(Icons.Outlined.TouchApp, "You stay in control", "Review suggestions before sending. Automation starts off and requires explicit contact rules.")
                                InfoRow(Icons.Outlined.Lock, "Private by design", "Sensitive stored content is encrypted. Optional cloud replies require your separate opt-in.")
                            }
                        }
                        1 -> {
                            Text("Only the apps\nyou choose.", style = MaterialTheme.typography.displaySmall)
                            Text("Android notification access is broad. VEIL processes only the supported apps you explicitly enable here.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            GlassPanel {
                                PlatformToggle(MessagingApp.WHATSAPP, whatsapp, { whatsapp = it }, system)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                                PlatformToggle(MessagingApp.INSTAGRAM, instagram, { instagram = it }, system)
                            }
                            Text("History is kept for 1 day by default. Templates stay local. OpenAI receives selected message content only after your explicit cloud opt-in; no analytics or advertising is included.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        2 -> {
                            Text("Notification access.\nOn your terms.", style = MaterialTheme.typography.displaySmall)
                            Text("Notification access lets VEIL inspect supported message notifications and their reply actions. Enable it only if you are comfortable with that access.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            GlassPanel {
                                StatusPill(if (system.notificationAccess) "Notification access enabled" else "Notification access needed", warning = !system.notificationAccess)
                                Text("Connect through Android Settings", style = MaterialTheme.typography.titleLarge)
                                Text("Select VEIL in the notification-access list. On some sideloaded installs, Android first requires “Allow restricted settings” from VEIL’s App info menu.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = notificationSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open notification settings") }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                                Text("Then ask a consenting contact to send a harmless message. The Lab will show whether an active reply action is available.", style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("Dispatch accepted by Android does not prove receipt. WhatsApp and Instagram must be tested separately on your tablet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (current > 0) TextButton(onClick = { step-- }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
                        Button(onClick = {
                            if (current < 2) step++ else onFinish(buildSet { if (whatsapp) add(MessagingApp.WHATSAPP); if (instagram) add(MessagingApp.INSTAGRAM) })
                        }, modifier = Modifier.weight(1f).heightIn(min = 54.dp)) {
                            Text(when (current) { 0 -> "Get started"; 1 -> "Continue"; else -> "Open VEIL" })
                            Spacer(Modifier.width(10.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlatformToggle(app: MessagingApp, enabled: Boolean, onChange: (Boolean) -> Unit, system: SystemSnapshot) {
    val info = system.apps.firstOrNull { it.app == app }
    InfoRow(if (app == MessagingApp.WHATSAPP) Icons.Outlined.ChatBubbleOutline else Icons.Outlined.AlternateEmail,
        app.label, if (info?.installed == true) "Installed · ${info.version ?: "version unavailable"}" else "Not detected on this device",
        trailing = { Switch(checked = enabled, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = "Observe ${app.label} notifications" }) })
}
