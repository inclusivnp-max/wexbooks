package client.wexbooks.data

import client.wexbooks.reader.core.ReaderStyle

enum class ReaderFont(val label: String, val css: String) {
    Serif("Книжный", "serif"),
    Sans("Без засечек", "sans-serif"),
    Mono("Моно", "monospace"),
}

enum class ReaderPalette(val label: String) {
    Adaptive("Как в приложении"),
    Paper("Бумага"),
    Sepia("Тёплая"),
    Night("Ночь"),
    Black("OLED"),
}

enum class TapLayout(val label: String) {
    Classic("Обычная"),
    RightHand("Правая рука"),
    LeftHand("Левая рука"),
    ButtonsOnly("Без зон"),
}

enum class ReaderTap {
    Previous,
    Controls,
    Next,
}

/** Fractions, not pixels: the same controls on phones, tablets and larger system text. */
fun readerTap(x: Float, layout: TapLayout): ReaderTap =
    when (layout) {
        TapLayout.Classic ->
            when {
                x < .30f -> ReaderTap.Previous
                x > .70f -> ReaderTap.Next
                else -> ReaderTap.Controls
            }
        TapLayout.RightHand ->
            when {
                x < .20f -> ReaderTap.Previous
                x > .45f -> ReaderTap.Next
                else -> ReaderTap.Controls
            }
        TapLayout.LeftHand ->
            when {
                x < .55f -> ReaderTap.Next
                x > .80f -> ReaderTap.Previous
                else -> ReaderTap.Controls
            }
        TapLayout.ButtonsOnly -> ReaderTap.Controls
    }

data class PageColors(
    val background: Int,
    val ink: Int,
    val surface: Int,
    val muted: Int,
    val accent: Int,
    val dark: Boolean,
)

fun ReaderPalette.colors(appDark: Boolean): PageColors =
    when (this) {
        ReaderPalette.Adaptive ->
            if (appDark) ReaderPalette.Night.colors(false) else ReaderPalette.Paper.colors(false)
        ReaderPalette.Paper ->
            PageColors(
                0xFFF7F6F2.toInt(),
                0xFF242A31.toInt(),
                0xFFFEFDFB.toInt(),
                0xFF636B75.toInt(),
                0xFF406B74.toInt(),
                false,
            )
        ReaderPalette.Sepia ->
            PageColors(
                0xFFF2E7D2.toInt(),
                0xFF382F25.toInt(),
                0xFFF8EEDC.toInt(),
                0xFF685D4C.toInt(),
                0xFF4E665E.toInt(),
                false,
            )
        ReaderPalette.Night ->
            PageColors(
                0xFF141A20.toInt(),
                0xFFF1EEE8.toInt(),
                0xFF1E262F.toInt(),
                0xFFA8B0BB.toInt(),
                0xFFABD3D9.toInt(),
                true,
            )
        ReaderPalette.Black ->
            PageColors(
                0xFF000000.toInt(),
                0xFFCACBC5.toInt(),
                0xFF111518.toInt(),
                0xFF9CA5AB.toInt(),
                0xFFA4CBD1.toInt(),
                true,
            )
    }

fun ReaderPreferences.normalized() =
    copy(
        fontSize = fontSize.coerceIn(16, 40),
        lineHeight = lineHeight.coerceIn(125, 200),
        horizontalMargin = horizontalMargin.coerceIn(12, 48),
        paragraphSpacing = paragraphSpacing.coerceIn(0, 120),
        letterSpacing = letterSpacing.coerceIn(0, 15),
        brightness = if (brightness < 0) -1 else brightness.coerceIn(5, 100),
    )

fun ReaderPreferences.resetTypography() =
    copy(
        fontSize = 22,
        font = ReaderFont.Serif,
        lineHeight = 155,
        horizontalMargin = 26,
        paragraphSpacing = 60,
        letterSpacing = 0,
        firstLineIndent = false,
        justify = false,
    )

fun ReaderPreferences.readingStyle(appDark: Boolean): ReaderStyle {
    val safe = normalized()
    val colors = safe.palette.colors(safe.darkTheme ?: appDark)
    return ReaderStyle(
        safe.fontSize,
        safe.scroll,
        colors.dark,
        safe.font.css,
        safe.lineHeight / 100.0,
        safe.horizontalMargin,
        safe.paragraphSpacing / 100.0,
        safe.letterSpacing / 100.0,
        safe.firstLineIndent,
        safe.justify,
        colors.background,
        colors.ink,
    )
}

fun ReaderPreferences.toggleReaderNight(dark: Boolean): ReaderPreferences =
    if (palette == ReaderPalette.Adaptive) copy(darkTheme = !dark)
    else copy(palette = if (dark) ReaderPalette.Paper else ReaderPalette.Night)
