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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Badge

@Composable
fun MachinesScreen(catalog: Catalog, onOpen: (Route) -> Unit) {
    val current = catalog.machines.filter { it.active }
    val older = catalog.machines.filterNot { it.active }

    LazyColumn(Modifier.fillMaxWidth()) {
        // Picking your machine out of eleven is the slow way round when the
        // answer is screwed to the inside of the door.
        item {
            Card(onClick = { onOpen(Route.PlateScan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Badge, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(24.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.which_machine_is_in_front_of_you),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.read_the_type_plate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SectionHeader(stringResource(R.string.technical)) }
        item {
            Card(onClick = { onOpen(Route.Components) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.how_the_machine_works), style = MaterialTheme.typography.titleMedium)
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
        item { SectionHeader(stringResource(R.string.current_range), "${current.size}") }
        items(current, key = { it.id }) { machine -> MachineCard(catalog, machine, onOpen) }
        item { SectionHeader(stringResource(R.string.older_phasing_out), "${older.size}") }
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
                            tone = if (machine.serviceMenu == "new") MaterialTheme.colorScheme.primary else null,
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
    // Once the type plate has said which build is standing there, everything
    // on this screen is about that build — not about the machine line.
    val faults = catalog.forBuild(
        catalog.faults.filter { machine.id in it.machines }, variant,
    ) { catalog.forVariant(it.codes, variant) }.size
    val procs = catalog.forBuild(
        catalog.procedures.filter { machine.id in it.machines && it.steps.isNotEmpty() }, variant,
    ) { catalog.forVariant(it.codes, variant) }.size
    val parts = catalog.partCount(machine.id, variant)

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
                    if (machine.brewer.isNotEmpty()) Pill(stringResource(R.string.brewer_2, machine.brewer))
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
            item { SectionHeader(stringResource(R.string.service_menu)) }
            item {
                Card {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill(
                                serviceMenuLabel(machine.serviceMenu),
                                tone = if (machine.serviceMenu == "new") MaterialTheme.colorScheme.primary else null,
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


        item { SectionHeader(stringResource(R.string.in_the_app)) }
        item {
            Column {
                JumpRow(stringResource(R.string.faults), if (faults > 0) count(R.plurals.n_messages, faults) else stringResource(R.string.nothing_recorded_yet), faults > 0) {
                    onJump(Tab.Faults, machine.id)
                }
                val cards = catalog.cardsFor(machine.id, variant).size
                JumpRow(stringResource(R.string.maintenance_card), if (cards > 0) count(R.plurals.n_cards_from_manufacturer, cards) else stringResource(R.string.no_card_2), cards > 0) {
                    onOpen(Route.Cards)
                }
                JumpRow(stringResource(R.string.maintenance_and_procedures), if (procs > 0) count(R.plurals.n_procedures, procs) else stringResource(R.string.nothing_recorded_yet), procs > 0) {
                    onJump(Tab.Maintenance, machine.id)
                }
                JumpRow(stringResource(R.string.parts), if (parts > 0) count(R.plurals.n_rows_from_parts_book, parts) else stringResource(R.string.no_spare_parts_book), parts > 0) {
                    onJump(Tab.Parts, machine.id)
                }
            }
        }

        // Six full-page pictures would push everything useful below the fold,
        // so an aanzicht opens when it is asked for.
        val views = catalog.viewsFor(machine.id)
        if (views.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.views), "${views.size}") }
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
                                count(R.plurals.n_callouts, view.callouts.size),
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
            item { SectionHeader(stringResource(R.string.dimensions_and_connections)) }
            if (machine.specsSource.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.source_2, machine.specsSource),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                }
            }
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
                SectionHeader(stringResource(R.string.builds), "${machine.variants.size}")
            }
            item {
                Text(
                    stringResource(R.string.tap_the_build_in_front_of_you_the_type_code),
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
            item { SectionHeader(stringResource(R.string.details)) }
            item {
                val properties = buildList {
                    if (machine.cabinet.isNotEmpty()) add(R.string.cabinet to machine.cabinet)
                    if (machine.screen.isNotEmpty()) add(R.string.screen to machine.screen)
                    if (machine.brewer.isNotEmpty()) add(R.string.brewer to machine.brewer)
                    if (machine.typeCode.isNotEmpty()) add(R.string.type_code to machine.typeCode)
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
            item { SectionHeader(stringResource(R.string.where_this_comes_from)) }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                    machine.docs.forEach { doc ->
                        Text(stringResource(R.string.text_3, doc), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.my_note)) }
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
                    placeholder = { Text(stringResource(R.string.serial_number_location_what_you_replaced)) },
                    minLines = 3,
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.stays_on_this_phone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        item { SectionHeader(stringResource(R.string.general)) }
        item {
            Card(onClick = { onOpen(Route.Specs) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.technical_data), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
                Text(stringResource(R.string.technical_data), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.values_from_the_virtu_and_lua_user_manuals_c),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to stringResource(R.string.all_machines)) +
                    documented.map { it.id as String? to it.name },
                selected = filter,
                onSelect = onFilter,
            )
        }
        item { Spacer(Modifier.height(4.dp)) }

        if (shown.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.no_technical_section),
                    stringResource(R.string.the_technical_manual_for_this_machine_is_not),
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
                Text(stringResource(R.string.where_does_this_come_from), style = MaterialTheme.typography.headlineSmall,
                     fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.everything_in_this_app_is_taken_from_de_jong),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        item { SectionHeader(stringResource(R.string.how_it_was_put_together)) }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(
                    stringResource(R.string.the_machine_behind_the_door_follows_the_mode),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.dutch_messages_come_from_the_dutch_manual_th),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.independent_tool_made_privately_not_publishe),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.working_on_these_machines_means_hot_water_st),
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
    "old" -> stringResource(R.string.old_service_menu)
    "new" -> stringResource(R.string.new_service_menu)
    "both" -> stringResource(R.string.old_or_new_service_menu)
    else -> stringResource(R.string.service_menu_unknown)
}
