package client.wexbooks

import android.app.Application
import androidx.room.Room
import client.wexbooks.data.BookRepository
import client.wexbooks.data.LIBRARY_MIGRATION_1_2
import client.wexbooks.data.LIBRARY_MIGRATION_2_3
import client.wexbooks.data.LibraryDatabase
import client.wexbooks.data.ReaderPreferencesStore
import client.wexbooks.reader.ReadiumEngine
import client.wexbooks.reader.ReadiumReaderAdapter
import client.wexbooks.reader.core.ReaderCore
import client.wexbooks.reader.nativeengine.NativeReaderEngine

class WexBooksApplication : Application() {
    val container by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    val database =
        Room.databaseBuilder(application, LibraryDatabase::class.java, "library.db")
            .addMigrations(LIBRARY_MIGRATION_1_2, LIBRARY_MIGRATION_2_3)
            .build()
    val preferences = ReaderPreferencesStore(application)
    val native = NativeReaderEngine()
    private val epub = ReadiumEngine(application)
    val books = BookRepository(application, database, epub, native)
    val reader = ReaderCore(listOf(ReadiumReaderAdapter(application, epub), native))
}
