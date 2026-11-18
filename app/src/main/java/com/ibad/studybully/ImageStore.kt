package com.ibad.studybully

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Stores images privately on the phone (works offline) and rejects duplicates:
 * exact byte duplicates AND the same picture at a different size/compression.
 */
object ImageStore {
    enum class Result { ADDED, DUPLICATE, INVALID }

    data class Counts(var added: Int = 0, var duplicate: Int = 0, var failed: Int = 0) {
        override fun toString() = "$added added, $duplicate duplicates skipped, $failed failed"
    }

    private const val T = 32 // thumbnail side for near-duplicate check
    private val lock = Any()

    fun dir(ctx: Context): File = File(ctx.filesDir, "images").apply { mkdirs() }
    private fun thumbDir(ctx: Context): File = File(ctx.filesDir, "thumbs").apply { mkdirs() }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles()?.filter { it.isFile && it.name.endsWith(".img") }?.sortedBy { it.name }
            ?: emptyList()

    fun count(ctx: Context) = list(ctx).size

    fun clearAll(ctx: Context) {
        synchronized(lock) {
            dir(ctx).listFiles()?.forEach { it.delete() }
            thumbDir(ctx).listFiles()?.forEach { it.delete() }
            Prefs(ctx).clearCollectionState()
        }
    }

    fun addUris(ctx: Context, uris: List<Uri>): Counts {
        val c = Counts()
        for (u in uris) {
            try {
                val bytes = ctx.contentResolver.openInputStream(u)?.use { it.readBytes() }
                if (bytes == null) { c.failed++; continue }
                when (add(ctx, bytes)) {
                    Result.ADDED -> c.added++
                    Result.DUPLICATE -> c.duplicate++
                    Result.INVALID -> c.failed++
                }
            } catch (e: Exception) {
                c.failed++
            }
        }
        return c
    }

    fun add(ctx: Context, bytes: ByteArray): Result {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w < 150 || h < 150) return Result.INVALID
        val thumb = thumbnail(bytes, w, h) ?: return Result.INVALID
        val aspect = w.toFloat() / h

        synchronized(lock) {
            val name = sha256(bytes).take(24) + ".img"
            val file = File(dir(ctx), name)
            if (file.exists()) return Result.DUPLICATE

            // Near-duplicate scan against everything already stored.
            for (img in list(ctx)) {
                val tf = File(thumbDir(ctx), img.name + ".t")
                if (!tf.exists()) continue
                val data = tf.readBytes()
                if (data.size != T * T + 4) continue
                val otherAspect = java.nio.ByteBuffer.wrap(data, T * T, 4).float
                if (abs(otherAspect - aspect) / aspect > 0.03f) continue
                if (similar(thumb, data)) return Result.DUPLICATE
            }

            file.writeBytes(bytes)
            val buf = java.nio.ByteBuffer.allocate(T * T + 4)
            buf.put(thumb).putFloat(aspect)
            File(thumbDir(ctx), name + ".t").writeBytes(buf.array())
            return Result.ADDED
        }
    }

    /** Same picture re-encoded or resized: almost every cell matches closely. */
    private fun similar(a: ByteArray, b: ByteArray): Boolean {
        var total = 0L
        var bigDiffs = 0
        for (i in 0 until T * T) {
            val d = abs((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF))
            total += d
            if (d > 48) bigDiffs++
        }
        return total / (T * T) < 8 && bigDiffs <= (T * T) / 100
    }

    private fun thumbnail(bytes: ByteArray, w: Int, h: Int): ByteArray? {
        var sample = 1
        while (min(w, h) / (sample * 2) >= 128) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val small = Bitmap.createScaledBitmap(bmp, T, T, true)
        val out = ByteArray(T * T)
        for (y in 0 until T) for (x in 0 until T) {
            val p = small.getPixel(x, y)
            out[y * T + x] = ((Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000).toByte()
        }
        if (small !== bmp) small.recycle()
        bmp.recycle()
        return out
    }

    private fun sha256(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /** Decode sized for the current screen so huge originals don't exhaust memory. */
    fun decodeForScreen(file: File, longestSidePx: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (max(o.outWidth, o.outHeight) / (s * 2) >= longestSidePx) s *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })
    }
}
