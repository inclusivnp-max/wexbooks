@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package client.wexbooks.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import client.wexbooks.data.ReaderPositionCodec
import client.wexbooks.data.ReaderPreferences
import client.wexbooks.data.readingStyle
import client.wexbooks.data.toggleReaderNight
import client.wexbooks.reader.BooksViewModel
import client.wexbooks.reader.EpubReaderSession
import client.wexbooks.reader.OpenedBook
import client.wexbooks.reader.QuoteDraft
import client.wexbooks.reader.core.ReaderPosition
import client.wexbooks.reader.readiumPreferences
import client.wexbooks.ui.GlassIconButton
import client.wexbooks.ui.components.ReaderIconAction
import client.wexbooks.ui.components.WexDialog
import client.wexbooks.ui.components.WexIcons
import client.wexbooks.ui.glass.wexGlass
import client.wexbooks.ui.motion.EpubTextMorph
import client.wexbooks.ui.motion.WexAnimatedText
import client.wexbooks.ui.motion.WexMotion
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.navigator.common.Decoration
import org.readium.navigator.web.reflowable.ReflowableWebDecorationLocation
import org.readium.navigator.web.reflowable.ReflowableWebRendition
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderScreen(
    session: OpenedBook,
    preferences: ReaderPreferences,
    darkTheme: Boolean,
    model: BooksViewModel,
) {
    val engine = session.reader as EpubReaderSession
    var viewportChanging by remember { mutableStateOf(false) }
    val controls = LocalReadingControls.current
    val controlsVisible = controls?.visible != false
    val rootView = androidx.compose.ui.platform.LocalView.current
    EpubTextMorph(
        engine,
        preferences.reduceMotion || !preferences.animateText || viewportChanging,
        preferences.scroll,
        preferences.fontSize,
        darkTheme,
        preferences.readingStyle(darkTheme),
        viewportKey = controlsVisible,
    )
    val colors = MaterialTheme.colorScheme
    val backdrop = rememberCanvasBackdrop { drawRect(colors.background) }
    val controller = engine.rendition.controller
    val location = controller?.location?.toLocator()
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<String?>(null) }
    var quote by remember { mutableStateOf<QuoteDraft?>(null) }
    var quoteHelp by remember { mutableStateOf(false) }
    val annotationFlow =
        remember(session.book.id) { model.repository.dao.annotations(session.book.id) }
    val annotations by annotationFlow.collectAsStateWithLifecycle(emptyList())
    LaunchedEffect(controller, annotations, colors.primary) {
        controller?.let { target ->
            val tint = colors.primary.copy(alpha = 0.2f).toArgb()
            val highlights =
                withContext(Dispatchers.Default) {
                    annotations
                        .mapNotNull { saved ->
                            ReaderPositionCodec.decode(saved.locatorJson)
                                ?.payload
                                ?.let {
                                    runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull()
                                }
                                ?.let { locator ->
                                    ReflowableWebDecorationLocation(locator)?.let { where ->
                                        Decoration(
                                            Decoration.Id(saved.id),
                                            where,
                                            Decoration.Style.Highlight(tint),
                                        )
                                    }
                                }
                        }
                        .toPersistentList()
                }
            target.decorations = target.decorations.putting("quotes", highlights)
        }
    }
    var bookmarked by remember(controller?.location) { mutableStateOf(false) }
    val readerStyle = preferences.readingStyle(darkTheme)
    LaunchedEffect(controller, readerStyle) {
        controller?.preferences = readerStyle.readiumPreferences()
    }
    LaunchedEffect(session) {
        snapshotFlow { engine.rendition.controller?.location?.toLocator() }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { engine.publish(it) }
    }
    SideEffect {
        if (controls?.reader === engine)
            controls.blocked = sheet != null || quote != null || quoteHelp
    }
    BackHandler {
        if (sheet != null) sheet = null
        else if (!controlsVisible) controls?.visible = true else model.closeBook()
    }
    Box(Modifier.fillMaxSize().background(colors.background).testTag("epub-reader")) {
        ReaderViewport(
            controlsVisible,
            preferences,
            horizontalMargin = 0.dp,
            normalBottom = 106.dp,
            onMotionChanged = { viewportChanging = it },
            awaitVisualReady = { client.wexbooks.ui.motion.awaitWebViewport(rootView) },
        ) {
            ReflowableWebRendition(
                state = engine.rendition,
                inputListener =
                    remember(controls, preferences.scroll) {
                        object : org.readium.navigator.common.InputListener {
                            override fun onTap(
                                event: org.readium.navigator.common.TapEvent,
                                context: org.readium.navigator.common.TapContext,
                            ) {
                                if (context.viewport.width.value > 0)
                                    controls?.tap(
                                        event.offset.x.value / context.viewport.width.value,
                                        preferences.scroll,
                                    )
                            }
                        }
                    },
                modifier = Modifier.fillMaxSize(),
            )
        }
        AnimatedVisibility(
            controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            enter = fadeIn(WexMotion.response(preferences.reduceMotion)),
            exit = fadeOut(WexMotion.response(preferences.reduceMotion)),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GlassIconButton(
                    WexIcons.Back,
                    "Назад в библиотеку",
                    backdrop,
                    darkTheme,
                    preferences.reduceTransparency,
                ) {
                    model.closeBook()
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        session.book.title,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                    )
                    WexAnimatedText(
                        if (location == null) "Открываем страницы…"
                        else
                            "${((location.locations.totalProgression ?: 0.0) * 100).toInt()}% прочитано",
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant,
                        modifier =
                            Modifier.testTag(
                                if (location != null) "reader-ready" else "reader-loading"
                            ),
                    )
                }
                GlassIconButton(
                    WexIcons.Book,
                    "Оглавление",
                    backdrop,
                    darkTheme,
                    preferences.reduceTransparency,
                ) {
                    sheet = "toc"
                }
                GlassIconButton(
                    WexIcons.Focus,
                    "Скрыть панели",
                    backdrop,
                    darkTheme,
                    preferences.reduceTransparency,
                ) {
                    controls?.toggle()
                }
            }
        }
        AnimatedVisibility(
            controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(WexMotion.response(preferences.reduceMotion)),
            exit = fadeOut(WexMotion.response(preferences.reduceMotion)),
        ) {
            Row(
                Modifier.navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .wexGlass(backdrop, darkTheme, preferences.reduceTransparency, 32.dp)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReaderIconAction(
                    WexIcons.Back,
                    "Предыдущая страница",
                    enabled = controller?.canMoveBackward == true,
                ) {
                    controls?.previous() ?: run { scope.launch { controller?.moveBackward() } }
                }
                TextButton(
                    onClick = { sheet = "type" },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Aa", fontSize = 21.sp)
                }
                ReaderIconAction(
                    WexIcons.Bookmark,
                    "Добавить закладку",
                    enabled = controller != null,
                    selected = bookmarked,
                    modifier = Modifier.testTag("add-bookmark"),
                ) {
                    model.bookmark(session)
                    bookmarked = true
                }
                ReaderIconAction(
                    WexIcons.Notes,
                    "Сохранить выделение",
                    enabled = controller != null,
                ) {
                    scope.launch {
                        val selected = controller?.currentSelection()
                        if (selected == null || selected.text.isBlank()) quoteHelp = true
                        else if (selected.text.length <= 12_000) {
                            val locator = selected.location.toLocator()
                            quote =
                                QuoteDraft(
                                    ReaderPosition(
                                        locator.href.toString(),
                                        progression = locator.locations.totalProgression ?: 0.0,
                                        payload = locator.toJSON().toString(),
                                    ),
                                    0,
                                    0,
                                    selected.text,
                                )
                        } else quoteHelp = true
                    }
                }
                ReaderIconAction(if (darkTheme) WexIcons.Sun else WexIcons.Moon, "Сменить тему") {
                    model.updatePreferences { it.toggleReaderNight(darkTheme) }
                }
                ReaderIconAction(
                    WexIcons.Arrow,
                    "Следующая страница",
                    enabled = controller?.canMoveForward == true,
                ) {
                    controls?.next() ?: run { scope.launch { controller?.moveForward() } }
                }
            }
        }
        ReaderBrightnessEdge(preferences)
        quote?.let { draft ->
            QuoteEditor(draft.text, onDismiss = { quote = null }) { note ->
                model.saveQuote(session, draft, note)
                controller?.clearSelection()
            }
        }
        if (quoteHelp)
            WexDialog(
                title = "Цитаты из книги",
                onDismissRequest = { quoteHelp = false },
                confirmLabel = "Понятно",
            ) {
                Text(
                    "Зажмите слово и растяните выделение, затем нажмите значок заметок в нижней панели. Можно сохранить до 12 000 символов за раз.",
                    color = colors.onSurfaceVariant,
                )
            }
        if (sheet != null) {
            ModalBottomSheet(
                onDismissRequest = { sheet = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = colors.surface,
            ) {
                ReadingDialogWindowEffects()
                if (sheet == "type") {
                    ReadingComfortSheet(preferences, model, true) { sheet = null }
                } else {
                    Text(
                        "Оглавление",
                        Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        fontSize = 25.sp,
                    )
                    val chapters =
                        remember(session) { flattenToc(engine.publication.tableOfContents) }
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 480.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(chapters, key = { it.first.href.toString() + it.second }) {
                            (link, depth) ->
                            TextButton(
                                onClick = {
                                    sheet = null
                                    scope.launch { controller?.goTo(link.url()) }
                                },
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(start = (16 + depth * 12).dp, end = 16.dp)
                                        .semantics {
                                            contentDescription =
                                                "Открыть главу: ${link.title ?: "Глава"}"
                                        }
                                        .testTag("chapter-${link.href}"),
                            ) {
                                Text(
                                    link.title ?: "Глава",
                                    Modifier.fillMaxWidth(),
                                    fontSize = 16.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun flattenToc(links: List<Link>, depth: Int = 0): List<Pair<Link, Int>> = links.flatMap {
    listOf(it to depth) + flattenToc(it.children, depth + 1)
}
