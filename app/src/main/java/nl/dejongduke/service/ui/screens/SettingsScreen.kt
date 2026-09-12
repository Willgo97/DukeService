package nl.dejongduke.service.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import nl.dejongduke.service.BuildConfig
import nl.dejongduke.service.data.DrawingIndexer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.ui.ChipRow
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader
import nl.dejongduke.service.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    catalog: Catalog,
    thema: ThemeMode,
    onThema: (ThemeMode) -> Unit,
    meldingTaal: String,
    onMeldingTaal: (String) -> Unit,
    standaardMachine: String?,
    onMachine: (String?) -> Unit,
    scanDirect: Boolean,
    onScanDirect: (Boolean) -> Unit,
    onResetTicks: () -> Unit,
    onOpen: (Route) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item { SectionHeader("Weergave") }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                ThemeMode.entries.forEach { mode ->
                    Keuze(
                        titel = mode.label,
                        onder = when (mode) {
                            ThemeMode.System -> "Volgt de stand van je telefoon"
                            ThemeMode.Light -> "Altijd licht"
                            ThemeMode.Dark -> "Altijd donker — prettiger in een donkere ruimte"
                        },
                        gekozen = mode == thema,
                    ) { onThema(mode) }
                }
            }
        }

        item { SectionHeader("Taal van de meldingen") }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Keuze(
                    "Nederlands voorop",
                    "Zoals een machine die op Nederlands staat het toont",
                    meldingTaal == "nl",
                ) { onMeldingTaal("nl") }
                Keuze(
                    "Engels voorop",
                    "Zoals de handleiding en een machine die op Engels staat",
                    meldingTaal == "en",
                ) { onMeldingTaal("en") }
            }
        }
        item {
            Text(
                "Beide talen blijven zichtbaar; dit bepaalt alleen welke bovenaan staat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        item { SectionHeader("Standaardmachine") }
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to "Alle machines") +
                    catalog.machines.filter { m -> catalog.parts.any { it.machine == m.id } }
                        .map { it.id as String? to it.naam },
                selected = standaardMachine,
                onSelect = onMachine,
            )
        }
        item {
            Text(
                "Waar de lijsten mee openen. Je kunt altijd wisselen bovenin een lijst.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        item { SectionHeader("Scanner") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Direct openen", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Bij één treffer meteen de pagina openen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = scanDirect, onCheckedChange = onScanDirect)
            }
        }

        item { SectionHeader("Onderhoud") }
        item {
            TextButton(onClick = onResetTicks, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text("Vinkjes van vandaag wissen")
            }
        }

        if (BuildConfig.DEBUG) {
            item { SectionHeader("Ontwikkelen") }
            item {
                var stand by remember { mutableStateOf("") }
                val bereik = rememberCoroutineScope()
                val context = LocalContext.current
                Column(Modifier.padding(horizontal = 12.dp)) {
                    TextButton(onClick = {
                        bereik.launch {
                            stand = "bezig…"
                            stand = DrawingIndexer.run(context) { stand = it }
                        }
                    }) { Text("Tekeningen indexeren") }
                    if (stand.isNotEmpty()) {
                        Text(
                            stand,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                        )
                    }
                }
            }
        }

        item { SectionHeader("Over") }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Regel("Versie", "1.1")
                Regel("Inhoud", "${catalog.faultGroups.size} storingen · ${catalog.parts.size} onderdelen")
                Regel("Werkt offline", "Ook de tekstherkenning van de scanner")
                Spacer(Modifier.height(10.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                TextButton(onClick = { onOpen(Route.Bronnen) }) { Text("Waar komt dit vandaan?") }
            }
        }
        item {
            Text(
                "Privéwerk, geen officiële uitgave van De Jong DUKE. Bij twijfel zijn de handleiding " +
                    "en het typeplaatje in de machine leidend.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Keuze(titel: String, onder: String, gekozen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (gekozen) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = gekozen, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.bodyLarge)
            Text(
                onder,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Regel(kop: String, waarde: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(kop, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(waarde, style = MaterialTheme.typography.bodyMedium)
    }
}
