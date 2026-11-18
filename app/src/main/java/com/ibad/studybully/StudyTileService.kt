package com.ibad.studybully

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** The STUDY MODE toggle in the Samsung Quick Settings panel. */
class StudyTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        val on = !Prefs(this).active
        val ok = StudyMode.setActive(this, on)
        refresh()
        if (!ok) {
            // Android refused a background start: hop through an invisible activity.
            val i = Intent(this, ToggleActivity::class.java)
                .setAction(ToggleActivity.ACTION_SYNC)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 3, i, PendingIntent.FLAG_IMMUTABLE))
            } else {
                startActivityAndCollapse(i)
            }
        }
    }

    private fun refresh() {
        val t = qsTile ?: return
        val on = Prefs(this).active
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Study Mode"
        t.subtitle = if (on) "Bully ON" else "Off"
        t.updateTile()
    }
}
