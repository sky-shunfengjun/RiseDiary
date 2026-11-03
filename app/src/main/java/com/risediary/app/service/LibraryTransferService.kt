package com.risediary.app.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.risediary.app.MainActivity
import com.risediary.app.R
import com.risediary.app.data.library.LibraryTransferManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*

@AndroidEntryPoint
class LibraryTransferService : Service() {
    @Inject lateinit var transfers: LibraryTransferManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var work: Job? = null
    private var progress: Job? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.library_transfer_channel), NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) { transfers.cancel(); if (work == null) stopSelf(); return START_NOT_STICKY }
        ServiceCompat.startForeground(this, ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (work?.isActive != true) {
            progress = scope.launch {
                transfers.state.collect {
                    if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                        getSystemService(NotificationManager::class.java).notify(ID, notification())
                }
            }
            work = scope.launch {
                try { transfers.run() } finally {
                    progress?.cancel(); progress = null
                    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        transfers.cancel(); progress?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() { transfers.cancel(); scope.cancel(); super.onDestroy() }
    private fun notification(): Notification {
        val cancel = PendingIntent.getService(this, 231, Intent(this, LibraryTransferService::class.java).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 232, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE)
        val state = transfers.state.value
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(getString(R.string.library_transfer_title))
            .setContentText(getString(R.string.library_transfer_count, state.completed, state.items.size))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setProgress(100, if ((state.totalBytes ?: 0) > 0) (state.bytes.toDouble() / state.totalBytes!! * 100).toInt().coerceIn(0, 100) else 0, state.totalBytes == null)
            .addAction(0, getString(R.string.action_cancel), cancel).build()
    }
    companion object {
        const val CANCEL = "com.risediary.library.CANCEL"
        private const val CHANNEL = "library_transfers"
        private const val ID = 230
    }
}
