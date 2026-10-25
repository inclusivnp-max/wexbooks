package client.wexbooks

import androidx.test.platform.app.InstrumentationRegistry
import client.wexbooks.data.ReaderPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.rules.ExternalResource

/** Clears only the dedicated .dev test application, never the release user's library. */
class ReaderTestSetup : ExternalResource() {
    private var app: android.app.Application? = null
    private val keepAwake =
        object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) {
                activity.window.addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
            }

            override fun onActivityCreated(
                activity: android.app.Activity,
                state: android.os.Bundle?,
            ) = Unit

            override fun onActivityStarted(activity: android.app.Activity) = Unit

            override fun onActivityPaused(activity: android.app.Activity) = Unit

            override fun onActivityStopped(activity: android.app.Activity) = Unit

            override fun onActivitySaveInstanceState(
                activity: android.app.Activity,
                state: android.os.Bundle,
            ) = Unit

            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        }

    override fun after() {
        app?.unregisterActivityLifecycleCallbacks(keepAwake)
        app = null
    }

    override fun before() {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as WexBooksApplication
        check(app.packageName == "client.wexbooks.dev")
        this.app = app
        app.registerActivityLifecycleCallbacks(keepAwake)
        runBlocking {
            withContext(Dispatchers.IO) { app.container.database.clearAllTables() }
            app.container.preferences.update { ReaderPreferences() }
        }
    }
}
