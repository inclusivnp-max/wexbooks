@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package client.wexbooks.ui.reader

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import client.wexbooks.data.ReaderPreferences
import client.wexbooks.reader.BooksViewModel
import client.wexbooks.reader.EpubReaderSession
import client.wexbooks.reader.OpenedBook
import client.wexbooks.ui.GlassIconButton
import client.wexbooks.ui.components.ReaderIconAction
import client.wexbooks.ui.components.WexIcons
import client.wexbooks.ui.glass.wexGlass
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.readium.navigator.web.reflowable.ReflowableWebRendition
import org.readium.navigator.web.reflowable.preferences.ReflowableWebPreferences
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.shared.publication.Link

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderScreen(
    session: OpenedBook,
    preferences: ReaderPreferences,
    darkTheme: Boolean,
    model: BooksViewModel,
) {
    val engine = session.reader as EpubReaderSession
    val colors = MaterialTheme.colorScheme
    val backdrop = rememberCanvasBackdrop { drawRect(colors.background) }
    val controller = engine.rendition.controller
    val location = controller?.location?.toLocator()
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<String?>(null) }
    var bookmarked by remember(controller?.location) { mutableStateOf(false) }
    val background = colors.background.toArgb()
    val foreground = colors.onBackground.toArgb()
    LaunchedEffect(controller, preferences.fontSize, preferences.scroll, background, foreground) {
        controller?.preferences =
            ReflowableWebPreferences(
                fontSize = preferences.fontSize / 16.0,
                scroll = preferences.scroll,
                backgroundColor = ReadiumColor(background),
                textColor = ReadiumColor(foreground),
                overridePublisherColors = true,
                lineHeight = 1.55,
                hyphens = true,
                minMargins = 1.2,
            )
    }
    LaunchedEffect(session) {
        snapshotFlow { engine.rendition.controller?.location?.toLocator() }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { engine.publish(it) }
    }
    BackHandler { if (sheet != null) sheet = null else model.closeBook() }
    Box(Modifier.fillMaxSize().background(colors.background).testTag("epub-reader")) {
        Box(Modifier.fillMaxSize()) {
            ReflowableWebRendition(
                state = engine.rendition,
                modifier =
                    Modifier.fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(top = 80.dp, bottom = 106.dp),
            )
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
                Text(
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
                enabled = controller?.canMoveBackward == true,
            ) {
                scope.launch { controller?.moveBackward() }
            }
            TextButton(onClick = { sheet = "type" }, modifier = Modifier.heightIn(min = 48.dp)) {
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
            ReaderIconAction(if (darkTheme) WexIcons.Sun else WexIcons.Moon, "Сменить тему") {
                model.updatePreferences { it.copy(darkTheme = !darkTheme) }
            }
            ReaderIconAction(
                WexIcons.Arrow,
                "Следующая страница",
                enabled = controller?.canMoveForward == true,
            ) {
                scope.launch { controller?.moveForward() }
            }
        }
        if (sheet != null) {
            ModalBottomSheet(
                onDismissRequest = { sheet = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = colors.surface,
            ) {
                if (sheet == "type") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)
                    ) {
                        Text("Как вам удобно", fontSize = 24.sp)
                        Spacer(Modifier.height(24.dp))
                        Row(
                            Modifier.fillMaxWidth(),
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
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Вертикальная прокрутка", Modifier.weight(1f))
                            Switch(
                                preferences.scroll,
                                onCheckedChange = { checked ->
                                    model.updatePreferences { it.copy(scroll = checked) }
                                },
                                modifier = Modifier.testTag("reading-scroll"),
                            )
                        }
                    }
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
