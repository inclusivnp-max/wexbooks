package client.wexbooks.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
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
            iterations = 3,
            setupBlock = {
                pressHome()
                killProcess()
                startActivityAndWait()
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
                device.findObject(By.desc("Оглавление")).click()
                check(device.wait(Until.hasObject(By.text("Искусство не спешить")), 5_000))
                device.findObject(By.text("Искусство не спешить")).click()
                device.waitForIdle()
            },
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
            iterations = 3,
            setupBlock = {
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
                val uri =
                    FileProvider.getUriForFile(context, "${context.packageName}.fixtures", file)
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
                var book =
                    device.wait(Until.findObject(By.textContains("Native benchmark")), 10_000)
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
                device.findObject(By.desc("Оглавление")).click()
                check(device.wait(Until.hasObject(By.text("Глава 1. Нативный тест")), 5_000))
                device.findObject(By.text("Глава 1. Нативный тест")).click()
                device.waitForIdle()
            },
        ) {
            repeat(12) {
                checkNotNull(device.findObject(By.desc("Следующая страница"))).click()
                device.waitForIdle()
            }
        }
}
