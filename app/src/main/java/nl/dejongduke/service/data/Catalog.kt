package nl.dejongduke.service.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.text.Normalizer

/**
 * Everything the app knows, read once from the bundled assets.
 *
 * The catalog is deliberately plain in-memory data: an engineer standing in a
 * basement has no connection, so there is nothing to fetch and nothing to cache.
 */
class Catalog(
    val machines: List<Machine>,
    val faults: List<Fault>,
    val procedures: List<Procedure>,
    val schemas: List<Schema>,
    val parts: List<Part>,
    val specs: List<SpecGroep>,
    val components: List<Component> = emptyList(),
    val menu: List<MenuItem> = emptyList(),
    /** "machine|section" -> the exploded drawing that goes with it. */
    val drawings: Map<String, String> = emptyMap(),
    /** drawing name -> the balloon numbers found on it. */
    val hotspots: Map<String, List<Hotspot>> = emptyMap(),
) {
    /** Faults collapsed by screen message; the list screens show these. */
    val faultGroups: List<FaultGroup> = faults
        .groupBy { it.melding }
        .map { (melding, varianten) -> FaultGroup(melding, varianten) }
        .sortedBy { it.melding.lowercase() }

    private val groupByMessage = faultGroups.associateBy { it.melding }

    private val procById = procedures.associateBy { it.id }
    private val machineById = machines.associateBy { it.id }

    /** Search keys are precomputed once; every keystroke scans them. */
    private val faultKeys = faults.map { normalize(it.melding + " " + it.nl + " " + it.cat + " " + it.oorzaak) }
    private val procKeys = procedures.map { p ->
        normalize(p.titel + " " + p.doel + " " + p.stappen.joinToString(" ") { it.tekst })
    }
    private val partNumberKeys = parts.map { normalize(it.nummer) }
    private val partTextKeys = parts.map { normalize(it.omschrijving + " " + it.sectie) }
    private val machineKeys = machines.map { normalize(it.naam + " " + it.serie + " " + it.typecode + " " + it.brewer) }
    // Title and body are scored apart: a hit in the heading of a section is
    // what the engineer was looking for, a hit halfway its description usually
    // is not.
    private val componentTitles = components.map { normalize(it.titel) }
    private val componentKeys = components.map { normalize(it.tekst) }
    private val menuTitles = menu.map { normalize(it.titel + " " + it.pad) }
    private val menuKeys = menu.map { normalize(it.tekst) }

    fun procedure(id: String): Procedure? = procById[id]

    fun faultGroup(melding: String): FaultGroup? = groupByMessage[melding]

    fun component(nr: String): Component? = components.firstOrNull { it.nr == nr }

    fun menuItem(nr: String): MenuItem? = menu.firstOrNull { it.nr == nr }

    fun drawing(machine: String, sectie: String): String? = drawings["$machine|$sectie"]

    /** Balloons on the drawing for this section, keyed by position. */
    fun balloons(machine: String, sectie: String): List<Hotspot> {
        val path = drawing(machine, sectie) ?: return emptyList()
        return hotspots[path.substringAfterLast('/').removeSuffix(".webp")].orEmpty()
    }

    fun machine(id: String): Machine? = machineById[id]

    /** Human-readable machine names for a list of ids, e.g. "Virtu, Lua". */
    fun machineNames(ids: List<String>): String =
        ids.mapNotNull { machineById[it]?.naam }.joinToString(", ").ifEmpty { ids.joinToString(", ") }

    /**
     * Parts for one machine, ranked. The parts screen used to normalize every
     * description on every keystroke; this reuses the keys built at load time.
     */
    fun searchParts(machine: String?, raw: String, limit: Int = 120): List<Part> {
        val q = normalize(raw)
        if (q.length < 2) return emptyList()
        return parts.indices
            .mapNotNull { i ->
                if (machine != null && parts[i].machine != machine) return@mapNotNull null
                val byNumber = score(partNumberKeys[i], q)?.plus(100)
                val hit = byNumber ?: score(partTextKeys[i], q)
                hit?.let { it to parts[i] }
            }
            .sortedByDescending { it.first }
            .take(limit)
            .map { it.second }
    }

    fun search(raw: String, limit: Int = 60): SearchResult {
        val q = normalize(raw)
        if (q.length < 2) return SearchResult()

        val hitFaults = faults.indices
            .mapNotNull { i -> score(faultKeys[i], q)?.let { it to faults[i] } }
            .sortedByDescending { it.first }
            .mapNotNull { groupByMessage[it.second.melding] }
            .distinct()
            .take(limit)

        val hitProcs = procedures.indices
            .mapNotNull { i -> score(procKeys[i], q)?.let { it to procedures[i] } }
            .sortedByDescending { it.first }
            .take(limit).map { it.second }

        // A part number is what an engineer types most often, so number matches
        // outrank description matches instead of competing with them.
        val hitParts = parts.indices
            .mapNotNull { i ->
                val byNumber = score(partNumberKeys[i], q)?.plus(100)
                val byText = byNumber ?: score(partTextKeys[i], q)
                byText?.let { it to parts[i] }
            }
            .sortedByDescending { it.first }
            .take(limit).map { it.second }

        val hitComponents = components.indices
            .mapNotNull { i -> best(componentTitles[i], componentKeys[i], q)?.let { it to components[i] } }
            .sortedByDescending { it.first }
            .take(limit).map { it.second }

        val hitMenu = menu.indices
            .mapNotNull { i -> best(menuTitles[i], menuKeys[i], q)?.let { it to menu[i] } }
            .sortedByDescending { it.first }
            .take(limit).map { it.second }

        val hitMachines = machines.indices
            .mapNotNull { i -> score(machineKeys[i], q)?.let { it to machines[i] } }
            .sortedByDescending { it.first }
            .map { it.second }

        return SearchResult(hitFaults, hitProcs, hitParts, hitMachines, hitComponents, hitMenu)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(context: Context): Catalog {
            fun <T> read(name: String, parse: (String) -> T): T =
                parse(context.assets.open(name).bufferedReader().use { it.readText() })

            return Catalog(
                machines = read("machines.json") { json.decodeFromString(it) },
                faults = read("faults.json") { json.decodeFromString(it) },
                procedures = read("procedures.json") { json.decodeFromString(it) },
                schemas = read("maintenance.json") { json.decodeFromString(it) },
                parts = read("parts.json") { json.decodeFromString(it) },
                specs = read("specs.json") { json.decodeFromString(it) },
                components = read("components.json") { json.decodeFromString(it) },
                menu = read("servicemenu.json") { json.decodeFromString(it) },
                drawings = read("drawings.json") { json.decodeFromString(it) },
                hotspots = read("hotspots.json") { json.decodeFromString(it) },
            )
        }

        /** Lowercase, strip accents, and drop the punctuation that part numbers carry. */
        fun normalize(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("[^a-z0-9 ]+"), " ")
                .replace(Regex(" +"), " ")
                .trim()

        /** Best of a title hit (weighted up) and a body hit. */
        fun best(title: String, body: String, query: String): Int? {
            val t = score(title, query)?.times(3)
            val b = score(body, query)
            return when {
                t != null && b != null -> maxOf(t, b)
                else -> t ?: b
            }
        }

        /**
         * Ranks a haystack against an already normalized query: whole-string match
         * beats start-of-word, which beats a match anywhere.
         */
        fun score(haystack: String, query: String): Int? {
            if (haystack.isEmpty()) return null
            val terms = query.split(' ').filter { it.isNotEmpty() }
            if (terms.isEmpty()) return null
            var total = 0
            for (term in terms) {
                val at = haystack.indexOf(term)
                if (at < 0) return null
                total += when {
                    haystack == term -> 60
                    at == 0 -> 40
                    haystack[at - 1] == ' ' -> 25
                    else -> 10
                }
            }
            // Shorter haystacks matched the query more completely, but the
            // penalty has to stay gentle: a component description runs to
            // thousands of characters, and dividing by its full length rounded
            // every one of those hits down to zero.
            return total * 1000 / (40 + minOf(haystack.length, 1200))
        }
    }
}

data class SearchResult(
    val faults: List<FaultGroup> = emptyList(),
    val procedures: List<Procedure> = emptyList(),
    val parts: List<Part> = emptyList(),
    val machines: List<Machine> = emptyList(),
    val components: List<Component> = emptyList(),
    val menu: List<MenuItem> = emptyList(),
    /** "machine|section" -> the exploded drawing that goes with it. */
    val drawings: Map<String, String> = emptyMap(),
    /** drawing name -> the balloon numbers found on it. */
    val hotspots: Map<String, List<Hotspot>> = emptyMap(),
) {
    val empty: Boolean
        get() = faults.isEmpty() && procedures.isEmpty() && parts.isEmpty() &&
            machines.isEmpty() && components.isEmpty() && menu.isEmpty()
    val total: Int
        get() = faults.size + procedures.size + parts.size + machines.size +
            components.size + menu.size
}
