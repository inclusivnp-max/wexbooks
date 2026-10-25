package client.wexbooks.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import client.wexbooks.ui.components.WexDialog

@Composable
fun QuoteEditor(text: String, onDismiss: () -> Unit, onSave: suspend (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    WexDialog(
        title = "Сохранить цитату",
        onDismissRequest = onDismiss,
        confirmLabel = "Сохранить",
        dismissLabel = "Отмена",
        confirmTag = "save-quote",
        onConfirm = {
            onSave(note)
            true
        },
    ) { enabled ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.width(3.dp)
                    .height(36.dp)
                    .background(colors.primary.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
            )
            SelectionContainer {
                Text(
                    text,
                    fontFamily = FontFamily.Serif,
                    fontSize = 20.sp,
                    lineHeight = 29.sp,
                    color = colors.onSurface,
                )
            }
        }
        Column(
            Modifier.fillMaxWidth()
                .background(colors.onSurface.copy(alpha = 0.04f), RoundedCornerShape(20.dp))
                .border(0.7.dp, colors.onSurface.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Text(
                "ВАША МЫСЛЬ",
                color = colors.onSurfaceVariant,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
            )
            BasicTextField(
                note,
                { if (it.length <= 2000) note = it },
                modifier =
                    Modifier.fillMaxWidth().padding(top = 12.dp).testTag("quote-note").semantics {
                        contentDescription = "Ваша мысль"
                    },
                textStyle =
                    TextStyle(color = colors.onSurface, fontSize = 16.sp, lineHeight = 23.sp),
                cursorBrush = SolidColor(colors.primary),
                minLines = 3,
                maxLines = 6,
                decorationBox = { inner ->
                    Box {
                        if (note.isEmpty())
                            Text(
                                "Что хочется запомнить?",
                                color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                                fontSize = 16.sp,
                            )
                        inner()
                    }
                },
            )
            Text(
                "${note.length} / 2000 · необязательно",
                Modifier.align(Alignment.End).padding(top = 10.dp),
                color = colors.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
    }
}
