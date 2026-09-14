package nl.dejongduke.service

import kotlinx.serialization.json.Json
import nl.dejongduke.service.data.Book
import nl.dejongduke.service.data.Component
import nl.dejongduke.service.data.Fault
import nl.dejongduke.service.data.Hotspot
import nl.dejongduke.service.data.MaintenanceCard
import nl.dejongduke.service.data.Machine
import nl.dejongduke.service.data.MachineView
import nl.dejongduke.service.data.MenuItem
import nl.dejongduke.service.data.Part
import nl.dejongduke.service.data.Procedure
import nl.dejongduke.service.data.SpecGroup
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The assets are generated from the manuals by kbtools/. This reads them the
 * same way the app does, so a field that changed shape fails the build instead
 * of the app.
 */
class AssetsTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val assets = File("src/main/assets")

    private inline fun <reified T> read(name: String): T =
        json.decodeFromString(File(assets, name).readText())

    @Test
    fun everyAssetParses() {
        val machines: List<Machine> = read("machines.json")
        val faults: List<Fault> = read("faults.json")
        val procedures: List<Procedure> = read("procedures.json")
        val cards: List<MaintenanceCard> = read("cards.json")
        val parts: List<Part> = read("parts.json")
        val specs: List<SpecGroup> = read("specs.json")
        val components: List<Component> = read("components.json")
        val menu: List<MenuItem> = read("servicemenu.json")
        val books: List<Book> = read("books.json")
        val drawings: Map<String, List<String>> = read("drawings.json")
        val namen: Map<String, String> = read("drawingnames.json")
        val hotspots: Map<String, List<Hotspot>> = read("hotspots.json")

        assertTrue("machines", machines.size >= 10)
        assertTrue("faults", faults.size >= 40)
        assertTrue("procedures", procedures.size >= 70)
        assertTrue("maintenance cards", cards.size >= 50)
        assertTrue("parts", parts.size >= 30_000)
        assertTrue("specs", specs.isNotEmpty())
        assertTrue("components", components.size >= 200)
        assertTrue("menu", menu.size >= 200)
        assertTrue("books", books.size >= 200)
        val views: List<MachineView> = read("views.json")
        assertTrue("views", views.isNotEmpty())
        assertTrue("drawings", drawings.size >= 1_000)
        assertTrue("drawing names", namen.size >= 1_000)
        assertTrue("hotspots", hotspots.isNotEmpty())
    }

    @Test
    fun everyDrawingAndPictureExists() {
        val drawings: Map<String, List<String>> = read("drawings.json")
        val missing = drawings.values.flatten().distinct()
            .filterNot { File(assets, it).exists() }
        assertTrue("missing drawings: ${missing.take(5)}", missing.isEmpty())

        val cards: List<MaintenanceCard> = read("cards.json")
        val steps = cards.flatMap { it.steps }.flatMap { it.images }.distinct()
        assertTrue("maintenance pictures", steps.isNotEmpty())
        assertTrue("missing step pictures",
            steps.none { !File(assets, it).exists() })

        val components: List<Component> = read("components.json")
        val figures = components.flatMap { it.images }.distinct()
        assertTrue("missing figures", figures.none { !File(assets, it).exists() })
    }

    @Test
    fun partsPointAtKnownMachinesAndBuilds() {
        val machines: List<Machine> = read("machines.json")
        val parts: List<Part> = read("parts.json")
        val known = machines.associateBy { it.id }
        val strays = parts.map { it.machine }.distinct().filterNot { known.containsKey(it) }
        assertTrue("unknown machine in parts: $strays", strays.isEmpty())

        val builds = parts.map { it.machine to it.variant }.distinct()
        val unknownBuild = builds.filter { (machine, code) ->
            code.isNotEmpty() && known[machine]?.variants?.none { it.code == code } == true
        }
        assertTrue("build not listed on its machine: $unknownBuild", unknownBuild.isEmpty())
    }

    @Test
    fun everyDrawingKeyHasPartsOrIsAnOverview() {
        val drawings: Map<String, List<String>> = read("drawings.json")
        val names: Map<String, String> = read("drawingnames.json")
        val parts: List<Part> = read("parts.json")
        val have = parts.map { "${it.machine}|${it.variant}|${it.section}" }.toHashSet()
        // An overview sheet shows where everything sits and has no table of its
        // own; anything else without parts would be an extraction mistake.
        val orphans = drawings.keys
            .filterNot { have.contains(it) }
            .filterNot { names[it].orEmpty().contains("Overview", ignoreCase = true) }
        assertTrue("drawings without a parts table: ${orphans.take(5)}", orphans.isEmpty())
    }
}
