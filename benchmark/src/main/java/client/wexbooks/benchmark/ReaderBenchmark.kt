package client.wexbooks.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class ReaderBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()
    private val target = "client.wexbooks.benchmark"
    private val pageIterations
        get() =
            InstrumentationRegistry.getArguments()
                .getString("pageIterations")
                ?.toIntOrNull()
                ?.coerceIn(1, 10) ?: 3

    /** Setup is not a tap benchmark: an incoming heads-up must not steal this click. */
    private fun clickSetupControl(description: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun find(
            node: android.view.accessibility.AccessibilityNodeInfo
        ): android.view.accessibility.AccessibilityNodeInfo? {
            if (node.packageName?.toString() != target) return null
            if (node.contentDescription?.toString() == description && node.isClickable) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { child ->
                find(child)?.let {
                    return it
                }
            }
            return null
        }
        val roots =
            automation.windows.mapNotNull { it.root } + listOfNotNull(automation.rootInActiveWindow)
        val control = roots.firstNotNullOfOrNull { find(it) }
        checkNotNull(control) { "Reader setup control missing: $description" }
        check(control.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
    }

    @Test
    fun libraryColdStart() =
        benchmark.measureRepeated(
            packageName = target,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
        }

    @Test
    fun epubPageTurns() =
        benchmark.measureRepeated(
            packageName = target,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = pageIterations,
            setupBlock = { openEpub() },
        ) {
            repeat(12) {
                checkNotNull(device.findObject(By.desc("Следующая страница"))).click()
                device.waitForIdle()
            }
        }

    @Test
    fun nativePageTurns() =
        benchmark.measureRepeated(
            packageName = target,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = pageIterations,
            setupBlock = { openNative() },
        ) {
            repeat(12) {
                checkNotNull(device.findObject(By.desc("Следующая страница"))).click()
                device.waitForIdle()
            }
        }

    private fun MacrobenchmarkScope.openEpub() {
        pressHome()
        killProcess()
        try {
            startActivityAndWait()
        } catch (failure: IllegalStateException) {
            // Android 17 can return an empty am-start completion even after opening the app.
            // This is unmeasured setup: confirm the actual target UI instead of assuming launch.
            if (failure.message?.startsWith("Unable to confirm activity launch completion") != true)
                throw failure
            check(device.wait(Until.hasObject(By.pkg(target).text("Библиотека")), 5_000)) {
                "Target library did not appear after am-start completion failure"
            }
        }
        check(device.wait(Until.hasObject(By.text("Библиотека")), 15_000))
        val existing = device.findObject(By.textContains("Тихие страницы"))
        if (existing != null) existing.click()
        else {
            var button = device.findObject(By.text("Попробовать чтение"))
            repeat(3) {
                if (button == null) {
                    device.swipe(
                        device.displayWidth / 2,
                        device.displayHeight * 3 / 4,
                        device.displayWidth / 2,
                        device.displayHeight / 3,
                        12,
                    )
                    button = device.findObject(By.text("Попробовать чтение"))
                }
            }
            checkNotNull(button).click()
        }
        check(device.wait(Until.hasObject(By.textContains("% прочитано")), 25_000))
        clickSetupControl("Оглавление")
        device.waitForIdle()
        val chapter =
            device.wait(
                Until.findObject(By.desc("Открыть главу: Искусство не спешить")),
                5_000,
            )
        if (chapter == null) {
            val path =
                InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let {
                    File(it)
                }
            if (path != null) {
                path.mkdirs()
                device.takeScreenshot(File(path, "epub-toc-failure.png"))
                File(path, "epub-toc-failure.xml").outputStream().use {
                    device.dumpWindowHierarchy(it)
                }
            }
        }
        checkNotNull(chapter).click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.openNative() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val file = File(context.cacheDir, "fixtures/Native benchmark.txt")
        file.parentFile!!.mkdirs()
        if (!file.exists())
            file.writeText(
                buildString {
                    repeat(10) { chapter ->
                        append("Глава ${chapter + 1}. Нативный тест\n\n")
                        repeat(25) {
                            append(
                                "Это воспроизводимый текст для проверки собственной вёрстки WexBooks. Книга остаётся спокойной, а переход к следующей странице должен сразу отвечать на касание.\n\n"
                            )
                        }
                    }
                }
            )
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fixtures", file)
        pressHome()
        killProcess()
        // force-stop revokes temporary grants, so grant only after killing the target.
        context.grantUriPermission(target, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivityAndWait(
            Intent(Intent.ACTION_VIEW)
                .setComponent(ComponentName(target, "client.wexbooks.MainActivity"))
                .setDataAndType(uri, "text/plain")
        )
        check(device.wait(Until.hasObject(By.text("Библиотека")), 15_000))
        var book = device.wait(Until.findObject(By.textContains("Native benchmark")), 10_000)
        repeat(3) {
            if (book == null) {
                device.swipe(
                    device.displayWidth / 2,
                    device.displayHeight * 3 / 4,
                    device.displayWidth / 2,
                    device.displayHeight / 3,
                    12,
                )
                book = device.findObject(By.textContains("Native benchmark"))
            }
        }
        checkNotNull(book).click()
        check(device.wait(Until.hasObject(By.textContains("% прочитано")), 20_000))
        clickSetupControl("Оглавление")
        check(device.wait(Until.hasObject(By.text("Глава 1. Нативный тест")), 5_000))
        device.findObject(By.text("Глава 1. Нативный тест")).click()
        device.waitForIdle()
    }

    @Test fun nativeFocusTransitions() = focusTransitions(native = true)

    @Test fun epubFocusTransitions() = focusTransitions(native = false)

    private fun focusTransitions(native: Boolean) =
        benchmark.measureRepeated(
            packageName = target,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = 2,
            setupBlock = { if (native) openNative() else openEpub() },
        ) {
            repeat(4) {
                // Measure the real page tap, away from the system notification strip.
                val bounds = checkNotNull(device.findObject(By.pkg(target))).visibleBounds
                device.click(bounds.centerX(), bounds.centerY())
                check(device.wait(Until.gone(By.desc("Скрыть панели")), 3_000)) {
                    "Focus tap at ${bounds.centerX()},${bounds.centerY()} in $bounds did not hide controls"
                }
                device.waitForIdle()
                device.pressBack()
                check(device.wait(Until.hasObject(By.desc("Скрыть панели")), 3_000))
                device.waitForIdle()
            }
        }
}
