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
import nl.dejongduke.service.R
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.Locales
import nl.dejongduke.service.ui.ChipRow
import nl.dejongduke.service.ui.count
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.SectionHeader
import nl.dejongduke.service.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    catalog: Catalog,
    thema: ThemeMode,
    onThema: (ThemeMode) -> Unit,
    language: String?,
    onLanguage: (String?) -> Unit,
    messageLanguage: String,
    onMessageLanguage: (String) -> Unit,
    defaultMachine: String?,
    onMachine: (String?) -> Unit,
    scanDirect: Boolean,
    onScanDirect: (Boolean) -> Unit,
    onOpen: (Route) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item { SectionHeader(stringResource(R.string.taal)) }
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to stringResource(R.string.taal_van_de_telefoon)) +
                    Catalog.LANGUAGES.map { it as String? to (Locales.NAMES[it] ?: it) },
                selected = language,
                onSelect = onLanguage,
            )
        }
        item {
            Text(
                stringResource(R.string.de_hele_app_volgt_deze_keuze_teksten_uit_de),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        item { SectionHeader(stringResource(R.string.weergave)) }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                ThemeMode.entries.forEach { mode ->
                    Choice(
                        title = stringResource(mode.label),
                        onder = when (mode) {
                            ThemeMode.System -> stringResource(R.string.volgt_de_stand_van_je_telefoon)
                            ThemeMode.Light -> stringResource(R.string.altijd_licht)
                            ThemeMode.Dark -> stringResource(R.string.altijd_donker_prettiger_in_een_donkere_ruimt)
                        },
                        selected = mode == thema,
                    ) { onThema(mode) }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.taal_van_de_meldingen)) }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Choice(
                    stringResource(R.string.nederlands_voorop),
                    stringResource(R.string.zoals_een_machine_die_op_nederlands_staat_he),
                    messageLanguage == "nl",
                ) { onMessageLanguage("nl") }
                Choice(
                    stringResource(R.string.engels_voorop),
                    stringResource(R.string.zoals_de_handleiding_en_een_machine_die_op_e),
                    messageLanguage == "en",
                ) { onMessageLanguage("en") }
            }
        }
        item {
            Text(
                stringResource(R.string.beide_talen_blijven_zichtbaar_dit_bepaalt_al),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        item { SectionHeader(stringResource(R.string.standaardmachine)) }
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to stringResource(R.string.alle_machines)) +
                    catalog.machinesWithParts
                        .map { it.id as String? to it.name },
                selected = defaultMachine,
                onSelect = onMachine,
            )
        }
        item {
            Text(
                stringResource(R.string.waar_de_lijsten_mee_openen_je_kunt_altijd_wi),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        item { SectionHeader(stringResource(R.string.scanner)) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.direct_openen), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.bij_een_treffer_meteen_de_pagina_openen),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = scanDirect, onCheckedChange = onScanDirect)
            }
        }

        if (BuildConfig.DEBUG) {
            item { SectionHeader(stringResource(R.string.ontwikkelen)) }
            item {
                var status by remember { mutableStateOf("") }
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                val busy = stringResource(R.string.bezig)
                Column(Modifier.padding(horizontal = 12.dp)) {
                    TextButton(onClick = {
                        scope.launch {
                            status = busy
                            status = DrawingIndexer.run(context) { status = it }
                        }
                    }) { Text(stringResource(R.string.tekeningen_indexeren)) }
                    if (status.isNotEmpty()) {
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                        )
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.over)) }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                InfoRow(stringResource(R.string.versie), "1.1")
                InfoRow(stringResource(R.string.inhoud), count(R.plurals.n_faults, catalog.faultGroups.size) + " · " +
                    count(R.plurals.n_parts, catalog.parts.size))
                InfoRow(stringResource(R.string.werkt_offline), stringResource(R.string.ook_de_tekstherkenning_van_de_scanner))
                Spacer(Modifier.height(10.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                TextButton(onClick = { onOpen(Route.Sources) }) { Text(stringResource(R.string.waar_komt_dit_vandaan)) }
            }
        }
        item {
            Text(
                stringResource(R.string.privewerk_geen_officiele_uitgave_van_de_jong),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Choice(title: String, onder: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                onder,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoRow(key: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
