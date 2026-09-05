package com.sibi.store.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sibi.store.core.AppIcon
import com.sibi.store.core.StoreApp

internal val TvGold = Color(0xFFFFD600)
internal val TvBlack = Color(0xFF000000)
internal val TvSidebar = Color(0xFF050505)
internal val TvWhite = Color(0xFFF2F2F2)
internal val TvMuted = Color(0xFFB8B8B8)
internal val TvBorder = Color(0xFF282828)
internal val TvCardBrush = Brush.linearGradient(listOf(Color(0xFF0B0B0B), Color(0xFF070707)))

@Composable internal fun TvBrand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("sibi", fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
        Text("store", fontSize = 17.sp, color = TvGold, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Bottom).padding(bottom = 3.dp))
        Icon(painterResource(com.sibi.store.core.R.drawable.ic_brand_bag), null, tint = TvGold, modifier = Modifier.padding(start = 3.dp).size(27.dp))
    }
}

@Composable internal fun TvAppIcon(app: StoreApp, size: Dp) {
    // Real APK artwork is never substituted with the mockup's example app icons.
    if (app.icon != null || !app.title.contains("Sibi", true)) AppIcon(app, size)
    else Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.24f))
        .background(Brush.linearGradient(listOf(Color(0xFFFFD800), Color(0xFFFFC900)))), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val p = PathParser().parsePathString("M 7 5 Q 7 3.5 8.5 4.3 L 18 10.3 Q 20 11.5 18 12.7 L 8.5 18.7 Q 7 19.5 7 18 Z").toPath()
            scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) { drawPath(p, TvBlack) }
        }
    }
}

/** Thin round-ended glyphs, independent of Material's heavier icon defaults. */
@Composable internal fun TvGlyph(name: String, size: Dp = 24.dp, tint: Color = LocalContentColor.current) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) {
                val stroke = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                fun path(data: String) = drawPath(PathParser().parsePathString(data).toPath(), tint, style = stroke)
                when (name) {
                    "Library" -> for (x in listOf(2f, 13f)) for (y in listOf(2f, 13f)) drawRoundRect(tint, Offset(x, y), Size(9f, 9f), CornerRadius(1.8f))
                    "Updates" -> path("M 12 2 L 12 16 M 6 10 L 12 16 L 18 10 M 2 17 L 2 21 Q 2 22 3 22 L 21 22 Q 22 22 22 21 L 22 17")
                    "UpdateNotice" -> { drawCircle(tint, 10.5f, Offset(12f, 12f), style = stroke); path("M 12 5 L 12 18 M 7 13 L 12 18 L 17 13") }
                    "Search" -> { drawCircle(tint, 7.6f, Offset(10f, 10f), style = stroke); path("M 16 16 L 22 22") }
                    "Settings" -> {
                        path("M 9 2 L 15 2 L 16 6 L 19 7 L 22 6 L 24 11 L 21 14 L 21 17 L 22 20 L 17 23 L 14 20 L 11 20 L 8 23 L 3 20 L 4 16 L 3 13 L 0 11 L 3 6 L 6 7 L 8 5 Z")
                        drawCircle(tint, 4f, Offset(12f, 12f), style = stroke)
                    }
                    "Computer" -> path("M 3 3 L 21 3 L 21 17 L 3 17 Z M 1 21 L 23 21 M 8 18 L 8 21 M 16 18 L 16 21")
                    "Chevron" -> path("M 9 5 L 16 12 L 9 19")
                    "Back" -> path("M 7 5 L 2 10 L 7 15 M 2 10 L 13 10 C 24 10 24 22 14 22 L 8 22")
                    "Select" -> drawCircle(tint, 10.5f, Offset(12f, 12f), style = Stroke(1.1f))
                    "Navigate" -> path("M 10 1 L 14 1 L 15 3 L 15 6 L 13 7 L 10 7 L 9 5 L 9 3 Z M 10 17 L 14 17 L 15 19 L 15 22 L 13 23 L 10 23 L 9 21 L 9 19 Z M 1 10 L 3 9 L 6 9 L 7 11 L 7 14 L 5 15 L 3 15 L 1 13 Z M 17 10 L 19 9 L 22 9 L 24 12 L 22 15 L 19 15 L 17 13 Z")
                    "Pause" -> path("M 8 4 L 8 20 M 16 4 L 16 20")
                    "Close" -> path("M 5 5 L 19 19 M 19 5 L 5 19")
                }
            }
        }
        if (name == "Select") Text("OK", fontSize = 10.sp, color = tint)
    }
}
