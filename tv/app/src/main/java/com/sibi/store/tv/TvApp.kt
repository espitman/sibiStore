package com.sibi.store.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sibi.store.core.*

// Reference: 1680 × 941 artwork, mapped to the TV's 960 × 540 logical canvas.
@Composable fun TvApp(model: StoreModel, action: (StoreApp) -> Unit) {
    val state by model.state.collectAsState()
    var page by remember { mutableStateOf("Library") }
    var selected by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val updates = state.apps.filter { model.status(it) == Availability.UPDATE }
    val apps = (if (page == "Updates") updates else state.apps).filter { it.title.contains(query, true) || it.packageName.contains(query, true) }
    val app = apps.find { it.packageName == selected } ?: apps.firstOrNull()
    val first = remember { FocusRequester() }
    val actionFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    LaunchedEffect(apps.isEmpty(), page) {
        if (apps.isNotEmpty() && page != "Settings") {
            kotlinx.coroutines.delay(100)
            runCatching { first.requestFocus() }
        }
    }
    BackHandler(page != "Library" || search) { if (search) { search = false; query = "" } else page = "Library" }
    CompositionLocalProvider(LocalContentColor provides TvWhite, LocalTextStyle provides TextStyle(
        fontFamily = FontFamily.SansSerif, fontSize = 13.sp, letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false))) {
        Row(Modifier.fillMaxSize().background(TvBlack)) {
            Column(Modifier.width(163.dp).fillMaxHeight().background(TvSidebar)
                .padding(start = 19.dp, end = 19.dp, top = 33.dp, bottom = 60.dp)) {
                TvBrand(Modifier.padding(start = 7.dp))
                Spacer(Modifier.height(51.dp))
                listOf("Library", "Updates", "Settings").forEach { name ->
                    TvControl(onClick = { page = name; query = ""; search = false },
                        modifier = Modifier.fillMaxWidth().height(49.dp), selected = page == name, borderless = true) {
                        TvGlyph(name, size = 24.dp)
                        Spacer(Modifier.width(13.dp))
                        Text(name, fontSize = 13.sp)
                        if (name == "Updates" && updates.isNotEmpty()) {
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.size(18.dp).background(TvGold, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                                Text("${updates.size}", fontSize = 12.sp, color = TvBlack)
                            }
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.padding(start = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvGlyph("Computer", size = 24.dp, tint = TvMuted)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(7.dp).background(if (state.connected) Green else TvMuted, RoundedCornerShape(50)))
                    Text(if (state.connected) "Mac connected" else "Mac offline", fontSize = 10.sp, color = TvMuted, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(TvBorder))
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 32.dp, end = 35.dp, top = 31.dp, bottom = 17.dp)) {
                if (page == "Settings" || state.apps.isEmpty() && state.host == null) {
                    Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    if (state.error != null) Text(state.error!!, color = TvGold, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        ConnectionPanel(model, state, Modifier.widthIn(max = 530.dp).verticalScroll(rememberScrollState()).padding(vertical = 20.dp))
                    }
                } else {
                    BoxWithConstraints(Modifier.fillMaxWidth().height(58.dp)) {
                        Column {
                            Text(if (page == "Updates") "Updates" else "My apps", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text(if (page == "Updates") "New versions from your Mac" else "Your personal library", color = TvMuted, fontSize = 13.5.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                        TvControl(onClick = { search = !search }, modifier = Modifier.offset(x = maxWidth - 317.dp).width(93.dp).height(33.dp)) {
                            TvGlyph("Search", size = 19.dp)
                            Text("Search", fontSize = 13.5.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                        Text("${apps.size} apps", color = TvMuted, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp))
                    }
                    if (search) OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search apps") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(39.dp)) {
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Row(Modifier.fillMaxWidth().height(41.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (updates.isNotEmpty()) TvControl(onClick = { page = "Updates" }, compact = true, borderless = true) {
                                    TvGlyph("UpdateNotice", size = 17.dp, tint = TvGold)
                                    Text("${updates.size} ${if (updates.size == 1) "update" else "updates"} available", color = TvGold, fontSize = 12.5.sp, modifier = Modifier.padding(horizontal = 9.dp))
                                    TvGlyph("Chevron", size = 17.dp, tint = TvMuted)
                                } else Text(if (state.connected) "Your library is up to date" else "Mac offline · showing saved library", color = TvMuted, fontSize = 12.sp)
                            }
                            if (state.error != null || state.message != null) TvControl(onClick = { model.clearMessage() }, modifier = Modifier.fillMaxWidth(), compact = true, borderless = true) {
                                Text(state.error ?: state.message ?: "", color = TvGold, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 2)
                                TvGlyph("Close", size = 15.dp)
                            }
                            if (apps.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(if (query.isNotEmpty()) "No matching apps" else if (page == "Updates") "You're up to date" else "Copy APKs into your Mac's library folder.", color = TvMuted, fontSize = 15.sp)
                            } else LazyVerticalGrid(columns = GridCells.Fixed(3), state = gridState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 5.dp)) {
                                itemsIndexed(apps, key = { _, a -> a.packageName }) { index, a ->
                                    AppCard(a, model, if (index == 0) Modifier.focusRequester(first) else Modifier,
                                        onFocus = { selected = a.packageName }, onClick = { selected = a.packageName; runCatching { actionFocus.requestFocus() } })
                                }
                            }
                            Row(Modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                                val pageCount = (apps.size + 5) / 6
                                if (pageCount > 1) repeat(pageCount.coerceAtMost(8)) { index ->
                                    Box(Modifier.padding(end = 6.dp).size(8.dp).background(if (index == gridState.firstVisibleItemIndex / 6) TvGold else TvMuted.copy(alpha = 0.5f), RoundedCornerShape(50)))
                                }
                                val visibleCount = gridState.layoutInfo.visibleItemsInfo.count { it.offset.y >= 0 && it.offset.y + it.size.height <= gridState.layoutInfo.viewportEndOffset }
                                Text("Showing ${visibleCount.coerceAtMost(apps.size)} of ${apps.size} apps", color = TvMuted, fontSize = 11.5.sp, modifier = Modifier.padding(start = if (pageCount > 1) 4.dp else 0.dp))
                            }
                        }
                        if (app != null) TvInspector(app, model, state, action, Modifier.width(260.dp).fillMaxHeight().padding(bottom = 18.dp), actionFocus)
                    }
                }
                Divider(color = TvBorder, thickness = 0.7.dp)
                Row(Modifier.fillMaxWidth().height(39.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("Navigate", "Select", "Back").forEach { label ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            TvGlyph(label, size = 27.dp, tint = TvMuted)
                            Text(label, color = TvMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun TvControl(onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, gold: Boolean = false,
    compact: Boolean = false, borderless: Boolean = false, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (selected) 10.dp else 7.dp)
    val brush = when {
        gold -> Brush.verticalGradient(listOf(Color(0xFFFFD300), Color(0xFFFFC800)))
        selected -> Brush.verticalGradient(listOf(Color(0xFF171400), Color(0xFF100E00)))
        else -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    }
    Row(modifier.onFocusChanged { focused = it.isFocused }.clip(shape).background(brush)
        .border(if (focused) 1.5.dp else 0.8.dp, when { focused -> TvGold; gold -> Color.Transparent; selected -> Color(0xFF756000); borderless -> Color.Transparent; else -> Color(0xFF737373) }, shape)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick)
        .padding(start = if (compact) 0.dp else if (borderless) 14.dp else 11.dp,
            end = if (compact || borderless) 0.dp else 11.dp, top = if (compact) 4.dp else 5.dp, bottom = if (compact) 4.dp else 5.dp), verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(LocalContentColor provides if (gold) TvBlack else if (selected) TvGold else TvWhite) { content() }
    }
}

@Composable private fun AppCard(app: StoreApp, model: StoreModel, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    val status = model.status(app)
    Column(modifier.fillMaxWidth().height(149.dp).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
        .graphicsLayer { scaleX = if (focused) 1.055f else 1f; scaleY = if (focused) 1.045f else 1f }
        .clip(shape).background(TvCardBrush).border(if (focused) 1.5.dp else 0.8.dp, if (focused) TvGold else TvBorder, shape)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
        .padding(start = 7.dp, end = 7.dp, top = 16.dp, bottom = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TvAppIcon(app, 62.dp)
        Spacer(Modifier.height(10.dp))
        Text(app.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(model.release(app)?.versionName ?: "—", fontSize = 13.sp, color = TvMuted, modifier = Modifier.padding(top = 4.dp))
        Text(when (status) {
            Availability.UPDATE -> "Update available"; Availability.INSTALL -> "Ready to install"; Availability.CURRENT -> "Installed"
            Availability.NEWER -> "Newer installed"; Availability.INCOMPATIBLE -> "Not compatible"; Availability.SIGNATURE_MISMATCH -> "Signature mismatch"
        }, fontSize = 12.5.sp, color = if (status == Availability.UPDATE) TvGold else TvMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable private fun TvInspector(app: StoreApp, model: StoreModel, state: StoreState, action: (StoreApp) -> Unit, modifier: Modifier, actionFocus: FocusRequester) {
    val r = model.release(app) ?: app.versions.first()
    val current = state.installed[app.packageName]
    val download = state.downloads[r.sha256]
    val busy = download?.state in listOf("downloading", "queued")
    val status = model.status(app)
    Column(modifier.clip(RoundedCornerShape(11.dp)).background(TvCardBrush).border(0.8.dp, TvBorder, RoundedCornerShape(11.dp))
        .padding(start = 21.dp, end = 21.dp, top = 19.dp, bottom = 9.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(19.dp)) {
            TvAppIcon(app, 71.dp)
            Column { Text(app.title, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                Text("Android app", color = TvMuted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 5.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        InfoRow("Installed", current?.versionName ?: "Not installed")
        Divider(color = TvBorder, thickness = 0.6.dp)
        InfoRow("Available", r.versionName)
        Text("${bytesLabel(r.size)}  ·  ${androidRequirement(r.minSdk)}", color = TvMuted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
        val ready = download?.state == "ready" && status in listOf(Availability.INSTALL, Availability.UPDATE)
        TvControl(onClick = { if (busy) model.pause(r.sha256) else action(app) }, modifier = Modifier.fillMaxWidth().height(40.dp).focusRequester(actionFocus), gold = true,
            enabled = status !in listOf(Availability.INCOMPATIBLE, Availability.SIGNATURE_MISMATCH) && (state.connected || ready || status in listOf(Availability.CURRENT, Availability.NEWER))) {
            Spacer(Modifier.weight(1f)); TvGlyph(if (busy) "Pause" else "Updates", size = 21.dp); Spacer(Modifier.width(10.dp))
            Text(when { busy -> "Pause"; ready -> "Install"; download?.state in listOf("paused", "failed") -> "Resume"; else -> statusLabel(status) }, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
        }
        if (download != null && (busy || download.state in listOf("paused", "failed"))) {
            LinearProgressIndicator(progress = if (download.total > 0) download.bytes.toFloat() / download.total else 0f, modifier = Modifier.fillMaxWidth().padding(top = 15.dp), color = TvGold, trackColor = TvBorder)
            Text(download.error ?: "${bytesLabel(download.bytes)} of ${bytesLabel(download.total)}", color = TvMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(21.dp))
        Text("What’s new", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text("No release notes included with this APK.", color = TvMuted, fontSize = 12.5.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp).heightIn(min = 47.dp))
        Spacer(Modifier.height(8.dp)); Divider(color = TvBorder, thickness = 0.6.dp)
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TvGlyph("Computer", size = 25.dp, tint = TvMuted)
            Text("From your Mac", fontSize = 12.5.sp, color = TvMuted, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

@Composable private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TvMuted, fontSize = 12.5.sp); Text(value, fontSize = 12.5.sp)
    }
}

private fun androidRequirement(api: Int): String {
    val version = mapOf(21 to "5", 22 to "5.1", 23 to "6", 24 to "7", 25 to "7.1", 26 to "8", 27 to "8.1", 28 to "9", 29 to "10", 30 to "11", 31 to "12", 32 to "12L", 33 to "13", 34 to "14", 35 to "15")[api]
    return if (version != null) "Android $version+" else "Android API $api+"
}
