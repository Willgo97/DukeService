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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.dejongduke.service.R
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.Plate
import nl.dejongduke.service.data.Scanner
import nl.dejongduke.service.ui.Route

/** How long a plate the camera has left stays on screen. */
private const val PLATE_KEEP_ALIVE_MS = 30_000L

/**
 * Reads the type plate inside the door, and points the whole app at that
 * machine.
 *
 * The plate is one thing, not a list of finds, so it gets a scanner of its
 * own: nothing here looks for part numbers or screen messages, and the fields
 * fill in frame by frame. That matters because the plate usually sits behind a
 * milk cooler, and all you can get is a slanted look at half of it at a time.
 */
@Composable
fun PlateScanScreen(
    catalog: Catalog,
    direct: Boolean,
    onUseMachine: (String, String?) -> Unit,
    onOpen: (Route) -> Unit,
    onScanParts: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember(catalog) { Scanner(catalog) }
    var plate by remember { mutableStateOf<Plate?>(null) }
    var plateSeen by remember { mutableStateOf(0L) }
    // The camera is looking at a plate but has not read a field off it yet.
    var pending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    // The plate is complete and the app has already jumped to that machine;
    // frames keep coming, and each one would stack the screen again.
    var jumped by remember { mutableStateOf(false) }

    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val readingPhoto = stringResource(R.string.reading_photo)
    val cannotOpenPhoto = stringResource(R.string.cannot_open_the_photo)
    val noPlateInPhoto = stringResource(R.string.no_type_plate_in_the_photo)
    val readFailed = stringResource(R.string.reading_failed, "")

    /** What this frame read, folded into what was already on screen. */
    fun accept(reading: Plate, now: Long) {
        pending = reading.isEmpty
        val known = plate
        // A different type code is a different machine — the engineer has
        // moved on to the next one, so start that plate over.
        val fresh = known == null ||
            (reading.code.isNotEmpty() && known.code.isNotEmpty() && reading.code != known.code)
        val filled = if (fresh) reading else known.merge(reading)
        plate = filled.takeIf { !it.isEmpty }
        plateSeen = now
        message = ""
        if (direct && !jumped && filled.complete && filled.machine != null) {
            jumped = true
            onUseMachine(filled.machine.id, filled.build.ifEmpty { null })
            onOpen(Route.Machine(filled.machine.id))
        }
    }

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
                        val reading = scanner.readPlate(lines)
                        if (reading == null || reading.isEmpty) message = noPlateInPhoto
                        else accept(reading, System.currentTimeMillis())
                    }
                    .addOnFailureListener { message = readFailed + (it.message ?: "") }
            }
    }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            // Tapping the picture means "this is another machine, start over".
            detectTapGestures {
                plate = null
                pending = false
                message = ""
            }
        },
    ) {
        CameraAccess(
            explanation = stringResource(R.string.point_at_the_type_plate_inside_the_door_the_a),
            onPickPhoto = {
                pickPhoto.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
        ) {
            CameraReader(ENGRAVED_TEXT) { lines ->
                val now = System.currentTimeMillis()
                scope.launch {
                    val reading = withContext(Dispatchers.Default) { scanner.readPlate(lines) }
                    when {
                        reading != null -> accept(reading, now)
                        // The plate has left the picture. What was read off it
                        // stays a good while: reading it is fiddly enough
                        // without losing it the moment the phone dips.
                        plate != null && now - plateSeen > PLATE_KEEP_ALIVE_MS -> {
                            plate = null
                            pending = false
                        }
                        else -> pending = false
                    }
                }
            }

            // viewfinder guide, in the shape of a plate
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .fillMaxWidth(0.86f)
                    .height(190.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(14.dp)),
            )

            Column(
                Modifier.align(Alignment.TopCenter).padding(top = 245.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Banner(
                    when {
                        message.isNotEmpty() -> message
                        plate?.complete == true -> stringResource(R.string.type_plate_found)
                        plate != null || pending ->
                            stringResource(R.string.hold_still_reading_the_plate)
                        else -> stringResource(R.string.point_at_the_type_plate)
                    }
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotoButton {
                        pickPhoto.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    FilledTonalButton(onClick = onScanParts) {
                        Icon(Icons.Filled.Build, null, modifier = Modifier.height(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.part_or_message))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = plate != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                plate?.let { PlateCard(it, onUseMachine, onOpen) }
            }
        }
    }

    DisposableEffect(Unit) { onDispose { plate = null } }
}

/**
 * The type plate as it fills up: what has been read, what is still missing,
 * and — the moment the machine is known — the one button that matters.
 */
@Composable
private fun PlateCard(
    plate: Plate,
    onUseMachine: (String, String?) -> Unit,
    onOpen: (Route) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (plate.complete) Icons.Filled.CheckCircle else Icons.Filled.Build, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.type_plate), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (!plate.complete) {
                CircularProgressIndicator(
                    Modifier.height(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        PlateRow(stringResource(R.string.type_code), plate.code, mono = true)
        PlateRow(stringResource(R.string.serial_number_label), plate.serial, mono = true)
        PlateRow(stringResource(R.string.model_label), plate.model)
        PlateRow(stringResource(R.string.built_label), plate.built)

        plate.machine?.let { machine ->
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    onUseMachine(machine.id, plate.build.ifEmpty { null })
                    onOpen(Route.Machine(machine.id))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    listOfNotNull(machine.name, plate.build.ifEmpty { null })
                        .joinToString(" · ")
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.use_this_machine),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun PlateRow(label: String, value: String, mono: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            value.ifEmpty { stringResource(R.string.not_read_yet) },
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = if (mono && value.isNotEmpty()) FontFamily.Monospace else FontFamily.Default,
            color = if (value.isEmpty()) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
