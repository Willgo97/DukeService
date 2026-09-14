package nl.dejongduke.service.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
    val current = catalog.machines.filter { it.active }
    val older = catalog.machines.filterNot { it.active }

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
    val procs = catalog.procedures.count { machine.id in it.machines && it.steps.isNotEmpty() }
    val parts = catalog.partCount[machine.id] ?: 0

    Card(onClick = { onOpen(Route.Machine(machine.id)) }) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                if (machine.photo.isNotEmpty()) {
                    MachineThumb(machine.photo)
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            machine.name,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        machine.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                    if (machine.serviceMenu.isNotEmpty()) {
                        Pill(
                            serviceMenuLabel(machine.serviceMenu),
                            tone = if (machine.serviceMenu == "nieuw") MaterialTheme.colorScheme.primary else null,
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
    variant: String?,
    onUseMachine: (String, String?) -> Unit,
    note: String,
    onNote: (String, String) -> Unit,
    onOpen: (Route) -> Unit,
    onJump: (Tab, String) -> Unit,
) {
    val faults = catalog.faults.count { machine.id in it.machines }
    val procs = catalog.procedures.count { machine.id in it.machines && it.steps.isNotEmpty() }
    val parts = catalog.partCount[machine.id] ?: 0

    LazyColumn(Modifier.fillMaxWidth()) {
        if (machine.photo.isNotEmpty()) {
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
                    AssetPhoto(machine.photo, Modifier.fillMaxSize())
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(machine.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                if (machine.series.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        machine.series,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (machine.typeCode.isNotEmpty()) Pill(machine.typeCode)
                    if (machine.brewer.isNotEmpty()) Pill("${machine.brewer}-brewer")
                }
            }
        }

        if (machine.description.isNotEmpty()) {
            item {
                Text(
                    machine.description,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }

        if (machine.serviceMenu.isNotEmpty()) {
            item { SectionHeader("Servicemenu") }
            item {
                Card {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill(
                                serviceMenuLabel(machine.serviceMenu),
                                tone = if (machine.serviceMenu == "nieuw") MaterialTheme.colorScheme.primary else null,
                            )
                        }
                        if (machine.serviceMenuNote.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                machine.serviceMenuNote,
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
                    onJump(Tab.Faults, machine.id)
                }
                val cards = catalog.cardsFor(machine.id).size
                JumpRow("Onderhoudskaart", if (cards > 0) "$cards kaarten van de fabrikant" else "geen kaart", cards > 0) {
                    onOpen(Route.Cards)
                }
                val books = catalog.booksFor(machine.id).size
                JumpRow("Handleidingen", if (books > 0) "$books boeken" else "geen boeken", books > 0) {
                    onOpen(Route.Books)
                }
                JumpRow("Onderhoud en procedures", if (procs > 0) "$procs procedures" else "nog niets vastgelegd", procs > 0) {
                    onJump(Tab.Maintenance, machine.id)
                }
                JumpRow("Onderdelen", if (parts > 0) "$parts regels uit het onderdelenboek" else "geen onderdelenboek", parts > 0) {
                    onJump(Tab.Parts, machine.id)
                }
            }
        }

        // Six full-page pictures would push everything useful below the fold,
        // so an aanzicht opens when it is asked for.
        val views = catalog.viewsFor(machine.id)
        if (views.isNotEmpty()) {
            item { SectionHeader("Aanzichten", "${views.size}") }
            items(views, key = { it.id }) { view ->
                var open by rememberSaveable(view.id) { mutableStateOf(false) }
                Card(onClick = { open = !open }) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                view.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!open && view.callouts.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${view.callouts.size} verwijzingen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (open) {
                            view.images.forEach { image ->
                                Spacer(Modifier.height(8.dp))
                                AssetImage(image)
                            }
                            if (view.callouts.isNotEmpty()) Spacer(Modifier.height(8.dp))
                            view.callouts.forEach { callout ->
                                Text(callout, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }

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
                    machine.specs.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(vertical = 1.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                row.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1.2f),
                            )
                            // Printing the same value twice reads as a mistake.
                            if (row.small == row.medium) {
                                Text(
                                    row.small.ifEmpty { "—" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(2f),
                                )
                            } else {
                                Text(row.small.ifEmpty { "—" },
                                     style = MaterialTheme.typography.bodyMedium,
                                     modifier = Modifier.weight(1f))
                                Text(row.medium.ifEmpty { "—" },
                                     style = MaterialTheme.typography.bodyMedium,
                                     modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        if (machine.variants.isNotEmpty()) {
            item {
                SectionHeader("Uitvoeringen", "${machine.variants.size}")
            }
            item {
                Text(
                    "Tik de uitvoering aan die voor je staat — de typecode staat op het " +
                        "typeplaatje. De lijsten in de app gaan dan alleen nog daarover.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            item {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    machine.variants.forEach { u ->
                        val active = u.code == variant
                        Column(
                            Modifier.fillMaxWidth()
                                .padding(vertical = 1.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.surfaceContainerHighest
                                    else MaterialTheme.colorScheme.surfaceContainer
                                )
                                // Machine and build in one call: setting the
                                // machine re-reads the build it remembers, so
                                // the two cannot be set one after the other.
                                .clickable { onUseMachine(machine.id, if (active) null else u.code) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    u.code,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.width(88.dp),
                                )
                                Text(
                                    listOfNotNull(
                                        u.brewer.ifEmpty { null },
                                        u.cabinet.ifEmpty { null },
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

        val properties = buildList {
            if (machine.cabinet.isNotEmpty()) add("Kast" to machine.cabinet)
            if (machine.screen.isNotEmpty()) add("Scherm" to machine.screen)
            if (machine.brewer.isNotEmpty()) add("Brewer" to machine.brewer)
            if (machine.typeCode.isNotEmpty()) add("Typecode" to machine.typeCode)
        }
        if (properties.isNotEmpty()) {
            item { SectionHeader("Kenmerken") }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    properties.forEach { (key, value) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(
                                key,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(96.dp),
                            )
                            Text(value, style = MaterialTheme.typography.bodyMedium)
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
            var text by remember(machine.id) { mutableStateOf(note) }
            Column(Modifier.padding(horizontal = 12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
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
private fun JumpRow(title: String, sub: String, enabled: Boolean, onClick: () -> Unit) {
    Card(onClick = if (enabled) onClick else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
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
        catalog.specs.forEach { group ->
            item { SectionHeader(group.group) }
            item {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    group.items.forEach { item ->
                        Column(
                            Modifier.fillMaxWidth()
                                .padding(vertical = 1.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                item.key,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(item.value, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
fun SourcesScreen(catalog: Catalog, onOpen: (Route) -> Unit) {
    val perKind = catalog.books.groupingBy { it.kindName }.eachCount()
        .toList().sortedByDescending { it.second }
    val languages = catalog.books.map { it.language }.filter { it.isNotEmpty() }.distinct()

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Waar komt dit vandaan?", style = MaterialTheme.typography.headlineSmall,
                     fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Alles in deze app is overgenomen uit de servicedocumentatie van De Jong " +
                        "DUKE: ${catalog.books.size} boeken in ${languages.size} talen. De " +
                        "handleidingen zelf zitten er niet in — die zijn van de fabrikant.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        item { SectionHeader("De boeken", "${catalog.books.size}") }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                perKind.forEach { (kind, count) ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text("$count × $kind", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        item {
            Card(onClick = { onOpen(Route.Books) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Alle handleidingen", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Per machine en per soort, met versie en documentnummer",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null,
                         tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SectionHeader("Hoe het is samengevoegd") }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(
                    "De techniek achter de deur hangt aan de modelcode, niet aan het merk: een " +
                        "Avy CND en een Zia CND zijn dezelfde machine in een andere kast. Tekst " +
                        "uit een technische handleiding geldt daarom voor elk merk met dezelfde " +
                        "code; bij elke melding, elk component en elke procedure staat voor welke " +
                        "machines dat is. Wat wél over de kast of het scherm gaat — de " +
                        "aanzichten, de onderhoudskaarten, de tekeningen — blijft bij zijn " +
                        "eigen merk.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Nederlandse schermmeldingen komen uit de Nederlandstalige handleiding; de " +
                        "Engelse tekst staat erbij omdat een machine ook op Engels kan staan. " +
                        "Waar alleen een Engels boek bestaat, staat er een taallabel bij.",
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

fun serviceMenuLabel(kind: String) = when (kind) {
    "oud" -> "oud servicemenu"
    "nieuw" -> "nieuw servicemenu"
    "beide" -> "oud of nieuw servicemenu"
    else -> "servicemenu onbekend"
}
