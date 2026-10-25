package client.wexbooks.reader.nativeengine

import client.wexbooks.reader.BookImportException
import client.wexbooks.reader.core.BookFormat
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2

/** Streaming input with explicit document/block limits; never called on the UI thread. */
object NativeDocumentParser {
    const val MAX_CHARACTERS = 4_000_000
    const val MAX_BLOCK_CHARACTERS = 4096
    const val MAX_SOURCE_BYTES = 32L * 1024 * 1024

    fun parse(file: File, format: BookFormat, fallbackTitle: String): NativeDocument {
        if (file.length() > MAX_SOURCE_BYTES)
            throw BookImportException("Текстовая книга больше 32 МБ.")
        return when (format) {
            BookFormat.TXT -> file.inputStream().use { parseText(it, fallbackTitle) }
            BookFormat.FB2 -> {
                val zip = file.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }
                if (zip)
                    ZipFile(file).use { archive ->
                        val entries =
                            archive
                                .entries()
                                .asSequence()
                                .filter { !it.isDirectory && it.name.endsWith(".fb2", true) }
                                .take(2)
                                .toList()
                        if (entries.size != 1 || entries.single().size !in 1..MAX_SOURCE_BYTES)
                            throw BookImportException("Выберите ZIP с одной FB2-книгой до 32 МБ.")
                        archive.getInputStream(entries.single()).use { parseFb2(it, fallbackTitle) }
                    }
                else file.inputStream().use { parseFb2(it, fallbackTitle) }
            }
            else -> error("EPUB uses its own engine")
        }
    }

    fun parseText(input: InputStream, fallbackTitle: String): NativeDocument {
        val stream = BufferedInputStream(input)
        stream.mark(32772)
        val sample = ByteArray(32768)
        val size = stream.read(sample).coerceAtLeast(0)
        stream.reset()
        val (charset, skip) =
            when {
                size >= 3 &&
                    sample[0] == 0xef.toByte() &&
                    sample[1] == 0xbb.toByte() &&
                    sample[2] == 0xbf.toByte() -> Charsets.UTF_8 to 3
                size >= 2 && sample[0] == 0xff.toByte() && sample[1] == 0xfe.toByte() ->
                    Charsets.UTF_16LE to 2
                size >= 2 && sample[0] == 0xfe.toByte() && sample[1] == 0xff.toByte() ->
                    Charsets.UTF_16BE to 2
                else -> {
                    val decoder =
                        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    val valid =
                        !decoder
                            .decode(
                                ByteBuffer.wrap(sample, 0, size),
                                CharBuffer.allocate(32768),
                                false,
                            )
                            .isError
                    (if (valid) Charsets.UTF_8 else Charset.forName("windows-1251")) to 0
                }
            }
        repeat(skip) { stream.read() }
        val builder = DocumentBuilder(fallbackTitle)
        LimitedReader(InputStreamReader(stream, charset)).buffered().use { reader ->
            val paragraph = StringBuilder()
            fun flush() {
                val text = paragraph.toString().trim()
                if (text.isNotBlank())
                    builder.add(
                        text,
                        if (isHeading(text)) BlockKind.Heading else BlockKind.Paragraph,
                    )
                paragraph.clear()
            }
            reader.forEachLine { line ->
                if (line.isBlank()) flush()
                else if (isHeading(line.trim())) {
                    flush()
                    builder.add(line.trim(), BlockKind.Heading)
                } else {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line.trim())
                }
            }
            flush()
        }
        return builder.build()
    }

    fun parseFb2(input: InputStream, fallbackTitle: String): NativeDocument {
        val builder = DocumentBuilder(fallbackTitle)
        val handler =
            object : DefaultHandler2() {
                private val path = ArrayList<String>()
                private var inBody = false
                private var titleDepth = 0
                private var paragraph: StringBuilder? = null
                private var kind = BlockKind.Paragraph
                private val marks = ArrayList<InlineMark>()
                private val markStarts = ArrayList<Pair<MarkKind, Int>>()
                private var capturedMeta: StringBuilder? = null
                private var total = 0L
                private val authorParts = ArrayList<String>()

                override fun startDTD(name: String?, publicId: String?, systemId: String?) {
                    throw SAXException("DTD is not part of the text reader")
                }

                override fun resolveEntity(publicId: String?, systemId: String?): InputSource {
                    throw SAXException("External entity")
                }

                override fun startElement(
                    uri: String?,
                    localName: String?,
                    qName: String?,
                    attrs: Attributes,
                ) {
                    val name =
                        localName?.takeIf { it.isNotBlank() } ?: qName.orEmpty().substringAfter(':')
                    path += name
                    if (path.size > 128 || (path.size == 1 && name != "FictionBook"))
                        throw SAXException("Invalid FB2 structure")
                    if (name == "body") inBody = true
                    if (inBody && name == "title") titleDepth++
                    if (inBody && name in listOf("p", "v", "subtitle")) {
                        paragraph = StringBuilder()
                        marks.clear()
                        markStarts.clear()
                        kind =
                            if (titleDepth > 0 || name == "subtitle") BlockKind.Heading
                            else if (name == "v") BlockKind.Verse else BlockKind.Paragraph
                    }
                    if (paragraph != null && name in listOf("strong", "emphasis"))
                        markStarts +=
                            (if (name == "strong") MarkKind.Strong else MarkKind.Emphasis) to
                                paragraph!!.length
                    if (
                        !inBody &&
                            name in listOf("book-title", "first-name", "middle-name", "last-name")
                    )
                        capturedMeta = StringBuilder()
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    total += length
                    if (total > MAX_CHARACTERS) throw SAXException("Character limit exceeded")
                    paragraph?.append(ch, start, length)
                    capturedMeta?.append(ch, start, length)
                }

                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    val name =
                        localName?.takeIf { it.isNotBlank() } ?: qName.orEmpty().substringAfter(':')
                    if (
                        name in listOf("strong", "emphasis") &&
                            markStarts.isNotEmpty() &&
                            paragraph != null
                    ) {
                        val (type, start) = markStarts.removeAt(markStarts.lastIndex)
                        marks += InlineMark(start, paragraph!!.length, type)
                    }
                    if (inBody && name in listOf("p", "v", "subtitle")) {
                        paragraph?.let {
                            if (it.isNotBlank()) builder.add(it.toString(), kind, marks.toList())
                        }
                        paragraph = null
                    }
                    if (inBody && name == "title") titleDepth--
                    if (name == "body") inBody = false
                    if (
                        !inBody &&
                            capturedMeta != null &&
                            name in listOf("book-title", "first-name", "middle-name", "last-name")
                    ) {
                        val text = capturedMeta.toString().trim()
                        if (name == "book-title" && text.isNotBlank()) builder.title = text
                        else if (text.isNotBlank()) authorParts += text
                        capturedMeta = null
                    }
                    if (name == "author" && authorParts.isNotEmpty()) {
                        builder.authors += authorParts.joinToString(" ")
                        authorParts.clear()
                    }
                    if (path.isNotEmpty()) path.removeAt(path.lastIndex)
                }
            }
        try {
            val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
            val reader = factory.newSAXParser().xmlReader
            runCatching {
                reader.setFeature("http://xml.org/sax/features/external-general-entities", false)
            }
            runCatching {
                reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            reader.contentHandler = handler
            reader.entityResolver = handler
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
            reader.parse(InputSource(input))
        } catch (e: Exception) {
            throw BookImportException(
                "Проверьте структуру FB2 и размер текста (до 4 млн символов)."
            )
        }
        return builder.build()
    }

    private val headingPattern =
        Regex("^(глава|часть|пролог|эпилог|chapter|part)(?:\\s|$|[.:])", RegexOption.IGNORE_CASE)

    private fun isHeading(text: String): Boolean =
        text.length < 160 && headingPattern.containsMatchIn(text)

    private class LimitedReader(reader: Reader) : FilterReader(reader) {
        private var count = 0

        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            val read = super.read(cbuf, off, len)
            if (read > 0) {
                count += read
                if (count > MAX_CHARACTERS)
                    throw BookImportException("Текст длиннее 4 млн символов.")
            }
            return read
        }
    }

    private class DocumentBuilder(var title: String) {
        val authors = ArrayList<String>()
        private val blocks = ArrayList<NativeBlock>()
        private val chapters = ArrayList<NativeChapter>()

        fun add(text: String, kind: BlockKind, marks: List<InlineMark> = emptyList()) {
            if (kind == BlockKind.Heading) chapters += NativeChapter(text.take(160), blocks.size)
            var start = 0
            while (start < text.length) {
                var end = (start + MAX_BLOCK_CHARACTERS).coerceAtMost(text.length)
                if (end < text.length) {
                    val boundary = text.lastIndexOf(' ', end)
                    if (boundary > start + MAX_BLOCK_CHARACTERS / 2) end = boundary
                    if (end > start && Character.isHighSurrogate(text[end - 1])) end--
                }
                val clipped = marks.mapNotNull { mark ->
                    val left = maxOf(mark.start, start)
                    val right = minOf(mark.end, end)
                    if (left < right) InlineMark(left - start, right - start, mark.kind) else null
                }
                if (blocks.size >= 50_000)
                    throw BookImportException("В книге слишком много отдельных блоков текста.")
                blocks +=
                    NativeBlock(
                        text.substring(start, end),
                        kind,
                        "block-${blocks.size}",
                        clipped,
                        start > 0,
                    )
                start = end
                while (start < text.length && text[start] == ' ') start++
            }
        }

        fun build(): NativeDocument {
            if (blocks.isEmpty()) throw BookImportException("В книге нет читаемого текста.")
            return NativeDocument(
                title.take(1024),
                authors.distinct().joinToString(", ").ifBlank { "Автор не указан" },
                blocks,
                chapters,
            )
        }
    }
}
