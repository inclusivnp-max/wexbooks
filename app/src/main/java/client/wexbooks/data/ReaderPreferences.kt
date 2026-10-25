package client.wexbooks.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.readerDataStore by preferencesDataStore("reader_preferences")

data class ReaderPreferences(
    val darkTheme: Boolean? = null,
    val reduceTransparency: Boolean = false,
    val reduceMotion: Boolean = false,
    val fontSize: Int = 22,
    val scroll: Boolean = false,
    val font: ReaderFont = ReaderFont.Serif,
    val palette: ReaderPalette = ReaderPalette.Adaptive,
    val lineHeight: Int = 155,
    val horizontalMargin: Int = 26,
    val paragraphSpacing: Int = 60,
    val letterSpacing: Int = 0,
    val firstLineIndent: Boolean = false,
    val justify: Boolean = false,
    val brightness: Int = -1,
    val keepScreenOn: Boolean = true,
    val volumePaging: Boolean = false,
    val brightnessGesture: Boolean = false,
    val tapLayout: TapLayout = TapLayout.Classic,
    val immersive: Boolean = true,
    val animateText: Boolean = true,
    val pageHaptics: Boolean = false,
)

class ReaderPreferencesStore(context: Context) {
    private val store = context.applicationContext.readerDataStore
    private val dark = booleanPreferencesKey("dark_theme")
    private val transparency = booleanPreferencesKey("reduce_transparency")
    private val motion = booleanPreferencesKey("reduce_motion")
    private val font = intPreferencesKey("font_size")
    private val scroll = booleanPreferencesKey("scroll")
    private val typeface = stringPreferencesKey("reader_font")
    private val palette = stringPreferencesKey("reader_palette")
    private val lineHeight = intPreferencesKey("line_height")
    private val margin = intPreferencesKey("horizontal_margin")
    private val paragraph = intPreferencesKey("paragraph_spacing")
    private val letters = intPreferencesKey("letter_spacing")
    private val indent = booleanPreferencesKey("first_line_indent")
    private val justify = booleanPreferencesKey("justify")
    private val brightness = intPreferencesKey("reader_brightness")
    private val keepOn = booleanPreferencesKey("keep_screen_on")
    private val volume = booleanPreferencesKey("volume_paging")
    private val brightnessGesture = booleanPreferencesKey("brightness_gesture")
    private val tapLayout = stringPreferencesKey("tap_layout")
    private val immersive = booleanPreferencesKey("immersive")
    private val animateText = booleanPreferencesKey("animate_text")
    private val pageHaptics = booleanPreferencesKey("page_haptics")

    val preferences: Flow<ReaderPreferences> =
        store.data
            .catch {
                if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
                else throw it
            }
            .map(::decode)

    suspend fun update(transform: (ReaderPreferences) -> ReaderPreferences) {
        store.edit { data ->
            val next = transform(decode(data)).normalized()
            next.darkTheme?.let { data[dark] = it } ?: data.remove(dark)
            data[transparency] = next.reduceTransparency
            data[motion] = next.reduceMotion
            data[font] = next.fontSize
            data[scroll] = next.scroll
            data[typeface] = next.font.name
            data[palette] = next.palette.name
            data[lineHeight] = next.lineHeight
            data[margin] = next.horizontalMargin
            data[paragraph] = next.paragraphSpacing
            data[letters] = next.letterSpacing
            data[indent] = next.firstLineIndent
            data[justify] = next.justify
            data[brightness] = next.brightness
            data[keepOn] = next.keepScreenOn
            data[volume] = next.volumePaging
            data[brightnessGesture] = next.brightnessGesture
            data[tapLayout] = next.tapLayout.name
            data[immersive] = next.immersive
            data[animateText] = next.animateText
            data[pageHaptics] = next.pageHaptics
        }
    }

    private fun decode(data: Preferences) =
        ReaderPreferences(
                darkTheme = data[dark],
                reduceTransparency = data[transparency] ?: false,
                reduceMotion = data[motion] ?: false,
                fontSize = data[font] ?: 22,
                scroll = data[scroll] ?: false,
                font = enumValue(data[typeface], ReaderFont.Serif),
                palette = enumValue(data[palette], ReaderPalette.Adaptive),
                lineHeight = data[lineHeight] ?: 155,
                horizontalMargin = data[margin] ?: 26,
                paragraphSpacing = data[paragraph] ?: 60,
                letterSpacing = data[letters] ?: 0,
                firstLineIndent = data[indent] ?: false,
                justify = data[justify] ?: false,
                brightness = data[brightness] ?: -1,
                keepScreenOn = data[keepOn] ?: true,
                volumePaging = data[volume] ?: false,
                brightnessGesture = data[brightnessGesture] ?: false,
                tapLayout = enumValue(data[tapLayout], TapLayout.Classic),
                immersive = data[immersive] ?: true,
                animateText = data[animateText] ?: true,
                pageHaptics = data[pageHaptics] ?: false,
            )
            .normalized()
}

private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
    value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
