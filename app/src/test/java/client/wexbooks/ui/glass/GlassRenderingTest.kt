package client.wexbooks.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassRenderingTest {
    @Test
    fun android9To11UseOpaqueControls() {
        listOf(28, 29, 30).forEach {
            assertEquals(GlassRendering.Solid, glassRenderingFor(it, false))
        }
    }

    @Test
    fun android12UsesBlurWithoutRuntimeShaders() {
        listOf(31, 32).forEach {
            assertEquals(GlassRendering.Frosted, glassRenderingFor(it, false))
        }
    }

    @Test
    fun android13AndNewerUseLiquidGlass() {
        listOf(33, 35, 37).forEach {
            assertEquals(GlassRendering.Liquid, glassRenderingFor(it, false))
        }
    }

    @Test
    fun reducedTransparencyDisablesEffectsOnEverySupportedVersion() {
        (28..37).forEach { assertEquals(GlassRendering.Solid, glassRenderingFor(it, true)) }
    }
}
