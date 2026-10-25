@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package client.wexbooks.reader

import client.wexbooks.reader.core.ReaderStyle
import org.readium.navigator.web.reflowable.preferences.ReflowableWebPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign

fun ReaderStyle.readiumPreferences() =
    ReflowableWebPreferences(
        fontSize = fontSize / 16.0,
        scroll = scroll,
        fontFamily = FontFamily(fontFamily),
        backgroundColor =
            Color(backgroundColor ?: if (darkTheme) 0xFF141A20.toInt() else 0xFFF7F6F2.toInt()),
        textColor = Color(textColor ?: if (darkTheme) 0xFFF1EEE8.toInt() else 0xFF242A31.toInt()),
        overridePublisherColors = true,
        lineHeight = lineHeight,
        hyphens = true,
        minMargins = 1.2 * horizontalMargin / 26.0,
        paragraphSpacing = paragraphSpacing,
        paragraphIndent = if (firstLineIndent) 1.2 else 0.0,
        letterSpacing = letterSpacing * 2,
        textAlign = if (justify) TextAlign.JUSTIFY else TextAlign.START,
    )
