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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.R
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.Machine
import nl.dejongduke.service.ui.AssetPhoto
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.count
import nl.dejongduke.service.ui.EmptyState
import nl.dejongduke.service.ui.ChipRow
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader
import nl.dejongduke.service.ui.Tab

@Composable
fun MachinesScreen(catalog: Catalog, onOpen: (Route) -> Unit) {
    val current = catalog.machines.filter { it.active }
    val older = catalog.machines.filterNot { it.active }

    LazyColumn(Modifier.fillMaxWidth()) {
        item { SectionHeader(stringResource(R.string.techniek)) }
        item {
            Card(onClick = { onOpen(Route.Components) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.hoe_de_machine_werkt), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            count(R.plurals.n_components_with_diagrams, catalog.components.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SectionHeader(stringResource(R.string.huidig_assortiment), "${current.size}") }
        items(current, key = { it.id }) { machine -> MachineCard(catalog, machine, onOpen) }
        item { SectionHeader(stringResource(R.string.ouder_uitlopend), "${older.size}") }
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
                    if (faults > 0) Pill(count(R.plurals.n_faults, faults))
                    if (procs > 0) Pill(count(R.plurals.n_procedures, procs))
                    if (parts > 0) Pill(count(R.plurals.n_parts, parts))
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
                    if (machine.brewer.isNotEmpty()) Pill(stringResource(R.string.x_brewer, machine.brewer))
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
            item { SectionHeader(stringResource(R.string.servicemenu)) }
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


        item { SectionHeader(stringResource(R.string.in_de_app)) }
        item {
            Column {
                JumpRow(stringResource(R.string.storingen), if (faults > 0) count(R.plurals.n_messages, faults) else stringResource(R.string.nog_niets_vastgelegd), faults > 0) {
                    onJump(Tab.Faults, machine.id)
                }
                val cards = catalog.cardsFor(machine.id).size
                JumpRow(stringResource(R.string.onderhoudskaart), if (cards > 0) count(R.plurals.n_cards_from_manufacturer, cards) else stringResource(R.string.geen_kaart_2), cards > 0) {
                    onOpen(Route.Cards)
                }
                JumpRow(stringResource(R.string.onderhoud_en_procedures), if (procs > 0) count(R.plurals.n_procedures, procs) else stringResource(R.string.nog_niets_vastgelegd), procs > 0) {
                    onJump(Tab.Maintenance, machine.id)
                }
                JumpRow(stringResource(R.string.onderdelen), if (parts > 0) count(R.plurals.n_rows_from_parts_book, parts) else stringResource(R.string.geen_onderdelenboek), parts > 0) {
                    onJump(Tab.Parts, machine.id)
                }
            }
        }

        // Six full-page pictures would push everything useful below the fold,
        // so an aanzicht opens when it is asked for.
        val views = catalog.viewsFor(machine.id)
        if (views.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.aanzichten), "${views.size}") }
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
            item { SectionHeader(stringResource(R.string.afmetingen_en_aansluiting)) }
            item {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Spacer(Modifier.weight(1.2f))
                        Text(
                            stringResource(R.string.small),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.medium),
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
                SectionHeader(stringResource(R.string.uitvoeringen), "${machine.variants.size}")
            }
            item {
                Text(
                    stringResource(R.string.tik_de_uitvoering_aan_die_voor_je_staat_de_t),
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

        // Which of the four is filled in differs per machine, so the list is
        // built where it is drawn: only there can it read the labels.
        val hasProperties = listOf(machine.cabinet, machine.screen, machine.brewer,
                                   machine.typeCode).any { it.isNotEmpty() }
        if (hasProperties) {
            item { SectionHeader(stringResource(R.string.kenmerken)) }
            item {
                val properties = buildList {
                    if (machine.cabinet.isNotEmpty()) add(R.string.kast to machine.cabinet)
                    if (machine.screen.isNotEmpty()) add(R.string.scherm to machine.screen)
                    if (machine.brewer.isNotEmpty()) add(R.string.brewer to machine.brewer)
                    if (machine.typeCode.isNotEmpty()) add(R.string.typecode to machine.typeCode)
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    properties.forEach { (label, value) ->
                        val key = stringResource(label)
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
            item { SectionHeader(stringResource(R.string.bron_van_deze_gegevens)) }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                    machine.docs.forEach { doc ->
                        Text(stringResource(R.string.x_3, doc), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.mijn_notitie)) }
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
                    placeholder = { Text(stringResource(R.string.serienummer_locatie_wat_je_hebt_vervangen)) },
                    minLines = 3,
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.blijft_op_deze_telefoon_staan),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        item { SectionHeader(stringResource(R.string.algemeen)) }
        item {
            Card(onClick = { onOpen(Route.Specs) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.technische_gegevens), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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

/** "1.", "12." — a call-out number rather than the name of a value. */
private val NUMBER = Regex("\\d{1,2}\\.")

@Composable
fun SpecsScreen(
    catalog: Catalog,
    filter: String?,
    variant: String?,
    onFilter: (String?) -> Unit,
) {
    val documented = remember(catalog) {
        catalog.machines.filter { m -> catalog.specs.any { m.id in it.machines } }
    }
    // Fifty tables, of which at most a handful belong to the machine in front
    // of you; without this the screen is a scroll through other people's
    // machines.
    val shown = remember(catalog, filter, variant) {
        catalog.specs.filter {
            (filter == null || filter in it.machines) && catalog.forVariant(it.codes, variant)
        }
    }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.technische_gegevens), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.waarden_uit_de_gebruikershandleidingen_van_v),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to stringResource(R.string.alle_machines)) +
                    documented.map { it.id as String? to it.name },
                selected = filter,
                onSelect = onFilter,
            )
        }
        item { Spacer(Modifier.height(4.dp)) }

        if (shown.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.geen_techniek),
                    stringResource(R.string.voor_deze_machine_staat_de_technische_handle),
                )
            }
        }

        shown.forEach { group ->
            item { SectionHeader(group.group) }
            item {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    group.items.forEach { item ->
                        when {
                            // A line that runs across both columns of the
                            // printed table: a heading, or a sentence about the
                            // whole table. Neither is a value, so neither gets
                            // the two-line treatment.
                            item.value.isBlank() -> Text(
                                item.key,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 2.dp, top = 14.dp, bottom = 4.dp),
                            )
                            item.key.isBlank() -> Text(
                                item.value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
                            )
                            // A numbered call-out beside a picture: one line,
                            // the way it is printed under the drawing.
                            NUMBER.matches(item.key) -> Row(
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    item.key,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(34.dp),
                                )
                                Text(item.value, style = MaterialTheme.typography.bodyLarge)
                            }
                            else -> Column(
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
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
fun SourcesScreen(catalog: Catalog, onOpen: (Route) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.waar_komt_dit_vandaan), style = MaterialTheme.typography.headlineSmall,
                     fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.alles_in_deze_app_is_overgenomen_uit_de_serv),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        item { SectionHeader(stringResource(R.string.hoe_het_is_samengevoegd)) }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(
                    stringResource(R.string.de_techniek_achter_de_deur_hangt_aan_de_mode),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.nederlandse_schermmeldingen_komen_uit_de_ned),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.onafhankelijk_hulpmiddel_prive_gemaakt_geen_),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.werken_aan_deze_machines_is_werken_met_heet_),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
fun serviceMenuLabel(kind: String) = when (kind) {
    "oud" -> stringResource(R.string.oud_servicemenu)
    "nieuw" -> stringResource(R.string.nieuw_servicemenu)
    "beide" -> stringResource(R.string.oud_of_nieuw_servicemenu)
    else -> stringResource(R.string.servicemenu_onbekend)
}
