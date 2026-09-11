package nl.dejongduke.service.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.Prefs
import nl.dejongduke.service.data.SearchResult
import nl.dejongduke.service.ui.theme.ThemeMode
import java.time.LocalDate

enum class Tab(val label: String) {
    Zoek("Zoek"),
    Storingen("Storingen"),
    Onderhoud("Onderhoud"),
    Onderdelen("Onderdelen"),
    Machines("Machines"),
}

sealed interface Route {
    data class Fault(val key: String) : Route
    data class Procedure(val id: String) : Route
    data class Machine(val id: String) : Route
    data class Schema(val id: String) : Route
    data class PartSection(val machine: String, val sectie: String) : Route
    data class Component(val nr: String) : Route
    data object Components : Route
    data class MenuItem(val nr: String) : Route
    data object Servicemenu : Route
    data object Procedures : Route
    data object Specs : Route
    data object Bronnen : Route
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)

    private val _catalog = MutableStateFlow<Catalog?>(null)
    val catalog: StateFlow<Catalog?> = _catalog.asStateFlow()

    private val _tab = MutableStateFlow(Tab.Zoek)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _stack = MutableStateFlow<List<Route>>(emptyList())
    val stack: StateFlow<List<Route>> = _stack.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow(SearchResult())
    val results: StateFlow<SearchResult> = _results.asStateFlow()

    /** null means "all machines"; otherwise a machine id the lists are narrowed to. */
    private val _filter = MutableStateFlow(prefs.machine)
    val filter: StateFlow<String?> = _filter.asStateFlow()

    private val _theme = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.theme) }.getOrDefault(ThemeMode.System)
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _ticks = MutableStateFlow<Map<String, Set<Int>>>(emptyMap())
    val ticks: StateFlow<Map<String, Set<Int>>> = _ticks.asStateFlow()

    /**
     * The day the checklists apply to. It has to be a flow: the view model
     * outlives midnight, and an engineer who reopens the app the next morning
     * would otherwise tick off yesterday's list.
     */
    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val _recent = MutableStateFlow(prefs.recent())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { Catalog.load(getApplication()) }
            _catalog.value = loaded
            _ticks.value = loaded.schemas.associate { it.id to prefs.ticked(it.id, _today.value) }
            withContext(Dispatchers.IO) { prefs.pruneChecklists(_today.value) }
        }
    }

    /** Call when the app returns to the foreground, so a new day starts clean. */
    fun refreshDay() {
        val now = LocalDate.now()
        if (now == _today.value) return
        _today.value = now
        val cat = _catalog.value ?: return
        _ticks.value = cat.schemas.associate { it.id to prefs.ticked(it.id, now) }
        viewModelScope.launch { withContext(Dispatchers.IO) { prefs.pruneChecklists(now) } }
    }

    fun selectTab(tab: Tab) {
        if (_tab.value == tab) _stack.value = emptyList() else _tab.value = tab
    }

    fun open(route: Route) {
        _stack.value = _stack.value + route
    }

    /** Returns false when there was nothing left to pop, so the system can handle back. */
    fun back(): Boolean {
        val current = _stack.value
        if (current.isEmpty()) return false
        _stack.value = current.dropLast(1)
        return true
    }

    fun setQuery(text: String) {
        _query.value = text
        val cat = _catalog.value ?: return
        _results.value = cat.search(text)
    }

    fun commitQuery() {
        if (_query.value.trim().length < 2) return
        prefs.addRecent(_query.value)
        _recent.value = prefs.recent()
    }

    fun clearRecent() {
        prefs.clearRecent()
        _recent.value = emptyList()
    }

    fun setFilter(machineId: String?) {
        _filter.value = machineId
        prefs.machine = machineId
    }

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.theme = mode.name
    }

    fun toggleTick(schema: String, index: Int) {
        val current = _ticks.value[schema].orEmpty().toMutableSet()
        if (!current.add(index)) current.remove(index)
        prefs.setTicked(schema, _today.value, current)
        _ticks.value = _ticks.value + (schema to current)
    }

    fun resetTicks(schema: String) {
        prefs.setTicked(schema, _today.value, emptySet())
        _ticks.value = _ticks.value + (schema to emptySet())
    }
}
