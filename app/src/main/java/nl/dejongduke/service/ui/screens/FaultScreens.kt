package nl.dejongduke.service.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.R
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.Fault
import nl.dejongduke.service.data.FaultGroup
import nl.dejongduke.service.data.SafetyNote
import nl.dejongduke.service.ui.ActionRow
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.count
import nl.dejongduke.service.ui.categoryLabel
import nl.dejongduke.service.ui.ChipRow
import nl.dejongduke.service.ui.EmptyState
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader
import nl.dejongduke.service.ui.WarnBanner

@Composable
fun FaultsScreen(
    catalog: Catalog,
    filter: String?,
    variant: String?,
    language: String,
    onFilter: (String?) -> Unit,
    onOpen: (Route) -> Unit,
) {
    var category by remember { mutableStateOf<String?>(null) }

    val documented = catalog.machines.filter { m -> catalog.faults.any { m.id in it.machines } }
    val forMachine = catalog.faultGroups.filter { filter == null || filter in it.machines }
    val byMachine = catalog.forBuild(forMachine, variant) { group ->
        group.variants.any { catalog.forVariant(it.codes, variant) }
    }
    val categories = byMachine.map { it.first.category }.distinct().sorted()
    // A category picked for one machine may not exist for the next one; leaving
    // it set would show an empty list with no chip to explain why.
    if (category != null && category !in categories) category = null
    val shown = byMachine.filter { category == null || it.first.category == category }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to stringResource(R.string.all_machines)) +
                    documented.map { it.id as String? to it.name },
                selected = filter,
                onSelect = onFilter,
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to stringResource(R.string.everything)) +
                    categories.map { it as String? to categoryLabel(it) },
                selected = category,
                onSelect = { category = it },
            )
        }
        item { SectionHeader(stringResource(R.string.screen_messages), "${shown.size}") }

        if (shown.isEmpty()) {
            val name = filter?.let { catalog.machine(it)?.name }
            item {
                EmptyState(
                    stringResource(R.string.no_messages),
                    if (name != null) stringResource(R.string.there_is_no_fault_list_for_the_in_the_app_ye, name)
                    else stringResource(R.string.there_is_no_fault_list_in_the_app_yet),
                )
            }
        }

        items(shown, key = { it.message }) { group ->
            FaultCard(catalog, group, showMachines = filter == null, language = language) {
                onOpen(Route.Fault(group.message))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun FaultCard(
    catalog: Catalog,
    group: FaultGroup,
    showMachines: Boolean,
    language: String = "nl",
    onClick: () -> Unit,
) {
    // Which line leads depends on what the machine in front of you displays.
    val key = if (language == "en") group.message else group.first.dutch
    val subtitle = if (language == "en") group.first.dutch else group.message
    Card(onClick = onClick) {
        Column {
            Row {
                Box(
                    Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape)
                        .background(
                            if (group.selfService) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.error
                        )
                )
                Spacer(Modifier.width(10.dp))
                Text(key, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            // The machine shows the message in one language and the manual
            // prints it in another; where the manufacturer never translated it
            // the two are the same sentence, and once is enough.
            if (!subtitle.equals(key, ignoreCase = true)) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 18.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.padding(start = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Pill(categoryLabel(group.first.category))
                if (showMachines) Pill(catalog.machineNames(group.machines))
                if (!group.selfService) Pill(stringResource(R.string.engineer), tone = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FaultDetail(
    catalog: Catalog,
    group: FaultGroup,
    pinned: Boolean,
    language: String,
    onPin: (String) -> Unit,
    onOpen: (Route) -> Unit,
) {
    val key = if (language == "en") group.message else group.first.dutch
    val subtitle = if (language == "en") group.first.dutch else group.message
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    stringResource(R.string.on_the_screen),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(key, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                if (!subtitle.equals(key, ignoreCase = true)) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Pill(categoryLabel(group.first.category))
                    Pill(catalog.machineNames(group.machines))
                    group.variants.flatMap { it.brewers }.distinct().forEach { Pill(it) }
                }
            }
        }

        item {
            ActionRow(
                pinKey = "fault:" + group.message,
                pinned = pinned,
                onPin = onPin,
                shareText = buildString {
                    appendLine(group.message)
                    appendLine(group.first.dutch)
                    if (group.first.cause.isNotEmpty()) appendLine(stringResource(R.string.cause) + group.first.cause)
                    group.first.solution.forEachIndexed { i, step -> appendLine("${i + 1}. $step") }
                    append(stringResource(R.string.duke_service_2))
                },
            )
        }

        group.variants.forEachIndexed { index, variant ->
            if (group.variants.size > 1) {
                item {
                    if (index > 0) {
                        HorizontalDivider(
                            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    SectionHeader(stringResource(R.string.for_label, catalog.machineNames(variant.machines)))
                }
            }
            faultBody(variant, catalog, onOpen)
        }
        item {
            val source = group.variants.mapNotNull { it.source.ifEmpty { null } }.distinct()
            if (source.isNotEmpty()) {
                Text(
                    stringResource(R.string.source) + source.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

/** The cause / steps / engineer-note block for one variant of a message. */
private fun androidx.compose.foundation.lazy.LazyListScope.faultBody(
    fault: Fault,
    catalog: Catalog,
    onOpen: (Route) -> Unit,
) {
    if (fault.cause.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.cause_2)) }
        item {
            Text(
                fault.cause,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }
    }

    if (fault.solution.isNotEmpty()) {
        item { SectionHeader(if (fault.selfService) stringResource(R.string.what_you_do) else stringResource(R.string.what_needs_to_happen)) }
        items(fault.solution.size) { index ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(fault.solution[index], style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    if (fault.engineerNote.isNotEmpty()) {
        item {
            Card {
                Column {
                    Text(
                        if (fault.selfService) stringResource(R.string.if_it_stays) else stringResource(R.string.service_message),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(fault.engineerNote, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (fault.note.isNotEmpty()) {
        item {
            WarnBanner(
                SafetyNote("let op", fault.note),
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }

    val linked = fault.procedures.mapNotNull { catalog.procedure(it) }
    if (linked.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.related_procedures)) }
        items(linked, key = { fault.message + it.id }) { procedures ->
            Card(onClick = { onOpen(Route.Procedure(procedures.id)) }) {
                Column {
                    Text(procedures.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        count(R.plurals.n_steps, procedures.steps.size) +
                            if (procedures.brewer.isNotEmpty()) stringResource(R.string.text_2, procedures.brewer) else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
