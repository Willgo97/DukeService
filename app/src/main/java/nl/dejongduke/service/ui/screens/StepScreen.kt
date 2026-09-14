package nl.dejongduke.service.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.dejongduke.service.R
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.ui.Pill

/** One step of a job: what to do, and the picture that goes with it. */
data class StepPage(
    val number: String,
    val points: List<String>,
    val notes: List<String> = emptyList(),
    val images: List<String> = emptyList(),
)

/**
 * A job one step at a time, swiped through like the paper card is flipped.
 *
 * Standing in front of an open machine, a page that scrolls past twelve steps
 * is the wrong shape: you want the step you are on, large, with its picture,
 * and nothing else. Swipe or tap to go on; the bar at the bottom says how far
 * along you are.
 */
@Composable
fun StepPlayer(title: String, subtitle: String, steps: List<StepPage>) {
    if (steps.isEmpty()) {
        EmptyStepState()
        return
    }
    val pager = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()
    var done by remember { mutableStateOf(setOf<Int>()) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge,
                 fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val step = steps[index]
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (step.number.isNotEmpty()) {
                        Text(
                            step.number,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    if (done.contains(index)) Pill(stringResource(R.string.afgevinkt))
                }
                Spacer(Modifier.height(10.dp))
                step.points.forEach { point ->
                    Text(stringResource(R.string.x_6, point), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(10.dp))
                }
                step.notes.forEach { note ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(12.dp),
                    ) {
                        Text(note, style = MaterialTheme.typography.bodyLarge,
                             color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                step.images.forEach { image ->
                    AssetImage(image)
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            LinearProgressIndicator(
                progress = { (pager.currentPage + 1f) / steps.size },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                    enabled = pager.currentPage > 0,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.height(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.terug))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${pager.currentPage + 1} van ${steps.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    done = if (done.contains(pager.currentPage)) done - pager.currentPage
                    else done + pager.currentPage
                    scope.launch {
                        if (pager.currentPage < steps.size - 1) {
                            pager.animateScrollToPage(pager.currentPage + 1)
                        }
                    }
                }) {
                    Icon(
                        if (pager.currentPage == steps.size - 1) Icons.Filled.Check
                        else Icons.AutoMirrored.Filled.ArrowForward,
                        null, Modifier.height(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (pager.currentPage == steps.size - 1) stringResource(R.string.klaar) else stringResource(R.string.volgende))
                }
            }
        }
    }
}

@Composable
private fun EmptyStepState() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.geen_stappen), style = MaterialTheme.typography.titleMedium)
    }
}

/** The steps of a maintenance card, ready for the player. */
fun Catalog.cardSteps(id: String): List<StepPage> =
    card(id)?.steps.orEmpty().map { step ->
        StepPage(step.number, step.points, step.notes, step.images)
    }.filter { it.points.isNotEmpty() || it.images.isNotEmpty() }

/** The steps of a procedure, ready for the player. */
fun Catalog.procedureSteps(id: String): List<StepPage> {
    val procedure = procedure(id) ?: return emptyList()
    return procedure.steps.mapIndexed { index, step ->
        StepPage((index + 1).toString(), listOf(step.text) + step.sub)
    }
}
