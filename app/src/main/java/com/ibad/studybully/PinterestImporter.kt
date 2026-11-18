package com.ibad.studybully

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Imports a PUBLIC Pinterest board using Pinterest's own public feeds
 * (the board RSS feed and the board embed-widget feed). No login, no scraping
 * behind sign-in. These feeds expose only the board's most recent pins
 * (typically 25–50), so larger boards are topped up via Share → Study Bully.
 */
object PinterestImporter {
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    /** Group 1 = size-independent image path, e.g. ab/cd/ef/abcdef123.jpg */
    private val PIN_IMG = Regex(
        """https?://i\.pinimg\.com/(?:originals|\d+x(?:\d+)?(?:_[a-z]+)?)/([0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]+\.(?:jpg|jpeg|png|gif|webp))""",
        RegexOption.IGNORE_CASE
    )
    private val OG_IMAGE_A = Regex("""og:image["'][^>]*?content=["']([^"']+)""", RegexOption.IGNORE_CASE)
    private val OG_IMAGE_B = Regex("""content=["']([^"']+)["'][^>]*?og:image["']""", RegexOption.IGNORE_CASE)
    private val ANY_URL = Regex("""https?://\S+""")

    fun parseBoard(url: String): Pair<String, String>? {
        val u = Uri.parse(url.trim())
        val segs = u.pathSegments.filter { it.isNotBlank() }
        if (u.host?.contains("pinterest") != true || segs.size < 2) return null
        return segs[0] to segs[1]
    }

    fun importBoard(ctx: Context, url: String, progress: (String) -> Unit): String {
        val (user, board) = parseBoard(url)
            ?: return "That doesn't look like a board link (expected pinterest.com/<user>/<board>/)."
        val notes = mutableListOf<String>()
        val keys = LinkedHashSet<String>()

        progress("Reading board feed…")
        try {
            val json = JSONObject(getText("https://widgets.pinterest.com/v3/pidgets/boards/$user/$board/pins/"))
            val pins = json.getJSONObject("data").getJSONArray("pins")
            for (i in 0 until pins.length()) {
                val images = pins.getJSONObject(i).optJSONObject("images") ?: continue
                for (k in images.keys()) {
                    val u = images.optJSONObject(k)?.optString("url") ?: continue
                    PIN_IMG.find(u)?.let { keys.add(it.groupValues[1]) }
                }
            }
        } catch (e: Exception) {
            notes.add("Widget feed unavailable (${e.message}).")
        }

        progress("Reading RSS feed…")
        try {
            val rss = getText("https://www.pinterest.com/$user/$board.rss")
            PIN_IMG.findAll(rss).forEach { keys.add(it.groupValues[1]) }
        } catch (e: Exception) {
            notes.add("RSS feed unavailable (${e.message}).")
        }

        if (keys.isEmpty()) {
            return "Found no pins. ${notes.joinToString(" ")} If the board is secret, make it public " +
                "or add pins with Share → Study Bully, or import from your gallery."
        }

        progress("Found ${keys.size} unique pins. Downloading full-quality originals…")
        val c = ImageStore.Counts()
        keys.forEachIndexed { i, key ->
            when (downloadKey(ctx, key)) {
                ImageStore.Result.ADDED -> c.added++
                ImageStore.Result.DUPLICATE -> c.duplicate++
                else -> c.failed++
            }
            if (i % 5 == 4) progress("${i + 1}/${keys.size}… ($c)")
        }
        return "Done: $c.\nPinterest's public feeds only expose the board's newest ~25–50 pins. " +
            "If your board is bigger, add the rest with Share → Study Bully from the Pinterest app."
    }

    /** Tries original quality first, then the largest standard sizes. */
    fun downloadKey(ctx: Context, key: String): ImageStore.Result? {
        val p = Prefs(ctx)
        if (key in p.pinKeys) return ImageStore.Result.DUPLICATE
        for (size in listOf("originals", "736x", "564x")) {
            try {
                val bytes = getBytes("https://i.pinimg.com/$size/$key")
                val r = ImageStore.add(ctx, bytes)
                if (r != ImageStore.Result.INVALID) {
                    p.addPinKey(key)
                    return r
                }
            } catch (_: Exception) { /* try next size */ }
        }
        return null
    }

    /** For a shared pin link (pin.it/… or pinterest.com/pin/…): find its main image. */
    fun importSharedText(ctx: Context, text: String): String {
        val links = ANY_URL.findAll(text).map { it.value.trimEnd('.', ',', ')') }.toList()
        if (links.isEmpty()) return "No link found in what was shared."
        val c = ImageStore.Counts()
        for (link in links) {
            try {
                val direct = PIN_IMG.find(link)?.groupValues?.get(1)
                val key = direct ?: run {
                    val html = getText(link)
                    val og = OG_IMAGE_A.find(html)?.groupValues?.get(1) ?: OG_IMAGE_B.find(html)?.groupValues?.get(1)
                    og?.let { PIN_IMG.find(it)?.groupValues?.get(1) }
                }
                if (key == null) { c.failed++; continue }
                when (downloadKey(ctx, key)) {
                    ImageStore.Result.ADDED -> c.added++
                    ImageStore.Result.DUPLICATE -> c.duplicate++
                    else -> c.failed++
                }
            } catch (_: Exception) {
                c.failed++
            }
        }
        return "Study Bully: $c"
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept-Language", "en")
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("HTTP $code")
        }
        return conn
    }

    private fun getText(url: String): String {
        val c = open(url)
        return try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
    }

    private fun getBytes(url: String): ByteArray {
        val c = open(url)
        try {
            c.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > 30 * 1024 * 1024) throw IllegalStateException("too large")
                    out.write(buf, 0, n)
                }
                return out.toByteArray()
            }
        } finally {
            c.disconnect()
        }
    }
}
