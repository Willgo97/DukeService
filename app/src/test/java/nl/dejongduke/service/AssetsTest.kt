package nl.dejongduke.service

import kotlinx.serialization.json.Json
import nl.dejongduke.service.data.Component
import nl.dejongduke.service.data.Content
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

    /** The nine languages the manuals exist in, and the app with them. */
    private val languages = listOf("nl", "en", "de", "fr", "sv", "no", "da", "fi", "cs")

    private fun content(language: String): Content = read("content-$language.json")

    @Test
    fun everyAssetParses() {
        val machines: List<Machine> = read("machines.json")
        val nl = content("nl")
        val faults: List<Fault> = nl.faults
        val procedures: List<Procedure> = nl.procedures
        val cards: List<MaintenanceCard> = read("cards.json")
        val parts: List<Part> = read("parts.json")
        val specs: List<SpecGroup> = nl.specs
        val components: List<Component> = nl.components
        val menu: List<MenuItem> = nl.menu
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
        val views: List<MachineView> = nl.views
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

        val components: List<Component> = content("nl").components
        val figures = components.flatMap { it.images }.distinct()
        assertTrue("missing figures", figures.none { !File(assets, it).exists() })
    }

    @Test
    fun everyLanguageIsCompleteAndPointsAtTheSameThings() {
        val dutch = content("nl")
        languages.forEach { language ->
            val c = content(language)
            // Translations replace the words, never the structure: the same
            // faults, procedures and drawings have to be there in every
            // language, or a screen would be empty for a Finnish engineer.
            assertTrue("$language faults", c.faults.size == dutch.faults.size)
            assertTrue("$language procedures", c.procedures.size == dutch.procedures.size)
            assertTrue("$language components", c.components.size == dutch.components.size)
            assertTrue("$language menu", c.menu.size == dutch.menu.size)
            assertTrue("$language views", c.views.size == dutch.views.size)
            assertTrue("$language specs", c.specs.size == dutch.specs.size)

            val empty = c.faults.count { it.message.isBlank() } +
                c.procedures.count { it.title.isBlank() } +
                c.components.count { it.title.isBlank() }
            assertTrue("$language has $empty untitled items", empty == 0)

            val figures = (c.components.flatMap { it.images } + c.views.flatMap { it.images })
                .distinct()
                .filterNot { File(assets, it).exists() }
            assertTrue("$language misses pictures: ${figures.take(3)}", figures.isEmpty())
        }
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
