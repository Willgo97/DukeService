package nl.dejongduke.service.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Machine(
    val id: String,
    val name: String,
    val series: String = "",
    val typeCode: String = "",
    val brewer: String = "",
    val cabinet: String = "",
    val screen: String = "",
    val active: Boolean = true,
    val photo: String = "",
    val serviceMenu: String = "",
    val serviceMenuNote: String = "",
    val summary: String = "",
    val description: String = "",
    val specs: List<SpecRow> = emptyList(),
    /** Which book the sizes are from; the figures are not typed in by hand. */
    val specsSource: String = "",
    val docs: List<String> = emptyList(),
    val variants: List<Variant> = emptyList(),
)

/** One build of a machine: the manuals treat these as separate documents. */
@Serializable
data class Variant(
    val code: String,
    val cabinet: String = "",
    val brewer: String = "",
    val doc: String = "",
)

@Serializable
data class SpecRow(
    val label: String,
    val small: String = "",
    val medium: String = "",
)

@Serializable
data class Fault(
    val message: String,
    val dutch: String,
    val machines: List<String>,
    /** Model codes the message is documented for: CEC, CND, XEA … */
    val codes: List<String> = emptyList(),
    val brewers: List<String> = emptyList(),
    val category: String,
    val cause: String = "",
    val solution: List<String> = emptyList(),
    val engineerNote: String = "",
    val procedures: List<String> = emptyList(),
    val selfService: Boolean = true,
    val note: String = "",
    val source: String = "",
    val language: String = "nl",
)

/**
 * One screen message, with the per-machine variants the manuals give for it.
 * Virtu and Lua word the same message differently often enough that keeping
 * them apart matters, but showing two identical-looking rows does not.
 */
data class FaultGroup(
    val message: String,
    val variants: List<Fault>,
) {
    val first: Fault get() = variants.first()
    val machines: List<String> get() = variants.flatMap { it.machines }.distinct()
    val selfService: Boolean get() = variants.all { it.selfService }
}

@Serializable
data class Procedure(
    val id: String,
    val title: String,
    val brewer: String = "",
    val machines: List<String> = emptyList(),
    val codes: List<String> = emptyList(),
    val interval: String = "",
    val intervalText: String = "",
    val purpose: String = "",
    val needed: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val warnings: List<SafetyNote> = emptyList(),
    val steps: List<Step> = emptyList(),
    val source: String = "",
    /** Dutch unless the machine's only manual is English, as with the Uni-Brewer. */
    val language: String = "nl",
)

@Serializable
data class SafetyNote(
    @SerialName("n") val level: String,
    @SerialName("t") val text: String,
)

@Serializable
data class Step(
    @SerialName("t") val text: String,
    @SerialName("s") val sub: List<String> = emptyList(),
)

@Serializable
data class Part(
    @SerialName("m") val machine: String,
    /** Model code of the book the row comes from, so two builds do not mix. */
    @SerialName("u") val variant: String = "",
    @SerialName("s") val section: String,
    @SerialName("d") val drawing: String,
    @SerialName("p") val pos: String,
    @SerialName("n") val number: String,
    @SerialName("q") val quantity: String,
    @SerialName("v") val stock: String,
    @SerialName("t") val description: String,
) {
    /** Parts marked "n/a" in the spare parts book are not sold separately. */
    val available: Boolean get() = number.isNotEmpty()
}

@Serializable
data class SpecGroup(
    val group: String,
    val brewer: String = "",
    val machines: List<String> = emptyList(),
    val codes: List<String> = emptyList(),
    val items: List<SpecItem> = emptyList(),
)

@Serializable
data class SpecItem(
    @SerialName("k") val key: String,
    @SerialName("v") val value: String,
)

/**
 * A component from the technical manual: how it works, and the pages it is
 * drawn on. The page renders are what an engineer actually wants next to a
 * schematic description.
 *
 * Every brewer has its own book, so the same subject appears more than once --
 * an open boiler behaves differently behind a Uni-Brewer than behind a CoEx.
 * [brewer] and [machines] say which machine a section belongs to; [number] is the
 * number in its own book and is therefore not unique, [id] is.
 */
@Serializable
data class Component(
    val id: String,
    val number: String,
    val title: String,
    val text: String,
    val group: String = "",
    val page: Int = 0,
    val images: List<String> = emptyList(),
    val machines: List<String> = emptyList(),
    val codes: List<String> = emptyList(),
    val brewer: String = "",
    val source: String = "",
    val language: String = "nl",
)

/** One entry of the service menu, as the technical manual documents it. */
@Serializable
data class MenuItem(
    val id: String,
    val number: String,
    val title: String,
    val text: String,
    val path: String = "",
    val level: String = "",
    val page: Int = 0,
    val image: String = "",
    val images: List<String> = emptyList(),
    val source: String = "",
    val purpose: String = "",
    val needed: List<String> = emptyList(),
    val interval: String = "",
    val steps: List<String> = emptyList(),
    val points: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val machines: List<String> = emptyList(),
    val codes: List<String> = emptyList(),
    val language: String = "nl",
) {
    /** Chapter 6 is the menu itself, 7 the step-by-step jobs. */
    val chapter: String get() = number.substringBefore('.')
}

/**
 * Everything that is written in a language, for one language.
 *
 * The manuals exist in nine; the app ships one file per language and reads the
 * one the engineer asked for, falling back to English where a book was never
 * translated.
 */
@Serializable
data class Content(
    val faults: List<Fault> = emptyList(),
    val components: List<Component> = emptyList(),
    val menu: List<MenuItem> = emptyList(),
    val procedures: List<Procedure> = emptyList(),
    val specs: List<SpecGroup> = emptyList(),
    val views: List<MachineView> = emptyList(),
)

/** A view of the machine with the numbered call-outs that belong to it. */
@Serializable
data class MachineView(
    val id: String,
    val number: String = "",
    val title: String,
    val callouts: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val machines: List<String> = emptyList(),
    val codes: List<String> = emptyList(),
    val source: String = "",
    val language: String = "nl",
)

/** A balloon number on an exploded drawing, in fractions of the image. */
@Serializable
data class Hotspot(
    @SerialName("n") val pos: String,
    val x: Float,
    val y: Float,
    val r: Float,
)

/**
 * A maintenance sheet as it hangs inside the machine: numbered steps, each with
 * the picture that goes with it. One per machine and interval, straight from
 * the manufacturer's fold-out card.
 */
@Serializable
data class MaintenanceCard(
    val id: String,
    val title: String,
    val interval: String = "",
    val machines: List<String> = emptyList(),
    val codes: List<String> = emptyList(),
    val language: String = "en",
    val source: String = "",
    val steps: List<MaintenanceStep> = emptyList(),
)

@Serializable
data class MaintenanceStep(
    @SerialName("n") val number: String = "",
    @SerialName("t") val points: List<String> = emptyList(),
    @SerialName("o") val notes: List<String> = emptyList(),
    @SerialName("a") val images: List<String> = emptyList(),
)

