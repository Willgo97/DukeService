package nl.dejongduke.service.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CoffeeMaker
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.ui.theme.ThemeMode
import nl.dejongduke.service.ui.screens.BronnenScreen
import nl.dejongduke.service.ui.screens.ComponentDetail
import nl.dejongduke.service.ui.screens.ComponentList
import nl.dejongduke.service.ui.screens.MenuDetail
import nl.dejongduke.service.ui.screens.MenuList
import nl.dejongduke.service.ui.screens.FaultDetail
import nl.dejongduke.service.ui.screens.FaultsScreen
import nl.dejongduke.service.ui.screens.MachineDetail
import nl.dejongduke.service.ui.screens.MachinesScreen
import nl.dejongduke.service.ui.screens.MaintenanceScreen
import nl.dejongduke.service.ui.screens.PartSectionDetail
import nl.dejongduke.service.ui.screens.PartsScreen
import nl.dejongduke.service.ui.screens.ProcedureDetail
import nl.dejongduke.service.ui.screens.ProcedureList
import nl.dejongduke.service.ui.screens.SchemaDetail
import nl.dejongduke.service.ui.screens.ScanScreen
import nl.dejongduke.service.ui.screens.SearchScreen
import nl.dejongduke.service.ui.screens.SettingsScreen
import nl.dejongduke.service.ui.screens.SpecsScreen

private fun tabIcon(tab: Tab): ImageVector = when (tab) {
    Tab.Zoek -> Icons.Filled.Search
    Tab.Storingen -> Icons.Filled.WarningAmber
    Tab.Onderhoud -> Icons.AutoMirrored.Filled.ListAlt
    Tab.Onderdelen -> Icons.Filled.Build
    Tab.Machines -> Icons.Filled.CoffeeMaker
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(vm: AppViewModel = viewModel()) {
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val stack by vm.stack.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()

    val current = stack.lastOrNull()
    BackHandler(enabled = current != null) { vm.back() }

    // Coming back after midnight has to move the checklists to the new day.
    LifecycleResumeEffect(Unit) {
        vm.refreshDay()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(titleFor(catalog, tab, current), maxLines = 1) },
                navigationIcon = {
                    if (current != null) {
                        IconButton(onClick = { vm.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.open(Route.Instellingen) }) {
                        Icon(Icons.Filled.Settings, "Instellingen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            if (current == null) {
                ExtendedFloatingActionButton(
                    onClick = { vm.open(Route.Scan) },
                    icon = { Icon(Icons.Filled.CameraAlt, null) },
                    text = { Text("Scan") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry && current == null,
                        onClick = { vm.selectTab(entry) },
                        icon = { Icon(tabIcon(entry), null) },
                        label = { Text(entry.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        val cat = catalog
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (cat == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Box
            }
            AnimatedContent(
                targetState = current to tab,
                transitionSpec = {
                    val forward = targetState.first != null
                    if (forward) {
                        (slideInHorizontally { it / 6 } + fadeIn()) togetherWith fadeOut()
                    } else {
                        fadeIn() togetherWith (slideOutHorizontally { it / 6 } + fadeOut())
                    }
                },
                label = "scherm",
            ) { (route, activeTab) ->
                Column(Modifier.fillMaxSize()) {
                    when (route) {
                        null -> RootScreen(vm, cat, activeTab, filter)
                        else -> DetailScreen(vm, cat, route)
                    }
                }
            }
        }
    }
}

@Composable
private fun RootScreen(vm: AppViewModel, cat: Catalog, tab: Tab, filter: String?) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val ticks by vm.ticks.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val pins by vm.pins.collectAsStateWithLifecycle()
    val taal by vm.meldingTaal.collectAsStateWithLifecycle()

    when (tab) {
        Tab.Zoek -> SearchScreen(
            catalog = cat,
            query = query,
            results = results,
            recent = recent,
            pins = pins,
            ticks = ticks,
            taal = taal,
            onQuery = vm::setQuery,
            onCommit = vm::commitQuery,
            onClearRecent = vm::clearRecent,
            onOpen = vm::open,
            onTab = vm::selectTab,
        )

        Tab.Storingen -> FaultsScreen(cat, filter, taal, vm::setFilter, vm::open)

        Tab.Onderhoud -> MaintenanceScreen(cat, filter, vm::setFilter, ticks, today, vm::open)

        Tab.Onderdelen -> PartsScreen(cat, filter, vm::setFilter, vm::open)

        Tab.Machines -> MachinesScreen(cat, vm::open)
    }
}

@Composable
private fun DetailScreen(vm: AppViewModel, cat: Catalog, route: Route) {
    val ticks by vm.ticks.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val pins by vm.pins.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()

    when (route) {
        is Route.Fault -> {
            val group = cat.faultGroup(route.key)
            if (group != null) {
                val taal by vm.meldingTaal.collectAsStateWithLifecycle()
                FaultDetail(
                    cat, group, pins.contains("fault:" + group.melding), taal, vm::togglePin, vm::open,
                )
            }
        }

        is Route.Procedure -> {
            val proc = cat.procedure(route.id)
            if (proc != null) {
                ProcedureDetail(cat, proc, pins.contains("proc:" + proc.id), vm::togglePin)
            }
        }

        Route.Procedures -> ProcedureList(cat, filter, vm::setFilter, vm::open)

        Route.Components -> ComponentList(cat, vm::open)

        Route.Servicemenu -> MenuList(cat, vm::open)

        Route.Scan -> {
            val direct by vm.scanDirect.collectAsStateWithLifecycle()
            ScanScreen(cat, direct, vm::open)
        }

        Route.Instellingen -> {
            val thema by vm.theme.collectAsStateWithLifecycle()
            val taal by vm.meldingTaal.collectAsStateWithLifecycle()
            val direct by vm.scanDirect.collectAsStateWithLifecycle()
            SettingsScreen(
                catalog = cat,
                thema = thema,
                onThema = vm::setTheme,
                meldingTaal = taal,
                onMeldingTaal = vm::setMeldingTaal,
                standaardMachine = filter,
                onMachine = vm::setFilter,
                scanDirect = direct,
                onScanDirect = vm::setScanDirect,
                onResetTicks = vm::resetAlleTicks,
                onOpen = vm::open,
            )
        }

        is Route.MenuItem -> {
            val item = cat.menuItem(route.nr)
            if (item != null) MenuDetail(item)
        }

        is Route.Component -> {
            val component = cat.component(route.nr)
            if (component != null) ComponentDetail(component)
        }

        is Route.Machine -> {
            val machine = cat.machine(route.id)
            if (machine != null) {
                MachineDetail(cat, machine, notes[machine.id].orEmpty(), vm::setNote, vm::open) { tab, machineId ->
                    vm.setFilter(machineId)
                    vm.back()
                    vm.selectTab(tab)
                }
            }
        }

        is Route.Schema -> {
            val schema = cat.schemas.firstOrNull { it.id == route.id }
            if (schema != null) {
                SchemaDetail(
                    catalog = cat,
                    schema = schema,
                    ticked = ticks[schema.id].orEmpty(),
                    onToggle = { vm.toggleTick(schema.id, it) },
                    onReset = { vm.resetTicks(schema.id) },
                    onOpen = vm::open,
                )
            }
        }

        is Route.PartSection -> PartSectionDetail(cat, route.machine, route.sectie)

        Route.Specs -> SpecsScreen(cat)

        Route.Bronnen -> BronnenScreen()
    }
}

private fun titleFor(catalog: Catalog?, tab: Tab, route: Route?): String = when (route) {
    null -> when (tab) {
        Tab.Zoek -> "DUKE Service"
        else -> tab.label
    }
    is Route.Fault -> "Storing"
    is Route.Procedure -> "Procedure"
    Route.Procedures -> "Procedures"
    Route.Components -> "Techniek"
    Route.Servicemenu -> "Servicemenu"
    Route.Scan -> "Scannen"
    Route.Instellingen -> "Instellingen"
    is Route.MenuItem -> "Servicemenu"
    is Route.Component -> "Techniek"
    is Route.Machine -> catalog?.machine(route.id)?.naam ?: "Machine"
    is Route.Schema -> "Checklist"
    is Route.PartSection -> "Onderdelen"
    Route.Specs -> "Technisch"
    Route.Bronnen -> "Bronnen"
}
