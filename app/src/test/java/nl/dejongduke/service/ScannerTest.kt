package nl.dejongduke.service

import kotlinx.serialization.json.Json
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.Content
import nl.dejongduke.service.data.Fault
import nl.dejongduke.service.data.Machine
import nl.dejongduke.service.data.Part
import nl.dejongduke.service.data.ScanHit
import nl.dejongduke.service.data.Scanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The camera reads whatever is in front of it, including text that merely
 * shares a word with a message. Those may not come out as a hit.
 */
class ScannerTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val assets = File("src/main/assets")

    private inline fun <reified T> read(name: String): T =
        json.decodeFromString(File(assets, name).readText())

    private val catalog by lazy {
        Catalog(
            machines = read<List<Machine>>("machines-nl.json"),
            faults = read<Content>("content-nl.json").faults,
            procedures = emptyList(),
            parts = read<List<Part>>("parts.json"),
            specs = emptyList(),
        )
    }
    private val scanner by lazy { Scanner(catalog) }

    private fun messages(vararg lines: String, minimum: Int = 75) =
        scanner.scan(lines.toList(), minimum).filterIsInstance<ScanHit.FaultHit>()
            .map { it.group.message }

    @Test
    fun oneSharedWordIsNotAMessage() {
        // A parts label, a menu screen, a sticker: all contain "brewer".
        assertEquals(emptyList<String>(), messages("CoEx brewer assembly 9DDG001"))
        assertEquals(emptyList<String>(), messages("Brewer cleaning"))
        assertEquals(emptyList<String>(), messages("Service menu brewer position sensor"))
    }

    @Test
    fun theWholeMessageIsAMessage() {
        assertTrue(messages("Brewer not in home position").contains("Brewer not in home position"))
        assertTrue(messages("Empty waste bucket").isNotEmpty())
        // read off a screen at an angle, with a character misread
        assertTrue(messages("Brewer not in horne position").isNotEmpty())
    }

    @Test
    fun aPartNumberIsReadEvenWhenMisread() {
        val exact = scanner.scan(listOf("5KAF058"), 75).filterIsInstance<ScanHit.PartHit>()
        assertTrue("exact part number", exact.any { it.part.number == "5KAF058" })
        assertTrue("confident", exact.first().confidence >= 95)
        // O instead of 0, S instead of 5: the folded form still matches
        val folded = scanner.scan(listOf("SKAFO58"), 75).filterIsInstance<ScanHit.PartHit>()
        assertTrue("folded part number", folded.any { it.part.number == "5KAF058" })
    }

    @Test
    fun plainProseFindsNothing() {
        assertTrue(
            scanner.scan(
                listOf("Deze machine is eigendom van de klant", "Onderhoud door de leverancier"),
                75,
            ).isEmpty()
        )
    }
}
