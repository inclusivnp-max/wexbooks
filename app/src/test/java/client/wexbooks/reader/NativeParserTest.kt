package client.wexbooks.reader

import client.wexbooks.reader.nativeengine.*
import java.nio.charset.Charset
import org.junit.Assert.*
import org.junit.Test

class NativeParserTest {
    @Test
    fun utf8AndHeadingArePreserved() {
        val doc =
            NativeDocumentParser.parseText(
                "Глава 1\nПривет, читатель.\n\nЕщё абзац.".byteInputStream(),
                "Тест",
            )
        assertEquals(3, doc.blocks.size)
        assertEquals(BlockKind.Heading, doc.blocks[0].kind)
        assertEquals("Глава 1", doc.chapters.single().title)
    }

    @Test
    fun windows1251IsDetectedWithoutChangingText() {
        val text = "Привет из старой книги."
        val doc =
            NativeDocumentParser.parseText(
                text.toByteArray(Charset.forName("windows-1251")).inputStream(),
                "Тест",
            )
        assertEquals(text, doc.blocks.single().text)
    }

    @Test
    fun utf16BomIsHandled() {
        val text = "Большая история"
        val bytes = byteArrayOf(0xff.toByte(), 0xfe.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(
            text,
            NativeDocumentParser.parseText(bytes.inputStream(), "Тест").blocks.single().text,
        )
    }

    @Test
    fun hugeParagraphIsSplitIntoBoundedChunks() {
        val text = ("Длинный абзац ".repeat(4000)).trim()
        val doc = NativeDocumentParser.parseText(text.byteInputStream(), "Тест")
        assertTrue(doc.blocks.size > 1)
        assertTrue(doc.blocks.all { it.text.length <= NativeDocumentParser.MAX_BLOCK_CHARACTERS })
        assertEquals(
            text.replace(" ", ""),
            doc.blocks.joinToString("") { it.text }.replace(" ", ""),
        )
    }

    @Test
    fun fb2MetadataAndInlineEmphasisArePreserved() {
        val xml =
            """<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"><description><title-info><book-title>История</book-title><author><first-name>Анна</first-name><last-name>Лесная</last-name></author></title-info></description><body><section><title><p>Первая глава</p></title><p>Обычный <strong>важный</strong> текст.</p></section></body></FictionBook>"""
        val doc = NativeDocumentParser.parseFb2(xml.byteInputStream(), "Fallback")
        assertEquals("История", doc.title)
        assertEquals("Анна Лесная", doc.author)
        assertEquals(BlockKind.Heading, doc.blocks.first().kind)
        val marked = doc.blocks[1]
        val span = marked.marks.single()
        assertEquals("важный", marked.text.substring(span.start, span.end))
        assertEquals(MarkKind.Strong, span.kind)
    }

    @Test(expected = BookImportException::class)
    fun externalEntityDocumentIsRejected() {
        NativeDocumentParser.parseFb2(
            """<!DOCTYPE FictionBook [<!ENTITY secret SYSTEM "file:///etc/passwd">]><FictionBook><body><p>&secret;</p></body></FictionBook>"""
                .byteInputStream(),
            "Тест",
        )
    }

    @Test(expected = BookImportException::class)
    fun emptyTextIsRejected() {
        NativeDocumentParser.parseText(" \n\n".byteInputStream(), "Тест")
    }

    @Test
    fun locationUsesDocumentCoordinatesNotPixelOffsets() {
        val doc =
            NativeDocumentParser.parseText(
                "Первый абзац.\n\nВторой абзац.".byteInputStream(),
                "Тест",
            )
        val position = doc.position(1, 4)
        assertEquals(1, position.block)
        assertEquals(4, position.offset)
        assertTrue(position.progression in 0.0..1.0)
    }
}
