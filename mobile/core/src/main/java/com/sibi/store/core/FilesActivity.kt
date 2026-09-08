package com.sibi.store.core

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect

/** Native Android file-sharing screen shared by phone, TV, and VR. */
class FilesActivity : ComponentActivity() {
    private val manager by lazy { PeerManager.get(applicationContext) }
    private val recipients = linkedSetOf<String>()
    private lateinit var content: LinearLayout
    private var pickerError: String? = null
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val uris = buildList {
            data.clipData?.let { clips -> repeat(clips.itemCount) { add(clips.getItemAt(it).uri) } }
            if (isEmpty()) data.data?.let(::add)
        }.distinct()
        uris.forEach { runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        pickerError = null
        if (uris.isNotEmpty()) manager.chooseFiles(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = INK; window.navigationBarColor = INK
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(18), dp(22), dp(30)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(INK); addView(content) })
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { manager.state.collect(::render) } }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 33)
        manager.start()
    }

    override fun onResume() {
        super.onResume()
        manager.start()
    }

    private fun render(state: PeerState) {
        val focusedKey = content.findFocus()?.tag as? String
        recipients.retainAll(state.peers.mapTo(hashSetOf()) { it.id })
        content.removeAllViews()
        content.addView(label("Files", 30, Color.WHITE, true))
        content.addView(label("Send files directly to nearby Sibi Store devices.", 14, MUTED).spaced(5, 18))
        state.error?.let { content.addView(message(it)) }; pickerError?.let { content.addView(message(it)) }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            content.addView(message("File request notifications are off. Keep Sibi Store open or enable notifications in system settings."))
        content.addView(section("Files to send")); content.addView(button("Choose files", enabled = !state.busy) { openPicker() }.apply { tag = "choose" })
        content.addView(button("Choose received files", enabled = !state.busy) { chooseReceivedFiles() }.apply { tag = "received" })
        if (state.files.isEmpty()) content.addView(label("No files selected", 14, MUTED).spaced(12, 10))
        else state.files.forEach { content.addView(fileLine(it)) }
        content.addView(section("Nearby devices"))
        if (state.peers.isEmpty()) content.addView(label(if (state.busy) "Looking for devices…" else "No nearby devices found", 14, MUTED).spaced(4, 10))
        state.peers.forEach { peer -> content.addView(CheckBox(this).apply {
            text = "${peer.name}  ·  ${peer.platform}"; setTextColor(Color.WHITE); textSize = 16f
            isChecked = peer.id in recipients; isFocusable = true; minHeight = dp(52); tag = "peer:${peer.id}"
            buttonTintList = android.content.res.ColorStateList.valueOf(GOLD)
            setOnCheckedChangeListener { _, checked ->
                if (checked) recipients += peer.id else recipients -= peer.id
                render(manager.state.value)
            }
        }) }
        content.addView(button(if (state.busy) "Working…" else "Send", true, state.files.isNotEmpty() && recipients.isNotEmpty() && !state.busy) { manager.send(recipients.toSet()) }.apply { tag = "send" }.spaced(10, 20))
        content.addView(section("Transfers"))
        if (state.transfers.isEmpty()) content.addView(label("No transfers yet", 14, MUTED))
        state.transfers.sortedBy { it.status.lowercase() != "pending" }.forEach { content.addView(transfer(it)) }
        if (focusedKey != null) content.post { findTagged(content, focusedKey)?.requestFocus() }
    }

    private fun transfer(item: PeerTransfer): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
        val pending = item.incoming && item.status.equals("pending", true)
        setBackgroundColor(if (pending) PENDING else PANEL)
        addView(label(if (pending) "Incoming file request" else item.status, 18, if (pending) GOLD else Color.WHITE, true))
        addView(label(if (item.incoming) "From ${item.senderName}" else "To ${item.recipientName}", 14, MUTED).spaced(4))
        item.files.forEach { addView(fileLine(it)) }
        val total = item.files.sumOf { it.size }
        if (!pending && total > 0) {
            addView(ProgressBar(this@FilesActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000; progress = ((item.bytes.coerceIn(0, total) * 1000) / total).toInt()
                progressTintList = android.content.res.ColorStateList.valueOf(GOLD)
            }.spaced(9))
            addView(label("${size(item.bytes)} of ${size(total)}", 12, MUTED))
        }
        if (pending) addView(LinearLayout(this@FilesActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END
            addView(button("Reject") { manager.decide(item.id, false) }.apply { tag = "reject:${item.id}" }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
            addView(button("Accept", true) { manager.decide(item.id, true) }.apply { tag = "accept:${item.id}" }, LinearLayout.LayoutParams(0, dp(48), 1f))
        }.spaced(12)) else if (item.status.lowercase() in listOf("pending", "queued", "accepted", "sending", "receiving", "connecting"))
            addView(button("Cancel") { manager.cancel(item.id) }.apply { tag = "cancel:${item.id}" }.spaced(10))
    }.spaced(bottom = 12)

    private fun openPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }
        try { picker.launch(intent) }
        catch (_: ActivityNotFoundException) { chooseReceivedFiles() }
        catch (e: Exception) { pickerError = e.message ?: "Could not open the file picker."; render(manager.state.value) }
    }
    private fun chooseReceivedFiles() {
        lifecycleScope.launch {
            try {
                val entries=withContext(Dispatchers.IO) {
                    val items=mutableListOf<Pair<String,Uri>>()
                    if(Build.VERSION.SDK_INT>=29) {
                        val collection=MediaStore.Downloads.EXTERNAL_CONTENT_URI
                        contentResolver.query(collection,arrayOf(MediaStore.Downloads._ID,MediaStore.Downloads.DISPLAY_NAME,MediaStore.Downloads.SIZE),
                            "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.IS_PENDING} = 0",arrayOf("${Environment.DIRECTORY_DOWNLOADS}/Sibi Store/"),"${MediaStore.Downloads.DATE_ADDED} DESC")?.use { cursor->
                            while(cursor.moveToNext()) items.add((cursor.getString(1)+" · "+size(cursor.getLong(2))) to ContentUris.withAppendedId(collection,cursor.getLong(0)))
                        }
                    } else {
                        File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),"Sibi Store").listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.forEach {
                            items.add((it.name+" · "+size(it.length())) to Uri.fromFile(it))
                        }
                    }
                    items
                }
                if(entries.isEmpty()) { pickerError="No received files yet. Receive files from your Mac or another device first.";render(manager.state.value);return@launch }
                val selected=linkedSetOf<Int>()
                val dialog=AlertDialog.Builder(this@FilesActivity).setTitle("Choose received files")
                    .setMultiChoiceItems(entries.map { it.first }.toTypedArray(),BooleanArray(entries.size)) { _,index,checked->if(checked)selected.add(index) else selected.remove(index) }
                    .setNegativeButton("Cancel",null).setPositiveButton("Choose") { _,_->
                        if(selected.isNotEmpty()) { pickerError=null;manager.chooseFiles(selected.map { entries[it].second }) }
                    }.create()
                dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(GOLD);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE)
            } catch(e:Exception) { pickerError=e.message ?: "Could not list received files";render(manager.state.value) }
        }
    }
    private fun fileLine(file: PeerFile) = label("${file.name}  ·  ${size(file.size)}", 14, Color.WHITE).spaced(8)
    private fun section(text: String) = label(text, 20, Color.WHITE, true).spaced(10, 8)
    private fun message(text: String) = label(text, 14, GOLD).apply { setBackgroundColor(PENDING); setPadding(dp(12), dp(10), dp(12), dp(10)) }.spaced(bottom = 10)
    private fun button(text: String, primary: Boolean = false, enabled: Boolean = true, action: () -> Unit) = Button(this).apply { this.text = text; isAllCaps = false; isEnabled = enabled; isFocusable = true; minHeight = dp(48); setTextColor(if (primary) INK else Color.WHITE); backgroundTintList = android.content.res.ColorStateList.valueOf(if (primary) GOLD else CARD); setOnClickListener { action() } }
    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(color); textDirection = View.TEXT_DIRECTION_LTR; if (bold) setTypeface(typeface, Typeface.BOLD) }
    private fun <T : View> T.spaced(top: Int = 0, bottom: Int = 0): T = apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) } }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun findTagged(root: View, key: String): View? {
        if (root.tag == key) return root
        if (root is android.view.ViewGroup) for (index in 0 until root.childCount) findTagged(root.getChildAt(index), key)?.let { return it }
        return null
    }
    companion object {
        private const val INK = 0xFF09090A.toInt(); private const val PANEL = 0xFF171719.toInt(); private const val CARD = 0xFF29292C.toInt(); private const val PENDING = 0xFF302810.toInt(); private const val MUTED = 0xFF9B9BA2.toInt(); private const val GOLD = 0xFFFFC107.toInt()
        private fun size(bytes: Long) = when { bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0); bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0); bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0); else -> "$bytes B" }
    }
}
