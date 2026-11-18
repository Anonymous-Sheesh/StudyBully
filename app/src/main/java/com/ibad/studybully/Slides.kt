package com.ibad.studybully

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/** Builds the full-screen "slides" (Pinterest image or text roast) used by both modes. */
class Slides(private val a: Activity) {
    class Slide(val view: View, val fg: ImageView?, val bmp: Bitmap?)

    private val red = Color.parseColor("#D7261E")
    private val dp get() = a.resources.displayMetrics.density

    fun build(item: Deck.Item): Slide = when (item) {
        is Deck.Item.Image -> image(item) ?: roast(Roasts.LINES.random())
        is Deck.Item.Roast -> roast(item.text)
    }

    private fun screenAspect(): Float {
        val b = a.windowManager.currentWindowMetrics.bounds
        return b.width().toFloat() / max(1, b.height())
    }

    /** Fill edge-to-edge when cropping loses almost nothing; otherwise never cut off the text. */
    fun scaleFor(bmp: Bitmap): ImageView.ScaleType {
        val img = bmp.width.toFloat() / bmp.height
        val scr = screenAspect()
        return if (abs(img - scr) / scr < 0.12f) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
    }

    private fun image(item: Deck.Item.Image): Slide? {
        val b = a.windowManager.currentWindowMetrics.bounds
        val bmp = ImageStore.decodeForScreen(item.file, max(b.width(), b.height())) ?: return null
        val frame = FrameLayout(a).apply { setBackgroundColor(Color.BLACK) }
        // Blurred, darkened copy fills the edges (matters on the Fold's square inner screen).
        val bg = ImageView(a).apply {
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setRenderEffect(RenderEffect.createBlurEffect(90f, 90f, Shader.TileMode.CLAMP))
            setColorFilter(Color.argb(170, 0, 0, 0), PorterDuff.Mode.SRC_ATOP)
        }
        val fg = ImageView(a).apply {
            setImageBitmap(bmp)
            scaleType = scaleFor(bmp)
        }
        frame.addView(bg, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        frame.addView(fg, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        return Slide(frame, fg, bmp)
    }

    private fun roast(text: String): Slide {
        val style = Random.nextInt(3)
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding((28 * dp).toInt(), (96 * dp).toInt(), (28 * dp).toInt(), (90 * dp).toInt())
        }
        val label = TextView(a).apply {
            this.text = listOf("▲ WARNING", "REALITY CHECK", "NOTICE OF FAILURE")[style]
            setTextColor(red)
            typeface = Typeface.create("monospace", Typeface.BOLD)
            textSize = 14f
            letterSpacing = 0.3f
        }
        val rule = View(a).apply { setBackgroundColor(if (style == 1) Color.WHITE else red) }
        val body = TextView(a).apply {
            this.text = text.uppercase()
            setTextColor(if (style == 1) red else Color.WHITE)
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setLineSpacing(0f, 0.92f)
            setAutoSizeTextTypeUniformWithConfiguration(28, 140, 2, TypedValue.COMPLEX_UNIT_SP)
        }
        box.addView(label, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        box.addView(rule, LinearLayout.LayoutParams(MATCH_PARENT, (5 * dp).toInt()).apply {
            topMargin = (12 * dp).toInt(); bottomMargin = (20 * dp).toInt()
        })
        box.addView(body, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        return Slide(box, null, null)
    }
}
