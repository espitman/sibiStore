package com.sibi.store.core

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class PeerService:Service() {
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        channels(this)
        val stop=PendingIntent.getService(this,0,Intent(this,PeerService::class.java).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(this,"peer-sharing").setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Sibi Store file sharing").setContentText("Ready for requests · Files require your approval")
            .setContentIntent(open(this)).setOngoing(true).addAction(0,"Stop sharing",stop).build()
        if(Build.VERSION.SDK_INT>=29)startForeground(8745,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(8745,notification)
        if(intent?.action=="stop") { stopSelf();return START_NOT_STICKY }
        PeerManager.get(this).run()
        if(intent?.action=="decision") intent.getStringExtra("id")?.let { PeerManager.get(this).decide(it,intent.getBooleanExtra("accept",false)) }
        return START_NOT_STICKY
    }
    override fun onDestroy(){PeerManager.get(this).stop();super.onDestroy()}
    override fun onTimeout(startId:Int,fgsType:Int){stopSelf()}
    companion object {
        private fun channels(context:Context) {
            val manager=context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("peer-sharing","File sharing",NotificationManager.IMPORTANCE_LOW))
            manager.createNotificationChannel(NotificationChannel("peer-requests","Incoming file requests",NotificationManager.IMPORTANCE_HIGH))
        }
        private fun open(context:Context)=PendingIntent.getActivity(context,0,Intent(context,FilesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        private fun notificationId(id:String)=20000+(id.hashCode() and 0x3fffffff)
        private fun answer(context:Context,id:String,accept:Boolean)=PendingIntent.getBroadcast(context,if(accept)1 else 2,
            Intent(context,PeerDecisionReceiver::class.java).setData(android.net.Uri.parse("sibi-peer://request/$id")).putExtra("id",id).putExtra("accept",accept),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        internal fun request(context:Context,offer:PeerTransfer) {
            channels(context)
            val description="${offer.files.size} files · ${bytesLabel(offer.files.sumOf { it.size })}"
            val notification=NotificationCompat.Builder(context,"peer-requests").setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("${offer.senderName} wants to send files").setContentText(description)
                .setStyle(NotificationCompat.BigTextStyle().bigText(description+"\n"+offer.files.take(5).joinToString("\n"){it.name}))
                .setContentIntent(open(context)).setOnlyAlertOnce(true).setAutoCancel(false)
                .addAction(0,"Accept",answer(context,offer.id,true)).addAction(0,"Reject",answer(context,offer.id,false)).build()
            runCatching { context.getSystemService(NotificationManager::class.java).notify(notificationId(offer.id),notification) }
        }
        internal fun dismiss(context:Context,id:String){context.getSystemService(NotificationManager::class.java).cancel(notificationId(id))}
        internal fun complete(context:Context,offer:PeerTransfer){
            val notification=NotificationCompat.Builder(context,"peer-sharing").setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Files received").setContentText("${offer.files.size} files saved in Downloads/Sibi Store").setContentIntent(open(context)).setAutoCancel(true).build()
            runCatching { context.getSystemService(NotificationManager::class.java).notify(notificationId(offer.id)+1,notification) }
        }
    }
}
class PeerDecisionReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val id=intent.getStringExtra("id") ?: return
        androidx.core.content.ContextCompat.startForegroundService(context,Intent(context,PeerService::class.java)
            .setAction("decision").putExtra("id",id).putExtra("accept",intent.getBooleanExtra("accept",false)))
    }
}
