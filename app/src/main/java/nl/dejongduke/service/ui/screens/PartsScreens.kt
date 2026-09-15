package nl.dejongduke.service.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.R
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.Part
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.count
import nl.dejongduke.service.ui.copyToClipboard
import nl.dejongduke.service.ui.ChipRow
import nl.dejongduke.service.ui.EmptyState
import nl.dejongduke.service.ui.PartNumber
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader

@Composable
fun PartsScreen(
    catalog: Catalog,
    filter: String?,
    variant: String?,
    onFilter: (String?) -> Unit,
    onVariant: (String?) -> Unit,
    onOpen: (Route) -> Unit,
) {
    var term by rememberSaveable { mutableStateOf("") }
    val documented = catalog.machinesWithParts
    // Without a machine chosen the section list would mix five books together,
    // so default to the first documented machine instead of showing everything.
    val machine = filter?.takeIf { id -> documented.any { it.id == id } } ?: documented.firstOrNull()?.id

    // Every brewer and cabinet size has its own parts book. Picking one first
    // is what the paper books force you to do as well, and it keeps a Small
    // cabinet from showing Medium part numbers.
    val builds = remember(catalog, machine) {
        catalog.parts.asSequence().filter { it.machine == machine }
            .map { it.variant }.filter { it.isNotEmpty() }.distinct().sorted().toList()
    }
    val selectedBuild = variant?.takeIf { builds.contains(it) } ?: builds.firstOrNull()
    val forMachine = remember(catalog, machine, selectedBuild) {
        catalog.parts.filter { it.machine == machine && it.variant == selectedBuild }
    }
    val searching = term.trim().length >= 2
    val hits = remember(catalog, machine, selectedBuild, term) {
        catalog.searchParts(machine, term, variant = selectedBuild)
    }
    val sections = remember(catalog, machine, selectedBuild) { forMachine.groupBy { it.section }.toSortedMap() }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ChipRow(
                options = documented.map { it.id as String? to it.name },
                selected = machine,
                onSelect = { onFilter(it) },
            )
        }
        if (builds.size > 1) {
            item {
                ChipRow(
                    options = builds.map { code ->
                        code as String? to (catalog.variants(machine ?: "")
                            .firstOrNull { it.code == code }
                            ?.let { "${it.brewer} ${it.cabinet}".trim() } ?: code)
                    },
                    selected = selectedBuild,
                    onSelect = onVariant,
                )
            }
        }
        item {
            OutlinedTextField(
                value = term,
                onValueChange = { term = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.number_or_description)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (term.isNotEmpty()) {
                        IconButton(onClick = { term = "" }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) }
                    }
                },
                singleLine = true,
            )
        }

        if (machine == null) {
            item { EmptyState(stringResource(R.string.no_spare_parts_book_2), stringResource(R.string.there_is_no_parts_list_for_this_machine_in_t)) }
            return@LazyColumn
        }

        if (searching) {
            item { SectionHeader(stringResource(R.string.found), "${hits.size}") }
            if (hits.isEmpty()) {
                item { EmptyState(stringResource(R.string.nothing_found), stringResource(R.string.try_part_of_the_number_or_an_english_term_su)) }
            }
            items(hits.size) { index -> PartRow(hits[index], showSection = true) }
            item { Spacer(Modifier.height(24.dp)) }
            return@LazyColumn
        }

        item { SectionHeader(stringResource(R.string.drawings), "${sections.size}") }
        items(sections.keys.toList()) { section ->
            val count = sections[section]?.size ?: 0
            val name = catalog.drawingName(machine, selectedBuild.orEmpty(), section)
            Card(onClick = { onOpen(Route.PartSection(machine, selectedBuild.orEmpty(), section)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (name.isEmpty()) section else "$section  $name",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            count(R.plurals.n_parts, count),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun PartSectionDetail(catalog: Catalog, machine: String, variant: String, section: String) {
    val parts = remember(catalog, machine, variant, section) {
        catalog.parts.filter {
            it.machine == machine && it.section == section &&
                (variant.isEmpty() || it.variant == variant)
        }
    }
    val sheets = catalog.drawing(machine, variant, section)
    val name = catalog.drawingName(machine, variant, section)
    val balloons = remember(catalog, machine, variant, section) {
        catalog.balloons(machine, variant, section)
    }
    var selected by remember(machine, variant, section) { mutableStateOf<String?>(null) }

    /** Position as the balloons write it: "05" in the table is "5" on the drawing. */
    fun summary(p: String) = p.trimStart('0').lowercase().ifEmpty { "0" }


    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    if (name.isEmpty()) section else name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(catalog.machine(machine)?.name ?: machine)
                    if (variant.isNotEmpty()) Pill(variant)
                    Pill(stringResource(R.string.dwg, section))
                    Pill(count(R.plurals.n_parts, parts.size))
                    if (balloons.isNotEmpty()) Pill(stringResource(R.string.clickable, balloons.size))
                }
            }
        }
        if (sheets.isNotEmpty()) {
            item {
                Column {
                    DrawingView(
                        path = sheets.first(),
                        balloons = balloons,
                        selected = selected,
                        // No scrolling: the answer appears under the drawing,
                        // so the picture you are reading stays in view.
                        onSelect = { pos -> selected = if (selected == pos) null else pos },
                    )
                    val selectedParts = parts.filter { selected != null && summary(it.pos) == selected }
                    if (selectedParts.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        selectedParts.forEach { part ->
                            PartRow(part, showSection = false, active = true)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (balloons.isEmpty())
                            stringResource(R.string.pinch_to_zoom_the_numbers_in_the_drawing_are)
                        else
                            stringResource(R.string.tap_a_number_in_the_drawing_or_tap_a_part_in),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            // A big assembly runs over more than one sheet. Each extra sheet is
            // its own row so the list can let go of the ones off screen; the
            // balloons were read from the first sheet only.
            if (sheets.size > 1) {
                items(sheets.drop(1), key = { it }) { sheet ->
                    Column {
                        DrawingView(
                            path = sheet,
                            balloons = emptyList(),
                            selected = null,
                            onSelect = {},
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        items(parts.size) { index ->
            val part = parts[index]
            PartRow(
                part = part,
                showSection = false,
                active = selected != null && summary(part.pos) == selected,
                onClick = if (balloons.any { it.pos == summary(part.pos) }) {
                    { selected = summary(part.pos) }
                } else null,
            )
        }
        item {
            Text(
                stringResource(R.string.se_van_stock_sw_warehouse_stock_pos_refers_t),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            )
        }
    }
}

@Composable
private fun PartRow(
    part: Part,
    showSection: Boolean,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    // Holding a row copies the number: with a glove on, that beats opening the
    // part and hunting for a copy button.
    Card(
        onClick = onClick,
        highlight = active,
        onLongClick = if (part.available) {
            { copyToClipboard(context, part.number) }
        } else null,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (part.available) PartNumber(part.number) else Pill(stringResource(R.string.not_sold_separately))
                Spacer(Modifier.width(8.dp))
                if (part.stock.isNotEmpty()) {
                    Pill(
                        part.stock,
                        tone = if (part.stock == "SE") MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (part.pos.isNotEmpty()) {
                    Text(
                        stringResource(R.string.pos_2, part.pos),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(part.description, style = MaterialTheme.typography.bodyLarge)
            if (showSection || part.quantity.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        if (showSection) append(part.section)
                        if (part.quantity.isNotEmpty()) {
                            if (isNotEmpty()) append("  ·  ")
                            append("${part.quantity}× per machine")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
