package nl.dejongduke.service.data

import java.util.Locale

/**
 * What the camera found. The scanner does not care whether it is looking at a
 * part label, a type plate or the machine's own display — it reads whatever
 * text is there and works out what it belongs to.
 */
sealed interface ScanHit {
    val confidence: Int

    data class PartHit(val part: Part, val read: String, override val confidence: Int) : ScanHit
    data class FaultHit(val group: FaultGroup, val read: String, override val confidence: Int) : ScanHit
    data class MachineHit(val machine: Machine, val read: String, override val confidence: Int) : ScanHit
    data class TypePlate(
        val machine: Machine?,
        /** The build the type code names: CKA, XEA, CECK … */
        val build: String,
        /** The type code as printed: 9CKAA211A2A00. */
        val code: String,
        val serienummer: String,
        /** "2014 · week 38", read out of the serial number. */
        val built: String,
        override val confidence: Int,
    ) : ScanHit
}

/**
 * Matches recognised text against the catalog.
 *
 * OCR on a greasy label in a dim plant room is not clean, so part numbers are
 * matched on a character-folded form: the mistakes a reader makes between O/0,
 * I/1, S/5 and B/8 are exactly the characters DUKE's numbering uses.
 */
class Scanner(private val catalog: Catalog) {

    private val partIndex: Map<String, MutableList<Part>> = buildMap {
        for (p in catalog.parts) {
            if (p.number.isEmpty()) continue
            getOrPut(fold(p.number)) { mutableListOf() }.add(p)
        }
    }

    private val machineWords: List<Pair<String, Machine>> = catalog.machines.flatMap { m ->
        buildList {
            add(m.name.uppercase(Locale.ROOT) to m)
            if (m.typeCode.isNotEmpty()) {
                m.typeCode.split("/").map { it.trim() }.filter { it.length >= 4 }
                    .forEach { add(it.uppercase(Locale.ROOT) to m) }
            }
            m.variants.forEach { u -> add(u.code.uppercase(Locale.ROOT) to m) }
        }
    }

    /**
     * The builds a type code can name, longest first: a plate reading 9CECK…
     * is a CoEx 70/90, not the CEC it starts with.
     */
    private val builds: List<Pair<String, Machine?>> = buildList {
        catalog.machines.forEach { m ->
            m.variants.forEach { add(it.code.uppercase(Locale.ROOT) to m) }
        }
        // A build we have no book for is still on a plate somewhere; naming it
        // is better than reading 9CECK as the CEC it starts with.
        BUILDS.forEach { add(it to null) }
    }.filter { it.first.length >= 3 }
        .distinctBy { it.first + (it.second?.id ?: "") }
        .sortedWith(compareByDescending<Pair<String, Machine?>> { it.first.length }
            .thenBy { it.second == null })

    /** The full message text, for a straight containment match. */
    private val faultTexts: List<Pair<List<String>, FaultGroup>> = catalog.faultGroups.map { g ->
        listOf(g.message, g.first.dutch).filter { it.isNotBlank() }.map { plain(it) } to g
    }

    /** Screen messages, split into the words worth matching on. */
    private val faultWords: List<Pair<Set<String>, FaultGroup>> = catalog.faultGroups.map { g ->
        val words = (g.message + " " + g.first.dutch)
            .lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 && it !in STOP }
            .toSet()
        words to g
    }

    /**
     * @param minimum drop anything the reader is less sure about than this.
     *   Live camera frames ask for a high bar, a photo the engineer picked on
     *   purpose can be read more generously.
     */
    fun scan(lines: List<String>, minimum: Int = 0): List<ScanHit> {
        val hits = mutableListOf<ScanHit>()
        val joined = lines.joinToString(" ")
        val upper = joined.uppercase(Locale.ROOT)
        val seenWords = joined.lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+")).filter { it.length >= 4 }.toSet()

        // --- part numbers -------------------------------------------------
        for (token in TOKEN.findAll(upper).map { it.value }.distinct()) {
            if (token.length < 5) continue
            val found = partIndex[fold(token)] ?: continue
            val part = found.first()
            val exact = found.any { it.number.equals(token, ignoreCase = true) }
            hits += ScanHit.PartHit(part, token, if (exact) 100 else 80)
        }

        // --- type plate ---------------------------------------------------
        // The plate inside the door is the one thing that says exactly which
        // machine is standing there: "Type: 9CKAA211A2A00" is a Nio with a
        // CoEx brewer, and the serial number says when it was built.
        readPlate(upper)?.let { hits += it }

        // --- machine name on the housing ----------------------------------
        for ((word, machine) in machineWords) {
            if (word.length < 3) continue
            if (!Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(upper)) continue
            if (hits.any { it is ScanHit.MachineHit && it.machine.id == machine.id }) continue
            // The plate above already says which machine it is, and it says
            // the build as well; a bare name underneath adds nothing.
            if (hits.any { it is ScanHit.TypePlate && it.machine?.id == machine.id }) continue
            hits += ScanHit.MachineHit(machine, word, if (word.length > 4) 85 else 76)
        }

        // --- a message on the machine's display ---------------------------
        // The message has to be there as a phrase, not as a bag of words that
        // happen to occur. "Brewer out of position" and a label reading
        // "brewer position sensor" share both their words; only one of them is
        // the message, and the difference is the words in between.
        //
        // Read off a glossy screen at an angle a character or two comes back
        // wrong, so the phrase is matched with a few edits allowed.
        val flat = plain(joined)
        for ((texts, group) in faultTexts) {
            val exact = texts.firstOrNull { it.length >= 6 && flat.contains(it) }
            if (exact != null) {
                hits += ScanHit.FaultHit(group, exact, 98)
                continue
            }
            val close = texts.firstOrNull {
                it.length >= 10 && fuzzyContains(it, flat, budget(it))
            }
            if (close != null) {
                hits += ScanHit.FaultHit(group, close, 92)
                continue
            }
            // A long message may lose a word to a reflection; a short one may
            // not, because two common words are no evidence at all.
            val words = faultWordsOf(group)
            if (words.size < 4) continue
            val overlap = words.count { word -> seenWords.any { near(word, it) } }
            if (overlap * 100 / words.size < 80) continue
            hits += ScanHit.FaultHit(group, group.message, 80)
        }

        return hits.filter { it.confidence >= minimum }
            .sortedByDescending { it.confidence }.distinctBy { key(it) }.take(8)
    }

    /**
     * Reads the type plate out of whatever the camera got.
     *
     * Three things can be on it and any one of them is worth something: the
     * type code (which machine and which build), the serial number (when it
     * was built) and the model line. A plate behind a milk cooler is often
     * only readable at an angle, so a hit on one of the three is enough.
     */
    private fun readPlate(upper: String): ScanHit.TypePlate? {
        val found = builds.firstNotNullOfOrNull { (build, machine) ->
            TYPE_CODE(build).find(upper)?.let { Triple(machine, build, it.value) }
        }
        // Without a book for that build the machine has to come from the model
        // line: "Model: Virtu 70.2 CoEx".
        val machine = found?.first ?: found?.let {
            machineWords.firstOrNull { (word, _) ->
                word.length >= 3 && Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(upper)
            }?.second
        }
        val serial = SERIAL.find(upper)?.value
            ?: if (found != null) LOOSE_SERIAL.find(upper)?.value else null
        if (found == null && serial == null) return null
        val confidence = when {
            found != null && serial != null -> 98
            found != null -> 92
            else -> 85
        }
        return ScanHit.TypePlate(
            machine = machine,
            build = found?.second.orEmpty(),
            code = found?.third.orEmpty(),
            serienummer = serial.orEmpty(),
            built = serial?.let { built(it) }.orEmpty(),
            confidence = confidence,
        )
    }

    private fun faultWordsOf(group: FaultGroup): Set<String> =
        faultWords.firstOrNull { it.second === group }?.first.orEmpty()

    fun key(hit: ScanHit): String = when (hit) {
        is ScanHit.PartHit -> "p:" + hit.part.number
        is ScanHit.FaultHit -> "f:" + hit.group.message
        is ScanHit.MachineHit -> "m:" + hit.machine.id
        is ScanHit.TypePlate -> "t:" + hit.serienummer
    }

    companion object {
        private val TOKEN = Regex("[0-9A-Z][0-9A-Z.\\-]{3,18}")

        /**
         * Every build DUKE puts on a type plate. The catalog knows the ones we
         * have books for; a machine on site may be one we do not, and reading
         * its plate should still say which build it is.
         */
        private val BUILDS = listOf(
            "CECK", "CECP", "CEC", "CND", "FEC", "FND", "IEA", "INB",
            "XEA", "XNA", "XKA", "CKA",
        )

        /**
         * A type code is a 9, the build, and the configuration behind it:
         * 9CKAA211A2A00, 9CECKB110A1A00. The 9 is missing on some plates and
         * the reader may drop it, so it is optional.
         */
        fun TYPE_CODE(build: String) =
            Regex("(?<![A-Z0-9])9?" + Regex.escape(build) + "[A-Z0-9]{3,}(?![A-Z0-9])")

        /**
         * The serial number is year, week, batch and number: 2014386812001 or
         * 2021.14.0428.001. Older plates print fewer digits.
         */
        private val SERIAL = Regex("\\b(?:(?:19|20)[0-9]{2}\\.[0-9]{2}\\.[0-9]{3,4}(?:\\.[0-9]{1,3})?|(?:19|20)[0-9]{11})\\b")
        private val LOOSE_SERIAL = Regex("\\b(?:(?:19|20)[0-9]{2}\\.[0-9]{3,5}|[0-9]{7,10})\\b")

        /** "2014 · week 38" out of 2014386812001 or 2014.38.6812.001. */
        fun built(serial: String): String {
            val digits = serial.filter { it.isDigit() }
            if (digits.length < 6) return ""
            val year = digits.take(4).toIntOrNull() ?: return ""
            if (year < 1990 || year > 2100) return ""
            val week = digits.drop(4).take(2).toIntOrNull() ?: return ""
            return if (week in 1..53) "$year · week $week" else "$year"
        }
        private val STOP = setOf(
            "machine", "koffiemachine", "coffee", "niet", "the", "een", "van", "voor",
            "door", "worden", "wordt", "deze", "your", "please", "with",
        )

        /** Lowercase, letters and digits only — how two bits of text are compared. */
        fun plain(s: String): String =
            s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()

        /** True when two words differ by at most one or two characters. */
        fun near(a: String, b: String): Boolean {
            if (a == b) return true
            if (kotlin.math.abs(a.length - b.length) > 2) return false
            val allowed = when {
                a.length >= 9 -> 2
                a.length >= 6 -> 1
                else -> 0
            }
            return allowed > 0 && distance(a, b, allowed) <= allowed
        }

        /** How many misread characters a message of this length may carry. */
        fun budget(needle: String) = maxOf(2, needle.length / 8)

        /**
         * Whether `needle` occurs in `haystack` as a phrase, give or take
         * `max` characters. Edit distance with a free start and end, so the
         * message may sit anywhere in what the camera read, but the words in
         * between still have to be there.
         */
        fun fuzzyContains(needle: String, haystack: String, max: Int): Boolean {
            if (needle.isEmpty() || haystack.length + max < needle.length) return false
            var prev = IntArray(haystack.length + 1)          // free start
            for (i in 1..needle.length) {
                val cur = IntArray(haystack.length + 1)
                cur[0] = i
                var best = cur[0]
                for (j in 1..haystack.length) {
                    val cost = if (needle[i - 1] == haystack[j - 1]) 0 else 1
                    cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                    best = minOf(best, cur[j])
                }
                if (best > max) return false
                prev = cur
            }
            return prev.min() <= max                          // free end
        }

        /** Levenshtein, cut off once it exceeds the budget. */
        private fun distance(a: String, b: String, max: Int): Int {
            var prev = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val cur = IntArray(b.length + 1)
                cur[0] = i
                var best = cur[0]
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                    best = minOf(best, cur[j])
                }
                if (best > max) return max + 1
                prev = cur
            }
            return prev[b.length]
        }

        /** Folds the character pairs OCR mixes up, so a misread still matches. */
        fun fold(s: String): String = buildString {
            for (c in s.uppercase(Locale.ROOT)) {
                when (c) {
                    'O', 'Q', 'D' -> append('0')
                    'I', 'L', '|' -> append('1')
                    'S' -> append('5')
                    'B' -> append('8')
                    'Z' -> append('2')
                    'G' -> append('6')
                    '-', '.', ' ' -> Unit
                    else -> append(c)
                }
            }
        }
    }
}
