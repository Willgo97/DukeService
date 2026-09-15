package nl.dejongduke.service.data

import java.util.Locale

/**
 * What the camera found on a part label or on the machine's display.
 *
 * The type plate is deliberately not in here: it is read by its own scanner,
 * on its own screen. Its pressures and power ratings read like part numbers
 * and its model line shares words with a screen message, so a scanner that
 * looks for all three at once is worse at all three.
 */
sealed interface ScanHit {
    val confidence: Int

    data class PartHit(val part: Part, val read: String, override val confidence: Int) : ScanHit
    data class FaultHit(val group: FaultGroup, val read: String, override val confidence: Int) : ScanHit
}

/**
 * A type plate as the camera reads it, which is rarely in one go.
 *
 * The plate sits inside the door, often behind a milk cooler, and comes into
 * frame line by line. Every frame adds what it could read; what was already
 * read stays. [complete] is true once the two lines that matter are in.
 */
data class Plate(
    val machine: Machine? = null,
    /** The build the type code names: CKA, XEA, CECK … */
    val build: String = "",
    /** The type code as printed: 9CKAA211A2A00. */
    val code: String = "",
    val serial: String = "",
    /** "2014 · week 38", read out of the serial number. */
    val built: String = "",
    /** The model line: "Nio 20.2 FM [a] CoEx bean2cup". */
    val model: String = "",
) {
    /** Nothing read off it yet: worth a "hold still", not worth a panel. */
    val isEmpty: Boolean get() = code.isEmpty() && serial.isEmpty() && model.isEmpty()

    /** Everything a plate carries has been read. */
    val complete: Boolean get() = code.isNotEmpty() && serial.isNotEmpty()

    /** What this frame added to what was already on screen. */
    fun merge(newer: Plate) = Plate(
        machine = machine ?: newer.machine,
        build = build.ifEmpty { newer.build },
        code = code.ifEmpty { newer.code },
        serial = serial.ifEmpty { newer.serial },
        built = built.ifEmpty { newer.built },
        model = model.ifEmpty { newer.model },
    )
}

/**
 * Matches recognised text against the catalog.
 *
 * OCR on a greasy label in a dim plant room is not clean, so part numbers are
 * matched on a character-folded form: the mistakes a reader makes between O/0,
 * I/1, S/5 and B/8 are exactly the characters DUKE's numbering uses.
 *
 * Everything that can be prepared is prepared once, in the constructor: this
 * runs on every camera frame, and a regex compiled inside the loop over the
 * catalog is a thousand regexes compiled per frame.
 */
class Scanner(private val catalog: Catalog) {

    private val partIndex: Map<String, MutableList<Part>> = buildMap {
        for (p in catalog.parts) {
            if (p.number.isEmpty()) continue
            getOrPut(fold(p.number)) { mutableListOf() }.add(p)
        }
    }

    /** Machine names and type codes, each with the pattern that finds it. */
    private val machineWords: List<Triple<String, Machine, Regex>> = catalog.machines.flatMap { m ->
        buildList {
            add(m.name.uppercase(Locale.ROOT))
            if (m.typeCode.isNotEmpty()) {
                addAll(m.typeCode.split("/").map { it.trim() }.filter { it.length >= 4 })
            }
            m.variants.forEach { add(it.code) }
        }.map { it.uppercase(Locale.ROOT) }
            .filter { it.length >= 3 }
            .distinct()
            .map { Triple(it, m, word(it)) }
    }

    /**
     * The builds a type code can name, longest first: a plate reading 9CECK…
     * is a CoEx 70/90, not the CEC it starts with.
     */
    private val builds: List<Triple<String, Machine?, Regex>> = buildList {
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
        .map { (build, machine) -> Triple(build, machine, TYPE_CODE(build)) }

    /**
     * One row per screen message: the message as printed (in the app's
     * language and in English, because the machine in front of you may be set
     * to either), and the words worth matching it on.
     */
    private class Message(val texts: List<String>, val words: Set<String>, val group: FaultGroup)

    private val messages: List<Message> = catalog.faultGroups.map { g ->
        val printed = listOf(g.message, g.first.dutch).filter { it.isNotBlank() }
        Message(
            texts = printed.map { plain(it) }.distinct(),
            words = printed.joinToString(" ")
                .lowercase(Locale.ROOT)
                .split(WORD_BREAK)
                .filter { it.length >= 4 && it !in STOP }
                .toSet(),
            group = g,
        )
    }

    /**
     * Reads a part label or a machine display.
     *
     * @param minimum drop anything the reader is less sure about than this.
     *   Live camera frames ask for a high bar, a photo the engineer picked on
     *   purpose can be read more generously.
     * @param machine the machine the app is pointed at. The same number sits in
     *   a dozen books; the row worth showing is the one from the machine in
     *   front of you.
     */
    fun scan(lines: List<String>, minimum: Int = 0, machine: String? = null): List<ScanHit> {
        val hits = mutableListOf<ScanHit>()
        val joined = lines.joinToString(" ")
        val upper = joined.uppercase(Locale.ROOT)

        // --- part numbers -------------------------------------------------
        for (token in TOKEN.findAll(upper).map { it.value }.distinct()) {
            if (token.length < 5) continue
            val found = partIndex[fold(token)] ?: continue
            val part = found.firstOrNull { it.machine == machine } ?: found.first()
            val exact = found.any { it.number.equals(token, ignoreCase = true) }
            hits += ScanHit.PartHit(part, token, if (exact) 100 else 80)
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
        val seenWords = flat.split(' ').filter { it.length >= 4 }.toSet()
        for (message in messages) {
            val exact = message.texts.firstOrNull { it.length >= 6 && flat.contains(it) }
            if (exact != null) {
                hits += ScanHit.FaultHit(message.group, exact, 98)
                continue
            }
            // Matching a phrase letter by letter costs time proportional to
            // how much text is in the picture. A screen message is short; a
            // drawing page that happens to be in frame is not, and the camera
            // hands over the next frame in a few hundred milliseconds.
            val close = if (flat.length > FUZZY_LIMIT) null else message.texts.firstOrNull {
                it.length >= 10 && fuzzyContains(it, flat, budget(it))
            }
            if (close != null) {
                hits += ScanHit.FaultHit(message.group, close, 92)
                continue
            }
            // A long message may lose a word to a reflection; a short one may
            // not, because two common words are no evidence at all. With a
            // page of text in frame this is skipped: every message would find
            // its words somewhere, and comparing them all costs more time than
            // there is between two frames.
            if (seenWords.size > BUSY_FRAME) continue
            if (message.words.size < 4) continue
            val overlap = message.words.count { word -> seenWords.any { near(word, it) } }
            if (overlap * 100 / message.words.size < 80) continue
            hits += ScanHit.FaultHit(message.group, message.group.message, 80)
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
     *
     * Returns null when there is no plate in front of the camera at all — and
     * an empty [Plate] when there is one but this frame could not read a field
     * off it yet, which is what "hold still" is for.
     */
    fun readPlate(lines: List<String>): Plate? {
        val upper = lines.joinToString(" ").uppercase(Locale.ROOT)
        val onAPlate = looksLikePlate(upper)
        val fields = plateFields(upper, loose = onAPlate)
        if (fields == null && !onAPlate) return null
        val plate = (fields ?: Plate()).copy(model = modelLine(lines, upper))
        // Without a book for that build the machine has to come from the model
        // line: "Model: Virtu 70.2 CoEx".
        return if (plate.machine != null) plate else plate.copy(machine = machineIn(upper))
    }

    /**
     * Whether the camera is looking at a type plate at all.
     *
     * The plate carries the maker's name and the words in front of its fields.
     * One of those on its own is no proof — the maker's name is printed on the
     * front, on stickers and on the drip tray — but on the plate scanner it is
     * reason enough to say "hold still" while the fields are being read.
     */
    private fun looksLikePlate(upper: String): Boolean =
        PLATE_FIELDS.any { upper.contains(it) } || PLATE_HINTS.any { upper.contains(it) }

    /**
     * The model line as printed — "Nio 20.2 FM [a] CoEx bean2cup" — because it
     * is the line the engineer recognises the machine by. The reader does not
     * always keep the word "Model" with it, so a line that names a machine and
     * carries a number counts too.
     */
    private fun modelLine(lines: List<String>, upper: String): String {
        val labelled = lines.firstOrNull { it.uppercase(Locale.ROOT).contains("MODEL") }
        if (labelled != null) {
            val text = labelled.substringAfter(':', labelled).trim()
            if (text.isNotEmpty() && !text.equals("model", ignoreCase = true)) return text.take(60)
        }
        // Nothing names a machine anywhere in frame: no line can be the one.
        if (machineIn(upper) == null) return ""
        return lines.firstOrNull { line ->
            line.any { it.isDigit() } && machineIn(line.uppercase(Locale.ROOT)) != null
        }?.trim()?.take(60).orEmpty()
    }

    /** The first machine named in already-uppercased text. */
    private fun machineIn(upper: String): Machine? =
        machineWords.firstOrNull { it.third.containsMatchIn(upper) }?.second

    /**
     * @param loose accept a serial number that is only a run of digits. On a
     *   plate that is the serial; anywhere else it is a drawing number.
     */
    private fun plateFields(upper: String, loose: Boolean): Plate? {
        val found = builds.firstNotNullOfOrNull { (build, machine, pattern) ->
            pattern.find(upper)?.let { Triple(machine, build, it.value) }
        }
        val serial = SERIAL.find(upper)?.value
            ?: if (loose || found != null) LOOSE_SERIAL.find(upper)?.value else null
        if (found == null && serial == null) return null
        return Plate(
            machine = found?.first,
            build = found?.second.orEmpty(),
            code = found?.third.orEmpty(),
            serial = serial.orEmpty(),
            built = serial?.let { built(it) }.orEmpty(),
        )
    }

    fun key(hit: ScanHit): String = when (hit) {
        is ScanHit.PartHit -> "p:" + hit.part.number
        is ScanHit.FaultHit -> "f:" + hit.group.message
    }

    companion object {
        /**
         * Guards against a pathological frame, not against a busy one: a whole
         * page of text is read in about 25 ms, so the limits sit far above
         * anything a machine's screen can hold.
         */
        private const val FUZZY_LIMIT = 4_000
        private const val BUSY_FRAME = 400

        private val TOKEN = Regex("[0-9A-Z][0-9A-Z.\\-]{3,18}")
        private val WORD_BREAK = Regex("[^a-z0-9]+")
        private val NOT_WORD = Regex("[^a-z0-9]+")

        /** A whole word, not a word inside another: "CEC" is not "9CECK". */
        private fun word(text: String) = Regex("\\b${Regex.escape(text)}\\b")

        /** The words in front of the fields: these stand on the plate only. */
        private val PLATE_FIELDS = listOf(
            "SERIAL NR", "SERIAL NO", "SERIENR", "RATED PRESSURE", "LINE PRESSURE",
        )

        /**
         * On the plate too, but not only there: the maker's name is printed on
         * the front, on stickers and on the drip tray.
         */
        private val PLATE_HINTS = listOf(
            "JONG DUKE", "DEJONGDUKE", "MADE IN HOLLAND", "SLIEDRECHT", "TYPE:",
        )

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
            s.lowercase(Locale.ROOT).replace(NOT_WORD, " ").trim()

        /** True when two words differ by at most one or two characters. */
        fun near(a: String, b: String): Boolean {
            if (a == b) return true
            if (kotlin.math.abs(a.length - b.length) > 2) return false
            val allowed = when {
                a.length >= 9 -> 2
                a.length >= 6 -> 1
                else -> 0
            }
            return allowed > 0 && editDistance(a, b, allowed, free = false) <= allowed
        }

        /** How many misread characters a message of this length may carry. */
        fun budget(needle: String) = maxOf(2, needle.length / 8)

        /**
         * Whether `needle` occurs in `haystack` as a phrase, give or take
         * `max` characters. The message may sit anywhere in what the camera
         * read, but the words in between still have to be there.
         */
        fun fuzzyContains(needle: String, haystack: String, max: Int): Boolean {
            if (needle.isEmpty() || haystack.length + max < needle.length) return false
            return editDistance(needle, haystack, max, free = true) <= max
        }

        /**
         * Levenshtein, cut off once it exceeds the budget. With [free] the
         * start and the end of the haystack cost nothing, which turns the
         * whole-string comparison into "does this phrase occur in there".
         */
        private fun editDistance(needle: String, haystack: String, max: Int, free: Boolean): Int {
            var prev = IntArray(haystack.length + 1) { if (free) 0 else it }
            for (i in 1..needle.length) {
                val cur = IntArray(haystack.length + 1)
                cur[0] = i
                var best = cur[0]
                for (j in 1..haystack.length) {
                    val cost = if (needle[i - 1] == haystack[j - 1]) 0 else 1
                    cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                    best = minOf(best, cur[j])
                }
                if (best > max) return max + 1
                prev = cur
            }
            return if (free) prev.min() else prev[haystack.length]
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
