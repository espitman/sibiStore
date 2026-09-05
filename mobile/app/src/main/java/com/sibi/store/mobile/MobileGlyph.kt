package com.sibi.store.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Reference-style navigation glyphs; the surrounding tab supplies its accessible label. */
@Composable internal fun MobileGlyph(name: String, selected: Boolean, tint: Color = LocalContentColor.current) {
    Canvas(Modifier.size(26.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun path(data: String) = drawPath(PathParser().parsePathString(data).toPath(), tint, style = stroke)
            when (name) {
                "Library" -> for (x in listOf(2f, 13f)) for (y in listOf(2f, 13f)) {
                    drawRoundRect(tint, Offset(x, y), Size(9f, 9f), CornerRadius(1.8f), style = if (selected) Fill else stroke)
                }
                "Updates" -> path("M 12 2 L 12 16 M 6 10 L 12 16 L 18 10 M 2 17 L 2 21 Q 2 22 3 22 L 21 22 Q 22 22 22 21 L 22 17")
                "Settings" -> {
                    path("M 9 2 L 15 2 L 16 5 L 18 6 L 21 6 L 23 10 L 21 12 L 21 15 L 22 18 L 18 21 L 15 19 L 12 20 L 10 23 L 6 21 L 6 18 L 4 16 L 1 15 L 1 10 L 4 9 L 6 7 L 6 4 Z")
                    drawCircle(tint, 4f, Offset(12f, 12f), style = stroke)
                }
            }
        }
    }
}
