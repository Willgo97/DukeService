package nl.dejongduke.service.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.R
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.count
import nl.dejongduke.service.ui.ChipRow
import nl.dejongduke.service.ui.EmptyState
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader

/**
 * Maintenance: the manufacturer's own cards, per machine and per interval.
 *
 * The card is what hangs inside the machine — numbered steps with the drawing
 * that belongs to each one — so that is what the app shows, rather than a list
 * rewritten around it.
 */
@Composable
fun MaintenanceScreen(
    catalog: Catalog,
    filter: String?,
    variant: String?,
    onFilter: (String?) -> Unit,
    onOpen: (Route) -> Unit,
) {
    val documented = catalog.machines.filter { m -> catalog.cards.any { m.id in it.machines } }
    val cards = catalog.cardsFor(filter, variant)
    val procedures = catalog.procedures.count { it.steps.isNotEmpty() }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to stringResource(R.string.all_machines)) +
                    documented.map { it.id as String? to it.name },
                selected = filter,
                onSelect = onFilter,
            )
        }
        if (cards.isEmpty()) {
            item {
                EmptyState(stringResource(R.string.no_maintenance_card),
                           stringResource(R.string.there_is_no_manufacturer_s_card_for_this_mac))
            }
        }
        maintenanceCards(catalog, cards, filter, onOpen)
        item { SectionHeader(stringResource(R.string.procedures)) }
        item {
            Card(onClick = { onOpen(Route.Procedures) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.all_procedures), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            count(R.plurals.n_step_by_step, procedures),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null,
                         tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}
