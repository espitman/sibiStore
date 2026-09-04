package com.sibi.store.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import android.util.Base64
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

val Gold = Color(0xFFFFC107)
val DeepBlack = Color(0xFF050505)
val Panel = Color(0xFF0E0E0F)
val Border = Color(0xFF29292C)
val Muted = Color(0xFFA6A6AD)
val Green = Color(0xFF16C98D)
@Composable fun SibiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Gold,onPrimary=DeepBlack,background=DeepBlack,surface=Panel,onSurface=Color(0xFFE8E8E8),outline=Border)) {
        CompositionLocalProvider(LocalContentColor provides Color(0xFFE8E8E8), content=content)
    }
}
@Composable fun Brand(modifier: Modifier = Modifier, size: Int = 29) {
    Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
        Text("sibi",fontSize=size.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp)
        Text("store",fontSize=(size*0.66).sp,color=Gold,fontWeight=FontWeight.SemiBold,modifier=Modifier.align(Alignment.Bottom).padding(bottom=3.dp))
        Icon(painterResource(R.drawable.ic_brand_bag),null,tint=Gold,modifier=Modifier.padding(start=4.dp).size(size.dp))
    }
}
@Composable fun AppIcon(app: StoreApp, size: Dp = 52.dp) {
    val shape = RoundedCornerShape(size*0.23f)
    val bytes = remember(app.icon) { app.icon?.takeIf { it.startsWith("data:image/") && it.contains(";base64,") }?.let { runCatching { Base64.decode(it.substringAfter(";base64,"), Base64.DEFAULT) }.getOrNull() } }
    Box(Modifier.size(size).clip(shape).background(if(app.title.contains("Sibi",true)) Gold else Color(0xFF202025)),contentAlignment=Alignment.Center) {
        if(app.title.contains("Sibi",true)) Icon(Icons.Filled.PlayArrow,null,tint=DeepBlack,modifier=Modifier.size(size*0.7f))
        else Text(app.title.take(1).uppercase(),fontSize=(size.value*0.48f).sp,fontWeight=FontWeight.Bold)
        if (bytes != null) AsyncImage(model=bytes,contentDescription=null,modifier=Modifier.fillMaxSize())
    }
}
@Composable fun Dot(connected: Boolean) { Box(Modifier.size(8.dp).clip(RoundedCornerShape(8.dp)).background(if(connected) Green else Muted)) }
fun bytesLabel(bytes: Long) = if(bytes >= 1048576) "${bytes/1048576} MB" else "${bytes/1024} KB"
fun statusLabel(status: Availability) = when(status) { Availability.INSTALL -> "Install"; Availability.UPDATE -> "Update"; Availability.CURRENT,Availability.NEWER -> "Open"; Availability.INCOMPATIBLE -> "Not compatible"; Availability.SIGNATURE_MISMATCH -> "Signature mismatch" }
