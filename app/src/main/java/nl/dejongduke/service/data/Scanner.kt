package nl.dejongduke.service.data

import java.util.Locale

/**
 * What the camera found. The scanner does not care whether it is looking at a
 * part label, a type plate or the machine's own display — it reads whatever
 * text is there and works out what it belongs to.
 */
sealed interface ScanHit {
    val zekerheid: Int

    data class Onderdeel(val part: Part, val gelezen: String, override val zekerheid: Int) : ScanHit
    data class Storing(val groep: FaultGroup, val gelezen: String, override val zekerheid: Int) : ScanHit
    data class MachineHit(val machine: Machine, val gelezen: String, override val zekerheid: Int) : ScanHit
    data class Typeplaat(
        val machine: Machine?,
        val code: String,
        val serienummer: String,
        override val zekerheid: Int,
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
            if (p.nummer.isEmpty()) continue
            getOrPut(fold(p.nummer)) { mutableListOf() }.add(p)
        }
    }

    private val machineWords: List<Pair<String, Machine>> = catalog.machines.flatMap { m ->
        buildList {
            add(m.naam.uppercase(Locale.ROOT) to m)
            if (m.typecode.isNotEmpty()) {
                m.typecode.split("/").map { it.trim() }.filter { it.length >= 4 }
                    .forEach { add(it.uppercase(Locale.ROOT) to m) }
            }
            m.uitvoeringen.forEach { u -> add(u.code.uppercase(Locale.ROOT) to m) }
        }
    }

    /** The full message text, for a straight containment match. */
    private val faultTexts: List<Pair<List<String>, FaultGroup>> = catalog.faultGroups.map { g ->
        listOf(g.melding, g.eerste.nl).filter { it.isNotBlank() }.map { plain(it) } to g
    }

    /** Screen messages, split into the words worth matching on. */
    private val faultWords: List<Pair<Set<String>, FaultGroup>> = catalog.faultGroups.map { g ->
        val words = (g.melding + " " + g.eerste.nl)
            .lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 && it !in STOP }
            .toSet()
        words to g
    }

    fun scan(lines: List<String>): List<ScanHit> {
        val hits = mutableListOf<ScanHit>()
        val joined = lines.joinToString(" ")
        val upper = joined.uppercase(Locale.ROOT)

        // --- part numbers -------------------------------------------------
        for (token in TOKEN.findAll(upper).map { it.value }.distinct()) {
            if (token.length < 5) continue
            val found = partIndex[fold(token)] ?: continue
            val part = found.first()
            val exact = found.any { it.nummer.equals(token, ignoreCase = true) }
            hits += ScanHit.Onderdeel(part, token, if (exact) 100 else 80)
        }

        // --- type plate ---------------------------------------------------
        val serial = SERIAL.find(upper)?.value
        val code = machineWords.firstOrNull { (word, _) -> word.length >= 4 && upper.contains(word) }
        if (serial != null) {
            hits += ScanHit.Typeplaat(code?.second, code?.first.orEmpty(), serial, 90)
        }

        // --- machine name on the housing ----------------------------------
        for ((word, machine) in machineWords) {
            if (word.length < 3) continue
            if (!Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(upper)) continue
            if (hits.any { it is ScanHit.MachineHit && it.machine.id == machine.id }) continue
            hits += ScanHit.MachineHit(machine, word, if (word.length > 4) 85 else 70)
        }

        // --- a message on the machine's display ---------------------------
        // Short ones ("Lekbak vol") have too few long words to score on, so a
        // straight containment match comes first.
        val flat = plain(joined)
        for ((texts, group) in faultTexts) {
            val hit = texts.firstOrNull { it.length >= 6 && flat.contains(it) }
            if (hit != null) {
                hits += ScanHit.Storing(group, hit, 98)
                continue
            }
            // the same message with a character or two misread
            val loose = texts.firstOrNull { it.length >= 10 && nearIn(it, flat) }
            if (loose != null) hits += ScanHit.Storing(group, loose, 88)
        }

        // Word matching is fuzzy on purpose. Read off a glossy display at an
        // angle, "Koffiemolen" comes back as "Koffiemnolen" often enough that
        // exact comparison would miss the very message you are standing in
        // front of.
        val seen = joined.lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+")).filter { it.length >= 4 }.toSet()
        for ((words, group) in faultWords) {
            if (words.isEmpty()) continue
            val overlap = words.count { word -> seen.any { near(word, it) } }
            val ratio = overlap * 100 / words.size
            if (overlap >= 2 && ratio >= 50 || (words.size == 1 && overlap == 1)) {
                hits += ScanHit.Storing(group, group.melding, minOf(99, 55 + ratio / 3))
            }
        }

        return hits.sortedByDescending { it.zekerheid }.distinctBy { key(it) }.take(8)
    }

    private fun key(hit: ScanHit): String = when (hit) {
        is ScanHit.Onderdeel -> "p:" + hit.part.nummer
        is ScanHit.Storing -> "f:" + hit.groep.melding
        is ScanHit.MachineHit -> "m:" + hit.machine.id
        is ScanHit.Typeplaat -> "t:" + hit.serienummer
    }

    companion object {
        private val TOKEN = Regex("[0-9A-Z][0-9A-Z.\\-]{3,18}")
        /** DUKE serial numbers on the type plate look like 2019.1234 or 20190101. */
        private val SERIAL = Regex("\\b(?:20[0-9]{2}\\.[0-9]{3,5}|[0-9]{7,10})\\b")
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

        /** Whether `needle` appears in `haystack` allowing a couple of misreads. */
        fun nearIn(needle: String, haystack: String): Boolean {
            val words = needle.split(' ').filter { it.length >= 4 }
            if (words.isEmpty()) return false
            val seen = haystack.split(' ')
            return words.all { w -> seen.any { near(w, it) } }
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
