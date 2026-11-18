package com.ibad.studybully

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.widget.Toast

/** Restores Study Mode after reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        StudyMode.sync(context)
    }
}

/** Invisible activity for launcher shortcuts (long-press icon) and the tile fallback. */
class ToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = Prefs(this)
        when (intent?.action) {
            ACTION_ON -> StudyMode.setActive(this, true)
            ACTION_OFF -> StudyMode.setActive(this, false)
            ACTION_TOGGLE -> StudyMode.setActive(this, !p.active)
            else -> StudyMode.sync(this)
        }
        Toast.makeText(applicationContext,
            if (p.active) "STUDY MODE ON. Good luck hiding." else "Study Mode off.",
            Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val ACTION_ON = "com.ibad.studybully.ON"
        const val ACTION_OFF = "com.ibad.studybully.OFF"
        const val ACTION_TOGGLE = "com.ibad.studybully.TOGGLE"
        const val ACTION_SYNC = "com.ibad.studybully.SYNC"
    }
}

/** Share a pin (link) or an image from Pinterest / Gallery → "Add to Study Bully". */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        val i = intent
        val uris = ArrayList<Uri>()
        if (i.action == Intent.ACTION_SEND_MULTIPLE) {
            streams(i)?.let { uris.addAll(it) }
        } else if (i.action == Intent.ACTION_SEND) {
            stream(i)?.let { uris.add(it) }
        }
        val text = i.getStringExtra(Intent.EXTRA_TEXT)
        Toast.makeText(app, "Adding to your collection…", Toast.LENGTH_SHORT).show()
        Thread {
            val msg = try {
                if (uris.isNotEmpty()) "Study Bully: " + ImageStore.addUris(app, uris)
                else if (!text.isNullOrBlank()) PinterestImporter.importSharedText(app, text)
                else "Nothing usable was shared."
            } catch (e: Exception) {
                "Study Bully: import failed (${e.message})"
            }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
                finish()
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun stream(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri

    @Suppress("DEPRECATION")
    private fun streams(i: Intent): List<Uri>? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.mapNotNull { it as? Uri }
}
