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

@Entity(
    tableName = "annotations",
    foreignKeys =
        [
            ForeignKey(
                entity = BookEntity::class,
                parentColumns = ["id"],
                childColumns = ["bookId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices =
        [
            Index("bookId"),
            Index(value = ["bookId", "locatorJson", "endBlock", "endOffset"], unique = true),
        ],
)
data class AnnotationEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val locatorJson: String,
    val endBlock: Int,
    val endOffset: Int,
    val quote: String,
    val note: String,
    val createdAt: Long,
)

data class SavedAnnotation(
    val id: String,
    val bookId: String,
    val locatorJson: String,
    val endBlock: Int,
    val endOffset: Int,
    val quote: String,
    val note: String,
    val createdAt: Long,
    val bookTitle: String,
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAnnotation(annotation: AnnotationEntity): Long

    @Query(
        "SELECT annotations.*, books.title AS bookTitle FROM annotations INNER JOIN books ON books.id = annotations.bookId ORDER BY createdAt DESC"
    )
    fun observeAnnotations(): Flow<List<SavedAnnotation>>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY createdAt")
    fun annotations(bookId: String): Flow<List<AnnotationEntity>>

    @Query("DELETE FROM annotations WHERE id = :id") suspend fun deleteAnnotation(id: String)

    @Query("DELETE FROM books WHERE id = :id") suspend fun deleteBook(id: String)
}

@Database(
    entities = [BookEntity::class, BookmarkEntity::class, AnnotationEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
}

val LIBRARY_MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE books ADD COLUMN format TEXT NOT NULL DEFAULT 'EPUB'")
        }
    }

val LIBRARY_MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS annotations (id TEXT NOT NULL, bookId TEXT NOT NULL, locatorJson TEXT NOT NULL, endBlock INTEGER NOT NULL, endOffset INTEGER NOT NULL, quote TEXT NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_annotations_bookId ON annotations(bookId)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_annotations_bookId_locatorJson_endBlock_endOffset ON annotations(bookId, locatorJson, endBlock, endOffset)"
            )
        }
    }
