package com.sibi.store.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sibi.store.core.*

@Composable fun MobileApp(model: StoreModel, action: (StoreApp)->Unit) {
    val state by model.state.collectAsState()
    var tab by remember { mutableStateOf("Library") }; var selected by remember { mutableStateOf<String?>(null) }
    val app = state.apps.find { it.packageName == selected }
    val updates = state.apps.filter { model.status(it) == Availability.UPDATE }
    BackHandler(selected != null || tab != "Library") { if(selected != null) selected=null else tab="Library" }
    Scaffold(containerColor=DeepBlack,bottomBar={
        Column { Divider(color=Border); NavigationBar(containerColor=Panel,tonalElevation=0.dp,modifier=Modifier.height(76.dp)) {
            listOf("Library" to Icons.Outlined.GridView,"Updates" to Icons.Outlined.Download,"Settings" to Icons.Outlined.Settings).forEach { (name,icon) ->
                NavigationBarItem(selected=tab==name,onClick={tab=name;selected=null},icon={ BadgedBox(badge={if(name=="Updates" && updates.isNotEmpty()) Badge(containerColor=Gold,contentColor=DeepBlack){Text("${updates.size}")} }) { Icon(icon,name,Modifier.size(26.dp)) } },label={Text(name,fontSize=12.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Gold,selectedTextColor=Gold,indicatorColor=Color.Transparent,unselectedIconColor=Muted,unselectedTextColor=Muted))
            }
        } }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(state.error != null || state.message != null) Surface(color=Color(0xFF302810)) { Row(Modifier.fillMaxWidth().padding(start=16.dp),verticalAlignment=Alignment.CenterVertically) { Text(state.error ?: state.message ?: "",Modifier.weight(1f),fontSize=12.sp,color=Gold); IconButton(onClick={model.clearMessage()}) { Icon(Icons.Outlined.Close,"Dismiss",Modifier.size(18.dp)) } } }
            when {
                app != null -> AppDetails(app,model,state,action){selected=null}
                tab == "Settings" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp)) { Text("Settings",fontSize=28.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(25.dp)); ConnectionPanel(model,state); Spacer(Modifier.height(30.dp)); Text("Sibi Store 0.1.0",color=Muted,fontSize=12.sp) }
                tab == "Updates" -> UpdatesScreen(updates,model,state,action)
                else -> LibraryScreen(model,state,updates.size,action,{selected=it.packageName},{tab="Updates"},{tab="Settings"})
            }
        }
    }
}
@Composable private fun LibraryScreen(model: StoreModel,state: StoreState,count: Int,action: (StoreApp)->Unit,select: (StoreApp)->Unit,updates: ()->Unit,settings: ()->Unit) {
    var query by remember { mutableStateOf("") }; var filter by remember { mutableStateOf("All") }
    val apps = state.apps.filter { (it.title.contains(query,true)||it.packageName.contains(query,true)) && when(filter){"Installed"->state.installed.containsKey(it.packageName);"Not installed"->!state.installed.containsKey(it.packageName);else->true} }
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=13.dp,bottom=25.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Brand(size=28)
            Row(Modifier.clip(RoundedCornerShape(30.dp)).border(1.dp,Border,RoundedCornerShape(30.dp)).clickable(onClick=settings).padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) { Dot(state.connected); Text(if(state.connected) "Mac connected" else "Connect Mac",fontSize=11.sp,color=if(state.connected) Color(0xFF9BE4BD) else Muted) }
        }
        Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("My apps",fontSize=27.sp,fontWeight=FontWeight.Bold); Text("Your apps, all in one place",color=Muted,fontSize=14.sp,modifier=Modifier.padding(top=5.dp)) }; IconButton(onClick={model.refresh()},enabled=!state.loading) { Icon(Icons.Outlined.Refresh,"Refresh",tint=Muted,modifier=Modifier.size(20.dp)) } }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(value=query,onValueChange={query=it},placeholder={Text("Search apps",fontSize=14.sp)},leadingIcon={Icon(Icons.Outlined.Search,null,tint=Muted)},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(10.dp),colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Color(0xFF161617),unfocusedBorderColor=Border))
        Row(Modifier.fillMaxWidth().padding(vertical=14.dp),horizontalArrangement=Arrangement.spacedBy(9.dp)) { listOf("All","Installed","Not installed").forEach { label ->
            val active = filter == label
            Box(Modifier.weight(if(label=="Not installed")1.3f else 1f).height(35.dp).clip(RoundedCornerShape(8.dp)).background(if(active) Gold else DeepBlack).border(1.dp,if(active) Gold else Border,RoundedCornerShape(8.dp)).clickable { filter=label },contentAlignment=Alignment.Center) { Text(label,color=if(active) DeepBlack else Color.White,fontSize=12.sp) }
        } }
        if(count>0) Row(Modifier.fillMaxWidth().background(Panel).clickable(onClick=updates).padding(vertical=12.dp,horizontal=9.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.Sync,null,tint=Muted,modifier=Modifier.size(18.dp)); Text("$count updates available",Modifier.weight(1f).padding(start=12.dp),fontSize=13.sp); Icon(Icons.Outlined.ChevronRight,null,tint=Muted) }
        if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth(),color=Gold,trackColor=Border)
        if(state.apps.isEmpty() && state.host==null) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical=24.dp)) { ConnectionPanel(model,state) }
        else if(apps.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) { Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) { Icon(Icons.Outlined.Inventory2,null,tint=Muted,modifier=Modifier.size(38.dp)); Text(if(query.isNotBlank()) "No matching apps" else "Your library is empty",fontWeight=FontWeight.Medium); Text("Copy APKs into your Mac's library folder.",color=Muted,fontSize=12.sp) } }
        else LazyColumn(Modifier.weight(1f)) { items(apps,key={it.packageName}) { app ->
            Row(Modifier.fillMaxWidth().clickable { select(app) }.padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) { AppIcon(app,52.dp); Column(Modifier.weight(1f)) { Text(app.title,fontSize=16.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis); Text(model.release(app)?.versionName ?: "Not compatible",fontSize=13.sp,color=Muted,modifier=Modifier.padding(top=4.dp)) }; ActionButton(app,model,state,action) }; Divider(color=Color(0xFF1C1C1E))
        } }
    }
}
@Composable private fun ActionButton(app: StoreApp,model: StoreModel,state: StoreState,action:(StoreApp)->Unit,wide:Boolean=false) {
    val status = model.status(app); val r=model.release(app); val download=state.downloads[r?.sha256]
    val busy=download?.state in listOf("queued","downloading")
    val ready=download?.state=="ready" && status in listOf(Availability.INSTALL,Availability.UPDATE)
    val enabled=status !in listOf(Availability.INCOMPATIBLE,Availability.SIGNATURE_MISMATCH) && !busy && (state.connected || ready || status in listOf(Availability.CURRENT,Availability.NEWER))
    val label=when {ready->"Install";busy->if(download?.state=="queued")"Queued" else "${if(download!!.total>0) download.bytes*100/download.total else 0}%";download?.state in listOf("paused","failed")->"Resume";wide && status==Availability.UPDATE->"Download update";else->statusLabel(status)}
    val gold=status in listOf(Availability.UPDATE,Availability.INSTALL)
    Button(onClick={action(app)},enabled=enabled,shape=RoundedCornerShape(8.dp),contentPadding=PaddingValues(horizontal=14.dp,vertical=0.dp),modifier=if(wide) Modifier.fillMaxWidth().height(49.dp) else Modifier.height(35.dp).widthIn(min=76.dp,max=120.dp),colors=ButtonDefaults.buttonColors(containerColor=if(gold)Gold else Color.Transparent,contentColor=if(gold)DeepBlack else Color.White,disabledContainerColor=Color(0xFF292514),disabledContentColor=Muted),border=if(gold)null else BorderStroke(1.dp,Border)) { if(wide && gold) { Icon(Icons.Outlined.Download,null,Modifier.size(21.dp)); Spacer(Modifier.width(10.dp)) }; Text(label,fontSize=if(wide)16.sp else 12.sp,fontWeight=FontWeight.Medium,maxLines=1) }
}
@Composable private fun AppDetails(app:StoreApp,model:StoreModel,state:StoreState,action:(StoreApp)->Unit,back:()->Unit) {
    val release=model.release(app) ?: app.versions.first(); val current=state.installed[app.packageName]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=21.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.SpaceBetween) { IconButton(onClick=back){Icon(Icons.Outlined.ArrowBack,"Back")}; IconButton(onClick={model.refresh()}){Icon(Icons.Outlined.Refresh,"Refresh")} }
        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) { AppIcon(app,100.dp); Text(app.title,fontSize=27.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=17.dp)); Text(app.packageName,color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=6.dp)); if(model.status(app)==Availability.UPDATE) Text("New version",fontSize=12.sp,color=Gold,modifier=Modifier.padding(top=15.dp).border(1.dp,Color(0xFF66530D),RoundedCornerShape(20.dp)).padding(horizontal=12.dp,vertical=5.dp)) }
        Spacer(Modifier.height(24.dp)); ActionButton(app,model,state,action,wide=true)
        val download=state.downloads[release.sha256]
        if(download!=null && download.state in listOf("downloading","queued","paused","failed")) { Spacer(Modifier.height(12.dp)); DownloadProgress(download,model) }
        Row(Modifier.fillMaxWidth().padding(top=19.dp).clip(RoundedCornerShape(12.dp)).background(Panel).border(1.dp,Border,RoundedCornerShape(12.dp)).padding(vertical=18.dp)) {
            listOf("Version" to release.versionName,"Size" to bytesLabel(release.size),"Android" to "API ${release.minSdk}+").forEach { (label,value) -> Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) { Text(label,color=Muted,fontSize=12.sp); Text(value,fontSize=14.sp,modifier=Modifier.padding(top=8.dp)) } }
        }
        Spacer(Modifier.height(30.dp)); Text("What's new",fontSize=20.sp,fontWeight=FontWeight.SemiBold); Text("No release notes included with this APK.",color=Muted,fontSize=14.sp,modifier=Modifier.padding(top=12.dp,bottom=27.dp))
        DetailRow("Installed version",current?.versionName ?: "Not installed"); DetailRow("Available version",release.versionName); DetailRow("Version code",release.versionCode.toString())
        Row(Modifier.fillMaxWidth().padding(vertical=23.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.Computer,null,tint=Muted); Text("Mac library",color=Muted,fontSize=14.sp,modifier=Modifier.padding(start=11.dp)) }
    }
}
@Composable private fun DetailRow(label:String,value:String) { Divider(color=Border); Row(Modifier.fillMaxWidth().padding(vertical=15.dp),horizontalArrangement=Arrangement.SpaceBetween) { Text(label,color=Muted,fontSize=14.sp); Text(value,fontSize=14.sp) } }
@Composable private fun DownloadProgress(download:Download,model:StoreModel) {
    Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(when(download.state){"downloading"->"Downloading…";"queued"->"Waiting for network…";"paused"->"Paused";else->download.error ?: "Download failed"},color=Gold,fontSize=12.sp); LinearProgressIndicator(progress=if(download.total>0)download.bytes.toFloat()/download.total else 0f,modifier=Modifier.fillMaxWidth().padding(top=10.dp,bottom=8.dp).height(4.dp),color=Gold,trackColor=Border); Text("${bytesLabel(download.bytes)} of ${bytesLabel(download.total)}",color=Muted,fontSize=12.sp) }; if(download.state in listOf("downloading","queued")) IconButton(onClick={model.pause(download.hash)}) { Icon(Icons.Outlined.PauseCircle,"Pause",tint=Muted) } }
}
@Composable private fun UpdatesScreen(apps:List<StoreApp>,model:StoreModel,state:StoreState,action:(StoreApp)->Unit) {
    Column(Modifier.fillMaxSize().padding(21.dp)) { Text("Updates",fontSize=28.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=11.dp)); Text("${apps.size} new ${if(apps.size==1)"version" else "versions"} in your library",fontSize=14.sp,color=Muted,modifier=Modifier.padding(top=8.dp,bottom=25.dp))
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)) { items(apps,key={it.packageName}) { app ->
            val release=model.release(app)!!; val download=state.downloads[release.sha256]
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).border(1.dp,Border,RoundedCornerShape(14.dp)).padding(17.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
                AppIcon(app,62.dp); Column(Modifier.weight(1f)) { Text(app.title,fontWeight=FontWeight.SemiBold,fontSize=17.sp); Spacer(Modifier.height(10.dp)); if(download?.state in listOf("downloading","queued")) DownloadProgress(download!!,model) else { Text("Installed: ${state.installed[app.packageName]?.versionName}",color=Muted,fontSize=12.sp); Text("Available: ${release.versionName}",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=6.dp)); download?.error?.let { Text(it,color=Gold,fontSize=11.sp) } } }; if(download?.state !in listOf("downloading","queued")) ActionButton(app,model,state,action)
            }
        }; if(apps.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(top=100.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(15.dp)) { Icon(Icons.Outlined.CheckCircle,null,tint=Gold,modifier=Modifier.size(42.dp)); Text(if(state.connected)"You're up to date" else "Connect to check for updates",color=Muted) } } }
        Row(Modifier.fillMaxWidth().padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) { Icon(Icons.Outlined.Info,null,tint=Muted,modifier=Modifier.size(17.dp)); Text("Confirm installation after downloading",color=Muted,fontSize=12.sp,modifier=Modifier.padding(start=10.dp)) }
    }
}
