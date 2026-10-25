package client.wexbooks.reader

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

object EpubFileRules {
    const val MAX_FILE_BYTES = 128L * 1024 * 1024
    const val MAX_UNPACKED_BYTES = 256L * 1024 * 1024
    const val MAX_ENTRIES = 8192

    fun copyAndHash(
        input: InputStream,
        output: OutputStream,
        limit: Long = MAX_FILE_BYTES,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            if (total > limit) throw BookImportException("Книга больше ${limit / 1024 / 1024} МБ.")
            digest.update(buffer, 0, count)
            output.write(buffer, 0, count)
        }
        if (total == 0L) throw BookImportException("Выбранный файл пуст.")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun validate(file: File) {
        try {
            ZipFile(file).use { archive ->
                var count = 0
                var total = 0L
                val seen = HashSet<String>()
                val entries = archive.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name.replace('\\', '/')
                    if (
                        ++count > MAX_ENTRIES ||
                            !seen.add(name) ||
                            name.startsWith('/') ||
                            name.split('/').any { it == ".." } ||
                            name.contains('\u0000') ||
                            Regex("^[A-Za-z]:").containsMatchIn(name)
                    ) {
                        throw BookImportException("В EPUB некорректная структура файлов.")
                    }
                    if (entry.size < 0 || entry.size > MAX_UNPACKED_BYTES - total) {
                        throw BookImportException("Распакованная книга слишком велика.")
                    }
                    total += entry.size
                }
                val mimetype =
                    archive.getEntry("mimetype")
                        ?: throw BookImportException("В EPUB отсутствует описание формата.")
                if (
                    mimetype.size > 128 ||
                        archive.getInputStream(mimetype).bufferedReader().use {
                            it.readText().trim()
                        } != "application/epub+zip"
                ) {
                    throw BookImportException("Выберите файл EPUB.")
                }
                val container =
                    archive.getEntry("META-INF/container.xml")
                        ?: throw BookImportException("В EPUB отсутствует оглавление ресурсов.")
                if (container.size > 1024 * 1024)
                    throw BookImportException("Описание EPUB слишком велико.")
            }
        } catch (e: BookImportException) {
            throw e
        } catch (e: Exception) {
            throw BookImportException("EPUB повреждён или не является ZIP-книгой.")
        }
    }
}
