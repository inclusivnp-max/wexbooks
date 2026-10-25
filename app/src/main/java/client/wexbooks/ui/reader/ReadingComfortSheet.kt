package client.wexbooks.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import client.wexbooks.data.*
import client.wexbooks.reader.BooksViewModel
import client.wexbooks.ui.motion.WexAnimatedText

@Composable
fun ReadingComfortSheet(
    preferences: ReaderPreferences,
    model: BooksViewModel,
    supportsScroll: Boolean,
    onDone: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val colors = MaterialTheme.colorScheme
    val appDark = preferences.darkTheme ?: androidx.compose.foundation.isSystemInDarkTheme()
    Column(Modifier.fillMaxWidth().testTag("reading-comfort")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Ваше чтение", fontSize = 26.sp, letterSpacing = (-.7).sp)
                Text(
                    "Подстройте страницу под себя",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            TextButton(onClick = onDone, modifier = Modifier.testTag("comfort-done")) {
                Text("Готово")
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Страница", "Свет", "Управление").forEachIndexed { index, title ->
                ComfortChoice(
                    title,
                    tab == index,
                    Modifier.weight(1f).testTag("comfort-tab-$index"),
                ) {
                    tab = index
                }
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 590.dp).testTag("comfort-list"),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (tab == 0) {
                item {
                    ComfortLabel("БЫСТРЫЙ НАСТРОЙ")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ComfortChoice(
                            "Обычный",
                            false,
                            Modifier.weight(1f).testTag("preset-default"),
                        ) {
                            model.updatePreferences { it.resetTypography() }
                        }
                        ComfortChoice(
                            "Книжный",
                            false,
                            Modifier.weight(1f).testTag("preset-book"),
                        ) {
                            model.updatePreferences {
                                it.resetTypography()
                                    .copy(
                                        firstLineIndent = true,
                                        paragraphSpacing = 20,
                                        justify = true,
                                    )
                            }
                        }
                        ComfortChoice(
                            "Крупный",
                            false,
                            Modifier.weight(1f).testTag("preset-large"),
                        ) {
                            model.updatePreferences {
                                it.resetTypography()
                                    .copy(
                                        fontSize = 28,
                                        font = ReaderFont.Sans,
                                        lineHeight = 175,
                                        horizontalMargin = 20,
                                    )
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Страница, в которой хочется задержаться. Спокойный ритм, ясные строки и только ваша история.",
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.background)
                            .padding(18.dp)
                            .testTag("reading-preview"),
                        fontFamily = preferences.font.composeFamily(),
                        fontSize = preferences.fontSize.sp,
                        lineHeight = (preferences.fontSize * preferences.lineHeight / 100f).sp,
                        letterSpacing =
                            (preferences.fontSize * preferences.letterSpacing / 100f).sp,
                        textAlign = if (preferences.justify) TextAlign.Justify else TextAlign.Start,
                        style =
                            androidx.compose.ui.text.TextStyle(
                                textIndent =
                                    if (preferences.firstLineIndent)
                                        androidx.compose.ui.text.style.TextIndent(firstLine = 24.sp)
                                    else androidx.compose.ui.text.style.TextIndent.None
                            ),
                        maxLines = 2,
                    )
                }
                item {
                    ComfortLabel("РАЗМЕР БУКВ")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
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
                        WexAnimatedText(
                            "${preferences.fontSize}",
                            Modifier.testTag("font-size"),
                            fontSize = 20.sp,
                        )
                        TextButton(
                            onClick = {
                                model.updatePreferences {
                                    it.copy(fontSize = (it.fontSize + 2).coerceAtMost(40))
                                }
                            },
                            modifier = Modifier.testTag("font-increase"),
                        ) {
                            Text("А+", fontSize = 26.sp)
                        }
                    }
                }
                item {
                    ComfortLabel("ШРИФТ")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReaderFont.entries.forEach { font ->
                            ComfortChoice(
                                font.label,
                                preferences.font == font,
                                Modifier.weight(1f).testTag("reader-font-${font.name}"),
                            ) {
                                model.updatePreferences { it.copy(font = font) }
                            }
                        }
                    }
                }
                item {
                    ComfortSlider(
                        "Межстрочный интервал",
                        preferences.lineHeight,
                        125..200,
                        14,
                        "line-height",
                        "${preferences.lineHeight}%",
                    ) { value ->
                        model.updatePreferences { it.copy(lineHeight = value) }
                    }
                }
                item {
                    ComfortSlider(
                        "Боковые поля",
                        preferences.horizontalMargin,
                        12..48,
                        8,
                        "page-margins",
                        "${preferences.horizontalMargin}",
                    ) { value ->
                        model.updatePreferences { it.copy(horizontalMargin = value) }
                    }
                }
                item {
                    ComfortSlider(
                        "Между абзацами",
                        preferences.paragraphSpacing,
                        0..120,
                        11,
                        "paragraph-spacing",
                        "${preferences.paragraphSpacing}%",
                    ) { value ->
                        model.updatePreferences { it.copy(paragraphSpacing = value) }
                    }
                }
                item {
                    ComfortSlider(
                        "Расстояние между буквами",
                        preferences.letterSpacing,
                        0..15,
                        14,
                        "letter-spacing",
                        "${preferences.letterSpacing}%",
                    ) { value ->
                        model.updatePreferences { it.copy(letterSpacing = value) }
                    }
                }
                item {
                    ComfortSwitch(
                        "Красная строка",
                        "Отступ в начале абзаца",
                        preferences.firstLineIndent,
                        "first-line-indent",
                    ) { checked ->
                        model.updatePreferences { it.copy(firstLineIndent = checked) }
                    }
                }
                item {
                    ComfortSwitch(
                        "Ровные края",
                        "Выравнивание текста по ширине",
                        preferences.justify,
                        "text-justify",
                    ) { checked ->
                        model.updatePreferences { it.copy(justify = checked) }
                    }
                }
                if (supportsScroll)
                    item {
                        ComfortSwitch(
                            "Вертикальная прокрутка",
                            "Вместо отдельных страниц",
                            preferences.scroll,
                            "reading-scroll",
                        ) { checked ->
                            model.updatePreferences { it.copy(scroll = checked) }
                        }
                    }
                item {
                    TextButton(
                        onClick = { model.updatePreferences { it.resetTypography() } },
                        modifier = Modifier.fillMaxWidth().testTag("reset-typography"),
                    ) {
                        Text("Сбросить оформление текста")
                    }
                }
            } else if (tab == 1) {
                item {
                    ComfortLabel("ЦВЕТ СТРАНИЦЫ")
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ReaderPalette.entries.forEach { palette ->
                            val page = palette.colors(appDark)
                            Column(
                                Modifier.width(82.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(page.background))
                                    .border(
                                        if (preferences.palette == palette) 2.dp else .7.dp,
                                        if (preferences.palette == palette) colors.primary
                                        else colors.onSurface.copy(alpha = .16f),
                                        RoundedCornerShape(18.dp),
                                    )
                                    .selectable(
                                        preferences.palette == palette,
                                        role = Role.RadioButton,
                                        onClick = {
                                            model.updatePreferences { it.copy(palette = palette) }
                                        },
                                    )
                                    .padding(10.dp)
                                    .testTag("palette-${palette.name}"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "Aa",
                                    fontSize = 27.sp,
                                    fontFamily = FontFamily.Serif,
                                    color = Color(page.ink),
                                )
                                Text(
                                    (if (palette == ReaderPalette.Adaptive) "Авто"
                                    else palette.label),
                                    fontSize = 10.sp,
                                    color = Color(page.ink),
                                    textAlign = TextAlign.Center,
                                    minLines = 2,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
                item {
                    val controls = LocalReadingControls.current
                    val value =
                        controls?.brightnessPreview
                            ?: preferences.brightness.takeIf { it >= 0 }
                            ?: controls?.systemBrightness
                            ?: 50
                    ComfortSlider(
                        "Яркость страницы",
                        value,
                        5..100,
                        0,
                        "reader-brightness",
                        if (preferences.brightness < 0 && controls?.brightnessPreview == null)
                            "Системная"
                        else "$value%",
                        onPreview = { controls?.previewBrightness(it) },
                    ) { number ->
                        model.updatePreferences { it.copy(brightness = number) }
                    }
                    TextButton(
                        onClick = {
                            controls?.brightnessPreview = null
                            model.updatePreferences { it.copy(brightness = -1) }
                        },
                        modifier = Modifier.testTag("system-brightness"),
                    ) {
                        Text("Использовать системную")
                    }
                    Text(
                        "Меняется только в читалке. При выходе яркость восстановится.",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                item {
                    ComfortSwitch(
                        "Яркость жестом",
                        "Проведите вверх или вниз по левому краю",
                        preferences.brightnessGesture,
                        "brightness-gesture",
                    ) { checked ->
                        model.updatePreferences { it.copy(brightnessGesture = checked) }
                    }
                }
                item {
                    ComfortSwitch(
                        "Не гасить экран",
                        "Пока книга открыта",
                        preferences.keepScreenOn,
                        "keep-screen-on",
                    ) { checked ->
                        model.updatePreferences { it.copy(keepScreenOn = checked) }
                    }
                }
            } else {
                item {
                    ComfortLabel("ЗОНЫ КАСАНИЙ")
                    TapLayout.entries.forEach { layout ->
                        ComfortChoice(
                            layout.label,
                            preferences.tapLayout == layout,
                            Modifier.fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .testTag("tap-layout-${layout.name}"),
                        ) {
                            model.updatePreferences { it.copy(tapLayout = layout) }
                        }
                    }
                    Text(
                        "Обычная: слева назад, справа вперёд, центр — панели. Для одной руки большая зона находится под большим пальцем. «Без зон» отключает перелистывание касанием; свайпы и кнопки остаются.",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                item {
                    ComfortSwitch(
                        "Кнопки громкости",
                        "Тише — вперёд, громче — назад. Только в книге.",
                        preferences.volumePaging,
                        "volume-paging",
                    ) { checked ->
                        model.updatePreferences { it.copy(volumePaging = checked) }
                    }
                }
                item {
                    ComfortSwitch(
                        "Полный экран",
                        "Скрывать системные полосы вместе с панелями",
                        preferences.immersive,
                        "immersive-reading",
                    ) { checked ->
                        model.updatePreferences { it.copy(immersive = checked) }
                    }
                }
                item {
                    ComfortSwitch(
                        "Подмена букв",
                        "Анимация в стиле Telegram при перелистывании",
                        preferences.animateText,
                        "text-morph-enabled",
                    ) { checked ->
                        model.updatePreferences { it.copy(animateText = checked) }
                    }
                }
                item {
                    ComfortSwitch(
                        "Тактильный отклик",
                        "Лёгкое подтверждение перелистывания",
                        preferences.pageHaptics,
                        "page-haptics",
                    ) { checked ->
                        model.updatePreferences { it.copy(pageHaptics = checked) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComfortLabel(text: String) {
    Text(
        text,
        Modifier.padding(bottom = 10.dp),
        fontSize = 10.sp,
        letterSpacing = 1.3.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ComfortChoice(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) colors.primary.copy(alpha = .14f)
                else colors.onSurface.copy(alpha = .045f)
            )
            .border(
                .7.dp,
                if (selected) colors.primary.copy(alpha = .6f)
                else colors.onSurface.copy(alpha = .08f),
                RoundedCornerShape(16.dp),
            )
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) colors.primary else colors.onSurface,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ComfortSwitch(
    title: String,
    description: String,
    checked: Boolean,
    tag: String,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(
                description,
                Modifier.padding(top = 4.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked, onCheckedChange = onChecked, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun ComfortSlider(
    title: String,
    value: Int,
    range: IntRange,
    steps: Int,
    tag: String,
    valueLabel: String,
    onPreview: (Int) -> Unit = {},
    onCommit: (Int) -> Unit,
) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = 14.sp)
            Text(
                if (draft.toInt() == value) valueLabel else "${draft.toInt()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
        Slider(
            value = draft,
            onValueChange = {
                draft = it
                onPreview(it.toInt())
            },
            onValueChangeFinished = { onCommit(draft.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps,
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
    }
}

fun ReaderFont.composeFamily(): FontFamily =
    when (this) {
        ReaderFont.Serif -> FontFamily.Serif
        ReaderFont.Sans -> FontFamily.SansSerif
        ReaderFont.Mono -> FontFamily.Monospace
    }
