package nl.dejongduke.service.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.Schema
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.ChipRow
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("nl"))

@Composable
fun MaintenanceScreen(
    catalog: Catalog,
    filter: String?,
    onFilter: (String?) -> Unit,
    ticks: Map<String, Set<Int>>,
    today: LocalDate,
    onOpen: (Route) -> Unit,
) {
    val documented = catalog.machines.filter { m -> catalog.schemas.any { m.id in it.machines } }
    val shown = catalog.schemas.filter { filter == null || filter in it.machines }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to "Alle machines") +
                    documented.map { it.id as String? to it.naam },
                selected = filter,
                onSelect = onFilter,
            )
        }
        item {
            Text(
                dayFormat.format(today).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        shown.groupBy { it.brewer }.forEach { (brewer, schemas) ->
            item { SectionHeader("$brewer-brewer", catalog.machineNames(schemas.first().machines)) }
            items(schemas, key = { it.id }) { schema -> SchemaCard(catalog, schema, ticks, onOpen) }
        }
        item { SectionHeader("Losse procedures") }
        item {
            Card(onClick = { onOpen(Route.Procedures) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Alle procedures", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${catalog.procedures.count { it.stappen.isNotEmpty() }} stap-voor-stap instructies",
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
private fun SchemaCard(
    catalog: Catalog,
    schema: Schema,
    ticks: Map<String, Set<Int>>,
    onOpen: (Route) -> Unit,
) {
    val done = ticks[schema.id].orEmpty().size
    val total = schema.taken.size
    val progress by animateFloatAsState(
        if (total == 0) 0f else done.toFloat() / total,
        label = "voortgang",
    )
    Card(onClick = { onOpen(Route.Schema(schema.id)) }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(schema.titel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "$done/$total",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (done == total && total > 0) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = if (done == total && total > 0) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
fun SchemaDetail(
    catalog: Catalog,
    schema: Schema,
    ticked: Set<Int>,
    onToggle: (Int) -> Unit,
    onReset: () -> Unit,
    onOpen: (Route) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(schema.titel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(schema.brewer)
                    Pill(catalog.machineNames(schema.machines))
                }
            }
        }
        if (schema.letOp.isNotEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(12.dp),
                ) {
                    Text(
                        schema.letOp,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${ticked.size} van ${schema.taken.size} afgevinkt",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
                TextButton(onClick = onReset, enabled = ticked.isNotEmpty()) { Text("Opnieuw") }
            }
        }

        items(schema.taken.size) { index ->
            val taak = schema.taken[index]
            val checked = index in ticked
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onToggle(index) }
                    .padding(start = 8.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = { onToggle(index) })
                Text(
                    taak.tekst,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                )
                if (taak.procedure.isNotEmpty()) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                            .clickable { onOpen(Route.Procedure(taak.procedure)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.ChevronRight,
                            "Open procedure",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Spacer(Modifier.width(40.dp))
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}
