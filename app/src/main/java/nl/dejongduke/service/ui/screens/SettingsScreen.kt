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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun SettingsScreen(
    catalog: Catalog,
    theme: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    language: String?,
    onLanguage: (String?) -> Unit,
    messageLanguage: String,
    onMessageLanguage: (String) -> Unit,
    scanDirect: Boolean,
    onScanDirect: (Boolean) -> Unit,
    onOpen: (Route) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item { SectionHeader(stringResource(R.string.language)) }
        item {
            ChipRow(
                options = listOf<Pair<String?, String>>(null to stringResource(R.string.phone_language)) +
                    Catalog.LANGUAGES.map { it as String? to (Locales.NAMES[it] ?: it) },
                selected = language,
                onSelect = onLanguage,
            )
        }
        item {
            Text(
                stringResource(R.string.the_whole_app_follows_this_choice_text_from),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        item { SectionHeader(stringResource(R.string.appearance)) }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                ThemeMode.entries.forEach { mode ->
                    Choice(
                        title = stringResource(mode.label),
                        subtitle = when (mode) {
                            ThemeMode.System -> stringResource(R.string.follows_your_phone)
                            ThemeMode.Light -> stringResource(R.string.always_light)
                            ThemeMode.Dark -> stringResource(R.string.always_dark_easier_on_the_eyes_in_a_dim_room)
                        },
                        selected = mode == theme,
                    ) { onTheme(mode) }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.language_of_the_messages)) }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Choice(
                    stringResource(R.string.dutch_first),
                    stringResource(R.string.such_as_a_machine_set_to_dutch_shows_it),
                    messageLanguage == "nl",
                ) { onMessageLanguage("nl") }
                Choice(
                    stringResource(R.string.english_first),
                    stringResource(R.string.such_as_the_manual_and_a_machine_set_to_engl),
                    messageLanguage == "en",
                ) { onMessageLanguage("en") }
            }
        }
        item {
            Text(
                stringResource(R.string.both_languages_stay_visible_this_only_sets_w),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        item { SectionHeader(stringResource(R.string.scanner)) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.open_straight_away), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.open_the_page_straight_away_on_a_single_hit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = scanDirect, onCheckedChange = onScanDirect)
            }
        }

        if (BuildConfig.DEBUG) {
            item { SectionHeader(stringResource(R.string.development)) }
            item {
                var status by remember { mutableStateOf("") }
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                val busy = stringResource(R.string.working)
                Column(Modifier.padding(horizontal = 12.dp)) {
                    TextButton(onClick = {
                        scope.launch {
                            status = busy
                            status = DrawingIndexer.run(context) { status = it }
                        }
                    }) { Text(stringResource(R.string.index_drawings)) }
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

        item { SectionHeader(stringResource(R.string.about)) }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                InfoRow(stringResource(R.string.version), BuildConfig.VERSION_NAME)
                InfoRow(stringResource(R.string.contents), count(R.plurals.n_faults, catalog.faultGroups.size) + " · " +
                    count(R.plurals.n_parts, catalog.parts.size))
                InfoRow(stringResource(R.string.works_offline), stringResource(R.string.including_the_scanner_s_text_recognition))
                Spacer(Modifier.height(10.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                TextButton(onClick = { onOpen(Route.Sources) }) { Text(stringResource(R.string.where_does_this_come_from)) }
            }
        }
        item {
            Text(
                stringResource(R.string.private_work_not_an_official_de_jong_duke_re),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Choice(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
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
                subtitle,
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
