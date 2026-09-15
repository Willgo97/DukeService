package nl.dejongduke.service.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
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
    val parts: List<Part>,
    val specs: List<SpecGroup>,
    val components: List<Component> = emptyList(),
    val menu: List<MenuItem> = emptyList(),
    /** The manufacturer's maintenance sheets, step by step with pictures. */
    val cards: List<MaintenanceCard> = emptyList(),
    /** Front, back and inside views with their call-outs. */
    val views: List<MachineView> = emptyList(),
    /** "machine|build|section" -> the exploded drawing sheets of that section. */
    val drawings: Map<String, List<String>> = emptyMap(),
    /** The same key -> the name the parts book gives that drawing. */
    val drawingNames: Map<String, String> = emptyMap(),
    /** drawing name -> the balloon numbers found on it. */
    val hotspots: Map<String, List<Hotspot>> = emptyMap(),
) {
    /** Faults collapsed by screen message; the list screens show these. */
    val faultGroups: List<FaultGroup> = faults
        .groupBy { it.message }
        .map { (message, variants) -> FaultGroup(message, variants) }
        .sortedBy { it.message.lowercase() }

    private val groupByMessage = faultGroups.associateBy { it.message }

    /** How many part rows each machine has; counting 40.000 rows per frame is
     *  what a list of machines would otherwise do. */
    val partCount: Map<String, Int> = parts.groupingBy { it.machine }.eachCount()

    private val partCountByBuild: Map<Pair<String, String>, Int> =
        parts.groupingBy { it.machine to it.variant }.eachCount()

    /** Which rows belong to one book, so searching in it need not walk all 38.000. */
    private val partsByBuild: Map<Pair<String, String>, List<Int>> by lazy {
        parts.indices.groupBy { parts[it].machine to parts[it].variant }
    }

    /** Rows for a machine, or for one build of it. */
    fun partCount(machineId: String, variant: String? = null): Int =
        if (variant == null) partCount[machineId] ?: 0
        else partCountByBuild[machineId to variant] ?: 0

    /** Machines that have a parts book at all. */
    val machinesWithParts: List<Machine> = machines.filter { partCount.containsKey(it.id) }

    private val partByNumber: Map<String, Part> =
        parts.asReversed().associateBy { it.number }

    fun part(number: String): Part? = partByNumber[number]

    /** "CoEx Medium (CEC)" — the code alone means nothing until you know it. */
    fun variantLabel(machineId: String?, code: String): String {
        val build = machineById[machineId]?.variants?.firstOrNull { it.code == code }
            ?: machines.firstNotNullOfOrNull { m -> m.variants.firstOrNull { it.code == code } }
        val words = listOfNotNull(build?.brewer?.ifEmpty { null }, build?.cabinet?.ifEmpty { null })
            .joinToString(" ")
        return if (words.isEmpty()) code else "$words ($code)"
    }

    /**
     * Narrow a machine's list to one build — but only when the books really do
     * split it that way.
     *
     * For most machines a message or a procedure carries the build codes it
     * belongs to, and six to nine out of ten survive the narrowing. Where the
     * manuals never split that build out, almost nothing does, and an engineer
     * would be looking at three of the forty-two messages his screen can show.
     * Then the machine's own list is the honest answer.
     */
    fun <T> forBuild(all: List<T>, variant: String?, matches: (T) -> Boolean): List<T> {
        if (variant == null) return all
        val narrowed = all.filter(matches)
        return if (narrowed.size * 4 >= all.size) narrowed else all
    }

    /**
     * Whether a record holds for the build being worked on.
     *
     * A record without model codes is general; one that names them holds only
     * for those. With no build chosen everything passes.
     */
    fun forVariant(codes: List<String>, variant: String?): Boolean =
        variant == null || codes.isEmpty() || codes.contains(variant)

    private val procById = procedures.associateBy { it.id }
    private val machineById = machines.associateBy { it.id }

    /**
     * Search keys, built the first time something is searched for.
     *
     * Building them eagerly put a second of normalising eighty thousand rows in
     * front of the first screen, and did it twice, because the catalog is built
     * again when the parts table arrives.
     */
    private val faultKeys by lazy {
        faults.map { normalize(it.message + " " + it.dutch + " " + it.category + " " + it.cause) }
    }
    private val procKeys by lazy {
        procedures.map { p ->
            normalize(p.title + " " + p.purpose + " " + p.steps.joinToString(" ") { it.text })
        }
    }
    private val partNumberKeys by lazy { parts.map { normalize(it.number) } }
    private val partTextKeys by lazy { parts.map { normalize(it.description + " " + it.section) } }
    private val machineKeys by lazy {
        machines.map { normalize(it.name + " " + it.series + " " + it.typeCode + " " + it.brewer) }
    }
    // Title and body are scored apart: a hit in the heading of a section is
    // what the engineer was looking for, a hit halfway its description usually
    // is not.
    private val componentTitles by lazy { components.map { normalize(it.title) } }
    private val componentKeys by lazy { components.map { normalize(it.text) } }
    private val menuTitles by lazy { menu.map { normalize(it.title + " " + it.path) } }
    private val menuKeys by lazy { menu.map { normalize(it.text) } }
    private val cardTitles by lazy {
        cards.map { k ->
            normalize(k.title + " " + machineNames(k.machines) + " " + k.codes.joinToString(" "))
        }
    }
    private val cardKeys by lazy {
        cards.map { k -> normalize(k.steps.joinToString(" ") { it.points.joinToString(" ") }) }
    }

    /** Builds a machine is sold in, tasks from the books that describe it. */
    fun variants(machineId: String): List<Variant> =
        machineById[machineId]?.variants.orEmpty()

    fun card(id: String): MaintenanceCard? = cards.firstOrNull { it.id == id }

    fun cardsFor(machineId: String?, code: String? = null): List<MaintenanceCard> = cards
        .filter { card ->
            (machineId == null || card.machines.contains(machineId)) &&
                forVariant(card.codes, code)
        }
        // Without a machine chosen the list is fifty cards long, so keep the
        // ones for the same machine together.
        .sortedWith(compareBy({ it.machines.firstOrNull() ?: "" },
                              { it.codes.firstOrNull() ?: "" }))

    /** The same catalog with the parts table filled in. */
    fun withParts(rows: List<Part>) = Catalog(
        machines = machines, faults = faults, procedures = procedures, parts = rows,
        specs = specs, components = components, menu = menu, cards = cards,
        views = views, drawings = drawings,
        drawingNames = drawingNames, hotspots = hotspots,
    )

    fun procedure(id: String): Procedure? = procById[id]

    fun faultGroup(message: String): FaultGroup? = groupByMessage[message]

    fun component(id: String): Component? = components.firstOrNull { it.id == id }

    fun menuItem(id: String): MenuItem? = menu.firstOrNull { it.id == id }

    fun drawing(machine: String, variant: String, section: String): List<String> =
        drawings["$machine|$variant|$section"].orEmpty()

    fun drawingName(machine: String, variant: String, section: String): String =
        drawingNames["$machine|$variant|$section"].orEmpty()

    /** Balloons on the drawing for this section, keyed by position. */
    fun balloons(machine: String, variant: String, section: String): List<Hotspot> {
        val path = drawing(machine, variant, section).firstOrNull() ?: return emptyList()
        return hotspots[path.substringAfterLast('/').removeSuffix(".webp")].orEmpty()
    }

    /** Machine views for one machine, front first. */
    fun viewsFor(machineId: String?): List<MachineView> =
        views.filter { machineId == null || it.machines.contains(machineId) }
            .sortedBy { it.number }

    fun machine(id: String): Machine? = machineById[id]

    /** Human-readable machine names for a list of ids, e.g. "Virtu, Lua". */
    fun machineNames(ids: List<String>): String =
        ids.mapNotNull { machineById[it]?.name }.joinToString(", ").ifEmpty { ids.joinToString(", ") }

    /**
     * Parts for one machine, ranked. The parts screen used to normalize every
     * description on every keystroke; this reuses the keys built at load time.
     */
    fun searchParts(machine: String?, raw: String, limit: Int = 120,
                    variant: String? = null): List<Part> {
        val q = normalize(raw)
        if (q.length < 2) return emptyList()
        val words = terms(q)
        // With a machine and a build chosen — which is how the parts screen
        // always asks — only that book's rows are worth looking at.
        val rows = if (machine != null && variant != null) {
            partsByBuild[machine to variant].orEmpty()
        } else {
            parts.indices
        }
        return rows
            .mapNotNull { i ->
                if (machine != null && parts[i].machine != machine) return@mapNotNull null
                if (variant != null && parts[i].variant != variant) return@mapNotNull null
                val byNumber = score(partNumberKeys[i], words)?.plus(100)
                val hit = byNumber ?: score(partTextKeys[i], words)
                hit?.let { it to parts[i] }
            }
            .sortedByDescending { it.first }
            .take(limit)
            .map { it.second }
    }

    fun search(raw: String, limit: Int = 60): SearchResult {
        val q = normalize(raw)
        if (q.length < 2) return SearchResult()
        val words = terms(q)

        val hitFaults = faults.indices
            .mapNotNull { i -> score(faultKeys[i], words)?.let { it to faults[i] } }
            .sortedByDescending { it.first }
            .mapNotNull { groupByMessage[it.second.message] }
            .distinct()
            .take(limit)

        val hitProcs = procedures.indices
            .mapNotNull { i -> score(procKeys[i], words)?.let { it to procedures[i] } }
            .sortedByDescending { it.first }
            .take(limit).map { it.second }

        // A part number is what an engineer types most often, so number matches
        // outrank description matches instead of competing with them.
        val hitParts = parts.indices
            .mapNotNull { i ->
                val byNumber = score(partNumberKeys[i], words)?.plus(100)
                val byText = byNumber ?: score(partTextKeys[i], words)
                byText?.let { it to parts[i] }
            }
            .sortedByDescending { it.first }
            .take(limit).map { it.second }

        val hitComponents = components.indices
            .mapNotNull { i -> best(componentTitles[i], componentKeys[i], words)?.let { it to components[i] } }
            .sortedByDescending { it.first }
            .take(limit).map { it.second }

        val hitMenu = menu.indices
            .mapNotNull { i -> best(menuTitles[i], menuKeys[i], words)?.let { it to menu[i] } }
            .sortedByDescending { it.first }
            .take(limit).map { it.second }

        val hitMachines = machines.indices
            .mapNotNull { i -> score(machineKeys[i], words)?.let { it to machines[i] } }
            .sortedByDescending { it.first }
            .map { it.second }

        val hitCards = cards.indices
            .mapNotNull { i -> best(cardTitles[i], cardKeys[i], words)?.let { it to cards[i] } }
            .sortedByDescending { it.first }
            .take(limit).map { it.second }

        return SearchResult(hitFaults, hitProcs, hitParts, hitMachines, hitComponents,
                            hitMenu, hitCards)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The languages the manuals — and therefore the app — exist in. */
        val LANGUAGES = listOf("nl", "en", "de", "fr", "sv", "no", "da", "fi", "cs")

        /**
         * Everything except the parts table, which is by far the biggest file.
         *
         * Reading all of it before the first screen appears costs seconds on a
         * phone, and the engineer opening the app is usually after a message or
         * a procedure. [loadParts] follows in the background.
         */
        fun load(context: Context, language: String = "nl"): Catalog {
            fun <T> read(name: String, parse: (String) -> T): T =
                parse(context.assets.open(name).bufferedReader().use { it.readText() })

            val wanted = if (language in LANGUAGES) language else "nl"
            val content: Content = read("content-$wanted.json") { json.decodeFromString(it) }

            return Catalog(
                parts = emptyList(),
                machines = read("machines-$wanted.json") { json.decodeFromString(it) },
                faults = content.faults,
                procedures = content.procedures,
                specs = content.specs,
                components = content.components,
                menu = content.menu,
                cards = read("cards.json") { json.decodeFromString(it) },
                views = content.views,
                drawings = read("drawings.json") { json.decodeFromString(it) },
                drawingNames = read("drawingnames.json") { json.decodeFromString(it) },
                hotspots = read("hotspots.json") { json.decodeFromString(it) },
            )
        }

        /**
         * The parts table, read after the app is already usable.
         *
         * Straight off the stream: as a string first, those 4.8 MB of JSON are
         * ten in memory before parsing even starts.
         */
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        fun loadParts(context: Context): List<Part> =
            context.assets.open("parts.json").use { json.decodeFromStream(it) }

        // Built once. These used to be constructed inside normalize(), which
        // the catalog calls eighty thousand times while indexing the parts.
        private val ACCENTS = Regex("\\p{Mn}+")
        private val NOISE = Regex("[^a-z0-9 ]+")
        private val SPACES = Regex(" +")

        /** Lowercase, strip accents, and drop the punctuation that part numbers carry. */
        fun normalize(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
                .replace(ACCENTS, "")
                .replace(NOISE, " ")
                .replace(SPACES, " ")
                .trim()

        /** Best of a title hit (weighted up) and a body hit. */
        fun best(title: String, body: String, terms: List<String>): Int? {
            val t = score(title, terms)?.times(3)
            val b = score(body, terms)
            return when {
                t != null && b != null -> maxOf(t, b)
                else -> t ?: b
            }
        }

        /** The words of an already normalized query, in the order they were typed. */
        fun terms(query: String): List<String> = query.split(' ').filter { it.isNotEmpty() }

        /**
         * Ranks a haystack against the words of a query: whole-string match beats
         * start-of-word, which beats a match anywhere.
         *
         * This runs once per row of every collection — eighty thousand times on
         * a keystroke — so the query is split by the caller, once.
         */
        fun score(haystack: String, terms: List<String>): Int? {
            if (haystack.isEmpty() || terms.isEmpty()) return null
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
    val cards: List<MaintenanceCard> = emptyList(),
) {
    val empty: Boolean
        get() = faults.isEmpty() && procedures.isEmpty() && parts.isEmpty() &&
            machines.isEmpty() && components.isEmpty() && menu.isEmpty() &&
            cards.isEmpty()
}
