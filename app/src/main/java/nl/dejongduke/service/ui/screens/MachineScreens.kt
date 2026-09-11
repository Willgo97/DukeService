package nl.dejongduke.service.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.Machine
import nl.dejongduke.service.ui.AssetPhoto
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader
import nl.dejongduke.service.ui.Tab

@Composable
fun MachinesScreen(catalog: Catalog, onOpen: (Route) -> Unit) {
    val current = catalog.machines.filter { it.actief }
    val older = catalog.machines.filterNot { it.actief }

    LazyColumn(Modifier.fillMaxWidth()) {
        item { SectionHeader("Techniek") }
        item {
            Card(onClick = { onOpen(Route.Components) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hoe de machine werkt", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${catalog.components.size} onderdelen met schema's uit de technische handleiding",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SectionHeader("Huidig assortiment", "${current.size}") }
        items(current, key = { it.id }) { machine -> MachineCard(catalog, machine, onOpen) }
        item { SectionHeader("Ouder / uitlopend", "${older.size}") }
        items(older, key = { it.id }) { machine -> MachineCard(catalog, machine, onOpen) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MachineCard(catalog: Catalog, machine: Machine, onOpen: (Route) -> Unit) {
    val faults = catalog.faults.count { machine.id in it.machines }
    val procs = catalog.procedures.count { machine.id in it.machines && it.stappen.isNotEmpty() }
    val parts = catalog.parts.count { it.machine == machine.id }

    Card(onClick = { onOpen(Route.Machine(machine.id)) }) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                if (machine.foto.isNotEmpty()) {
                    MachineThumb(machine.foto)
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            machine.naam,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        machine.kort,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                    if (machine.servicemenu.isNotEmpty()) {
                        Pill(
                            servicemenuLabel(machine.servicemenu),
                            tone = if (machine.servicemenu == "nieuw") MaterialTheme.colorScheme.primary else null,
                        )
                    }
                    if (faults > 0) Pill("$faults storingen")
                    if (procs > 0) Pill("$procs procedures")
                    if (parts > 0) Pill("$parts onderdelen")
                    }
                }
            }
        }
    }
}

/** Small machine photo for a list row. */
@Composable
private fun MachineThumb(path: String) {
    Box(
        Modifier
            .width(78.dp)
            .height(104.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        AssetPhoto(path, Modifier.fillMaxSize())
    }
}

@Composable
fun MachineDetail(
    catalog: Catalog,
    machine: Machine,
    notitie: String,
    onNote: (String, String) -> Unit,
    onOpen: (Route) -> Unit,
    onJump: (Tab, String) -> Unit,
) {
    val faults = catalog.faults.count { machine.id in it.machines }
    val procs = catalog.procedures.count { machine.id in it.machines && it.stappen.isNotEmpty() }
    val parts = catalog.parts.count { it.machine == machine.id }

    LazyColumn(Modifier.fillMaxWidth()) {
        if (machine.foto.isNotEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    AssetPhoto(machine.foto, Modifier.fillMaxSize())
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(machine.naam, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                if (machine.serie.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        machine.serie,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (machine.typecode.isNotEmpty()) Pill(machine.typecode)
                    if (machine.brewer.isNotEmpty()) Pill("${machine.brewer}-brewer")
                }
            }
        }

        if (machine.omschrijving.isNotEmpty()) {
            item {
                Text(
                    machine.omschrijving,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }

        if (machine.servicemenu.isNotEmpty()) {
            item { SectionHeader("Servicemenu") }
            item {
                Card {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill(
                                servicemenuLabel(machine.servicemenu),
                                tone = if (machine.servicemenu == "nieuw") MaterialTheme.colorScheme.primary else null,
                            )
                        }
                        if (machine.servicemenuUitleg.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                machine.servicemenuUitleg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item { SectionHeader("In de app") }
        item {
            Column {
                JumpRow("Storingen", if (faults > 0) "$faults meldingen" else "nog niets vastgelegd", faults > 0) {
                    onJump(Tab.Storingen, machine.id)
                }
                JumpRow("Onderhoud en procedures", if (procs > 0) "$procs procedures" else "nog niets vastgelegd", procs > 0) {
                    onJump(Tab.Onderhoud, machine.id)
                }
                JumpRow("Onderdelen", if (parts > 0) "$parts regels uit het onderdelenboek" else "geen onderdelenboek", parts > 0) {
                    onJump(Tab.Onderdelen, machine.id)
                }
            }
        }

        if (machine.specs.isNotEmpty()) {
            item { SectionHeader("Afmetingen en aansluiting") }
            item {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Spacer(Modifier.weight(1.2f))
                        Text(
                            "Small",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Medium",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    machine.specs.forEach { rij ->
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(vertical = 1.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                rij.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1.2f),
                            )
                            Text(rij.small.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(rij.medium.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (machine.uitvoeringen.isNotEmpty()) {
            item { SectionHeader("Uitvoeringen", "${machine.uitvoeringen.size}") }
            item {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    machine.uitvoeringen.forEach { u ->
                        Column(
                            Modifier.fillMaxWidth()
                                .padding(vertical = 1.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    u.code,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.width(88.dp),
                                )
                                Text(
                                    listOfNotNull(
                                        u.brewer.ifEmpty { null },
                                        u.kast.ifEmpty { null },
                                    ).joinToString("  ·  "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (u.doc.isNotEmpty()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    u.doc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            }
        }

        val eigenschappen = buildList {
            if (machine.kast.isNotEmpty()) add("Kast" to machine.kast)
            if (machine.scherm.isNotEmpty()) add("Scherm" to machine.scherm)
            if (machine.brewer.isNotEmpty()) add("Brewer" to machine.brewer)
            if (machine.typecode.isNotEmpty()) add("Typecode" to machine.typecode)
        }
        if (eigenschappen.isNotEmpty()) {
            item { SectionHeader("Kenmerken") }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    eigenschappen.forEach { (kop, waarde) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(
                                kop,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(96.dp),
                            )
                            Text(waarde, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        if (machine.docs.isNotEmpty()) {
            item { SectionHeader("Bron van deze gegevens") }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                    machine.docs.forEach { doc ->
                        Text("· $doc", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        item { SectionHeader("Mijn notitie") }
        item {
            var tekst by remember(machine.id) { mutableStateOf(notitie) }
            Column(Modifier.padding(horizontal = 12.dp)) {
                OutlinedTextField(
                    value = tekst,
                    onValueChange = {
                        tekst = it
                        onNote(machine.id, it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Serienummer, locatie, wat je hebt vervangen…") },
                    minLines = 3,
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Blijft op deze telefoon staan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        item { SectionHeader("Algemeen") }
        item {
            Card(onClick = { onOpen(Route.Specs) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Technische gegevens", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun JumpRow(titel: String, sub: String, enabled: Boolean, onClick: () -> Unit) {
    Card(onClick = if (enabled) onClick else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    titel,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (enabled) {
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SpecsScreen(catalog: Catalog) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Technische gegevens", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Waarden uit de gebruikershandleidingen van Virtu en Lua, omgerekend naar metrisch. Gelden in grote lijnen voor de hele CoEx-familie; het typeplaatje in de machine is altijd leidend.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        catalog.specs.forEach { groep ->
            item { SectionHeader(groep.groep) }
            item {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    groep.items.forEach { item ->
                        Column(
                            Modifier.fillMaxWidth()
                                .padding(vertical = 1.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                item.kop,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(item.waarde, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
fun BronnenScreen() {
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Waar komt dit vandaan?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Alles in deze app komt uit De Jong DUKE-documentatie: gebruikershandleidingen, " +
                        "technische handleidingen en onderdelenboeken. De handleidingen zelf zitten niet in de app; " +
                        "wat je hier ziet is eruit overgenomen voor gebruik op de werkvloer.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        item { SectionHeader("Gebruikt") }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                listOf(
                    "Technische handleiding Avy CoEx Medium — 5DTCET10M NL V1.0" to
                        "De Nederlandse brontekst voor de storingen, de componentengids en het servicemenu.",
                    "Technische handleidingen Avy (CND, XEA), Lua Instant, Nio CoEx XL en Rosa" to
                        "Aanvullende typecodes en uitvoeringen per machine.",
                    "Gebruikershandleidingen Virtu (5DUCEK20I), Lua (5DUXES20I), Avy en Rosa" to
                        "Onderhoudsschema's, checklists en de procedures stap voor stap.",
                    "Onderdelenboeken Virtu, Zia, Nio, Lua, Avy en Rosa" to
                        "Alle onderdeelnummers, posities, tekeningnummers en voorraadcodes.",
                    "Productbrochures van dejongduke.com" to
                        "Afmetingen, gewichten, aansluitwaarden en modelbeschrijvingen.",
                ).forEach { (titel, uitleg) ->
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(titel, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            uitleg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { SectionHeader("Let op") }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(
                    "De Nederlandse schermmeldingen komen uit de Nederlandstalige technische handleiding; " +
                        "de Engelse tekst staat erbij omdat machines ook op Engels kunnen staan. " +
                        "De storingen, de componentengids en het servicemenu zijn beschreven voor de Avy met CoEx-brewer " +
                        "en gelden in grote lijnen voor de hele CoEx-familie. " +
                        "Voor Lina, Blu, Nio Next, Edge en Vareo was geen handleiding te vinden; " +
                        "van die modellen staan alleen de brochuregegevens in de app.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Deze app is intern gereedschap, geen officiële uitgave van De Jong DUKE. " +
                        "Bij twijfel: de handleiding en het typeplaatje in de machine zijn leidend.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

fun servicemenuLabel(soort: String) = when (soort) {
    "oud" -> "oud servicemenu"
    "nieuw" -> "nieuw servicemenu"
    "beide" -> "oud of nieuw servicemenu"
    else -> "servicemenu onbekend"
}
