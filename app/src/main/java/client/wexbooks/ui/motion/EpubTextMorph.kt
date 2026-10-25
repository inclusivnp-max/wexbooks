@file:OptIn(
    org.readium.r2.shared.ExperimentalReadiumApi::class,
    kotlinx.coroutines.FlowPreview::class,
)

package client.wexbooks.ui.motion

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import client.wexbooks.reader.EpubReaderSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

/** Public WebView/DOM APIs only; the navigator's text and selection anchors are never rewritten. */
@Composable
fun EpubTextMorph(
    engine: EpubReaderSession,
    reduced: Boolean,
    scroll: Boolean,
    fontSize: Int,
    themeKey: Boolean,
) {
    val root = LocalView.current
    val assets = LocalContext.current.assets
    LaunchedEffect(engine, reduced, scroll, fontSize, themeKey) {
        webViews(root).forEach {
            it.evaluateJavascript(
                "window.__wexStopTextMorph?.(); window.__wexTextFrame = null;",
                null,
            )
        }
        if (reduced || scroll) return@LaunchedEffect
        val script =
            withContext(Dispatchers.IO) {
                assets.open("motion/text-morph.js").bufferedReader().use { it.readText() }
            }
        var initial = true
        snapshotFlow { engine.rendition.controller?.location?.toLocator() }
            .filterNotNull()
            .map { it.href.toString() to it.locations.progression }
            .distinctUntilChanged()
            .debounce(60)
            .collect { _ ->
                val code = script.replace("__WEX_ANIMATE__", if (initial) "false" else "true")
                webViews(root)
                    .filter {
                        it.isShown &&
                            it.width > 0 &&
                            it.height > 0 &&
                            it.getGlobalVisibleRect(android.graphics.Rect())
                    }
                    .forEach { it.evaluateJavascript(code, null) }
                initial = false
            }
    }
    DisposableEffect(engine, root) {
        onDispose {
            webViews(root).forEach {
                it.evaluateJavascript(
                    "window.__wexStopTextMorph?.(); window.__wexTextFrame = null;",
                    null,
                )
            }
        }
    }
}

internal fun webViews(view: View): List<WebView> =
    when (view) {
        is WebView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { webViews(view.getChildAt(it)) }
        else -> emptyList()
    }
