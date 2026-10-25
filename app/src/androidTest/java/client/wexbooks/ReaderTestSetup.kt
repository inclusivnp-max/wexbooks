package client.wexbooks

import androidx.test.platform.app.InstrumentationRegistry
import client.wexbooks.data.ReaderPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.rules.ExternalResource

/** Clears only the dedicated .dev test application, never the release user's library. */
class ReaderTestSetup : ExternalResource() {
    override fun before() {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as WexBooksApplication
        check(app.packageName == "client.wexbooks.dev")
        runBlocking {
            withContext(Dispatchers.IO) { app.container.database.clearAllTables() }
            app.container.preferences.update { ReaderPreferences() }
        }
    }
}
