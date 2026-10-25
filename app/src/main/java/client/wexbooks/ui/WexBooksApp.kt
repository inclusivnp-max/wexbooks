package client.wexbooks.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import client.wexbooks.ui.components.WexIcons
import client.wexbooks.ui.glass.wexGlass
import client.wexbooks.ui.motion.LocalReducedMotion
import client.wexbooks.ui.motion.WexMotion
import client.wexbooks.ui.theme.WexBooksTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Production shell: local library, durable preferences, and the real EPUB navigator. */
@Composable
fun WexBooksApp(
    incomingUris: List<android.net.Uri> = emptyList(),
    onUrisConsumed: () -> Unit = {},
    model: client.wexbooks.reader.BooksViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onDarkThemeChanged: (Boolean) -> Unit = {},
) {
    val settings by model.preferences.collectAsStateWithLifecycle()
    val opened by model.opened.collectAsStateWithLifecycle()
    val opening by model.opening.collectAsStateWithLifecycle()
    val importing by model.importing.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val prefs = settings
    if (prefs == null) {
        WexBooksTheme(systemDark) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }
    val darkTheme = prefs.darkTheme ?: systemDark
    val reduceMotion = prefs.reduceMotion
    val reduceTransparency = prefs.reduceTransparency
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            model.importUris(uris)
        }
    val onImport: () -> Unit = {
        picker.launch(
            arrayOf(
                "application/epub+zip",
                "text/plain",
                "application/x-fictionbook+xml",
                "application/xml",
                "application/zip",
                "application/octet-stream",
            )
        )
    }
    SideEffect { onDarkThemeChanged(darkTheme) }
    LaunchedEffect(incomingUris) {
        if (incomingUris.isNotEmpty()) {
            model.importUris(incomingUris)
            onUrisConsumed()
        }
    }
    BackHandler(enabled = opened == null && tab != 0) { tab = 0 }
    CompositionLocalProvider(LocalReducedMotion provides reduceMotion) {
        WexBooksTheme(darkTheme) {
            val colors = MaterialTheme.colorScheme
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize().background(colors.background)) {
                AnimatedContent(
                    targetState = opened,
                    modifier = Modifier.fillMaxSize(),
                    contentKey = { it?.book?.id },
                    transitionSpec = {
                        WexMotion.pageTransition(reduceMotion, targetState != null).using(null)
                    },
                    label = "open publication",
                ) { session ->
                    if (session != null) {
                        when (session.reader) {
                            is client.wexbooks.reader.EpubReaderSession ->
                                client.wexbooks.ui.reader.EpubReaderScreen(
                                    session,
                                    prefs,
                                    darkTheme,
                                    model,
                                )
                            is client.wexbooks.reader.nativeengine.NativeReaderSession ->
                                client.wexbooks.ui.reader.NativeReaderScreen(
                                    session,
                                    prefs,
                                    darkTheme,
                                    model,
                                )
                        }
                    } else {
                        val books by model.books.collectAsStateWithLifecycle()
                        val bookmarks by model.bookmarks.collectAsStateWithLifecycle()
                        Box(Modifier.fillMaxSize()) {
                            Column(
                                Modifier.fillMaxSize()
                                    .layerBackdrop(backdrop)
                                    .background(colors.background)
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                                    .padding(start = 24.dp, end = 24.dp, top = 20.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().height(52.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "WEXBOOKS",
                                        fontSize = 12.sp,
                                        letterSpacing = 3.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(28.dp))
                                AnimatedContent(
                                    targetState = tab,
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    transitionSpec = {
                                        WexMotion.pageTransition(
                                                reduceMotion,
                                                targetState > initialState,
                                            )
                                            .using(null)
                                    },
                                    label = "library destinations",
                                ) { currentTab ->
                                    when (currentTab) {
                                        0 ->
                                            client.wexbooks.ui.library.BookLibraryScreen(
                                                books,
                                                importing,
                                                onImport,
                                                { model.openBook(it.id) },
                                                model::deleteBook,
                                            ) {
                                                LibraryContent(
                                                    { model.openSample() },
                                                    onImport,
                                                    importing == null,
                                                )
                                            }
                                        1 ->
                                            if (bookmarks.isEmpty()) {
                                                Column(
                                                    Modifier.fillMaxSize()
                                                        .verticalScroll(rememberScrollState())
                                                        .padding(bottom = 132.dp)
                                                ) {
                                                    NotesContent()
                                                }
                                            } else {
                                                client.wexbooks.ui.library.SavedBookmarksScreen(
                                                    bookmarks,
                                                    { model.openBook(it.bookId, it.locatorJson) },
                                                    model::deleteBookmark,
                                                )
                                            }
                                        else ->
                                            Column(
                                                Modifier.fillMaxSize()
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(bottom = 132.dp)
                                            ) {
                                                AppearanceContent(
                                                    darkTheme,
                                                    reduceTransparency,
                                                    reduceMotion,
                                                    { value ->
                                                        model.updatePreferences {
                                                            it.copy(darkTheme = value)
                                                        }
                                                    },
                                                    { value ->
                                                        model.updatePreferences {
                                                            it.copy(reduceTransparency = value)
                                                        }
                                                    },
                                                    { value ->
                                                        model.updatePreferences {
                                                            it.copy(reduceMotion = value)
                                                        }
                                                    },
                                                )
                                            }
                                    }
                                }
                            }
                            GlassIconButton(
                                if (darkTheme) WexIcons.Sun else WexIcons.Moon,
                                "Сменить тему",
                                backdrop,
                                darkTheme,
                                reduceTransparency,
                                Modifier.align(Alignment.TopEnd)
                                    .statusBarsPadding()
                                    .padding(top = 20.dp, end = 24.dp),
                            ) {
                                model.updatePreferences { it.copy(darkTheme = !darkTheme) }
                            }
                            GlassNavigation(
                                tab,
                                { tab = it },
                                backdrop,
                                darkTheme,
                                reduceTransparency,
                                Modifier.align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                            )
                        }
                    }
                }
                if (opening) {
                    Box(
                        Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(
                                "Открываем книгу…",
                                Modifier.padding(top = 16.dp),
                                color = colors.onSurfaceVariant,
                            )
                            TextButton(onClick = model::closeBook) { Text("Отмена") }
                        }
                    }
                }
                message?.let { text ->
                    AlertDialog(
                        onDismissRequest = model::dismissMessage,
                        title = { Text("Открытие книги") },
                        text = { Text(text) },
                        confirmButton = {
                            TextButton(onClick = model::dismissMessage) { Text("Понятно") }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryContent(onOpenDemo: () -> Unit, onImport: () -> Unit, importEnabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.testTag("library-screen")) {
        Text(
            "Библиотека",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.3).sp,
            color = colors.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Истории, в которые хочется вернуться.",
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onImport,
            enabled = importEnabled,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("import-books"),
        ) {
            Text("Добавить книгу")
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.fillMaxWidth()
                .height(310.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF425C65), Color(0xFF233B46))))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width * 0.82f, size.height * 0.32f)
                drawCircle(Color(0xFFD7B89C), size.width * 0.26f, center)
                repeat(10) { index ->
                    val inset = index * 13.dp.toPx()
                    drawArc(
                        Color(0xFFCDDAD0).copy(alpha = 0.2f),
                        190f,
                        180f,
                        false,
                        topLeft = Offset(size.width * 0.28f - inset, size.height * 0.15f - inset),
                        size = Size(size.width * 0.9f + inset * 2, size.height * 0.9f + inset * 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(0.8.dp.toPx()),
                    )
                }
            }
            Column(
                Modifier.fillMaxSize().padding(26.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "ПЕРВАЯ ГЛАВА",
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        color = Color.White.copy(alpha = 0.74f),
                    )
                    Text(
                        "01",
                        fontFamily = FontFamily.Serif,
                        fontSize = 17.sp,
                        color = Color.White.copy(alpha = 0.74f),
                    )
                }
                Column {
                    Text(
                        "Тихие\nстраницы",
                        fontSize = 42.sp,
                        lineHeight = 44.sp,
                        letterSpacing = (-1).sp,
                        fontFamily = FontFamily.Serif,
                        color = Color(0xFFFFF8ED),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Немного тишины. И целый новый мир.",
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        color = Color.White.copy(alpha = 0.76f),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 60.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.66f))
                .clickable(role = Role.Button, onClick = onOpenDemo)
                .testTag("open-reading-demo")
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(WexIcons.Book, null, Modifier.size(22.dp), tint = colors.primary)
            Spacer(Modifier.width(14.dp))
            Text(
                "Попробовать чтение",
                Modifier.weight(1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
            Icon(WexIcons.Arrow, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Ваше пространство",
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Добавьте EPUB, FB2 или TXT — книга останется\nдоступной даже без интернета.",
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun NotesContent() {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag("notes-screen")) {
        Text(
            "Заметки",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.3).sp,
            color = colors.onSurface,
        )
        Text(
            "Слова, которые остаются с вами.",
            Modifier.padding(top = 8.dp),
            fontSize = 14.sp,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(60.dp))
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(WexIcons.Notes, null, Modifier.size(32.dp), tint = colors.primary)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Здесь останется важное",
            fontSize = 22.sp,
            fontFamily = FontFamily.Serif,
            color = colors.onSurface,
        )
        Text(
            "Цитаты и мысли из ваших книг соберутся\nв одном спокойном месте.",
            Modifier.padding(top = 12.dp),
            fontSize = 15.sp,
            lineHeight = 24.sp,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppearanceContent(
    darkTheme: Boolean,
    reduceTransparency: Boolean,
    reduceMotion: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onTransparencyChange: (Boolean) -> Unit,
    onMotionChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.testTag("settings-screen")) {
        Text(
            "Оформление",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.3).sp,
            color = colors.onSurface,
        )
        Text(
            "Красиво. Спокойно. По-вашему.",
            Modifier.padding(top = 8.dp),
            fontSize = 14.sp,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Column(
            Modifier.clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.6f))
        ) {
            SettingRow(
                "Тёмное оформление",
                "Для вечерних историй",
                darkTheme,
                "dark-theme-switch",
                onDarkThemeChange,
            )
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = colors.outlineVariant)
            SettingRow(
                "Меньше прозрачности",
                "Плотные панели без размытия",
                reduceTransparency,
                "reduce-transparency-switch",
                onTransparencyChange,
            )
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = colors.outlineVariant)
            SettingRow(
                "Меньше движения",
                "Мгновенные переходы без пружин",
                reduceMotion,
                "reduce-motion-switch",
                onMotionChange,
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Стекло вокруг истории. Не поверх неё.",
            fontFamily = FontFamily.Serif,
            fontSize = 23.sp,
            lineHeight = 30.sp,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Плавающие панели ловят свет и мягко преломляют фон. Страница остаётся чистой — только вы и текст.",
            fontSize = 14.sp,
            lineHeight = 23.sp,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            "LIQUID GLASS · BACKDROP 2.0.1",
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                Modifier.padding(top = 5.dp),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked,
            onCheckedChange = onChange,
            modifier = Modifier.testTag(tag).semantics { contentDescription = title },
        )
    }
}

@Composable
private fun GlassNavigation(
    selected: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    darkTheme: Boolean,
    reduceTransparency: Boolean,
    modifier: Modifier = Modifier,
) {
    val titles = listOf("Библиотека", "Заметки", "Настройки")
    val icons = listOf(WexIcons.Book, WexIcons.Notes, WexIcons.Settings)
    val reducedMotion = LocalReducedMotion.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .wexGlass(backdrop, darkTheme, reduceTransparency, 34.dp)
            .padding(6.dp)
    ) {
        val itemWidth = maxWidth / titles.size
        val indicatorOffset by
            animateDpAsState(
                targetValue = itemWidth * selected,
                animationSpec = WexMotion.springResponse(reducedMotion),
                label = "sliding glass selection",
            )
        Box(
            Modifier.offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                .width(itemWidth)
                .height(64.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.11f))
                .testTag("tab-indicator")
        )
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            titles.forEachIndexed { index, title ->
                val active = selected == index
                val tint by
                    animateColorAsState(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = WexMotion.response(reducedMotion),
                        label = "tab tint",
                    )
                Column(
                    Modifier.weight(1f)
                        .heightIn(min = 64.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .selectable(active, role = Role.Tab, onClick = { onSelect(index) })
                        .testTag("tab-$index")
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                ) {
                    Icon(icons[index], null, Modifier.size(22.dp), tint = tint)
                    Text(
                        title,
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = tint,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
internal fun GlassIconButton(
    icon: ImageVector,
    description: String,
    backdrop: Backdrop,
    darkTheme: Boolean,
    reduceTransparency: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current
    val scale by
        animateFloatAsState(
            if (pressed && !reducedMotion) 0.94f else 1f,
            animationSpec = WexMotion.springResponse(reducedMotion),
            label = "glass press",
        )
    Box(
        modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .wexGlass(backdrop, darkTheme, reduceTransparency, 24.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactions,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}
