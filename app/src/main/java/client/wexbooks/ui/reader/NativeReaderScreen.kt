package client.wexbooks.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import client.wexbooks.data.ReaderPreferences
import client.wexbooks.reader.BooksViewModel
import client.wexbooks.reader.OpenedBook
import client.wexbooks.reader.nativeengine.*
import client.wexbooks.ui.GlassIconButton
import client.wexbooks.ui.components.ReaderIconAction
import client.wexbooks.ui.components.WexIcons
import client.wexbooks.ui.glass.wexGlass
import client.wexbooks.ui.motion.WexMotion
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeReaderScreen(
    session: OpenedBook,
    preferences: ReaderPreferences,
    darkTheme: Boolean,
    model: BooksViewModel,
) {
    val engine = session.reader as NativeReaderSession
    val render by engine.render.collectAsStateWithLifecycle()
    val position by engine.position.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val backdrop = rememberCanvasBackdrop { drawRect(colors.background) }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val resolver = LocalFontFamilyResolver.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var bookmarked by remember(position) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(
        size,
        preferences.fontSize,
        density.density,
        density.fontScale,
        direction,
        resolver,
    ) {
        if (size.width > 0 && size.height > 0)
            engine.configure(
                LayoutKey(
                    size.width,
                    size.height,
                    preferences.fontSize,
                    density.density,
                    density.fontScale,
                    direction,
                ),
                resolver,
            )
    }
    BackHandler { if (sheet != null) sheet = null else model.closeBook() }
    Box(Modifier.fillMaxSize().background(colors.background).testTag("native-reader")) {
        Box(
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 26.dp, end = 26.dp, top = 80.dp, bottom = 110.dp)
                .onSizeChanged { size = it }
        ) {
            val page = render.page
            if (page != null) {
                AnimatedContent(
                    page,
                    modifier = Modifier.fillMaxSize(),
                    contentKey = { it.start.block to it.start.offset },
                    transitionSpec = {
                        WexMotion.pageTransition(preferences.reduceMotion, render.forward)
                            .using(null)
                    },
                    label = "native page",
                ) { visible ->
                    val visibleText =
                        remember(visible) {
                            visible.fragments.joinToString("\n\n") { fragment ->
                                fragment.layout.layoutInput.text.text.substring(
                                    fragment.layout.getLineStart(fragment.firstLine),
                                    fragment.layout.getLineEnd(fragment.lastLine, true),
                                )
                            }
                        }
                    Canvas(
                        Modifier.fillMaxSize().testTag("native-page").semantics {
                            text = AnnotatedString(visibleText)
                        }
                    ) {
                        visible.fragments.forEach { fragment ->
                            clipRect(
                                top = fragment.top,
                                bottom =
                                    (fragment.top + fragment.height).coerceAtMost(
                                        size.height.toFloat()
                                    ),
                            ) {
                                drawText(
                                    fragment.layout,
                                    color = colors.onBackground,
                                    topLeft =
                                        Offset(
                                            0f,
                                            fragment.top -
                                                fragment.layout.getLineTop(fragment.firstLine),
                                        ),
                                )
                            }
                        }
                    }
                }
            } else if (render.error != null) {
                Text(render.error!!, Modifier.align(Alignment.Center))
            } else CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        Row(
            Modifier.align(Alignment.TopCenter)
                .fillMaxWidth()
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
                Text(
                    "${((position?.progression ?: 0.0) * 100).toInt()}% прочитано · ${session.book.format}",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                    modifier =
                        Modifier.testTag(
                            if (render.page != null) "reader-ready" else "reader-loading"
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
        }
        Row(
            Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .wexGlass(backdrop, darkTheme, preferences.reduceTransparency, 32.dp)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderIconAction(
                WexIcons.Back,
                "Предыдущая страница",
                enabled = render.page?.hasPrevious == true,
            ) {
                scope.launch { engine.backward() }
            }
            TextButton(onClick = { sheet = "type" }, modifier = Modifier.heightIn(min = 48.dp)) {
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
            ReaderIconAction(if (darkTheme) WexIcons.Sun else WexIcons.Moon, "Сменить тему") {
                model.updatePreferences { it.copy(darkTheme = !darkTheme) }
            }
            ReaderIconAction(
                WexIcons.Arrow,
                "Следующая страница",
                enabled = render.page?.hasNext == true,
            ) {
                scope.launch { engine.forward() }
            }
        }
        if (sheet != null)
            ModalBottomSheet(
                onDismissRequest = { sheet = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = colors.surface,
            ) {
                if (sheet == "type")
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)
                    ) {
                        Text("Как вам удобно", fontSize = 24.sp)
                        Row(
                            Modifier.fillMaxWidth().padding(top = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(
                                onClick = {
                                    model.updatePreferences {
                                        it.copy(fontSize = (it.fontSize - 2).coerceAtLeast(16))
                                    }
                                },
                                modifier = Modifier.testTag("font-decrease"),
                            ) {
                                Text("А−", fontSize = 20.sp)
                            }
                            Text(
                                "${preferences.fontSize}",
                                Modifier.testTag("font-size"),
                                fontSize = 20.sp,
                            )
                            TextButton(
                                onClick = {
                                    model.updatePreferences {
                                        it.copy(fontSize = (it.fontSize + 2).coerceAtMost(34))
                                    }
                                },
                                modifier = Modifier.testTag("font-increase"),
                            ) {
                                Text("А+", fontSize = 24.sp)
                            }
                        }
                        Text(
                            "Нативная вёрстка WexBooks · постраничное чтение",
                            Modifier.padding(top = 16.dp),
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                else {
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
