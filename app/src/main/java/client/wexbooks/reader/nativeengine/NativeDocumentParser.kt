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
import java.util.Base64
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
    const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    const val MAX_TOTAL_IMAGE_BYTES = 8 * 1024 * 1024
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
                private val markStarts = ArrayList<Triple<MarkKind, Int, String?>>()
                private var notesBody = false
                private val sections = ArrayList<String>()
                private val noteParts = linkedMapOf<String, MutableList<Pair<Boolean, String>>>()
                private var binary: StringBuilder? = null
                private var binaryId: String? = null
                private var binaryBytes = 0

                private fun flushParagraph() {
                    paragraph
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            if (notesBody) {
                                sections
                                    .lastOrNull { id -> id.isNotBlank() }
                                    ?.let { id ->
                                        noteParts.getOrPut(id) { arrayListOf() } +=
                                            (kind == BlockKind.Heading) to it.toString().trim()
                                    }
                            } else builder.add(it.toString(), kind, marks.toList())
                        }
                    paragraph = null
                }

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
                    if (name == "body") {
                        inBody = true
                        notesBody =
                            attrs.getValue("name")?.lowercase() in setOf("notes", "comments")
                    }
                    if (inBody && name == "section") {
                        val id = attrs.getValue("id").orEmpty()
                        sections += id
                        if (!notesBody && id.isNotBlank()) builder.anchor(id)
                    }
                    if (name == "binary") {
                        binaryId = attrs.getValue("id")
                        binary =
                            if (attrs.getValue("content-type") in setOf("image/png", "image/jpeg"))
                                StringBuilder()
                            else null
                    }
                    if (inBody && !notesBody && name == "image") {
                        val href =
                            attrs.getValue("http://www.w3.org/1999/xlink", "href")
                                ?: attrs.getValue("href")
                        val resume = paragraph != null
                        flushParagraph()
                        if (href?.startsWith("#") == true) builder.image(href.drop(1))
                        if (resume) {
                            paragraph = StringBuilder()
                            marks.clear()
                            markStarts.clear()
                        }
                    }
                    if (inBody && name == "title") titleDepth++
                    if (inBody && name in listOf("p", "v", "subtitle")) {
                        paragraph = StringBuilder()
                        marks.clear()
                        markStarts.clear()
                        kind =
                            if (titleDepth > 0 || name == "subtitle") BlockKind.Heading
                            else if (name == "v") BlockKind.Verse else BlockKind.Paragraph
                    }
                    if (paragraph != null && name in listOf("strong", "emphasis", "a")) {
                        val target =
                            (attrs.getValue("http://www.w3.org/1999/xlink", "href")
                                    ?: attrs.getValue("href"))
                                ?.takeIf { it.startsWith("#") }
                                ?.drop(1)
                        val type =
                            when (name) {
                                "strong" -> MarkKind.Strong
                                "emphasis" -> MarkKind.Emphasis
                                else -> MarkKind.Link
                            }
                        markStarts += Triple(type, paragraph!!.length, target)
                    }
                    if (
                        !inBody &&
                            name in listOf("book-title", "first-name", "middle-name", "last-name")
                    )
                        capturedMeta = StringBuilder()
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (path.lastOrNull() == "binary") {
                        binary?.let {
                            if (it.length.toLong() + length > MAX_IMAGE_BYTES * 4L / 3 + 16384)
                                throw SAXException("Image limit exceeded")
                            it.append(ch, start, length)
                        }
                        return
                    }
                    total += length
                    if (total > MAX_CHARACTERS) throw SAXException("Character limit exceeded")
                    paragraph?.append(ch, start, length)
                    capturedMeta?.append(ch, start, length)
                }

                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    val name =
                        localName?.takeIf { it.isNotBlank() } ?: qName.orEmpty().substringAfter(':')
                    if (
                        name in listOf("strong", "emphasis", "a") &&
                            markStarts.isNotEmpty() &&
                            paragraph != null
                    ) {
                        val (type, start, target) = markStarts.removeAt(markStarts.lastIndex)
                        if (type != MarkKind.Link || target != null)
                            marks += InlineMark(start, paragraph!!.length, type, target)
                    }
                    if (inBody && name in listOf("p", "v", "subtitle")) flushParagraph()
                    if (inBody && name == "section" && sections.isNotEmpty())
                        sections.removeAt(sections.lastIndex)
                    if (name == "binary") {
                        val id = binaryId
                        binary?.let { encoded ->
                            val bytes = Base64.getMimeDecoder().decode(encoded.toString())
                            binaryBytes += bytes.size
                            if (
                                bytes.size > MAX_IMAGE_BYTES ||
                                    binaryBytes > MAX_TOTAL_IMAGE_BYTES ||
                                    builder.images.size >= 64
                            )
                                throw SAXException("Image limit exceeded")
                            if (!id.isNullOrBlank())
                                NativeImageBounds.read(bytes)?.let { (w, h) ->
                                    builder.images[id] = NativeImage(bytes, w, h)
                                }
                        }
                        binary = null
                        binaryId = null
                    }
                    if (inBody && name == "title") titleDepth--
                    if (name == "body") {
                        inBody = false
                        notesBody = false
                        noteParts.forEach { (id, parts) ->
                            builder.notes[id] =
                                NativeNote(
                                    parts.firstOrNull { it.first }?.second ?: "Сноска",
                                    parts.filterNot { it.first }.joinToString("\n\n") { it.second },
                                )
                        }
                        noteParts.clear()
                    }
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
        val images = linkedMapOf<String, NativeImage>()
        val notes = linkedMapOf<String, NativeNote>()
        private val targets = linkedMapOf<String, Int>()

        fun anchor(id: String) {
            targets.putIfAbsent(id, blocks.size)
        }

        fun image(id: String) {
            if (blocks.size >= 50_000) throw BookImportException("Слишком много блоков.")
            blocks += NativeBlock("\uFFFC", BlockKind.Image, "block-${blocks.size}", imageId = id)
        }

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
                    if (left < right)
                        InlineMark(left - start, right - start, mark.kind, mark.target)
                    else null
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
                notes,
                images,
                targets.filterValues { it < blocks.size },
            )
        }
    }
}
