package client.wexbooks.reader

import client.wexbooks.ui.reader.readerSafePadding
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewportInsetsTest {
    @Test fun noCameraKeepsTheRequestedMargin() = assertEquals(22, readerSafePadding(22, 0, 12))

    @Test fun portraitCameraGetsBreathingRoom() = assertEquals(44, readerSafePadding(22, 32, 12))

    @Test
    fun landscapeSideCameraGetsTheSameProtection() = assertEquals(54, readerSafePadding(0, 42, 12))

    @Test fun largerUserMarginsArePreserved() = assertEquals(48, readerSafePadding(48, 24, 12))
}
