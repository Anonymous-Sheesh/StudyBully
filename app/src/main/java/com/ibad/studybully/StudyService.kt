package com.ibad.studybully

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Android only delivers unlock (USER_PRESENT) and screen-on events to a running
 * listener, so this lightweight foreground service holds that listener while
 * Study Mode is on (or while it is waiting for Do Not Disturb).
 */
class StudyService : Service() {
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> StudyMode.onWake(c, fromUnlock = true)
                Intent.ACTION_SCREEN_ON -> StudyMode.onWake(c, fromUnlock = false)
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> onDndChanged()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, f)
        registered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        val p = Prefs(this)
        if (intent?.action == ACTION_OFF) {
            p.active = false
            StudyMode.refreshTile(this)
        }
        if (!p.active && !p.followDnd) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        Notifs.update(this)
        return START_STICKY
    }

    private fun goForeground() {
        val n = Notifs.status(this, Prefs(this).active)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifs.ID_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifs.ID_STATUS, n)
        }
    }

    private fun onDndChanged() {
        val p = Prefs(this)
        if (!p.followDnd) return
        val on = StudyMode.dndOn(this)
        if (on != p.active) {
            p.active = on
            Notifs.update(this)
            StudyMode.refreshTile(this)
        }
    }

    override fun onDestroy() {
        if (registered) {
            try { unregisterReceiver(receiver) } catch (_: Exception) { }
            registered = false
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_OFF = "com.ibad.studybully.SERVICE_OFF"
    }
}
