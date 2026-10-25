package client.wexbooks.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import client.wexbooks.data.BookEntity
import client.wexbooks.data.SavedAnnotation
import client.wexbooks.data.SavedBookmark
import client.wexbooks.ui.components.WexDialog
import client.wexbooks.ui.components.WexIcons

@Composable
fun BookLibraryScreen(
    books: List<BookEntity>,
    importing: String?,
    onImport: () -> Unit,
    onOpen: (BookEntity) -> Unit,
    onDelete: (BookEntity) -> Unit,
    emptyContent: @Composable () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var deletion by remember { mutableStateOf<BookEntity?>(null) }
    val filtered =
        remember(books, search) {
            books.filter {
                search.isBlank() ||
                    it.title.contains(search, true) ||
                    it.author.contains(search, true)
            }
        }
    LazyColumn(
        Modifier.fillMaxSize().testTag("book-library-list"),
        contentPadding = PaddingValues(bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (books.isEmpty()) {
            item(key = "empty") { emptyContent() }
        } else {
            item(key = "heading") {
                Column(Modifier.testTag("library-screen")) {
                    Text(
                        "Библиотека",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.3).sp,
                    )
                    Text(
                        "Ваши истории всегда рядом.",
                        Modifier.padding(top = 8.dp),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onImport,
                        enabled = importing == null,
                        shape = RoundedCornerShape(18.dp),
                        modifier =
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("import-books"),
                    ) {
                        Text("Добавить книгу")
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        search,
                        { search = it },
                        singleLine = true,
                        placeholder = { Text("Книга или автор") },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().testTag("library-search"),
                    )
                }
            }
            items(filtered, key = { it.id }) { book ->
                BookCard(book, { onOpen(book) }, { deletion = book })
            }
            if (filtered.isEmpty())
                item {
                    Text(
                        "Попробуйте другое название или имя автора.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
        }
        if (importing != null)
            item(key = "import-progress") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(importing, fontSize = 13.sp)
                }
            }
    }
    deletion?.let { book ->
        WexDialog(
            title = "Удалить книгу?",
            onDismissRequest = { deletion = null },
            confirmLabel = "Удалить",
            dismissLabel = "Оставить",
            onConfirm = {
                onDelete(book)
                true
            },
        ) {
            Text(
                "«${book.title}» и её закладки и цитаты исчезнут из библиотеки. Исходный файл останется на месте.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BookCard(book: BookEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.55f))
            .clickable(onClick = onOpen)
            .testTag("book-${book.id}")
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(76.dp)
                .height(108.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF526F77), Color(0xFF253D48))))
                .padding(10.dp)
        ) {
            Text(
                book.title.take(32),
                fontFamily = FontFamily.Serif,
                color = Color(0xFFFFF5E6),
                fontSize = 15.sp,
                lineHeight = 18.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                book.title,
                color = colors.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${book.author} · ${book.format}",
                Modifier.padding(top = 6.dp),
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                if (book.lastOpenedAt == 0L) "Начать читать"
                else "Продолжить · ${(book.progression * 100).toInt()}%",
                fontSize = 12.sp,
                color = colors.primary,
            )
            LinearProgressIndicator(
                progress = { book.progression.toFloat() },
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(3.dp),
                color = colors.primary,
                trackColor = colors.primary.copy(alpha = 0.1f),
            )
        }
        IconButton(onDelete) {
            Icon(
                WexIcons.Trash,
                "Удалить ${book.title}",
                Modifier.size(20.dp),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SavedBookmarksScreen(
    bookmarks: List<SavedBookmark>,
    onOpen: (SavedBookmark) -> Unit,
    onDelete: (String) -> Unit,
    annotations: List<SavedAnnotation> = emptyList(),
    onOpenAnnotation: (SavedAnnotation) -> Unit = {},
    onDeleteAnnotation: (String) -> Unit = {},
) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("notes-screen"),
        contentPadding = PaddingValues(bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                "Заметки",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.3).sp,
            )
            Text(
                "Места, к которым хочется вернуться.",
                Modifier.padding(top = 8.dp),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(annotations, key = { "quote-${it.id}" }) { quote ->
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable { onOpenAnnotation(quote) }
                    .testTag("saved-quote-${quote.id}")
                    .padding(18.dp)
            ) {
                Text(
                    "«${quote.quote}»",
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Serif,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
                if (quote.note.isNotBlank())
                    Text(quote.note, Modifier.padding(top = 12.dp), fontSize = 14.sp)
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        quote.bookTitle,
                        Modifier.weight(1f),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onDeleteAnnotation(quote.id) }) {
                        Text("Убрать", fontSize = 11.sp)
                    }
                }
            }
        }
        items(bookmarks, key = { it.id }) { bookmark ->
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable { onOpen(bookmark) }
                    .testTag("saved-bookmark-${bookmark.id}")
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(WexIcons.Bookmark, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(bookmark.label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        bookmark.bookTitle,
                        Modifier.padding(top = 6.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onDelete(bookmark.id) }) { Text("Убрать", fontSize = 11.sp) }
            }
        }
    }
}
