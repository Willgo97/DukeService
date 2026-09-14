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
    Search("Zoek"),
    Faults("Storingen"),
    Maintenance("Onderhoud"),
    Parts("Onderdelen"),
    Machines("Machines"),
}

sealed interface Route {
    data class Fault(val key: String) : Route
    data class Procedure(val id: String) : Route
    data class Machine(val id: String) : Route
    data class PartSection(val machine: String, val variant: String, val section: String) : Route
    data class MaintenanceCard(val id: String) : Route
    /** A job walked through one step at a time; kind is "card" or "procedure". */
    data class Steps(val kind: String, val id: String) : Route
    data object Cards : Route
    data object Books : Route
    data class Component(val id: String) : Route
    data object Components : Route
    data class MenuItem(val id: String) : Route
    data object ServiceMenu : Route
    data object Scan : Route
    data object Settings : Route
    data object Procedures : Route
    data object Specs : Route
    data object Sources : Route
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)

    private val _catalog = MutableStateFlow<Catalog?>(null)
    val catalog: StateFlow<Catalog?> = _catalog.asStateFlow()

    private val _tab = MutableStateFlow(Tab.Search)
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

    /**
     * The day the checklists apply to. It has to be a flow: the view model
     * outlives midnight, and an engineer who reopens the app the next morning
     * would otherwise tick off yesterday's list.
     */
    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val _recent = MutableStateFlow(prefs.recent())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    private val _messageLanguage = MutableStateFlow(prefs.messageLanguage)
    val messageLanguage: StateFlow<String> = _messageLanguage.asStateFlow()

    private val _scanDirect = MutableStateFlow(prefs.scanDirect)
    val scanDirect: StateFlow<Boolean> = _scanDirect.asStateFlow()

    private val _pins = MutableStateFlow(prefs.pins())
    val pins: StateFlow<List<String>> = _pins.asStateFlow()

    private val _notes = MutableStateFlow(
        prefs.notedMachines().associateWith { prefs.note(it) }
    )
    val notes: StateFlow<Map<String, String>> = _notes.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { Catalog.load(getApplication()) }
            _catalog.value = loaded
            // The parts table is five megabytes; the app is usable without it.
            val rows = withContext(Dispatchers.IO) { Catalog.loadParts(getApplication()) }
            _catalog.value = _catalog.value?.withParts(rows)
        }
    }

    /** Call when the app returns to the foreground, so the date stays right. */
    fun refreshDay() {
        _today.value = LocalDate.now()
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
        val loaded = _catalog.value ?: return
        _results.value = loaded.search(text)
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

    fun setNote(machineId: String, text: String) {
        prefs.setNote(machineId, text)
        _notes.value = if (text.isBlank()) _notes.value - machineId
        else _notes.value + (machineId to text.trim())
    }

    fun togglePin(key: String) {
        prefs.togglePin(key)
        _pins.value = prefs.pins()
    }

    fun isPinned(key: String) = _pins.value.contains(key)

    fun setFilter(machineId: String?) {
        _filter.value = machineId
        prefs.machine = machineId
    }

    fun setMessageLanguage(language: String) {
        _messageLanguage.value = language
        prefs.messageLanguage = language
    }

    fun setScanDirect(aan: Boolean) {
        _scanDirect.value = aan
        prefs.scanDirect = aan
    }


    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.theme = mode.name
    }

}
