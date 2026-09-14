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
import nl.dejongduke.service.ui.screens.BookList
import nl.dejongduke.service.ui.screens.SourcesScreen
import nl.dejongduke.service.ui.screens.ComponentDetail
import nl.dejongduke.service.ui.screens.ComponentList
import nl.dejongduke.service.ui.screens.MenuDetail
import nl.dejongduke.service.ui.screens.MenuList
import nl.dejongduke.service.ui.screens.FaultDetail
import nl.dejongduke.service.ui.screens.FaultsScreen
import nl.dejongduke.service.ui.screens.CardDetail
import nl.dejongduke.service.ui.screens.CardList
import nl.dejongduke.service.ui.screens.MachineDetail
import nl.dejongduke.service.ui.screens.MachinesScreen
import nl.dejongduke.service.ui.screens.MaintenanceScreen
import nl.dejongduke.service.ui.screens.PartSectionDetail
import nl.dejongduke.service.ui.screens.PartsScreen
import nl.dejongduke.service.ui.screens.ProcedureDetail
import nl.dejongduke.service.ui.screens.ProcedureList
import nl.dejongduke.service.ui.screens.ScanScreen
import nl.dejongduke.service.ui.screens.SearchScreen
import nl.dejongduke.service.ui.screens.SettingsScreen
import nl.dejongduke.service.ui.screens.SpecsScreen
import nl.dejongduke.service.ui.screens.StepPlayer
import nl.dejongduke.service.ui.screens.cardSteps
import nl.dejongduke.service.ui.screens.procedureSteps

private fun tabIcon(tab: Tab): ImageVector = when (tab) {
    Tab.Search -> Icons.Filled.Search
    Tab.Faults -> Icons.Filled.WarningAmber
    Tab.Maintenance -> Icons.AutoMirrored.Filled.ListAlt
    Tab.Parts -> Icons.Filled.Build
    Tab.Machines -> Icons.Filled.CoffeeMaker
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(vm: AppViewModel = viewModel()) {
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val stack by vm.stack.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val variant by vm.variant.collectAsStateWithLifecycle()

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
                title = {
                    // Every list in the app narrows to the machine that was
                    // picked or scanned. That has to be visible, or a short
                    // list looks like missing data.
                    val machine = filter?.let { catalog?.machine(it) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(titleFor(catalog, tab, current), maxLines = 1)
                        if (machine != null && current == null) {
                            Text(
                                machine.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (current != null) {
                        IconButton(onClick = { vm.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.open(Route.Settings) }) {
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
        val loaded = catalog
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (loaded == null) {
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
                        null -> RootScreen(vm, loaded, activeTab, filter, variant)
                        else -> DetailScreen(vm, loaded, route)
                    }
                }
            }
        }
    }
}

@Composable
private fun RootScreen(
    vm: AppViewModel,
    loaded: Catalog,
    tab: Tab,
    filter: String?,
    variant: String?,
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val pins by vm.pins.collectAsStateWithLifecycle()
    val language by vm.messageLanguage.collectAsStateWithLifecycle()

    when (tab) {
        Tab.Search -> SearchScreen(
            catalog = loaded,
            query = query,
            results = results,
            recent = recent,
            pins = pins,
            filter = filter,
            variant = variant,
            onFilter = vm::setFilter,
            language = language,
            onQuery = vm::setQuery,
            onCommit = vm::commitQuery,
            onClearRecent = vm::clearRecent,
            onOpen = vm::open,
            onTab = vm::selectTab,
        )

        Tab.Faults -> FaultsScreen(loaded, filter, variant, language, vm::setFilter, vm::open)

        Tab.Maintenance -> MaintenanceScreen(loaded, filter, variant, today, vm::setFilter, vm::open)

        Tab.Parts -> PartsScreen(loaded, filter, variant, vm::setFilter, vm::setVariant, vm::open)

        Tab.Machines -> MachinesScreen(loaded, vm::open)
    }
}

@Composable
private fun DetailScreen(vm: AppViewModel, loaded: Catalog, route: Route) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val variant by vm.variant.collectAsStateWithLifecycle()
    val pins by vm.pins.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()

    when (route) {
        is Route.Fault -> {
            val group = loaded.faultGroup(route.key)
            if (group != null) {
                val language by vm.messageLanguage.collectAsStateWithLifecycle()
                FaultDetail(
                    loaded, group, pins.contains("fault:" + group.message), language, vm::togglePin, vm::open,
                )
            }
        }

        is Route.Procedure -> {
            val procedures = loaded.procedure(route.id)
            if (procedures != null) {
                ProcedureDetail(loaded, procedures, pins.contains("proc:" + procedures.id),
                                vm::togglePin, vm::open)
            }
        }

        Route.Procedures -> ProcedureList(loaded, filter, vm::setFilter, vm::open)

        Route.Components -> ComponentList(loaded, filter, variant, vm::setFilter, vm::open)

        Route.ServiceMenu -> MenuList(loaded, filter, variant, vm::setFilter, vm::open)

        Route.Scan -> {
            val direct by vm.scanDirect.collectAsStateWithLifecycle()
            ScanScreen(loaded, direct, vm::useMachine, vm::open)
        }

        Route.Settings -> {
            val thema by vm.theme.collectAsStateWithLifecycle()
            val language by vm.messageLanguage.collectAsStateWithLifecycle()
            val direct by vm.scanDirect.collectAsStateWithLifecycle()
            SettingsScreen(
                catalog = loaded,
                thema = thema,
                onThema = vm::setTheme,
                messageLanguage = language,
                onMessageLanguage = vm::setMessageLanguage,
                defaultMachine = filter,
                onMachine = vm::setFilter,
                scanDirect = direct,
                onScanDirect = vm::setScanDirect,
                onOpen = vm::open,
            )
        }

        is Route.MenuItem -> {
            val item = loaded.menuItem(route.id)
            if (item != null) MenuDetail(loaded, item)
        }

        is Route.Component -> {
            val component = loaded.component(route.id)
            if (component != null) ComponentDetail(loaded, component)
        }

        is Route.Machine -> {
            val machine = loaded.machine(route.id)
            if (machine != null) {
                MachineDetail(loaded, machine, variant, vm::useMachine,
                    notes[machine.id].orEmpty(), vm::setNote, vm::open) { tab, machineId ->
                    vm.setFilter(machineId)
                    vm.back()
                    vm.selectTab(tab)
                }
            }
        }

        is Route.PartSection -> PartSectionDetail(loaded, route.machine, route.variant, route.section)

        Route.Cards -> CardList(loaded, filter, variant, vm::setFilter, vm::open)

        is Route.MaintenanceCard -> {
            val card = loaded.card(route.id)
            if (card == null) EmptyState("Niet gevonden", "Deze onderhoudskaart staat niet in de app.")
            else CardDetail(loaded, card, vm::open)
        }

        Route.Books -> BookList(loaded, filter, vm::setFilter)

        is Route.Steps -> when (route.kind) {
            "card" -> {
                val card = loaded.card(route.id)
                StepPlayer(card?.title ?: "Stappen",
                           loaded.machineNames(card?.machines.orEmpty()),
                           loaded.cardSteps(route.id))
            }
            else -> {
                val procedure = loaded.procedure(route.id)
                StepPlayer(procedure?.title ?: "Stappen",
                           loaded.machineNames(procedure?.machines.orEmpty()),
                           loaded.procedureSteps(route.id))
            }
        }

        Route.Specs -> SpecsScreen(loaded)

        Route.Sources -> SourcesScreen(loaded, vm::open)
    }
}

private fun titleFor(catalog: Catalog?, tab: Tab, route: Route?): String = when (route) {
    null -> when (tab) {
        Tab.Search -> "DUKE Service"
        else -> tab.label
    }
    is Route.Fault -> "Storing"
    is Route.Procedure -> "Procedure"
    Route.Procedures -> "Procedures"
    Route.Components -> "Techniek"
    Route.ServiceMenu -> "Servicemenu"
    Route.Scan -> "Scannen"
    Route.Settings -> "Instellingen"
    is Route.MenuItem -> "Servicemenu"
    is Route.Component -> "Techniek"
    is Route.Machine -> catalog?.machine(route.id)?.name ?: "Machine"
    is Route.PartSection -> "Onderdelen"
    is Route.MaintenanceCard -> "Onderhoudskaart"
    Route.Cards -> "Onderhoudskaarten"
    Route.Books -> "Handleidingen"
    is Route.Steps -> "Stap voor stap"
    Route.Specs -> "Technisch"
    Route.Sources -> "Bronnen"
}
