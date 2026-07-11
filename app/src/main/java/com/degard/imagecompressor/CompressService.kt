package com.degard.imagecompressor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat

class CompressService : Service() {

    private val channelId = "compress_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val srcStr = intent?.getStringExtra("src") ?: return stopSelf().let { START_NOT_STICKY }
        val tmpStr = intent.getStringExtra("tmp") ?: return stopSelf().let { START_NOT_STICKY }
        val finalStr = intent.getStringExtra("final") ?: return stopSelf().let { START_NOT_STICKY }
        val quality = intent.getIntExtra("quality", 65)
        val maxRes = intent.getIntExtra("maxres", 1280)

        val srcUri = Uri.parse(srcStr)
        val tmpUri = Uri.parse(tmpStr)
        val finalUri = Uri.parse(finalStr)

        startForeground(1, buildNotification("Starting…"))

        Thread {
            val result = ImageCompressor.compress(
                context = this,
                sourceUri = srcUri,
                tmpUri = tmpUri,
                finalUri = finalUri,
                quality = quality,
                maxRes = maxRes
            ) { status ->
                updateNotification(status)
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(1, buildNotification("Done! ${result.compressed} compressed, ${result.errors} errors"))

            // Send result broadcast
            val broadcast = Intent("com.degard.imagecompressor.DONE").apply {
                putExtra("compressed", result.compressed)
                putExtra("errors", result.errors)
                putExtra("skipped", result.skipped)
                setPackage(packageName)
            }
            sendBroadcast(broadcast)

            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }.start()

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compress)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
