package nl.dejongduke.service.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.dejongduke.service.R
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.Locales
import nl.dejongduke.service.data.Prefs
import nl.dejongduke.service.data.SearchResult
import nl.dejongduke.service.ui.theme.ThemeMode

enum class Tab(@StringRes val label: Int) {
    Search(R.string.search),
    Faults(R.string.faults),
    Maintenance(R.string.maintenance),
    Parts(R.string.parts),
    Machines(R.string.machines),
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
    data class Component(val id: String) : Route
    data object Components : Route
    data class MenuItem(val id: String) : Route
    data object ServiceMenu : Route
    data object Scan : Route
    /** The type plate reader, which is a scanner of its own. */
    data object PlateScan : Route
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

    private val _recent = MutableStateFlow(prefs.recent())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    // Dutch leads on a fault card for a Dutch engineer; everyone else reads the
    // message the way it stands on the machine, which is English.
    private val _messageLanguage = MutableStateFlow(
        prefs.messageLanguage ?: if (Locales.wanted(app) == "nl") "nl" else "en")
    val messageLanguage: StateFlow<String> = _messageLanguage.asStateFlow()

    private val _scanDirect = MutableStateFlow(prefs.scanDirect)
    val scanDirect: StateFlow<Boolean> = _scanDirect.asStateFlow()

    private val _pins = MutableStateFlow(prefs.pins())
    val pins: StateFlow<List<String>> = _pins.asStateFlow()

    private val _notes = MutableStateFlow(
        prefs.notedMachines().associateWith { prefs.note(it) }
    )
    val notes: StateFlow<Map<String, String>> = _notes.asStateFlow()

    /** The language everything is read in: the setting, else the phone's. */
    private val _language = MutableStateFlow(Locales.wanted(app))
    val language: StateFlow<String> = _language.asStateFlow()

    /** Null follows the phone; the activity restarts itself to re-read its
     *  resources, the content is reloaded here. */
    fun setLanguage(code: String?) {
        prefs.language = code
        _languageSetting.value = code
        val wanted = code ?: Locales.device()
        if (wanted == _language.value) return
        _language.value = wanted
        // Unless it was set by hand, which message leads follows the app.
        if (prefs.messageLanguage == null) {
            _messageLanguage.value = if (wanted == "nl") "nl" else "en"
        }
        load()
    }

    /** What the setting itself is set to, which is not the same as [language]:
     *  null means the phone decides. */
    private val _languageSetting = MutableStateFlow(prefs.language)
    val languageSetting: StateFlow<String?> = _languageSetting.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                Catalog.load(getApplication(), _language.value)
            }
            _catalog.value = loaded
            // The parts table is five megabytes; the app is usable without it.
            // Both the reading and the indexing that follows belong off the
            // main thread — the index is eighty thousand normalised strings.
            val withParts = withContext(Dispatchers.Default) {
                val rows = Catalog.loadParts(getApplication())
                loaded.withParts(rows)
            }
            _catalog.value = withParts
        }
    }

    /** Call when the app returns to the foreground, so the date stays right. */
    fun refreshDay() {
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

    private var searchJob: Job? = null

    /**
     * Searching scans forty thousand part rows among everything else, which is
     * too much to do on the main thread between two keystrokes.
     */
    fun setQuery(text: String) {
        _query.value = text
        val loaded = _catalog.value ?: return
        searchJob?.cancel()
        if (text.trim().length < 2) {
            _results.value = SearchResult()
            return
        }
        searchJob = viewModelScope.launch {
            delay(90)
            val found = withContext(Dispatchers.Default) { loaded.search(text) }
            _results.value = found
        }
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


    fun setFilter(machineId: String?) {
        _filter.value = machineId
        prefs.machine = machineId
        _variant.value = machineId?.let { prefs.variant(it) }
    }

    /** The build of the machine the lists are narrowed to, if one was chosen. */
    private val _variant = MutableStateFlow(prefs.machine?.let { prefs.variant(it) })
    val variant: StateFlow<String?> = _variant.asStateFlow()

    fun setVariant(code: String?) {
        _variant.value = code
        _filter.value?.let { prefs.setVariant(it, code) }
    }

    /**
     * Point the whole app at the machine in front of you.
     *
     * Reading the type plate is the one moment the app knows exactly which
     * machine and which build it is dealing with; everything after that should
     * follow without being asked again.
     */
    fun useMachine(machineId: String, code: String? = null) {
        setFilter(machineId)
        if (code != null) setVariant(code)
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
