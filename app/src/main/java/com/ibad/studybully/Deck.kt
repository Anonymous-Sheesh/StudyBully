package com.ibad.studybully

import android.content.Context
import java.io.File

/** Shuffle-bag: random order, no repeats until every item in the collection has been shown. */
object Deck {
    sealed class Item {
        data class Image(val file: File) : Item()
        data class Roast(val text: String) : Item()
    }

    private fun ids(ctx: Context): List<String> {
        val imgs = ImageStore.list(ctx).map { "img:" + it.name }
        val roasts = if (Prefs(ctx).mixRoasts || imgs.isEmpty())
            Roasts.LINES.indices.map { "txt:$it" } else emptyList()
        return imgs + roasts
    }

    @Synchronized
    fun next(ctx: Context): Item {
        val p = Prefs(ctx)
        val all = ids(ctx)
        var seen = p.seen.intersect(all.toSet())
        var remaining = all.filter { it !in seen }
        if (remaining.isEmpty()) {
            seen = emptySet()
            remaining = all
        }
        val last = p.lastItem
        val pool = if (remaining.size > 1) remaining.filter { it != last } else remaining
        val id = pool.random()
        p.seen = seen + id
        p.lastItem = id
        return resolve(ctx, id)
    }

    private fun resolve(ctx: Context, id: String): Item =
        if (id.startsWith("img:")) Item.Image(File(ImageStore.dir(ctx), id.removePrefix("img:")))
        else {
            val i = id.removePrefix("txt:").toIntOrNull() ?: 0
            Item.Roast(Roasts.LINES.getOrElse(i) { Roasts.LINES[0] })
        }
}
