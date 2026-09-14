package nl.dejongduke.service.data

import android.content.Context
import java.time.LocalDate

/**
 * The small amount of state that outlives a visit: which checklist items are
 * searched for recently, pinned, and how the app should look.
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("duke", Context.MODE_PRIVATE)

    // --- The machine being worked on --------------------------------------
    // Which build of a machine was last looked at, so walking back into the
    // parts book lands on the right book instead of the first one.

    fun variant(machineId: String): String? = sp.getString("variant:$machineId", null)

    fun setVariant(machineId: String, code: String?) {
        if (code == null) sp.edit().remove("variant:$machineId").apply()
        else sp.edit().putString("variant:$machineId", code).apply()
    }

    // --- Pinned items -----------------------------------------------------
    // Keys look like "fault:Grinder blocked" or "part:5KAF119".

    fun pins(): List<String> =
        sp.getString("pins", "")!!.split("\n").filter { it.isNotBlank() }

    fun togglePin(key: String): Boolean {
        val current = pins().toMutableList()
        val added = if (current.remove(key)) false else { current.add(0, key); true }
        sp.edit().putString("pins", current.take(40).joinToString("\n")).apply()
        return added
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

    // --- Notes per machine ------------------------------------------------
    // Serial number, where it stands, what was replaced last time — the things
    // an engineer now writes on the back of a hand.

    fun note(machineId: String): String = sp.getString("note:$machineId", "")!!

    fun setNote(machineId: String, text: String) {
        if (text.isBlank()) sp.edit().remove("note:$machineId").apply()
        else sp.edit().putString("note:$machineId", text.trim()).apply()
    }

    fun notedMachines(): Set<String> =
        sp.all.keys.filter { it.startsWith("note:") }.map { it.removePrefix("note:") }.toSet()

    // --- Appearance -------------------------------------------------------

    var theme: String
        get() = sp.getString("theme", "System")!!
        set(value) = sp.edit().putString("theme", value).apply()

    /**
     * Which language leads on a fault card. A machine set to English shows
     * English on its display, so that is what you want to read first.
     */
    var messageLanguage: String
        get() = sp.getString("melding_taal", "nl")!!
        set(value) = sp.edit().putString("melding_taal", value).apply()

    /** Open a single scan result straight away instead of listing it. */
    var scanDirect: Boolean
        get() = sp.getBoolean("scan_direct", false)
        set(value) = sp.edit().putBoolean("scan_direct", value).apply()

    // --- Last used machine ------------------------------------------------

    var machine: String?
        get() = sp.getString("machine", null)
        set(value) = sp.edit().putString("machine", value).apply()
}
