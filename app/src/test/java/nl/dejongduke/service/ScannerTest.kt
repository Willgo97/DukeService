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

    // --- the type plate inside the door ----------------------------------
    // What the camera gets off a real plate, photographed at an angle because
    // there is a milk cooler in front of it.

    private fun plate(vararg lines: String) =
        scanner.scan(lines.toList(), 60).filterIsInstance<ScanHit.TypePlate>().firstOrNull()

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
        assertEquals("2014386812001", hit?.serienummer)
        assertEquals("2014 · week 38", hit?.built)
    }

    @Test
    fun theTypeCodeAloneIsEnough() {
        // Half the plate is behind the cooler; only the type line came out.
        val hit = plate("Type: 9CKAA211A2A00")
        assertEquals("nio", hit?.machine?.id)
        assertEquals("CKA", hit?.build)
        assertTrue("confidence", (hit?.confidence ?: 0) >= 90)
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
    }

    @Test
    fun aPlateIsReadAsAPlateAndNothingElse() {
        // The pressures and the power rating look like part numbers, and the
        // model line shares words with a screen message.
        val found = scanner.scan(
            listOf(
                "de JONG DUKE", "MADE IN HOLLAND",
                "Serial nr.: 2014386812001",
                "Type: 9CKAA211A2A00",
                "Model: Nio 20.2 FM [a] CoEx® bean2cup",
                "220-240V 50-60 Hz 2.9-3.4 kW",
                "Line pressure: 0.05 - 0.6 MPa (0.5 - 6.0 bar)",
            ),
            60,
        )
        assertEquals(1, found.size)
        assertTrue("type plate", found.first() is ScanHit.TypePlate)
    }

    @Test
    fun theMakersNameAloneSaysItIsAPlate() {
        assertTrue(scanner.isPlate(listOf("de JONG DUKE", "SLIEDRECHT, NL")))
        assertTrue(!scanner.isPlate(listOf("5KAF119 suction filter", "Brewer out of position")))
    }

    @Test
    fun aPlateFillsUpOverSeveralFrames() {
        // What the camera gets while you move the phone along the plate.
        var plate = scanner.readPlate(listOf("de JONG DUKE", "MADE IN HOLLAND"))!!
        assertTrue("nothing read yet", !plate.known)

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
    fun theMakersNameAloneIsNotAPlate() {
        assertTrue(!scanner.isPlate(listOf("de JONG DUKE", "5KAF119")))
        // The words in front of the fields are only ever on a plate.
        assertTrue(scanner.isPlate(listOf("Serial nr.: ", "Rated pressure:")))
        // And so is the maker's name together with where it was made.
        assertTrue(scanner.isPlate(listOf("de JONG DUKE", "MADE IN HOLLAND")))
    }

    @Test
    fun aPlateWithATypeCodeLeavesNothingElseInTheList() {
        val found = scanner.scan(
            listOf(
                "de JONG DUKE", "Serial nr.: 2014386812001", "Type: 9CKAA211A2A00",
                "220-240V 50-60 Hz 2.9-3.4 kW", "Line pressure: 0.05 - 0.6 MPa",
            ),
            60,
        )
        assertEquals(1, found.size)
        assertTrue("type plate", found.first() is ScanHit.TypePlate)
    }


    @Test
    fun aPlateWithNothingOnItYetIsNotAResult() {
        // Two words off the plate came through, no field did. That is a
        // "hold still", not something to put on screen instead of the label
        // the camera is actually pointed at.
        val reading = scanner.readPlate(listOf("de JONG DUKE", "MADE IN HOLLAND"))
        assertTrue("recognised", reading != null)
        assertTrue("empty", reading!!.isEmpty)

        val found = scanner.scan(
            listOf("de JONG DUKE", "MADE IN HOLLAND", "5KAF119", "Suction filter"), 60)
        assertTrue("the part is still found", found.any { it is ScanHit.PartHit })
    }

    @Test
    fun aLabelIsReadWithTheMakersNameBesideIt() {
        // What the camera really returns off a sticker on the machine.
        val lines = listOf("de jONG DUKE", "5KAF119", "Suction filter for 2 mixers", "Made in Holland")
        val found = scanner.scan(lines, 60)
        assertTrue("part", found.any { it is ScanHit.PartHit })
    }
}
