package nl.dejongduke.service.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Machine(
    val id: String,
    val naam: String,
    val serie: String = "",
    val typecode: String = "",
    val brewer: String = "",
    val kast: String = "",
    val scherm: String = "",
    val actief: Boolean = true,
    val servicemenu: String = "",
    val servicemenuUitleg: String = "",
    val kort: String = "",
    val omschrijving: String = "",
    val specs: List<SpecRij> = emptyList(),
    val docs: List<String> = emptyList(),
    val uitvoeringen: List<Uitvoering> = emptyList(),
)

/** One build of a machine: the manuals treat these as separate documents. */
@Serializable
data class Uitvoering(
    val code: String,
    val kast: String = "",
    val brewer: String = "",
    val doc: String = "",
)

@Serializable
data class SpecRij(
    val label: String,
    val small: String = "",
    val medium: String = "",
)

@Serializable
data class Fault(
    val melding: String,
    val nl: String,
    val machines: List<String>,
    val cat: String,
    val oorzaak: String = "",
    val oplossing: List<String> = emptyList(),
    val monteur: String = "",
    val proc: List<String> = emptyList(),
    val zelf: Boolean = true,
    val opmerking: String = "",
    val bron: String = "",
)

/**
 * One screen message, with the per-machine variants the manuals give for it.
 * Virtu and Lua word the same message differently often enough that keeping
 * them apart matters, but showing two identical-looking rows does not.
 */
data class FaultGroup(
    val melding: String,
    val varianten: List<Fault>,
) {
    val eerste: Fault get() = varianten.first()
    val machines: List<String> get() = varianten.flatMap { it.machines }.distinct()
    val zelf: Boolean get() = varianten.all { it.zelf }
}

@Serializable
data class Procedure(
    val id: String,
    val titel: String,
    val brewer: String = "",
    val machines: List<String> = emptyList(),
    val interval: String = "",
    val intervalTekst: String = "",
    val doel: String = "",
    val nodig: List<String> = emptyList(),
    @SerialName("let") val letOp: List<LetOp> = emptyList(),
    val stappen: List<Stap> = emptyList(),
)

@Serializable
data class LetOp(
    @SerialName("n") val niveau: String,
    @SerialName("t") val tekst: String,
)

@Serializable
data class Stap(
    @SerialName("t") val tekst: String,
    @SerialName("s") val sub: List<String> = emptyList(),
)

@Serializable
data class Schema(
    val id: String,
    val brewer: String,
    val interval: String,
    val titel: String,
    val machines: List<String> = emptyList(),
    @SerialName("let") val letOp: String = "",
    val taken: List<Taak> = emptyList(),
)

@Serializable
data class Taak(
    @SerialName("t") val tekst: String,
    @SerialName("p") val procedure: String = "",
)

@Serializable
data class Part(
    @SerialName("m") val machine: String,
    @SerialName("s") val sectie: String,
    @SerialName("d") val tekening: String,
    @SerialName("p") val pos: String,
    @SerialName("n") val nummer: String,
    @SerialName("q") val aantal: String,
    @SerialName("v") val voorraad: String,
    @SerialName("t") val omschrijving: String,
) {
    /** Parts marked "n/a" in the spare parts book are not sold separately. */
    val leverbaar: Boolean get() = nummer.isNotEmpty()
}

@Serializable
data class SpecGroep(
    val groep: String,
    val brewer: String = "",
    val items: List<SpecItem> = emptyList(),
)

@Serializable
data class SpecItem(
    @SerialName("k") val kop: String,
    @SerialName("v") val waarde: String,
)

/**
 * A component from the technical manual: how it works, and the page it is
 * drawn on. The page render is what an engineer actually wants next to a
 * schematic description.
 */
@Serializable
data class Component(
    val nr: String,
    val titel: String,
    val tekst: String,
    val pagina: Int = 0,
    @SerialName("pagina_tot") val paginaTot: Int = 0,
    val afb: String = "",
    val bron: String = "",
) {
    /** "4.1.2" -> "4.1": the group this component belongs to. */
    val groep: String get() = nr.substringBeforeLast('.', nr)
}

/** One entry of the service menu, as the technical manual documents it. */
@Serializable
data class MenuItem(
    val nr: String,
    val titel: String,
    val tekst: String,
    val pad: String = "",
    val niveau: String = "",
    val pagina: Int = 0,
    val afb: String = "",
    val afbs: List<String> = emptyList(),
    val bron: String = "",
    val doel: String = "",
    val nodig: List<String> = emptyList(),
    val interval: String = "",
    val stappen: List<String> = emptyList(),
) {
    /** Chapter 6 is the menu itself, 7 the step-by-step jobs. */
    val hoofdstuk: String get() = nr.substringBefore('.')
}
