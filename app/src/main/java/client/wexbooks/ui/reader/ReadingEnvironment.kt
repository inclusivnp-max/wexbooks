package client.wexbooks.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import client.wexbooks.data.*
import client.wexbooks.reader.BooksViewModel
import client.wexbooks.reader.OpenedBook
import client.wexbooks.reader.core.ReaderSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val LocalReadingControls = staticCompositionLocalOf<ReadingControls?> { null }

@Stable
class ReadingControls(
    val reader: ReaderSession?,
    private val model: BooksViewModel,
    private val scope: CoroutineScope,
    val systemBrightness: Int,
    private val haptic: () -> Unit,
) {
    var visible by mutableStateOf(true)
    var blocked by mutableStateOf(false)
    var brightnessPreview by mutableStateOf<Int?>(null)
    var preferences by mutableStateOf(ReaderPreferences())
    val brightness: Int
        get() = brightnessPreview ?: preferences.brightness.takeIf { it >= 0 } ?: systemBrightness

    fun toggle() {
        if (!blocked) visible = !visible
    }

    fun next() = turn(true)

    fun previous() = turn(false)

    private fun turn(forward: Boolean) {
        if (blocked || reader == null || model.opened.value?.reader !== reader) return
        if (preferences.pageHaptics) haptic()
        scope.launch { if (forward) reader.forward() else reader.backward() }
    }

    fun tap(fraction: Float, scrolling: Boolean = false) {
        if (blocked) return
        when (if (scrolling) ReaderTap.Controls else readerTap(fraction, preferences.tapLayout)) {
            ReaderTap.Previous -> previous()
            ReaderTap.Next -> next()
            ReaderTap.Controls -> toggle()
        }
    }

    fun previewBrightness(value: Int) {
        brightnessPreview = value.coerceIn(5, 100)
    }

    fun saveBrightness() {
        val value = brightnessPreview ?: return
        model.updatePreferences { it.copy(brightness = value) }
    }
}

/** One window owner outside the page transition: outgoing readers never restore over a new one. */
@Composable
fun ReadingEnvironment(
    session: OpenedBook?,
    preferences: ReaderPreferences,
    appDark: Boolean,
    model: BooksViewModel,
    content: @Composable (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val systemBrightness =
        remember(session?.reader) {
            runCatching {
                    Settings.System.getInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                    ) * 100 / 255
                }
                .getOrDefault(50)
                .coerceIn(5, 100)
        }
    val controls =
        remember(session?.reader) {
            ReadingControls(session?.reader, model, scope, systemBrightness) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    val blocked = controls.blocked
    SideEffect {
        controls.preferences = preferences
        session?.reader?.let { model.blockReaderInput(it, blocked) }
    }
    LaunchedEffect(preferences.brightness) {
        if (controls.brightnessPreview == preferences.brightness) controls.brightnessPreview = null
    }
    val page = preferences.palette.colors(appDark)
    if (session != null)
        ReadingWindowEffects(
            context.activity()?.window,
            preferences,
            controls,
            page.dark,
            dialog = false,
        )
    CompositionLocalProvider(LocalReadingControls provides controls.takeIf { session != null }) {
        content(if (session != null) page.dark else appDark)
    }
}

@Composable
fun ReadingPageTheme(
    preferences: ReaderPreferences,
    appDark: Boolean,
    content: @Composable (Boolean) -> Unit,
) {
    val page = preferences.palette.colors(appDark)
    val base = MaterialTheme.colorScheme
    val scheme =
        base.copy(
            background = Color(page.background),
            onBackground = Color(page.ink),
            surface = Color(page.surface),
            onSurface = Color(page.ink),
            onSurfaceVariant = Color(page.muted),
            surfaceDim = Color(page.background),
            surfaceBright = Color(page.surface),
            surfaceContainerLowest = Color(page.background),
            surfaceContainerLow = Color(page.surface),
            surfaceContainer = Color(page.surface),
            surfaceContainerHigh = Color(page.surface),
            surfaceContainerHighest =
                Color(page.muted).copy(alpha = .13f).compositeOver(Color(page.surface)),
            outline = Color(page.muted),
            outlineVariant = Color(page.muted).copy(alpha = .35f),
            surfaceVariant = Color(page.muted).copy(alpha = .13f),
            primary = Color(page.accent),
            onPrimary = Color(if (page.dark) page.background else 0xFFFFFFFF.toInt()),
            primaryContainer = Color(page.accent).copy(alpha = .15f),
            onPrimaryContainer = Color(page.ink),
            secondaryContainer = Color(page.accent).copy(alpha = .13f),
            onSecondaryContainer = Color(page.ink),
        )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) {
            content(page.dark)
        }
    }
}

@Composable
private fun ReadingWindowEffects(
    window: Window?,
    preferences: ReaderPreferences,
    controls: ReadingControls,
    dark: Boolean,
    dialog: Boolean,
) {
    val view = LocalView.current
    if (window == null) return
    val controller = remember(window) { WindowInsetsControllerCompat(window, window.decorView) }
    DisposableEffect(window) {
        val originalBrightness = window.attributes.screenBrightness
        val originalKeepOn =
            window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        val behavior = controller.systemBarsBehavior
        val status =
            ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.statusBars())
                ?: true
        val nav =
            ViewCompat.getRootWindowInsets(view)
                ?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        val oldStatusInk = controller.isAppearanceLightStatusBars
        val oldNavInk = controller.isAppearanceLightNavigationBars
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = originalBrightness }
            if (originalKeepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.systemBarsBehavior = behavior
            if (!dialog) {
                if (status) controller.show(WindowInsetsCompat.Type.statusBars())
                else controller.hide(WindowInsetsCompat.Type.statusBars())
                if (nav) controller.show(WindowInsetsCompat.Type.navigationBars())
                else controller.hide(WindowInsetsCompat.Type.navigationBars())
            }
            controller.isAppearanceLightStatusBars = oldStatusInk
            controller.isAppearanceLightNavigationBars = oldNavInk
        }
    }
    val brightness = controls.brightnessPreview ?: preferences.brightness
    val hideBars = preferences.immersive && !controls.visible && !controls.blocked
    SideEffect {
        val value =
            if (brightness < 0) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            else brightness.coerceIn(5, 100) / 100f
        if (window.attributes.screenBrightness != value)
            window.attributes = window.attributes.apply { screenBrightness = value }
        if (preferences.keepScreenOn)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
        if (!dialog) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hideBars) controller.hide(WindowInsetsCompat.Type.systemBars())
            else controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** Dialogs have their own Window; keep their brightness consistent with the reading page. */
@Composable
fun ReadingDialogWindowEffects() {
    val controls = LocalReadingControls.current ?: return
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    ReadingWindowEffects(
        window,
        controls.preferences,
        controls,
        MaterialTheme.colorScheme.background.luminance() < .5f,
        dialog = true,
    )
}

@Composable
fun ReaderBrightnessEdge(preferences: ReaderPreferences) {
    val controls = LocalReadingControls.current ?: return
    if (!preferences.brightnessGesture || controls.blocked) return
    var showing by remember { mutableStateOf(false) }
    var change by remember { mutableIntStateOf(0) }
    LaunchedEffect(change, showing) {
        if (showing) {
            delay(1100)
            showing = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(22.dp)
                .testTag("brightness-edge")
                .pointerInput(controls) {
                    var start = 50f
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            start = controls.brightness.toFloat()
                            total = 0f
                            showing = true
                        },
                        onDragEnd = { controls.saveBrightness() },
                        onDragCancel = { controls.saveBrightness() },
                    ) { event, amount ->
                        event.consume()
                        total += amount
                        controls.previewBrightness((start - total / size.height * 140).toInt())
                        showing = true
                        change++
                    }
                }
        )
        if (showing)
            Text(
                "☀  ${controls.brightness}%",
                Modifier.align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 90.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
    }
}

private fun Context.activity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }
