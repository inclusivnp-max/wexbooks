package client.wexbooks.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small app-owned outline set, with no dependency on an entire icon catalogue. */
object WexIcons {
    val Focus =
        outline("Focus") {
            moveTo(8f, 3f)
            lineTo(3f, 3f)
            lineTo(3f, 8f)
            moveTo(16f, 3f)
            lineTo(21f, 3f)
            lineTo(21f, 8f)
            moveTo(3f, 16f)
            lineTo(3f, 21f)
            lineTo(8f, 21f)
            moveTo(21f, 16f)
            lineTo(21f, 21f)
            lineTo(16f, 21f)
        }

    val Book =
        outline("Book") {
            moveTo(12f, 5f)
            curveTo(9f, 3f, 5f, 3f, 2.5f, 4f)
            lineTo(2.5f, 19f)
            curveTo(6f, 18f, 9f, 18.5f, 12f, 20f)
            curveTo(15f, 18.5f, 18f, 18f, 21.5f, 19f)
            lineTo(21.5f, 4f)
            curveTo(19f, 3f, 15f, 3f, 12f, 5f)
            lineTo(12f, 20f)
        }
    val Notes =
        outline("Notes") {
            moveTo(6f, 3f)
            lineTo(18f, 3f)
            curveTo(19.7f, 3f, 21f, 4.3f, 21f, 6f)
            lineTo(21f, 18f)
            curveTo(21f, 19.7f, 19.7f, 21f, 18f, 21f)
            lineTo(6f, 21f)
            curveTo(4.3f, 21f, 3f, 19.7f, 3f, 18f)
            lineTo(3f, 6f)
            curveTo(3f, 4.3f, 4.3f, 3f, 6f, 3f)
            moveTo(8f, 8f)
            lineTo(16f, 8f)
            moveTo(8f, 12f)
            lineTo(16f, 12f)
            moveTo(8f, 16f)
            lineTo(13f, 16f)
        }
    val Trash =
        outline("Trash") {
            moveTo(4f, 6f)
            lineTo(20f, 6f)
            moveTo(9f, 6f)
            lineTo(9f, 3f)
            lineTo(15f, 3f)
            lineTo(15f, 6f)
            moveTo(6f, 6f)
            lineTo(7f, 21f)
            lineTo(17f, 21f)
            lineTo(18f, 6f)
            moveTo(10f, 10f)
            lineTo(10f, 17f)
            moveTo(14f, 10f)
            lineTo(14f, 17f)
        }
    val Settings =
        outline("Settings") {
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            moveTo(4f, 17f)
            lineTo(20f, 17f)
            moveTo(9f, 4f)
            lineTo(9f, 10f)
            moveTo(15f, 14f)
            lineTo(15f, 20f)
        }
    val Arrow =
        outline("Arrow") {
            moveTo(5f, 12f)
            lineTo(19f, 12f)
            moveTo(13f, 6f)
            lineTo(19f, 12f)
            lineTo(13f, 18f)
        }
    val Back =
        outline("Back") {
            moveTo(15f, 5f)
            lineTo(8f, 12f)
            lineTo(15f, 19f)
        }
    val Bookmark =
        outline("Bookmark") {
            moveTo(6f, 4f)
            lineTo(18f, 4f)
            lineTo(18f, 21f)
            lineTo(12f, 17f)
            lineTo(6f, 21f)
            close()
        }
    val Sun =
        outline("Sun") {
            moveTo(16f, 12f)
            arcTo(4f, 4f, 0f, true, true, 8f, 12f)
            arcTo(4f, 4f, 0f, true, true, 16f, 12f)
            moveTo(12f, 2f)
            lineTo(12f, 4f)
            moveTo(12f, 20f)
            lineTo(12f, 22f)
            moveTo(2f, 12f)
            lineTo(4f, 12f)
            moveTo(20f, 12f)
            lineTo(22f, 12f)
            moveTo(5f, 5f)
            lineTo(6.5f, 6.5f)
            moveTo(17.5f, 17.5f)
            lineTo(19f, 19f)
            moveTo(19f, 5f)
            lineTo(17.5f, 6.5f)
            moveTo(6.5f, 17.5f)
            lineTo(5f, 19f)
        }
    val Moon =
        outline("Moon") {
            moveTo(20f, 14f)
            curveTo(13f, 17f, 7f, 11f, 10f, 4f)
            curveTo(4f, 5f, 1.5f, 12f, 5f, 17f)
            curveTo(8.5f, 22f, 17f, 22f, 20f, 14f)
        }

    private fun outline(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .apply {
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.65f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    pathFillType = PathFillType.NonZero,
                    pathBuilder = block,
                )
            }
            .build()
}
