package com.sibi.store.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sibi.store.core.*

@Composable fun TvApp(model: StoreModel,action:(StoreApp)->Unit) {
    val state by model.state.collectAsState()
    var page by remember { mutableStateOf("Library") }; var selected by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf(false) }; var query by remember { mutableStateOf("") }
    val updates=state.apps.filter { model.status(it)==Availability.UPDATE }
    val apps=(if(page=="Updates")updates else state.apps).filter { it.title.contains(query,true) || it.packageName.contains(query,true) }
    val app=apps.find { it.packageName==selected } ?: apps.firstOrNull()
    val first=remember { FocusRequester() }; val actionFocus=remember { FocusRequester() }
    LaunchedEffect(apps.isEmpty(),page) { if(apps.isNotEmpty() && page!="Settings") { kotlinx.coroutines.delay(100); runCatching { first.requestFocus() } } }
    BackHandler(page!="Library" || search) { if(search) {search=false;query=""} else page="Library" }
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(lineHeight=TextUnit.Unspecified,platformStyle=PlatformTextStyle(includeFontPadding=false))) {
    Row(Modifier.fillMaxSize().background(DeepBlack)) {
        Column(Modifier.width(165.dp).fillMaxHeight().background(Panel).padding(start=23.dp,end=17.dp,top=29.dp,bottom=29.dp)) {
            Brand(size=27); Spacer(Modifier.height(50.dp))
            listOf("Library" to Icons.Outlined.GridView,"Updates" to Icons.Outlined.Download,"Settings" to Icons.Outlined.Settings).forEach { (name,icon) ->
                TvControl(onClick={page=name;query="";search=false},modifier=Modifier.fillMaxWidth().height(48.dp),selected=page==name) {
                    Icon(icon,null,Modifier.size(21.dp)); Spacer(Modifier.width(12.dp)); Text(name,fontSize=14.sp); if(name=="Updates" && updates.isNotEmpty()) { Spacer(Modifier.weight(1f)); Text("${updates.size}",fontSize=12.sp,color=DeepBlack,modifier=Modifier.clip(RoundedCornerShape(12.dp)).background(Gold).padding(horizontal=6.dp,vertical=2.dp)) }
                }; Spacer(Modifier.height(11.dp))
            }
            Spacer(Modifier.weight(1f)); Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) { Icon(Icons.Outlined.Computer,null,tint=Muted,modifier=Modifier.size(21.dp)); Dot(state.connected); Text(if(state.connected) "Mac connected" else "Mac offline",fontSize=10.sp,color=Muted) }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Border))
        Column(Modifier.weight(1f).fillMaxHeight().padding(start=30.dp,end=30.dp,top=27.dp,bottom=16.dp)) {
            if(page=="Settings" || state.apps.isEmpty() && state.host==null) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text("Settings",fontSize=28.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("Sibi Store",color=Muted,fontSize=13.sp) }
                if(state.error!=null) Text(state.error!!,color=Gold,fontSize=12.sp,modifier=Modifier.padding(top=10.dp))
                Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.TopCenter) { ConnectionPanel(model,state,Modifier.widthIn(max=530.dp).verticalScroll(rememberScrollState()).padding(vertical=20.dp)) }
            } else {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top) {
                    Column(Modifier.weight(1f)) { Text(if(page=="Updates") "Updates" else "My apps",fontSize=31.sp,fontWeight=FontWeight.Bold); Text(if(page=="Updates") "New versions from your Mac" else "Your personal library",color=Muted,fontSize=14.sp,modifier=Modifier.padding(top=5.dp)) }
                    TvControl(onClick={search=!search},modifier=Modifier.height(34.dp)) { Icon(Icons.Outlined.Search,null,Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Search",fontSize=14.sp) }
                    Spacer(Modifier.width(22.dp)); Text("${apps.size} apps",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=8.dp))
                }
                if(search) OutlinedTextField(value=query,onValueChange={query=it},placeholder={Text("Search apps")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(top=10.dp))
                Row(Modifier.fillMaxWidth().height(44.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(updates.isNotEmpty()) TvControl(onClick={page="Updates"},compact=true) { Icon(Icons.Outlined.DownloadForOffline,null,tint=Gold,modifier=Modifier.size(17.dp)); Text("${updates.size} updates available",color=Gold,fontSize=13.sp,modifier=Modifier.padding(horizontal=9.dp)); Icon(Icons.Outlined.ChevronRight,null,tint=Muted,modifier=Modifier.size(17.dp)) }
                    else Text(if(state.connected) "Your library is up to date" else "Mac offline · showing saved library",color=Muted,fontSize=12.sp)
                    Spacer(Modifier.weight(1f)); TvControl(onClick={model.refresh()},compact=true) { Icon(Icons.Outlined.Refresh,"Refresh",tint=Muted,modifier=Modifier.size(17.dp)) }
                }
                if(state.error!=null || state.message!=null) TvControl(onClick={model.clearMessage()},modifier=Modifier.fillMaxWidth(),compact=true) { Text(state.error ?: state.message ?: "",color=Gold,fontSize=11.sp,modifier=Modifier.weight(1f),maxLines=2); Icon(Icons.Outlined.Close,"Dismiss",Modifier.size(15.dp)) }
                Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        if(apps.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) { Text(if(query.isNotEmpty()) "No matching apps" else if(page=="Updates") "You're up to date" else "Copy APKs into your Mac's library folder.",color=Muted,fontSize=15.sp) }
                        else LazyVerticalGrid(columns=GridCells.Fixed(3),modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(3.dp)) {
                            itemsIndexed(apps,key={_,a->a.packageName}) { index,a -> AppCard(a,model,if(index==0)Modifier.focusRequester(first) else Modifier,onFocus={selected=a.packageName},onClick={selected=a.packageName;runCatching{actionFocus.requestFocus()}}) }
                        }
                        Text("${apps.size} ${if(apps.size==1)"app" else "apps"} in your library",color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=10.dp,bottom=9.dp))
                    }
                    if(app!=null) TvInspector(app,model,state,action,Modifier.width(260.dp).fillMaxHeight().padding(bottom=8.dp),actionFocus)
                }
            }
            Divider(color=Border); Row(Modifier.fillMaxWidth().height(37.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) { Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.ControlCamera,null,tint=Muted,modifier=Modifier.size(22.dp)); Text("Navigate",color=Muted,fontSize=12.sp,modifier=Modifier.padding(start=9.dp)) }; Text("ⓞ  Select",color=Muted,fontSize=12.sp); Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.KeyboardReturn,null,tint=Muted,modifier=Modifier.size(22.dp));Text("Back",color=Muted,fontSize=12.sp,modifier=Modifier.padding(start=9.dp))} }
        }
    }
}
}
@Composable private fun TvControl(onClick:()->Unit,modifier:Modifier=Modifier,selected:Boolean=false,gold:Boolean=false,compact:Boolean=false,enabled:Boolean=true,content:@Composable RowScope.()->Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape=RoundedCornerShape(9.dp)
    Row(modifier.onFocusChanged{focused=it.isFocused}.clip(shape).background(if(gold)Gold else if(selected)Color(0xFF211C0C) else if(focused)Color(0xFF26231A) else Color.Transparent).border(if(focused)2.dp else 1.dp,if(focused)Gold else if(selected)Color(0xFF69530A) else if(compact)Color.Transparent else Border,shape).clickable(enabled=enabled,onClick=onClick).padding(horizontal=if(compact)5.dp else 12.dp,vertical=if(compact)5.dp else 7.dp),verticalAlignment=Alignment.CenterVertically) {
        CompositionLocalProvider(LocalContentColor provides if(gold)DeepBlack else if(selected)Gold else Color(0xFFE8E8E8)) { content() }
    }
}
@Composable private fun AppCard(app:StoreApp,model:StoreModel,modifier:Modifier,onFocus:()->Unit,onClick:()->Unit) {
    var focused by remember { mutableStateOf(false) }; val shape=RoundedCornerShape(12.dp); val status=model.status(app)
    Column(modifier.fillMaxWidth().height(151.dp).onFocusChanged{focused=it.isFocused;if(it.isFocused)onFocus()}.clip(shape).background(Panel).border(if(focused)2.dp else 1.dp,if(focused)Gold else Border,shape).clickable(onClick=onClick).padding(13.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        AppIcon(app,60.dp); Spacer(Modifier.height(12.dp)); Text(app.title,fontSize=15.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis); Text(model.release(app)?.versionName ?: "—",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=5.dp)); Text(when(status){Availability.UPDATE->"Update available";Availability.INSTALL->"Ready to install";Availability.CURRENT->"Installed";Availability.NEWER->"Newer installed";Availability.INCOMPATIBLE->"Not compatible";Availability.SIGNATURE_MISMATCH->"Signature mismatch"},fontSize=11.sp,color=if(status==Availability.UPDATE)Gold else Muted,maxLines=1,modifier=Modifier.padding(top=4.dp))
    }
}
@Composable private fun TvInspector(app:StoreApp,model:StoreModel,state:StoreState,action:(StoreApp)->Unit,modifier:Modifier,actionFocus:FocusRequester) {
    val r=model.release(app) ?: app.versions.first(); val current=state.installed[app.packageName];val download=state.downloads[r.sha256]; val busy=download?.state in listOf("downloading","queued");val status=model.status(app)
    Column(modifier.clip(RoundedCornerShape(13.dp)).background(Panel).border(1.dp,Border,RoundedCornerShape(13.dp)).padding(20.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) { AppIcon(app,65.dp); Column { Text(app.title,fontSize=19.sp,fontWeight=FontWeight.SemiBold,maxLines=2); Text("Android app",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=6.dp)) } }
        Spacer(Modifier.height(20.dp)); InfoRow("Installed",current?.versionName ?: "Not installed");Divider(color=Border);InfoRow("Available",r.versionName)
        Text("${bytesLabel(r.size)}  ·  Android API ${r.minSdk}+",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=10.dp,bottom=18.dp))
        val ready=download?.state=="ready" && status in listOf(Availability.INSTALL,Availability.UPDATE)
        TvControl(onClick={if(busy)model.pause(r.sha256) else action(app)},modifier=Modifier.fillMaxWidth().height(42.dp).focusRequester(actionFocus),gold=true,enabled=status !in listOf(Availability.INCOMPATIBLE,Availability.SIGNATURE_MISMATCH) && (state.connected || ready || status in listOf(Availability.CURRENT,Availability.NEWER))) {
            Spacer(Modifier.weight(1f)); Icon(if(busy)Icons.Outlined.Pause else Icons.Outlined.Download,null,Modifier.size(20.dp)); Spacer(Modifier.width(9.dp)); Text(when{busy->"Pause";ready->"Install";download?.state in listOf("paused","failed")->"Resume";else->statusLabel(status)},fontSize=16.sp,fontWeight=FontWeight.Medium);Spacer(Modifier.weight(1f))
        }
        if(download!=null && (busy || download.state in listOf("paused","failed"))) { LinearProgressIndicator(progress=if(download.total>0)download.bytes.toFloat()/download.total else 0f,modifier=Modifier.fillMaxWidth().padding(top=15.dp),color=Gold,trackColor=Border);Text(download.error ?: "${bytesLabel(download.bytes)} of ${bytesLabel(download.total)}",color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=8.dp)) }
        Spacer(Modifier.height(22.dp)); Text("What's new",fontSize=16.sp,fontWeight=FontWeight.Medium);Text("No release notes included with this APK.",color=Muted,fontSize=12.sp,lineHeight=19.sp,modifier=Modifier.padding(top=9.dp,bottom=18.dp))
        Divider(color=Border);Row(Modifier.padding(top=14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Computer,null,tint=Muted,modifier=Modifier.size(22.dp));Text("From your Mac",fontSize=12.sp,color=Muted,modifier=Modifier.padding(start=10.dp))}
    }
}
@Composable private fun InfoRow(label:String,value:String) {Row(Modifier.fillMaxWidth().padding(vertical=10.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(label,color=Muted,fontSize=13.sp);Text(value,fontSize=13.sp)}}
