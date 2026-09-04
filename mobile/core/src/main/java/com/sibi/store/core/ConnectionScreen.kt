package com.sibi.store.core

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction

@Composable fun ConnectionPanel(model: StoreModel, state: StoreState, modifier: Modifier = Modifier) {
    var address by remember(state.host?.url) { mutableStateOf(state.host?.url ?: "") }
    val focus = LocalFocusManager.current
    val connect = { focus.clearFocus(); model.connectAddress(address) }
    Column(modifier,verticalArrangement=Arrangement.spacedBy(17.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Computer,null,tint=Gold,modifier=Modifier.size(32.dp)); Column { Text("Connect to your Mac",fontSize=22.sp,fontWeight=FontWeight.SemiBold); Text("Keep Sibi Store open on your home network.",color=Muted,fontSize=13.sp) }
        }
        if(state.connected) Row(horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=Alignment.CenterVertically) { Dot(true); Text(state.host?.name ?: "Mac connected",fontSize=14.sp) }
        state.hosts.forEach { host -> OutlinedButton(onClick={model.connect(host)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(10.dp)) { Icon(Icons.Outlined.Computer,null); Spacer(Modifier.width(10.dp)); Text(host.name,maxLines=1); Spacer(Modifier.weight(1f)); Icon(Icons.Outlined.ChevronRight,null) } }
        if(state.hosts.isEmpty()) Text(if(state.loading) "Connecting…" else "Searching for Sibi Store…",color=Muted,fontSize=14.sp)
        OutlinedButton(onClick={model.discoverAgain()},enabled=!state.loading) { Icon(Icons.Outlined.Refresh,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Search again") }
        Divider(color=Border)
        Text("Or enter the Mac address",fontWeight=FontWeight.Medium)
        OutlinedTextField(value=address,onValueChange={address=it},singleLine=true,label={Text("Address")},placeholder={Text("192.168.1.20:8743")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(10.dp),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Go),keyboardActions=KeyboardActions(onGo={connect()}))
        Button(onClick=connect,enabled=address.isNotBlank() && !state.loading,shape=RoundedCornerShape(10.dp),modifier=Modifier.fillMaxWidth().height(48.dp)) { Text(if(state.loading) "Connecting…" else "Connect",fontWeight=FontWeight.SemiBold) }
        Text("Find the address in Sibi Store → Settings on your Mac.",fontSize=12.sp,color=Muted)
    }
}
