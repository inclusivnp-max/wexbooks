package client.wexbooks.reader

import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class EpubFileRulesTest {
    @Test
    fun streamedDigestMatchesKnownSha256() {
        val output = ByteArrayOutputStream()
        val hash = EpubFileRules.copyAndHash("abc".byteInputStream(), output)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash)
        assertEquals("abc", output.toString())
    }

    @Test(expected = BookImportException::class)
    fun oversizedInputStopsBeforeWritingPastLimit() {
        EpubFileRules.copyAndHash("abcdef".byteInputStream(), ByteArrayOutputStream(), limit = 4)
    }

    @Test(expected = BookImportException::class)
    fun emptyInputIsRejected() {
        EpubFileRules.copyAndHash(byteArrayOf().inputStream(), ByteArrayOutputStream())
    }
}
