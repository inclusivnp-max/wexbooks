package client.wexbooks

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import client.wexbooks.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryMigrationTest {
    @Test
    fun previousSchemasKeepBooksPositionsAndBookmarksAndAddQuotes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (version in 1..2) {
            val file =
                java.io.File(
                    context.cacheDir,
                    "migration-$version-${java.util.UUID.randomUUID()}.db",
                )
            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                when (version) {
                    1 -> {
                        old.execSQL(
                            """CREATE TABLE IF NOT EXISTS `books` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT NOT NULL, `fileName` TEXT NOT NULL, `importedAt` INTEGER NOT NULL, `lastOpenedAt` INTEGER NOT NULL, `locatorJson` TEXT, `progression` REAL NOT NULL, `furthestProgression` REAL NOT NULL, PRIMARY KEY(`id`))"""
                        )
                        old.execSQL(
                            """CREATE TABLE IF NOT EXISTS `bookmarks` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, `label` TEXT NOT NULL, `locatorJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""
                        )
                        old.execSQL(
                            """CREATE INDEX IF NOT EXISTS `index_bookmarks_bookId` ON `bookmarks` (`bookId`)"""
                        )
                        old.execSQL(
                            """CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_bookId_locatorJson` ON `bookmarks` (`bookId`, `locatorJson`)"""
                        )
                    }
                    2 -> {
                        old.execSQL(
                            """CREATE TABLE IF NOT EXISTS `books` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT NOT NULL, `fileName` TEXT NOT NULL, `importedAt` INTEGER NOT NULL, `lastOpenedAt` INTEGER NOT NULL, `locatorJson` TEXT, `progression` REAL NOT NULL, `furthestProgression` REAL NOT NULL, `format` TEXT NOT NULL DEFAULT 'EPUB', PRIMARY KEY(`id`))"""
                        )
                        old.execSQL(
                            """CREATE TABLE IF NOT EXISTS `bookmarks` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, `label` TEXT NOT NULL, `locatorJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"""
                        )
                        old.execSQL(
                            """CREATE INDEX IF NOT EXISTS `index_bookmarks_bookId` ON `bookmarks` (`bookId`)"""
                        )
                        old.execSQL(
                            """CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_bookId_locatorJson` ON `bookmarks` (`bookId`, `locatorJson`)"""
                        )
                    }
                }
                old.execSQL(
                    "INSERT INTO books(id,title,author,fileName,importedAt,lastOpenedAt,locatorJson,progression,furthestProgression) VALUES('book','Title','Author','book.epub',1,2,'old-position',0.25,0.75)"
                )
                old.execSQL(
                    "INSERT INTO bookmarks VALUES('bookmark','book','Saved place','bookmark-position',3)"
                )
                old.version = version
            }
            val database =
                Room.databaseBuilder(context, LibraryDatabase::class.java, file.absolutePath)
                    .addMigrations(LIBRARY_MIGRATION_1_2, LIBRARY_MIGRATION_2_3)
                    .build()
            try {
                val dao = database.library()
                val book = dao.book("book")!!
                assertEquals("old-position", book.locatorJson)
                assertEquals(0.75, book.furthestProgression, 0.0)
                assertEquals("EPUB", book.format)
                assertEquals("Saved place", dao.observeBookmarks().first().single().label)
                val quote =
                    AnnotationEntity("quote", "book", "quote-position", 0, 10, "Text", "Thought", 4)
                assertTrue(dao.insertAnnotation(quote) > 0)
                assertEquals(-1L, dao.insertAnnotation(quote.copy(id = "duplicate")))
                assertEquals("Thought", dao.observeAnnotations().first().single().note)
                dao.deleteBook("book")
                assertTrue(dao.observeAnnotations().first().isEmpty())
                assertTrue(dao.observeBookmarks().first().isEmpty())
            } finally {
                database.close()
                context.deleteDatabase(file.absolutePath)
            }
        }
    }
}
