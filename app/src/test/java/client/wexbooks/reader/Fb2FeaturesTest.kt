package client.wexbooks.reader

import client.wexbooks.reader.nativeengine.*
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class Fb2FeaturesTest {
    private fun parse(body: String, extra: String = "") =
        NativeDocumentParser.parseFb2(
            """<FictionBook xmlns:l="http://www.w3.org/1999/xlink"><body>$body</body>$extra</FictionBook>"""
                .byteInputStream(),
            "Книга",
        )

    @Test
    fun notesAreSeparateAndLinksKeepTargets() {
        val doc =
            parse(
                """<section id="start"><title><p>Глава</p></title><p>Слово <a l:href="#n1" type="note">[1]</a>.</p></section>""",
                """<body name="notes"><section id="n1"><title><p>1</p></title><p>Первая строка.</p><p>Продолжение.</p></section></body>""",
            )
        assertEquals(2, doc.blocks.size)
        assertEquals(1, doc.chapters.size)
        assertEquals(0, doc.targets["start"])
        val link = doc.blocks[1].marks.single()
        assertEquals("n1", link.target)
        assertEquals("[1]", doc.blocks[1].text.substring(link.start, link.end))
        assertEquals(NativeNote("1", "Первая строка.\n\nПродолжение."), doc.notes["n1"])
    }

    @Test
    fun splitLinksRetainOffsetsAndTargets() {
        val doc =
            parse("<p>" + "а".repeat(4080) + "<a href=\"#note\">" + "б".repeat(100) + "</a></p>")
        assertEquals(2, doc.blocks.size)
        assertEquals(100, doc.blocks.sumOf { it.marks.sumOf { m -> m.end - m.start } })
        assertTrue(doc.blocks.flatMap { it.marks }.all { it.target == "note" })
    }

    @Test
    fun externalLinksDoNotBecomeInternalNavigation() {
        assertTrue(
            parse("""<p><a l:href="https://example.com">Текст</a></p>""")
                .blocks
                .single()
                .marks
                .isEmpty()
        )
    }

    @Test
    fun embeddedPngHasBoundedDimensionsAndStableAnchor() {
        val bytes =
            Base64.getDecoder()
                .decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII="
                )
        val doc =
            parse(
                """<p>До</p><image l:href="#pic"/><p>После</p>""",
                """<binary id="pic" content-type="image/png">${Base64.getEncoder().encodeToString(bytes)}</binary>""",
            )
        assertEquals(BlockKind.Image, doc.blocks[1].kind)
        assertEquals("pic", doc.blocks[1].imageId)
        assertEquals(1, doc.images["pic"]!!.width)
        assertTrue(doc.position(2, 0).progression > doc.position(1, 0).progression)
    }

    @Test
    fun malformedAndOversizedImageHeadersAreIgnored() {
        assertNull(NativeImageBounds.read(byteArrayOf(0, 1, 2)))
        val bytes =
            Base64.getDecoder()
                .decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII="
                )
        bytes[16] = 0x7f
        assertNull(NativeImageBounds.read(bytes))
    }

    @Test(expected = BookImportException::class)
    fun oversizedBinaryIsRejectedBeforeDecoding() {
        parse(
            "<p>Текст</p>",
            "<binary id=\"big\" content-type=\"image/png\">" +
                "A".repeat(NativeDocumentParser.MAX_IMAGE_BYTES * 4 / 3 + 17000) +
                "</binary>",
        )
    }

    @Test
    fun nestedEmphasisAndLinksKeepIndependentRanges() {
        val doc =
            parse(
                """<p><strong>До <a href="#n"><emphasis>слово</emphasis></a> после</strong></p>"""
            )
        assertEquals(3, doc.blocks.single().marks.size)
        assertEquals(
            setOf(MarkKind.Strong, MarkKind.Emphasis, MarkKind.Link),
            doc.blocks.single().marks.map { it.kind }.toSet(),
        )
    }
}
