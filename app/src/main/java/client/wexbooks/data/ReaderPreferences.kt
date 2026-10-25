package client.wexbooks.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
)

class ReaderPreferencesStore(context: Context) {
    private val store = context.applicationContext.readerDataStore
    private val dark = booleanPreferencesKey("dark_theme")
    private val transparency = booleanPreferencesKey("reduce_transparency")
    private val motion = booleanPreferencesKey("reduce_motion")
    private val font = intPreferencesKey("font_size")
    private val scroll = booleanPreferencesKey("scroll")

    val preferences: Flow<ReaderPreferences> =
        store.data
            .catch {
                if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
                else throw it
            }
            .map(::decode)

    suspend fun update(transform: (ReaderPreferences) -> ReaderPreferences) {
        store.edit { data ->
            val next = transform(decode(data))
            next.darkTheme?.let { data[dark] = it } ?: data.remove(dark)
            data[transparency] = next.reduceTransparency
            data[motion] = next.reduceMotion
            data[font] = next.fontSize.coerceIn(16, 34)
            data[scroll] = next.scroll
        }
    }

    private fun decode(data: Preferences) =
        ReaderPreferences(
            darkTheme = data[dark],
            reduceTransparency = data[transparency] ?: false,
            reduceMotion = data[motion] ?: false,
            fontSize = (data[font] ?: 22).coerceIn(16, 34),
            scroll = data[scroll] ?: false,
        )
}
