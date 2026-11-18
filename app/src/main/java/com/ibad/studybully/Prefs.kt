package com.ibad.studybully

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

class Prefs(ctx: Context) {
    private val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("bully", Context.MODE_PRIVATE)

    var active: Boolean
        get() = sp.getBoolean("active", false)
        set(v) { sp.edit().putBoolean("active", v).apply() }

    /** When true, Do Not Disturb (e.g. driven by a Samsung Mode) switches Study Mode on/off. */
    var followDnd: Boolean
        get() = sp.getBoolean("followDnd", false)
        set(v) { sp.edit().putBoolean("followDnd", v).apply() }

    /** Mix built-in text roast cards into the rotation alongside the images. */
    var mixRoasts: Boolean
        get() = sp.getBoolean("mixRoasts", false)
        set(v) { sp.edit().putBoolean("mixRoasts", v).apply() }

    /** Seconds the screen refuses to be dismissed. */
    var stareSeconds: Int
        get() = sp.getInt("stare", 2)
        set(v) { sp.edit().putInt("stare", v).apply() }

    /** Seconds each slide stays up in Wall Mode. */
    var wallSeconds: Int
        get() = sp.getInt("wallSeconds", 30)
        set(v) { sp.edit().putInt("wallSeconds", v).apply() }

    var boardUrl: String
        get() = sp.getString("board", DEFAULT_BOARD) ?: DEFAULT_BOARD
        set(v) { sp.edit().putString("board", v).apply() }

    /** Items already shown in the current cycle (no repeats until all are seen). */
    var seen: Set<String>
        get() = sp.getStringSet("seen", emptySet())?.toSet() ?: emptySet()
        set(v) { sp.edit().putStringSet("seen", HashSet(v)).commit() }

    var lastItem: String?
        get() = sp.getString("lastItem", null)
        set(v) { sp.edit().putString("lastItem", v).commit() }

    /** Pinterest image keys already imported, so re-imports skip them. */
    val pinKeys: Set<String>
        get() = sp.getStringSet("pinKeys", emptySet())?.toSet() ?: emptySet()

    fun addPinKey(k: String) {
        sp.edit().putStringSet("pinKeys", HashSet(pinKeys + k)).commit()
    }

    fun clearCollectionState() {
        sp.edit().remove("pinKeys").remove("seen").remove("lastItem").commit()
    }

    fun bumpUnlockCount(): Int {
        val today = LocalDate.now().toString()
        val n = if (sp.getString("countDay", "") == today) sp.getInt("count", 0) + 1 else 1
        sp.edit().putString("countDay", today).putInt("count", n).apply()
        return n
    }

    companion object {
        const val DEFAULT_BOARD = "https://au.pinterest.com/ibad8090/studying-motivation/"
    }
}
