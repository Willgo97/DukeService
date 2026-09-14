package nl.dejongduke.service.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.R
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.Procedure
import nl.dejongduke.service.ui.ActionRow
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.languageName
import nl.dejongduke.service.ui.appLanguage
import nl.dejongduke.service.ui.count
import nl.dejongduke.service.ui.ChipRow
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader
import nl.dejongduke.service.ui.WarnBanner

private val intervalOrder = listOf("dag", "week", "maand", "halfjaar", "nodig")

@Composable
private fun intervalLabel(key: String) = when (key) {
    "dag" -> stringResource(R.string.dagelijks)
    "week" -> stringResource(R.string.wekelijks)
    "maand" -> stringResource(R.string.maandelijks)
    "halfjaar" -> stringResource(R.string.halfjaarlijks)
    else -> stringResource(R.string.wanneer_nodig)
}

@Composable
fun ProcedureList(
    catalog: Catalog,
    filter: String?,
    onFilter: (String?) -> Unit,
    onOpen: (Route) -> Unit,
) {
    val documented = catalog.machines.filter { m -> catalog.procedures.any { m.id in it.machines } }
    val shown = catalog.procedures
        .filter { filter == null || filter in it.machines }
        .filter { it.steps.isNotEmpty() }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to stringResource(R.string.alle_machines)) +
                    documented.map { it.id as String? to it.name },
                selected = filter,
                onSelect = onFilter,
            )
        }

        intervalOrder.forEach { interval ->
            val group = shown.filter { it.interval == interval }
            if (group.isEmpty()) return@forEach
            item { SectionHeader(intervalLabel(interval), "${group.size}") }
            items(group, key = { it.id }) { procedures ->
                Card(onClick = { onOpen(Route.Procedure(procedures.id)) }) {
                    Column {
                        Text(procedures.title, style = MaterialTheme.typography.titleMedium)
                        if (procedures.purpose.isNotEmpty()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                procedures.purpose,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill(count(R.plurals.n_steps, procedures.steps.size))
                            if (procedures.brewer.isNotEmpty() && procedures.brewer != "beide") Pill(procedures.brewer)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun ProcedureDetail(
    catalog: Catalog,
    procedure: Procedure,
    pinned: Boolean,
    onPin: (String) -> Unit,
    onOpen: (Route) -> Unit,
) {
    val spelled = intervalLabel(procedure.interval)
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(procedure.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(intervalLabel(procedure.interval))
                    if (procedure.brewer.isNotEmpty()) Pill(procedure.brewer)
                    Pill(catalog.machineNames(procedure.machines))
                    // Of this machine there is no Dutch manual; say so rather
                    // than let the engineer wonder about the translation.
                    // The manuals were not all translated; say so rather than
                    // leave the reader wondering why this one is in English.
                    if (procedure.language != appLanguage()) Pill(languageName(procedure.language))
                }
                if (procedure.steps.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = { onOpen(Route.Steps("procedure", procedure.id)) }) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.height(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.stap_voor_stap))
                    }
                }
            }
        }

        // The chip above already says "Dagelijks"; only spell the interval out
        // when the manual adds something the chip does not cover.
        if (procedure.images.isNotEmpty()) {
            item {
                Column {
                    procedure.images.forEach { image ->
                        AssetImage(image)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        item {
            ActionRow(
                pinKey = "proc:" + procedure.id,
                pinned = pinned,
                onPin = onPin,
                shareText = buildString {
                    appendLine(procedure.title)
                    if (procedure.intervalText.isNotEmpty()) appendLine(procedure.intervalText)
                    appendLine()
                    procedure.steps.forEachIndexed { i, step -> appendLine("${i + 1}. ${step.text}") }
                    append(stringResource(R.string.duke_service))
                },
            )
        }

        // The chip above already carries the interval; only spell it out when
        // the manual says something the chip does not.
        val intervalText = procedure.intervalText
        if (intervalText.isNotEmpty() && !intervalText.equals(spelled, ignoreCase = true)) {
            item {
                Text(
                    procedure.intervalText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }
        }

        if (procedure.purpose.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.waarom)) }
            item {
                Text(
                    procedure.purpose,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        if (procedure.needed.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.nodig)) }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    procedure.needed.forEach { item ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Box(
                                Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(item, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }

        if (procedure.warnings.isNotEmpty()) {
            item { Spacer(Modifier.height(12.dp)) }
            items(procedure.warnings.size) { index ->
                WarnBanner(
                    procedure.warnings[index],
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        if (procedure.steps.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.stappen), "${procedure.steps.size}") }
            items(procedure.steps.size) { index ->
                val step = procedure.steps[index]
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(step.text, style = MaterialTheme.typography.bodyLarge)
                        step.sub.forEach { sub ->
                            Spacer(Modifier.height(6.dp))
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(10.dp),
                            ) {
                                Text(
                                    sub,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (procedure.source.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.bron_x_3, procedure.source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}
