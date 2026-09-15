package nl.dejongduke.service

import kotlinx.serialization.json.Json
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.Content
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

    // --- the type plate inside the door ----------------------------------
    // Read by its own scanner, on its own screen. What the camera gets off a
    // real plate, photographed at an angle because there is a milk cooler in
    // front of it.

    private fun plate(vararg lines: String) = scanner.readPlate(lines.toList())

    @Test
    fun readsTheWholeTypePlate() {
        val hit = plate(
            "de JONG DUKE", "WWW.DEJONGDUKE.NL", "SLIEDRECHT, NL", "MADE IN HOLLAND",
            "Serial nr.: 2014386812001",
            "Type: 9CKAA211A2A00",
            "Model: Nio 20.2 FM [a] CoEx® bean2cup",
            "Fresh Milk", "220-240V 50-60 Hz 2.9-3.4 kW",
            "Date: September 2014",
        )
        assertEquals("nio", hit?.machine?.id)
        assertEquals("CKA", hit?.build)
        assertEquals("9CKAA211A2A00", hit?.code)
        assertEquals("2014386812001", hit?.serial)
        assertEquals("2014 · week 38", hit?.built)
        assertTrue("complete", hit?.complete == true)
    }

    @Test
    fun theTypeCodeAloneIsEnough() {
        // Half the plate is behind the cooler; only the type line came out.
        val hit = plate("Type: 9CKAA211A2A00")
        assertEquals("nio", hit?.machine?.id)
        assertEquals("CKA", hit?.build)
    }

    @Test
    fun aLongerBuildCodeWinsFromTheShorterOneItStartsWith() {
        // 9CECK is a Virtu 70/90, not the CEC it begins with.
        val hit = plate("Serial nr.: 2019120001001", "Type: 9CECKB110A1A00",
                        "Model: Virtu 70.2 CoEx bean2cup")
        assertEquals("CECK", hit?.build)
        assertEquals("virtu", hit?.machine?.id)
    }

    @Test
    fun aSerialWithoutATypeCodeStillNamesTheYear() {
        val hit = plate("Serial nr.: 2021.14.0428.001")
        assertEquals("2021 · week 14", hit?.built)
        assertEquals(null, hit?.machine)
    }

    @Test
    fun plainTextIsNotATypePlate() {
        assertEquals(null, plate("Clean the milk system", "5KAF119 suction filter"))
        assertEquals(null, plate("Login", "Hardware", "Brewer", "Counters"))
    }

    @Test
    fun aPlateWithNothingReadOffItYetSaysHoldStill() {
        // Two words off the plate came through, no field did.
        val reading = plate("de JONG DUKE", "MADE IN HOLLAND")
        assertTrue("recognised as a plate", reading != null)
        assertTrue("nothing read off it yet", reading!!.isEmpty)
    }

    @Test
    fun aPlateFillsUpOverSeveralFrames() {
        // What the camera gets while you move the phone along the plate.
        var plate = scanner.readPlate(listOf("de JONG DUKE", "MADE IN HOLLAND"))!!
        assertTrue("nothing read yet", plate.machine == null)

        plate = plate.merge(scanner.readPlate(listOf("Type: 9CKAA211A2A00"))!!)
        assertEquals("nio", plate.machine?.id)
        assertEquals("CKA", plate.build)
        assertTrue("not complete without the serial", !plate.complete)

        plate = plate.merge(scanner.readPlate(
            listOf("Serial nr.: 2014386812001", "Model: Nio 20.2 FM [a] CoEx® bean2cup"))!!)
        assertTrue("complete", plate.complete)
        assertEquals("Nio 20.2 FM [a] CoEx® bean2cup", plate.model)
        assertEquals("2014 · week 38", plate.built)
        // What was read first is not thrown away by a later frame.
        assertEquals("9CKAA211A2A00", plate.code)
    }

    // --- a label or a screen with the machine's own name in frame ---------
    // The maker's name is printed all over the machine, so it may not decide
    // what the camera is looking at.

    @Test
    fun aPartLabelIsStillReadWithTheMakersNameInFrame() {
        val found = scanner.scan(listOf("de JONG DUKE", "5KAF119", "Suction filter"), 60)
        assertTrue("part", found.any { it is ScanHit.PartHit })
    }

    @Test
    fun aScreenMessageIsStillReadWithTheMakersNameInFrame() {
        val found = scanner.scan(
            listOf("de JONG DUKE", "SLIEDRECHT", "Brewer out of position"), 60)
        assertTrue("fault", found.any { it is ScanHit.FaultHit })
    }

    @Test
    fun aLabelIsReadWithTheMakersNameBesideIt() {
        // What the camera really returns off a sticker on the machine.
        val lines = listOf("de jONG DUKE", "5KAF119", "Suction filter for 2 mixers", "Made in Holland")
        val found = scanner.scan(lines, 60)
        assertTrue("part", found.any { it is ScanHit.PartHit })
    }

    @Test
    fun aTypePlateInFrameInventsNoMessage() {
        // The model line shares words with a screen message and the pressures
        // read like part numbers. The part and message scanner does not read
        // plates, and it may not turn one into a fault either.
        val found = scanner.scan(
            listOf(
                "de JONG DUKE", "MADE IN HOLLAND",
                "Serial nr.: 2014386812001",
                "Type: 9CKAA211A2A00",
                "Model: Nio 20.2 FM [a] CoEx® bean2cup",
                "220-240V 50-60 Hz 2.9-3.4 kW",
                "Line pressure: 0.05 - 0.6 MPa (0.5 - 6.0 bar)",
            ),
            75,
        )
        assertEquals(emptyList<ScanHit>(), found.filterIsInstance<ScanHit.FaultHit>())
    }

    // --- what the camera drags in besides the label ------------------------

    @Test
    fun aWholePageOfTextDoesNotStallTheScanner() {
        // A drawing page or a service menu in frame: a lot of text, a lot of
        // numbers. The reader gets a frame every few hundred milliseconds, so
        // this may not take longer than that.
        val page = buildList {
            repeat(60) { add("4BBK052 Button head screw M4x10 qty 4 drawing 3532 pos $it") }
            add("Login > Hardware > Calibrations > Water flow meter")
            add("Boiler temperature 92 °C, pressure 11 bar, 2019.14.0428.001")
        }
        scanner.scan(listOf("warm up"), 75)      // building the index is not the scan
        val started = System.nanoTime()
        val found = scanner.scan(page, 75)
        val tookMs = (System.nanoTime() - started) / 1_000_000
        println("page scan took " + tookMs + " ms, " + found.size + " hits")
        assertTrue("a page may not take longer than a frame: " + tookMs + " ms", tookMs < 250)
    }

    @Test
    fun rubbishInDoesNotCrashOrInvent() {
        assertEquals(emptyList<ScanHit>(), scanner.scan(emptyList(), 75))
        assertEquals(emptyList<ScanHit>(), scanner.scan(listOf("", "   ", "@@@ ### %%%"), 75))
        assertEquals(emptyList<ScanHit>(), scanner.scan(listOf("x".repeat(4000)), 75))
    }

    @Test
    fun aServiceMenuScreenIsNotAFault() {
        // Words from the menu overlap with the messages; the phrase does not.
        val found = scanner.scan(
            listOf(
                "Login", "Hardware", "Brewer", "Grinder", "Mixer", "Water filter",
                "Boiler temperature", "Counters", "Software information",
            ),
            75,
        )
        assertEquals(emptyList<ScanHit>(), found.filterIsInstance<ScanHit.FaultHit>())
    }

    @Test
    fun thePartOfTheMachineYouArePointedAtComesFirst() {
        // A number that sits in more than one machine's parts book.
        val shared = catalog.parts.groupBy { it.number }
            .entries.first { (number, rows) ->
                number.isNotEmpty() && rows.map { it.machine }.distinct().size > 1
            }
        val machines = shared.value.map { it.machine }.distinct()
        for (machine in machines.take(3)) {
            val hit = scanner.scan(listOf(shared.key), 60, machine = machine)
                .filterIsInstance<ScanHit.PartHit>().firstOrNull()
            assertEquals("row for " + machine, machine, hit?.part?.machine)
        }
        // And without a machine it still finds the part.
        assertTrue("no machine", scanner.scan(listOf(shared.key), 60)
            .any { it is ScanHit.PartHit })
    }
}
