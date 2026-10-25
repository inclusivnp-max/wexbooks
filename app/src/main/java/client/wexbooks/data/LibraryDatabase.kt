package client.wexbooks.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val fileName: String,
    val importedAt: Long,
    val lastOpenedAt: Long = 0,
    val locatorJson: String? = null,
    val progression: Double = 0.0,
    val furthestProgression: Double = 0.0,
    @ColumnInfo(defaultValue = "'EPUB'") val format: String = "EPUB",
)

@Entity(
    tableName = "bookmarks",
    foreignKeys =
        [
            ForeignKey(
                entity = BookEntity::class,
                parentColumns = ["id"],
                childColumns = ["bookId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("bookId"), Index(value = ["bookId", "locatorJson"], unique = true)],
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val label: String,
    val locatorJson: String,
    val createdAt: Long,
)

data class SavedBookmark(
    val id: String,
    val bookId: String,
    val label: String,
    val locatorJson: String,
    val createdAt: Long,
    val bookTitle: String,
    val author: String,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC, importedAt DESC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id") suspend fun book(id: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(book: BookEntity): Long

    @Query("UPDATE books SET lastOpenedAt = :time WHERE id = :id")
    suspend fun markOpened(id: String, time: Long)

    @Query(
        "UPDATE books SET locatorJson = :locator, progression = :progression, furthestProgression = MAX(furthestProgression, :progression) WHERE id = :id"
    )
    suspend fun savePosition(id: String, locator: String, progression: Double)

    @Query(
        "SELECT bookmarks.*, books.title AS bookTitle, books.author AS author FROM bookmarks INNER JOIN books ON books.id = bookmarks.bookId ORDER BY createdAt DESC"
    )
    fun observeBookmarks(): Flow<List<SavedBookmark>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id") suspend fun deleteBookmark(id: String)

    @Query("DELETE FROM books WHERE id = :id") suspend fun deleteBook(id: String)
}

@Database(entities = [BookEntity::class, BookmarkEntity::class], version = 2, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
}

val LIBRARY_MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN format TEXT NOT NULL DEFAULT 'EPUB'")
        }
    }
