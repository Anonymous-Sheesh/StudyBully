package com.ibad.studybully

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

/**
 * WALL MODE: for when the Fold is open on the desk. The screen stays on and keeps
 * cycling your collection full-screen with a running session timer.
 * Tap = next one now. Long-press or Back = exit.
 */
class WallActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var timer: TextView
    private lateinit var hint: TextView
    private var current: View? = null
    private var currentFg: ImageView? = null
    private var currentBmp: Bitmap? = null
    private var startedAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val slides by lazy { Slides(this) }
    private val dp get() = resources.displayMetrics.density

    private val advance = object : Runnable {
        override fun run() {
            showNext()
            handler.postDelayed(this, Prefs(this@WallActivity).wallSeconds * 1000L)
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            val s = (SystemClock.elapsedRealtime() - startedAt) / 1000
            timer.text = "SESSION %02d:%02d:%02d — STILL NOT ENOUGH".format(s / 3600, (s / 60) % 60, s % 60)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setDecorFitsSystemWindows(false)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        startedAt = savedInstanceState?.getLong("startedAt") ?: SystemClock.elapsedRealtime()

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        timer = TextView(this).apply {
            setTextColor(Color.parseColor("#D7261E"))
            typeface = Typeface.create("monospace", Typeface.BOLD)
            textSize = 12f
            letterSpacing = 0.2f
        }
        hint = TextView(this).apply {
            text = "TAP = NEXT   ·   HOLD = EXIT"
            setTextColor(Color.parseColor("#9A9A9A"))
            typeface = Typeface.MONOSPACE
            textSize = 11f
            letterSpacing = 0.15f
        }
        root.addView(timer, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            leftMargin = (22 * dp).toInt(); topMargin = (40 * dp).toInt()
        })
        root.addView(hint, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = (28 * dp).toInt()
        })
        setContentView(root)
        hint.animate().alpha(0f).setStartDelay(4000).setDuration(800).start()

        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                handler.removeCallbacks(advance)
                advance.run()
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                Toast.makeText(applicationContext, "Leaving already? Noted.", Toast.LENGTH_SHORT).show()
                finish()
            }
        })
        root.setOnTouchListener { _, ev -> gestures.onTouchEvent(ev) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("startedAt", startedAt)
    }

    override fun onResume() {
        super.onResume()
        hideBars()
        handler.removeCallbacks(advance)
        handler.removeCallbacks(tick)
        advance.run()
        tick.run()
    }

    override fun onPause() {
        handler.removeCallbacks(advance)
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun hideBars() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showNext() {
        val slide = slides.build(Deck.next(this))
        val view = slide.view
        currentFg = slide.fg
        currentBmp = slide.bmp
        val old = current
        for (i in root.childCount - 1 downTo 0) {
            val c = root.getChildAt(i)
            if (c !== timer && c !== hint && c !== old) root.removeView(c)
        }
        root.addView(view, root.indexOfChild(timer), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        current = view

        // Small random offset each time = OLED burn-in protection; slow drift keeps it alive.
        val shift = 6 * dp
        view.translationX = Random.nextFloat() * 2 * shift - shift
        view.translationY = Random.nextFloat() * 2 * shift - shift
        view.alpha = 0f
        view.scaleX = 1.0f
        view.scaleY = 1.0f
        view.animate().alpha(1f).setDuration(900).setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (old != null) root.removeView(old)
                view.animate().scaleX(1.04f).scaleY(1.04f)
                    .setDuration(Prefs(this).wallSeconds * 1000L)
                    .setInterpolator(LinearInterpolator()).start()
            }.start()
        old?.animate()?.cancel()
        old?.animate()?.alpha(0f)?.setDuration(700)?.start()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        root.post {
            val fg = currentFg
            val bmp = currentBmp
            if (fg != null && bmp != null) fg.scaleType = slides.scaleFor(bmp)
            hideBars()
        }
    }
}
