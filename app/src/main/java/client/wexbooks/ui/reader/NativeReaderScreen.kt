package client.wexbooks.ui.reader

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import client.wexbooks.data.ReaderPreferences
import client.wexbooks.data.toggleReaderNight
import client.wexbooks.reader.BooksViewModel
import client.wexbooks.reader.OpenedBook
import client.wexbooks.reader.nativeengine.*
import client.wexbooks.ui.GlassIconButton
import client.wexbooks.ui.components.ReaderIconAction
import client.wexbooks.ui.components.WexIcons
import client.wexbooks.ui.glass.wexGlass
import client.wexbooks.ui.motion.WexAnimatedText
import client.wexbooks.ui.motion.WexMotion
import client.wexbooks.ui.motion.rememberNativeTextMorph
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeReaderScreen(
    session: OpenedBook,
    preferences: ReaderPreferences,
    darkTheme: Boolean,
    model: BooksViewModel,
) {
    val engine = session.reader as NativeReaderSession
    var viewportChanging by remember { mutableStateOf(false) }
    val render by engine.render.collectAsStateWithLifecycle()
    val position by engine.position.collectAsStateWithLifecycle()
    val textMotion =
        rememberNativeTextMorph(
            render.page,
            engine.layoutKey(),
            preferences.reduceMotion || !preferences.animateText || viewportChanging,
            render.forward,
        )
    val colors = MaterialTheme.colorScheme
    val backdrop = rememberCanvasBackdrop { drawRect(colors.background) }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val resolver = LocalFontFamilyResolver.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf<NativeSelection?>(null) }
    var quote by remember { mutableStateOf<client.wexbooks.reader.QuoteDraft?>(null) }
    var note by remember { mutableStateOf<NativeNote?>(null) }
    var image by remember { mutableStateOf<String?>(null) }
    val annotationFlow =
        remember(session.book.id) { model.repository.dao.annotations(session.book.id) }
    val savedFlow =
        remember(annotationFlow, engine) {
            annotationFlow
                .map { annotations ->
                    annotations.mapNotNull { annotation ->
                        client.wexbooks.data.ReaderPositionCodec.decode(annotation.locatorJson)
                            ?.takeIf { it.resource == "native" }
                            ?.let { start ->
                                NativeSelection(
                                    start,
                                    engine.document.position(
                                        annotation.endBlock,
                                        annotation.endOffset,
                                    ),
                                )
                            }
                    }
                }
                .flowOn(Dispatchers.Default)
        }
    val saved by savedFlow.collectAsStateWithLifecycle(emptyList())
    val clipboard = LocalClipboard.current
    LaunchedEffect(render.page?.start, preferences.fontSize) { selection = null }
    var bookmarked by remember(position) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val controls = LocalReadingControls.current
    val controlsVisible = controls?.visible != false
    LaunchedEffect(
        size,
        preferences.fontSize,
        preferences.font,
        preferences.lineHeight,
        preferences.paragraphSpacing,
        preferences.letterSpacing,
        preferences.firstLineIndent,
        preferences.justify,
        density.density,
        density.fontScale,
        direction,
        resolver,
    ) {
        engine.prepareReflow()
        kotlinx.coroutines.delay(80)
        if (size.width > 0 && size.height > 0)
            engine.configure(
                LayoutKey(
                    size.width,
                    size.height,
                    preferences.fontSize,
                    density.density,
                    density.fontScale,
                    direction,
                    preferences.font.css,
                    preferences.lineHeight,
                    preferences.paragraphSpacing,
                    preferences.letterSpacing,
                    preferences.firstLineIndent,
                    preferences.justify,
                ),
                resolver,
            )
    }
    SideEffect {
        if (controls?.reader === engine)
            controls.blocked = sheet != null || quote != null || image != null || selection != null
    }
    BackHandler {
        when {
            quote != null -> quote = null
            image != null -> image = null
            sheet != null -> sheet = null
            selection != null -> selection = null
            !controlsVisible -> controls?.visible = true
            else -> model.closeBook()
        }
    }
    Box(Modifier.fillMaxSize().background(colors.background).testTag("native-reader")) {
        ReaderViewport(
            controlsVisible,
            preferences,
            horizontalMargin = preferences.horizontalMargin.dp,
            normalBottom = 110.dp,
            onMotionChanged = { viewportChanging = it },
            contentReady = { target ->
                engine.layoutKey()?.let {
                    it.width == target.width && it.height == target.height
                } == true && !engine.render.value.loading
            },
        ) {
            Box(
                Modifier.fillMaxSize().onSizeChanged {
                    if (it != size) {
                        engine.prepareReflow()
                        size = it
                    }
                }
            ) {
                val page = render.page
                if (page != null) {
                    NativePageContent(
                        engine,
                        page,
                        selection,
                        saved.filter {
                            it.end.block >= page.start.block && it.start.block <= page.end.block
                        },
                        onSelection = { selection = it },
                        onLink = { id ->
                            val footnote = engine.document.notes[id]
                            if (footnote != null) {
                                note = footnote
                                sheet = "note"
                            } else
                                engine.document.targets[id]?.let { block ->
                                    scope.launch { engine.goTo(engine.document.position(block, 0)) }
                                }
                                    ?: run {
                                        note =
                                            NativeNote(
                                                "Сноска",
                                                "В файле книги нет текста этой сноски.",
                                            )
                                        sheet = "note"
                                    }
                        },
                        onImage = { image = it },
                        motion = textMotion,
                        onShowControls = { controls?.visible = true },
                        onPageTap = { fraction -> controls?.tap(fraction) },
                        onPageSwipe = { forward ->
                            if (forward) controls?.next() else controls?.previous()
                        },
                    )
                } else if (render.error != null) {
                    Text(render.error!!, Modifier.align(Alignment.Center))
                } else CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
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
                    onClick = model::closeBook,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        session.book.title,
                        color = colors.onSurface,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    WexAnimatedText(
                        "${((position?.progression ?: 0.0) * 100).toInt()}% прочитано · ${session.book.format}",
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant,
                        modifier =
                            Modifier.testTag(
                                if (render.page != null && !render.loading) "reader-ready"
                                else "reader-loading"
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
            controlsVisible || selection != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(WexMotion.response(preferences.reduceMotion)),
            exit = fadeOut(WexMotion.response(preferences.reduceMotion)),
        ) {
            Row(
                Modifier.navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .wexGlass(backdrop, darkTheme, preferences.reduceTransparency, 32.dp)
                    .animateContentSize(WexMotion.springResponse(preferences.reduceMotion))
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selection != null) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val text = selection?.draft(engine.document)?.text ?: return@launch
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("Цитата", text))
                                )
                                selection = null
                            }
                        },
                        modifier = Modifier.testTag("copy-selection"),
                    ) {
                        Text("Копировать")
                    }
                    TextButton(
                        onClick = { quote = selection?.draft(engine.document) },
                        modifier = Modifier.testTag("quote-selection"),
                    ) {
                        Text("В заметки")
                    }
                    TextButton(onClick = { selection = null }) { Text("Отмена") }
                } else {
                    ReaderIconAction(
                        WexIcons.Back,
                        "Предыдущая страница",
                        enabled = render.page?.hasPrevious == true && !render.loading,
                    ) {
                        controls?.previous() ?: run { scope.launch { engine.backward() } }
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
                        enabled = render.page != null,
                        selected = bookmarked,
                        modifier = Modifier.testTag("add-bookmark"),
                    ) {
                        model.bookmark(session)
                        bookmarked = true
                    }
                    ReaderIconAction(
                        if (darkTheme) WexIcons.Sun else WexIcons.Moon,
                        "Сменить тему",
                    ) {
                        model.updatePreferences { it.toggleReaderNight(darkTheme) }
                    }
                    ReaderIconAction(
                        WexIcons.Arrow,
                        "Следующая страница",
                        enabled = render.page?.hasNext == true && !render.loading,
                    ) {
                        controls?.next() ?: run { scope.launch { engine.forward() } }
                    }
                }
            }
        }
        ReaderBrightnessEdge(preferences)
        quote?.let { draft ->
            QuoteEditor(
                draft.text,
                onDismiss = {
                    quote = null
                    selection = null
                },
            ) { text ->
                model.saveQuote(session, draft, text)
            }
        }
        image?.let { id -> NativeImageViewer(engine, id) { image = null } }
        if (sheet != null)
            ModalBottomSheet(
                onDismissRequest = { sheet = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = colors.surface,
            ) {
                ReadingDialogWindowEffects()
                if (sheet == "type") ReadingComfortSheet(preferences, model, false) { sheet = null }
                else if (sheet == "note") {
                    val paragraphs by
                        produceState<List<String>>(emptyList(), note) {
                            value =
                                withContext(Dispatchers.Default) {
                                    note?.text.orEmpty().split("\n\n").flatMap { it.chunked(4096) }
                                }
                        }
                    Column(
                        Modifier.fillMaxWidth()
                            .heightIn(max = 520.dp)
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                            .testTag("footnote-sheet")
                    ) {
                        Text(note?.title ?: "Сноска", fontSize = 24.sp)
                        SelectionContainer {
                            LazyColumn(
                                Modifier.fillMaxWidth().heightIn(max = 340.dp).padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(paragraphs) { paragraph ->
                                    Text(
                                        paragraph,
                                        fontSize = preferences.fontSize.sp,
                                        lineHeight =
                                            (preferences.fontSize * preferences.lineHeight / 100f)
                                                .sp,
                                        fontFamily = preferences.font.composeFamily(),
                                        letterSpacing =
                                            (preferences.fontSize * preferences.letterSpacing /
                                                    100f)
                                                .sp,
                                        color = colors.onSurface,
                                    )
                                }
                            }
                        }
                        TextButton(
                            onClick = { sheet = null },
                            modifier = Modifier.align(Alignment.End).padding(top = 16.dp),
                        ) {
                            Text("К чтению")
                        }
                    }
                } else {
                    Text(
                        "Оглавление",
                        Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        fontSize = 25.sp,
                    )
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 480.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(engine.chapters, key = { it.position.block }) { chapter ->
                            TextButton(
                                onClick = {
                                    sheet = null
                                    scope.launch { engine.goTo(chapter.position) }
                                },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            ) {
                                Text(chapter.title, Modifier.fillMaxWidth(), fontSize = 16.sp)
                            }
                        }
                        if (engine.chapters.isEmpty())
                            item {
                                Text(
                                    "В этой книге нет отдельных заголовков.",
                                    Modifier.padding(24.dp),
                                )
                            }
                    }
                }
            }
    }
}
