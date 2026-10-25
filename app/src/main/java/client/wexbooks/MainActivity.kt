package client.wexbooks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowInsetsControllerCompat
import client.wexbooks.ui.WexBooksApp
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val incomingUris = MutableStateFlow<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The isolated Dev host stays awake between USB test runs; release follows reader settings.
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (savedInstanceState == null) receive(intent)
        setContent {
            val pending by incomingUris.collectAsState()
            WexBooksApp(
                incomingUris = pending,
                onUrisConsumed = { incomingUris.value = emptyList() },
            ) { darkTheme ->
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean =
        consumeVolumeKey(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean =
        consumeVolumeKey(event) || super.onKeyUp(keyCode, event)

    private fun consumeVolumeKey(event: android.view.KeyEvent): Boolean {
        val volume =
            event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN ||
                event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP
        if (volume) {
            val model =
                androidx.lifecycle
                    .ViewModelProvider(this)[client.wexbooks.reader.BooksViewModel::class.java]
            if (
                model.opened.value != null &&
                    model.preferences.value?.volumePaging == true &&
                    !model.readerInputBlocked
            ) {
                if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                    model.volumePage(event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
                return true
            }
        }
        return false
    }

    override fun onStop() {
        androidx.lifecycle
            .ViewModelProvider(this)[client.wexbooks.reader.BooksViewModel::class.java]
            .flushPosition()
        super.onStop()
    }

    @Suppress("DEPRECATION")
    private fun receive(intent: Intent) {
        val uris =
            when (intent.action) {
                Intent.ACTION_VIEW -> listOfNotNull(intent.data)
                Intent.ACTION_SEND ->
                    listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
                Intent.ACTION_SEND_MULTIPLE ->
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                else -> emptyList()
            }
        incomingUris.value = uris.filter { it.scheme == "content" || it.scheme == "file" }
    }
}
