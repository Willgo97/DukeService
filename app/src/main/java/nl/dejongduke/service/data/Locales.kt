package nl.dejongduke.service.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Which language the app speaks. The manuals exist in nine, so the interface
 * does too: an engineer in Helsinki reads the fault list in Finnish without
 * having to put the whole phone in Finnish first.
 *
 * The choice lives in [Prefs]; null there means "follow the phone". Because
 * Android hands resources to an activity before any of our code runs, the
 * activity re-wraps its own context in [wrap] and is recreated when the choice
 * changes.
 */
object Locales {

    /** Each language written the way it is written by the people who read it. */
    val NAMES = mapOf(
        "nl" to "Nederlands",
        "en" to "English",
        "de" to "Deutsch",
        "fr" to "Français",
        "sv" to "Svenska",
        "no" to "Norsk",
        "da" to "Dansk",
        "fi" to "Suomi",
        "cs" to "Čeština",
    )

    /** The phone's language, if the manuals are in it — otherwise Dutch. */
    fun device(): String {
        val tag = Locale.getDefault().language.lowercase()
        // Norwegian is "nb" or "nn" on a phone and "no" in the manuals.
        val mapped = if (tag == "nb" || tag == "nn") "no" else tag
        return if (mapped in Catalog.LANGUAGES) mapped else "nl"
    }

    /** The language to use now: the setting, else the phone's. */
    fun wanted(context: Context): String {
        val saved = Prefs(context).language
        return if (saved != null && saved in Catalog.LANGUAGES) saved else device()
    }

    /** The same context, but handing out resources in the chosen language. */
    fun wrap(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(wanted(base)))
        // Locale.setDefault stays untouched: it is how [device] still knows
        // what the phone itself is set to after a choice has been made.
        return base.createConfigurationContext(config)
    }
}
