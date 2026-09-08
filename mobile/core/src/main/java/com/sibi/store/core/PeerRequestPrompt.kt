package com.sibi.store.core

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Foreground fallback for devices where request notifications are hidden or disabled. */
@Composable fun PeerRequestPrompt() {
    val manager = PeerManager.get(LocalContext.current.applicationContext)
    val state by manager.state.collectAsState()
    val request = state.transfers.firstOrNull { it.incoming && it.status == "pending" } ?: return
    AlertDialog(
        onDismissRequest = {},
        containerColor = Color(0xFF242015),
        titleContentColor = Color(0xFFFFC107),
        textContentColor = Color.White,
        title = { Text("Incoming file request") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("${request.senderName} wants to send:")
                request.files.take(8).forEach { Text("${it.name}  ·  ${bytesLabel(it.size)}", Modifier.padding(top = 7.dp)) }
                if (request.files.size > 8) Text("and ${request.files.size - 8} more", Modifier.padding(top = 7.dp), color = Color(0xFFAAAAAF))
            }
        },
        dismissButton = { TextButton(onClick = { manager.decide(request.id, false) }) { Text("Reject", color = Color.White) } },
        confirmButton = { Button(onClick = { manager.decide(request.id, true) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107), contentColor = Color(0xFF09090A))) { Text("Accept") } }
    )
}
