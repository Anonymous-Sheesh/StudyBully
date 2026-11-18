package com.ibad.studybully

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log

object StudyMode {
    private const val TAG = "StudyBully"
    @Volatile private var lastTrigger = 0L

    fun setActive(ctx: Context, on: Boolean): Boolean {
        Prefs(ctx).active = on
        return sync(ctx)
    }

    /** Starts/stops the background listener to match settings. Returns false if Android refused. */
    fun sync(ctx: Context): Boolean {
        val p = Prefs(ctx)
        val app = ctx.applicationContext
        val i = Intent(app, StudyService::class.java)
        var ok = true
        if (p.active || p.followDnd) {
            try {
                app.startForegroundService(i)
            } catch (e: Exception) {
                Log.w(TAG, "Could not start service from background", e)
                ok = false
            }
        } else {
            app.stopService(i)
        }
        refreshTile(app)
        return ok
    }

    fun refreshTile(ctx: Context) {
        try {
            TileService.requestListeningState(ctx, ComponentName(ctx, StudyTileService::class.java))
        } catch (_: Exception) { }
    }

    fun dndOn(ctx: Context): Boolean {
        val f = ctx.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        return f != NotificationManager.INTERRUPTION_FILTER_ALL &&
            f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    /** Called on screen-on / unlock. Never shows over the lock screen, during calls, or twice in a row. */
    fun onWake(ctx: Context, fromUnlock: Boolean) {
        if (!Prefs(ctx).active) return
        val km = ctx.getSystemService(KeyguardManager::class.java)
        if (!fromUnlock && km.isKeyguardLocked) return // wait for the real unlock
        val mode = ctx.getSystemService(AudioManager::class.java).mode
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastTrigger < 3000) return
        lastTrigger = now
        launch(ctx, preview = false)
    }

    fun launch(ctx: Context, preview: Boolean) {
        val i = Intent(ctx, ConfrontActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(ConfrontActivity.EXTRA_PREVIEW, preview)
        if (preview || Settings.canDrawOverlays(ctx)) {
            try {
                ctx.startActivity(i)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Direct launch blocked", e)
            }
        }
        Notifs.fullScreen(ctx, i) // weaker fallback when "Appear on top" isn't granted
    }
}
