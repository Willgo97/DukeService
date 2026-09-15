package nl.dejongduke.service.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CoffeeMaker
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import nl.dejongduke.service.R
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.ScanHit
import nl.dejongduke.service.data.Scanner
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.categoryLabel
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import java.util.concurrent.Executors

/** Below this the camera is guessing, and a guess on a machine is worse than nothing. */
private const val LIVE_THRESHOLD = 75

/** A photo is chosen on purpose, so it may be read a little more generously. */
private const val PHOTO_THRESHOLD = 60

/** An exact part number or a message word for word needs no second opinion. */
private const val CERTAIN = 95

/** How often a weaker result has to come back before it is shown. */
private const val CONFIRMATIONS = 2

/** How long a result stays on screen after the camera last saw it. */
private const val KEEP_ALIVE_MS = 8_000L

/** After reading a photo, live frames are left alone for a while. */
private const val PHOTO_PAUSE_MS = 20_000L

/**
 * Point the camera at whatever is in front of you — a part label, the type
 * plate, or the machine's own display — and the app says what it is.
 *
 * Recognition runs on the device: no connection needed, which matters in the
 * plant rooms these machines live in.
 */
@Composable
fun ScanScreen(
    catalog: Catalog,
    direct: Boolean,
    onUseMachine: (String, String?) -> Unit,
    onOpen: (Route) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Info, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(40.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.camera_needed), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.point_at_a_part_label_the_type_plate_or_the),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.allow_camera)) }
        }
        return
    }

    // The scanner indexes every part number; building that in composition
    // freezes the screen on the way in.
    val scanner by produceState<Scanner?>(null, catalog) {
        value = withContext(Dispatchers.Default) { Scanner(catalog) }
    }
    var hits by remember { mutableStateOf<List<ScanHit>>(emptyList()) }
    var fromPhoto by remember { mutableStateOf(false) }
    // What the camera has seen lately, keyed by hit. A label drifts out of
    // frame while you are still reading it, and one frame of a bad angle
    // should not throw away what was on screen a moment ago.
    var seen by remember { mutableStateOf<Map<String, Sighting>>(emptyMap()) }
    var photoUntil by remember { mutableStateOf(0L) }

    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var message by remember { mutableStateOf("") }
    // Read here: the callbacks below run outside composition.
    val readingPhoto = stringResource(R.string.reading_photo)
    val cannotOpenPhoto = stringResource(R.string.cannot_open_the_photo)
    val noTextInPhoto = stringResource(R.string.no_text_in_the_photo)
    val readNothingFound = stringResource(R.string.text_read_nothing_recognised)
    val readFailed = stringResource(R.string.reading_failed, "")
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
                        // A photo is a deliberate choice, so read it more
                        // generously than a frame that happened to go by.
                        val found = (scanner ?: return@addOnSuccessListener)
                            .scan(lines, PHOTO_THRESHOLD)
                        hits = found
                        seen = emptyMap()
                        fromPhoto = true
                        photoUntil = System.currentTimeMillis() + PHOTO_PAUSE_MS
                        message = if (found.isEmpty()) {
                            if (lines.isEmpty()) noTextInPhoto
                            else readNothingFound + lines.take(3).joinToString(" · ")
                        } else ""
                    }
                    .addOnFailureListener { message = readFailed + (it.message ?: "") }
            }
    }

    Box(Modifier.fillMaxSize()) {
        CameraPreview { lines ->
            val reader = scanner ?: return@CameraPreview
            if (fromPhoto && System.currentTimeMillis() < photoUntil) return@CameraPreview
            fromPhoto = false
            val found = reader.scan(lines, LIVE_THRESHOLD)
            val now = System.currentTimeMillis()
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

            // Show a result once the camera has seen it twice, or straight away
            // when it is beyond doubt: an exact part number or the whole
            // message word for word.
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
            if (direct && shown.size == 1 && shown.first().confidence >= CERTAIN) {
                routeFor(shown.first())?.let(onOpen)
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
            Box(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    when {
                        message.isNotEmpty() -> message
                        hits.isEmpty() && seen.isNotEmpty() -> stringResource(R.string.hold_still)
                        hits.isEmpty() -> stringResource(R.string.point_at_a_label_type_plate_or_the_screen)
                        fromPhoto -> stringResource(R.string.found_in_the_photo, hits.size)
                        else -> stringResource(R.string.found_2, hits.size)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Filled.PhotoLibrary, null, modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.from_photo))
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
                    items(hits.size) { index ->
                        HitCard(catalog, hits[index], onUseMachine, onOpen)
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) { onDispose { hits = emptyList() } }
}

/** One result the camera saw, how often, and when it last did. */
private data class Sighting(val hit: ScanHit, val times: Int, val lastSeen: Long)

@Composable
private fun HitCard(
    catalog: Catalog,
    hit: ScanHit,
    onUseMachine: (String, String?) -> Unit,
    onOpen: (Route) -> Unit,
) {
    when (hit) {
        is ScanHit.PartHit -> HitRow(
            Icons.Filled.Build,
            hit.part.number,
            hit.part.description,
            "${catalog.machine(hit.part.machine)?.name ?: hit.part.machine}  ·  ${hit.part.section}",
            hit.confidence,
            mono = true,
        ) { onOpen(Route.PartSection(hit.part.machine, hit.part.variant, hit.part.section)) }

        is ScanHit.FaultHit -> HitRow(
            Icons.Filled.WarningAmber,
            hit.group.message,
            hit.group.first.dutch,
            stringResource(R.string.fault_2, categoryLabel(hit.group.first.category)),
            hit.confidence,
        ) { onOpen(Route.Fault(hit.group.message)) }

        is ScanHit.MachineHit -> HitRow(
            Icons.Filled.CoffeeMaker,
            hit.machine.name,
            hit.machine.summary,
            stringResource(R.string.machine_tap_to_point_the_app_at_it),
            hit.confidence,
        ) {
            onUseMachine(hit.machine.id, null)
            onOpen(Route.Machine(hit.machine.id))
        }

        // The type plate is the one moment the app knows exactly which machine
        // it is standing in front of. Take it: from here on every list is that
        // machine's list, without anyone picking it from a row of chips.
        is ScanHit.TypePlate -> HitRow(
            Icons.Filled.Info,
            listOfNotNull(
                hit.machine?.name ?: stringResource(R.string.type_plate),
                hit.build.ifEmpty { null },
            ).joinToString(" · "),
            listOfNotNull(
                hit.serienummer.ifEmpty { null }
                    ?.let { stringResource(R.string.serial_number, it) },
                hit.built.ifEmpty { null },
            ).joinToString("  ·  "),
            if (hit.code.isNotEmpty()) stringResource(R.string.type_code_tap_to_point_the_app_at_it, hit.code)
            else stringResource(R.string.from_the_type_plate),
            hit.confidence,
        ) {
            hit.machine?.let { machine ->
                onUseMachine(machine.id, hit.build.ifEmpty { null })
                onOpen(Route.Machine(machine.id))
            }
        }

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

/** CameraX preview with ML Kit text recognition on every other frame. */
@Composable
private fun CameraPreview(onText: (List<String>) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val view = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = view.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy -> analyse(proxy, recognizer, onText) }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            recognizer.close()
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun analyse(
    proxy: ImageProxy,
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    onText: (List<String>) -> Unit,
) {
    val media = proxy.image
    if (media == null) {
        proxy.close()
        return
    }
    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    recognizer.process(image)
        .addOnSuccessListener { result ->
            val lines = result.textBlocks.flatMap { block -> block.lines.map { it.text } }
            if (lines.isNotEmpty()) onText(lines)
        }
        .addOnCompleteListener { proxy.close() }
}

/** Where a scan result leads. */
private fun routeFor(hit: ScanHit): Route? = when (hit) {
    is ScanHit.PartHit -> Route.PartSection(hit.part.machine, hit.part.variant, hit.part.section)
    is ScanHit.FaultHit -> Route.Fault(hit.group.message)
    is ScanHit.MachineHit -> Route.Machine(hit.machine.id)
    is ScanHit.TypePlate -> hit.machine?.let { Route.Machine(it.id) }
}
