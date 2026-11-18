package com.ibad.studybully

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon

object Notifs {
    const val ID_STATUS = 1
    const val ID_SLAP = 2
    private const val CH_STATUS = "status"
    private const val CH_SLAP = "slap"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, "Study Mode status", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SLAP, "Unlock confrontation (fallback)", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun status(ctx: Context, active: Boolean): Notification {
        ensureChannels(ctx)
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(ctx, CH_STATUS)
            .setSmallIcon(R.drawable.ic_tile)
            .setColor(Color.parseColor("#D7261E"))
            .setOngoing(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        if (active) {
            val off = PendingIntent.getService(ctx, 1,
                Intent(ctx, StudyService::class.java).setAction(StudyService.ACTION_OFF),
                PendingIntent.FLAG_IMMUTABLE)
            b.setContentTitle("STUDY MODE ON")
                .setContentText("Every unlock gets confronted.")
                .addAction(Notification.Action.Builder(
                    Icon.createWithResource(ctx, R.drawable.ic_tile), "Turn off", off).build())
        } else {
            b.setContentTitle("Study Bully standing by")
                .setContentText("Study Mode switches on with Do Not Disturb.")
        }
        return b.build()
    }

    fun update(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java)
            .notify(ID_STATUS, status(ctx, Prefs(ctx).active))
    }

    fun fullScreen(ctx: Context, target: Intent) {
        ensureChannels(ctx)
        val pi = PendingIntent.getActivity(ctx, 2, target,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(ctx, CH_SLAP)
            .setSmallIcon(R.drawable.ic_tile)
            .setColor(Color.parseColor("#D7261E"))
            .setContentTitle("You unlocked your phone. Again.")
            .setContentText("Tap. Face it.")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setTimeoutAfter(20000)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(ID_SLAP, n)
        } catch (_: SecurityException) { }
    }
}
