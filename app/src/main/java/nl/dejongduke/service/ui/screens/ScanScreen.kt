package nl.dejongduke.service.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.dejongduke.service.R
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.ScanHit
import nl.dejongduke.service.data.Scanner
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import nl.dejongduke.service.ui.categoryLabel

/** Below this the camera is guessing, and a guess on a machine is worse than nothing. */
private const val LIVE_THRESHOLD = 75

/** A photo is chosen on purpose, so it may be read a little more generously. */
private const val PHOTO_THRESHOLD = 60

/**
 * An exact part number, or a message with all its words in the right order,
 * needs no second opinion.
 */
private const val CERTAIN = 92

/** How often a weaker result has to come back before it is shown. */
private const val CONFIRMATIONS = 2

/** How long a result stays on screen after the camera last saw it. */
private const val KEEP_ALIVE_MS = 8_000L

/** After reading a photo, live frames are left alone for a while. */
private const val PHOTO_PAUSE_MS = 20_000L

/**
 * Point the camera at a part label or at the machine's own display, and the
 * app says what it is.
 *
 * Only those two: the type plate has its own scanner, because a plate carries
 * numbers that read like part numbers and a model line that shares words with
 * a screen message. Looking for all three at once made all three worse.
 *
 * Recognition runs on the device: no connection needed, which matters in the
 * plant rooms these machines live in.
 */
@Composable
fun ScanScreen(
    catalog: Catalog,
    /** The machine the app is pointed at, so a part number shows its row. */
    machine: String?,
    direct: Boolean,
    onOpen: (Route) -> Unit,
    onScanPlate: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The scanner indexes every part number; building that in composition
    // freezes the screen on the way in.
    var scanner by remember { mutableStateOf<Scanner?>(null) }
    LaunchedEffect(catalog) {
        scanner = withContext(Dispatchers.Default) { Scanner(catalog) }
    }
    var hits by remember { mutableStateOf<List<ScanHit>>(emptyList()) }
    var fromPhoto by remember { mutableStateOf(false) }
    // What the camera has seen lately, keyed by hit. A label drifts out of
    // frame while you are still reading it, and one frame of a bad angle
    // should not throw away what was on screen a moment ago.
    var seen by remember { mutableStateOf<Map<String, Sighting>>(emptyMap()) }
    var photoUntil by remember { mutableStateOf(0L) }
    // The parts table is read after the app is already usable. A photo taken
    // in those first seconds is kept, so it can go past the scanner again once
    // the numbers are in instead of coming back as "nothing recognised".
    var lastLines by remember { mutableStateOf<List<String>>(emptyList()) }
    // Matching runs off the main thread, and the camera hands over the next
    // frame while it is still busy. Reading one frame at a time keeps the
    // preview smooth and costs nothing: the next frame is 30 ms away.
    var busy by remember { mutableStateOf(false) }
    // "Open straight away" fires off a camera frame, and the frames keep
    // coming while the label is still in view. Without this the same screen
    // lands on the back stack thirty times over.
    var jumped by remember { mutableStateOf(false) }

    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var message by remember { mutableStateOf("") }
    // Read here: the callbacks below run outside composition.
    val readingPhoto = stringResource(R.string.reading_photo)
    val cannotOpenPhoto = stringResource(R.string.cannot_open_the_photo)
    val noTextInPhoto = stringResource(R.string.no_text_in_the_photo)
    val readNothingFound = stringResource(R.string.text_read_nothing_recognised)
    val readFailed = stringResource(R.string.reading_failed, "")
    val partsLoading = stringResource(R.string.the_parts_list_is_still_loading_try_again_in)
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        message = readingPhoto
        runCatching { InputImage.fromFilePath(context, uri) }
            .onFailure { message = cannotOpenPhoto }
            .onSuccess { image ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        val lines = result.textBlocks.flatMap { b -> b.lines.map { it.text } }
                        val reader = scanner ?: return@addOnSuccessListener
                        lastLines = lines
                        scope.launch {
                            // A photo is a deliberate choice, so read it more
                            // generously than a frame that happened to go by.
                            val found = withContext(Dispatchers.Default) {
                                reader.scan(lines, PHOTO_THRESHOLD, machine)
                            }
                            hits = found
                            seen = emptyMap()
                            fromPhoto = true
                            photoUntil = System.currentTimeMillis() + PHOTO_PAUSE_MS
                            message = when {
                                found.isNotEmpty() -> ""
                                lines.isEmpty() -> noTextInPhoto
                                // Saying "nothing recognised" while half the
                                // catalog is still on its way is a lie the
                                // engineer acts on.
                                catalog.parts.isEmpty() -> partsLoading
                                else -> readNothingFound + lines.take(3).joinToString(" · ")
                            }
                        }
                    }
                    .addOnFailureListener { message = readFailed + (it.message ?: "") }
            }
    }

    // The scanner is rebuilt when the parts arrive; the photo that came back
    // empty a moment ago deserves a second pass.
    LaunchedEffect(scanner) {
        val reader = scanner ?: return@LaunchedEffect
        if (lastLines.isEmpty() || hits.isNotEmpty()) return@LaunchedEffect
        val found = withContext(Dispatchers.Default) {
            reader.scan(lastLines, PHOTO_THRESHOLD, machine)
        }
        if (found.isNotEmpty()) {
            hits = found
            fromPhoto = true
            photoUntil = System.currentTimeMillis() + PHOTO_PAUSE_MS
            message = ""
        }
    }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures {
                // Tapping the picture means "read what I am pointing at now".
                photoUntil = 0L
                fromPhoto = false
                hits = emptyList()
                seen = emptyMap()
                message = ""
            }
        },
    ) {
        CameraAccess(
            explanation = stringResource(R.string.point_at_a_part_label_or_the_screen_of_the_ma),
            onPickPhoto = {
                pickPhoto.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
        ) {
            CameraReader(SCREEN_TEXT) { lines ->
                val reader = scanner
                val now = System.currentTimeMillis()
                if (reader == null || busy) return@CameraReader
                if (fromPhoto && now < photoUntil) return@CameraReader
                if (lines.isEmpty() && seen.isEmpty() && hits.isEmpty()) return@CameraReader

                busy = true
                scope.launch {
                    val found = withContext(Dispatchers.Default) {
                        reader.scan(lines, LIVE_THRESHOLD, machine)
                    }
                    busy = false
                    // What was read off a photo stays until the camera has
                    // something of its own to say. Pointing at a wall is not a
                    // reason to throw away what the engineer just looked up.
                    if (fromPhoto && found.isEmpty()) return@launch
                    fromPhoto = false

                    val updated = seen.toMutableMap()
                    for (hit in found) {
                        val key = reader.key(hit)
                        val previous = updated[key]
                        updated[key] = Sighting(
                            hit = if (previous != null && previous.hit.confidence >= hit.confidence) previous.hit else hit,
                            times = (previous?.times ?: 0) + 1,
                            lastSeen = now,
                        )
                    }
                    updated.entries.removeAll { now - it.value.lastSeen > KEEP_ALIVE_MS }
                    seen = updated

                    // Show a result once the camera has seen it twice, or
                    // straight away when it is beyond doubt: an exact part
                    // number, or the message with all its words in order.
                    val shown = updated.values
                        .filter { it.times >= CONFIRMATIONS || it.hit.confidence >= CERTAIN }
                        .sortedByDescending { it.hit.confidence }
                        .map { it.hit }
                        .take(8)
                    if (shown != hits) {
                        hits = shown
                        message = ""
                    }
                    // One unambiguous hit and the setting on: skip the list.
                    if (direct && !jumped && shown.size == 1 && shown.first().confidence >= CERTAIN) {
                        jumped = true
                        onOpen(routeFor(shown.first()))
                    }
                }
            }

            // viewfinder guide
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .fillMaxWidth(0.82f)
                    .height(150.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(14.dp)),
            )

            Column(
                Modifier.align(Alignment.TopCenter).padding(top = 200.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Banner(
                    when {
                        message.isNotEmpty() -> message
                        // Part numbers cannot be found before the table is in.
                        hits.isEmpty() && catalog.parts.isEmpty() ->
                            stringResource(R.string.the_parts_list_is_still_loading_try_again_in)
                        hits.isEmpty() && seen.isNotEmpty() -> stringResource(R.string.hold_still)
                        hits.isEmpty() -> stringResource(R.string.point_at_a_label_or_the_screen)
                        fromPhoto -> stringResource(R.string.found_in_the_photo, hits.size)
                        else -> stringResource(R.string.found_2, hits.size)
                    }
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotoButton {
                        pickPhoto.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    FilledTonalButton(onClick = onScanPlate) {
                        Icon(Icons.Filled.Info, null, modifier = Modifier.height(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.type_plate))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = hits.isNotEmpty(),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                LazyColumn(Modifier.padding(top = 10.dp)) {
                    items(hits.size) { index -> HitCard(catalog, hits[index], onOpen) }
                }
            }
        }
    }

    DisposableEffect(Unit) { onDispose { hits = emptyList() } }
}

/** One result the camera saw, how often, and when it last did. */
private data class Sighting(val hit: ScanHit, val times: Int, val lastSeen: Long)

/** The dark strip over the picture that says what the camera is doing. */
@Composable
internal fun Banner(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

/** Reading a photo from the gallery: the way in when the camera cannot see it. */
@Composable
internal fun PhotoButton(onPick: () -> Unit) {
    FilledTonalButton(onClick = onPick) {
        Icon(Icons.Filled.PhotoLibrary, null, modifier = Modifier.height(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.from_photo))
    }
}

@Composable
private fun HitCard(catalog: Catalog, hit: ScanHit, onOpen: (Route) -> Unit) {
    when (hit) {
        is ScanHit.PartHit -> HitRow(
            Icons.Filled.Build,
            hit.part.number,
            hit.part.description,
            "${catalog.machine(hit.part.machine)?.name ?: hit.part.machine}  ·  ${hit.part.section}",
            hit.confidence,
            mono = true,
        ) { onOpen(routeFor(hit)) }

        is ScanHit.FaultHit -> HitRow(
            Icons.Filled.WarningAmber,
            hit.group.message,
            hit.group.first.dutch,
            stringResource(R.string.fault_2, categoryLabel(hit.group.first.category)),
            hit.confidence,
        ) { onOpen(routeFor(hit)) }
    }
}

@Composable
private fun HitRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    context: String,
    confidence: Int,
    mono: Boolean = false,
    onClick: () -> Unit,
) {
    Card(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(context)
                    if (confidence < 90) Pill("$confidence%")
                }
            }
        }
    }
}

/** Where a scan result leads. */
private fun routeFor(hit: ScanHit): Route = when (hit) {
    is ScanHit.PartHit -> Route.PartSection(hit.part.machine, hit.part.variant, hit.part.section)
    is ScanHit.FaultHit -> Route.Fault(hit.group.message)
}
