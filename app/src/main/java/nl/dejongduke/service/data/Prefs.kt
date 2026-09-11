package nl.dejongduke.service.data

import android.content.Context
import java.time.LocalDate

/**
 * The small amount of state that outlives a visit: which checklist items are
 * ticked today, what was searched for recently, and how it should look.
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("duke", Context.MODE_PRIVATE)

    // --- Checklists -------------------------------------------------------
    // Ticks are stored per schedule per day, so tomorrow starts clean without
    // anyone having to reset anything.

    private fun checkKey(schema: String, day: LocalDate) = "check:$schema:$day"

    fun ticked(schema: String, day: LocalDate): Set<Int> =
        sp.getStringSet(checkKey(schema, day), emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()

    fun setTicked(schema: String, day: LocalDate, items: Set<Int>) {
        sp.edit().putStringSet(checkKey(schema, day), items.map { it.toString() }.toSet()).apply()
    }

    /** Keeps a fortnight of history so the file cannot grow without bound. */
    fun pruneChecklists(today: LocalDate) {
        val keep = (0..13).map { today.minusDays(it.toLong()).toString() }.toSet()
        val stale = sp.all.keys.filter { key ->
            key.startsWith("check:") && key.substringAfterLast(':') !in keep
        }
        if (stale.isEmpty()) return
        sp.edit().apply { stale.forEach { remove(it) } }.apply()
    }

    // --- Recent searches --------------------------------------------------

    fun recent(): List<String> =
        sp.getString("recent", "")!!.split("\n").filter { it.isNotBlank() }

    fun addRecent(term: String) {
        val trimmed = term.trim()
        if (trimmed.length < 2) return
        val list = (listOf(trimmed) + recent().filter { !it.equals(trimmed, ignoreCase = true) }).take(8)
        sp.edit().putString("recent", list.joinToString("\n")).apply()
    }

    fun clearRecent() = sp.edit().remove("recent").apply()

    // --- Appearance -------------------------------------------------------

    var theme: String
        get() = sp.getString("theme", "System")!!
        set(value) = sp.edit().putString("theme", value).apply()

    // --- Last used machine ------------------------------------------------

    var machine: String?
        get() = sp.getString("machine", null)
        set(value) = sp.edit().putString("machine", value).apply()
}
