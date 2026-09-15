package nl.dejongduke.service.ui

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import nl.dejongduke.service.R
import nl.dejongduke.service.data.Locales

/**
 * A handful of words live in the data rather than in the resources: the
 * category a fault was sorted into and the name of a maintenance sheet are
 * written once, when the assets are built, and are therefore Dutch. The app is
 * read in nine languages, so they are translated here on the way to the screen.
 * Anything not listed is shown as it is written.
 */
@Composable
fun categoryLabel(name: String): String = when (name) {
    "Brewer" -> stringResource(R.string.brewer)
    "Water" -> stringResource(R.string.water_system)
    "Waste" -> stringResource(R.string.waste)
    "Cleaning" -> stringResource(R.string.cleaning)
    "Temperature" -> stringResource(R.string.temperature)
    "Grinder" -> stringResource(R.string.grinder)
    "Mixer" -> stringResource(R.string.mixer)
    "Cups" -> stringResource(R.string.cups)
    "Payment" -> stringResource(R.string.payment)
    "Controls" -> stringResource(R.string.controls)
    "Ingredients" -> stringResource(R.string.ingredients)
    "Operation" -> stringResource(R.string.operation)
    "Other" -> stringResource(R.string.other)
    else -> name
}

/** The two titles the maintenance sheets carry, as the pipeline writes them. */
@Composable
fun cardTitle(title: String): String = when (title) {
    "Daily maintenance" -> stringResource(R.string.daily_maintenance)
    "Regular maintenance" -> stringResource(R.string.regular_maintenance)
    else -> title
}

/**
 * "50 messages", and "1 message" when there is one. Which of the two — or of
 * the three Czech has — is the phone's business, not ours.
 */
@Composable
fun count(@PluralsRes plural: Int, quantity: Int): String =
    pluralStringResource(plural, quantity, quantity)

/** The language the app is being read in, as a two-letter code. */
@Composable
fun appLanguage(): String {
    val tag = LocalConfiguration.current.locales[0].language.lowercase()
    return if (tag == "nb" || tag == "nn") "no" else tag
}

/**
 * The name of a language, written in that language. Shown beside text the
 * manufacturer never translated, so it is clear why it is not in your own.
 */
fun languageName(code: String): String = Locales.NAMES[code] ?: code.uppercase()
