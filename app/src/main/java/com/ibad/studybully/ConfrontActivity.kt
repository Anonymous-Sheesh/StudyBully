package com.ibad.studybully

import android.animation.ObjectAnimator
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs

/** The full-screen, edge-to-edge confrontation shown on every unlock. */
class ConfrontActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var footer: TextView
    private lateinit var hint: TextView
    private var current: View? = null
    private var currentFg: ImageView? = null
    private var currentBmp: Bitmap? = null
    private var unlockAt = 0L
    private var leaving = false
    private var downX = 0f
    private var downY = 0f
    private val handler = Handler(Looper.getMainLooper())
    private val slides by lazy { Slides(this) }
    private val red = Color.parseColor("#D7261E")
    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSystemService(NotificationManager::class.java).cancel(Notifs.ID_SLAP)
        window.setDecorFitsSystemWindows(false)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        footer = TextView(this).apply {
            setTextColor(red)
            typeface = Typeface.create("monospace", Typeface.BOLD)
            textSize = 12f
            letterSpacing = 0.2f
        }
        hint = TextView(this).apply {
            setTextColor(Color.parseColor("#9A9A9A"))
            typeface = Typeface.create("monospace", Typeface.NORMAL)
            textSize = 11f
            letterSpacing = 0.15f
            alpha = 0f
        }
        root.addView(footer, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            leftMargin = (22 * dp).toInt(); topMargin = (44 * dp).toInt()
        })
        root.addView(hint, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = (30 * dp).toInt()
        })
        setContentView(root)
        hideBars()
        showNext()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!leaving) showNext()
    }

    override fun onResume() {
        super.onResume()
        hideBars()
    }

    override fun onStop() {
        super.onStop()
        // Screen off or user left: start fresh next unlock.
        if (!isChangingConfigurations) finish()
    }

    private fun hideBars() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showNext() {
        val preview = intent?.getBooleanExtra(EXTRA_PREVIEW, false) ?: false
        val p = Prefs(this)
        footer.text = if (preview) "PREVIEW" else "UNLOCK #${p.bumpUnlockCount()} TODAY"

        val slide = slides.build(Deck.next(this))
        val view = slide.view
        currentFg = slide.fg
        currentBmp = slide.bmp

        val old = current
        root.addView(view, root.indexOfChild(footer), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        current = view
        view.alpha = 0f
        view.scaleX = 1.06f
        view.scaleY = 1.06f
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(480)
            .setInterpolator(DecelerateInterpolator()).withEndAction {
                if (old != null) root.removeView(old)
            }.start()
        old?.animate()?.alpha(0f)?.setDuration(300)?.start()

        val stare = p.stareSeconds
        unlockAt = SystemClock.elapsedRealtime() + stare * 1000L
        hint.animate().cancel()
        hint.alpha = 0f
        hint.text = "SWIPE TO GO BACK TO WASTING TIME"
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ hint.animate().alpha(0.6f).setDuration(400).start() }, stare * 1000L)

        if (!preview) buzz()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Folding/unfolding: re-pick crop vs fit for the new screen shape.
        root.post {
            val fg = currentFg
            val bmp = currentBmp
            if (fg != null && bmp != null) fg.scaleType = slides.scaleFor(bmp)
            hideBars()
        }
    }

    private fun canLeave() = SystemClock.elapsedRealtime() >= unlockAt

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (leaving) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY }
            MotionEvent.ACTION_MOVE -> if (canLeave()) {
                current?.translationY = (e.rawY - downY) * 0.5f
                current?.translationX = (e.rawX - downX) * 0.5f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (canLeave()) dismiss(e.rawX - downX, e.rawY - downY) else refuse()
            }
        }
        return true
    }

    @Deprecated("Back gesture")
    override fun onBackPressed() {
        if (canLeave()) dismiss(0f, 0f) else refuse()
    }

    private fun refuse() {
        hint.animate().cancel()
        hint.text = "NOT YET. LOOK AT IT."
        hint.alpha = 1f
        current?.let {
            it.translationX = 0f; it.translationY = 0f
            ObjectAnimator.ofFloat(it, "translationX", 0f, 28f, -28f, 18f, -18f, 8f, 0f)
                .setDuration(380).start()
        }
    }

    @Suppress("DEPRECATION")
    private fun dismiss(dx: Float, dy: Float) {
        val v = current ?: run { finish(); return }
        leaving = true
        handler.removeCallbacksAndMessages(null)
        val a = v.animate().alpha(0f).setDuration(260)
        val threshold = 40 * dp
        when {
            abs(dx) < threshold && abs(dy) < threshold -> a.scaleX(0.92f).scaleY(0.92f)
            abs(dx) > abs(dy) -> a.translationX(if (dx > 0) root.width.toFloat() else -root.width.toFloat())
            else -> a.translationY(if (dy > 0) root.height.toFloat() else -root.height.toFloat())
        }
        hint.animate().alpha(0f).setDuration(200).start()
        footer.animate().alpha(0f).setDuration(200).start()
        a.withEndAction {
            finish()
            overridePendingTransition(0, android.R.anim.fade_out)
        }.start()
    }

    private fun buzz() {
        try {
            val vib = getSystemService(VibratorManager::class.java).defaultVibrator
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 45, 70, 110), -1))
        } catch (_: Exception) { }
    }

    companion object {
        const val EXTRA_PREVIEW = "preview"
    }
}
