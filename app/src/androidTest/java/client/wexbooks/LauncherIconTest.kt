package client.wexbooks

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Offscreen Android drawable rendering: no launcher settings or user data are changed. */
class LauncherIconTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun render(drawable: Drawable, size: Int = 512): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(it))
        }

    private fun save(bitmap: Bitmap, name: String) {
        val dir =
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun launcherResolvesBothAdaptiveLayersAndDedicatedMonochrome() {
        for (id in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val icon = context.getDrawable(id) as AdaptiveIconDrawable
            assertNotNull(icon.background)
            assertNotNull(icon.foreground)
            if (Build.VERSION.SDK_INT >= 33) assertNotNull(icon.monochrome)
        }
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        assertTrue(icon is AdaptiveIconDrawable)
    }

    @Test
    fun bothMarksStayInsideTheCircularSafeZone() {
        for (id in listOf(R.drawable.ic_launcher_foreground, R.drawable.ic_launcher_monochrome)) {
            val bitmap = render(context.getDrawable(id)!!, 432)
            var ink = 0
            var left = 432
            var right = 0
            var top = 432
            var bottom = 0
            for (y in 0 until 432) for (x in 0 until 432) {
                if (Color.alpha(bitmap.getPixel(x, y)) <= 8) continue
                ink++
                val dx = (x + .5) / 4 - 54
                val dy = (y + .5) / 4 - 54
                assertTrue("Safe zone at $x,$y for $id", dx * dx + dy * dy <= 33.3 * 33.3)
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
            }
            assertTrue(ink > 15_000)
            assertTrue(right - left >= 48 * 4)
            assertTrue(bottom - top >= 47 * 4)
        }
    }

    @Test
    fun actualPixelRasterIsConsistentForNormalRoundAndThemedIcons() {
        val normal = render(context.getDrawable(R.mipmap.ic_launcher)!!)
        val round = render(context.getDrawable(R.mipmap.ic_launcher_round)!!)
        assertTrue(normal.sameAs(round))
        assertEquals(0, Color.alpha(normal.getPixel(0, 0)))
        assertEquals(255, Color.alpha(normal.getPixel(256, 256)))
        assertNotEquals(Color.rgb(61, 220, 132), normal.getPixel(256, 60))
        save(normal, "pixel-launcher-icon.png")
        val mono = context.getDrawable(R.drawable.ic_launcher_monochrome)!!.mutate()
        mono.setTint(Color.rgb(34, 76, 81))
        save(
            render(AdaptiveIconDrawable(ColorDrawable(Color.rgb(221, 235, 229)), mono)),
            "pixel-launcher-icon-themed.png",
        )
    }
}
