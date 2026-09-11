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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import nl.dejongduke.service.data.Catalog
import nl.dejongduke.service.data.ScanHit
import nl.dejongduke.service.data.Scanner
import nl.dejongduke.service.ui.Card
import nl.dejongduke.service.ui.Pill
import nl.dejongduke.service.ui.Route
import java.util.concurrent.Executors

/**
 * Point the camera at whatever is in front of you — a part label, the type
 * plate, or the machine's own display — and the app says what it is.
 *
 * Recognition runs on the device: no connection needed, which matters in the
 * plant rooms these machines live in.
 */
@Composable
fun ScanScreen(catalog: Catalog, onOpen: (Route) -> Unit) {
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
            Text("Camera nodig", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Richt op een onderdeellabel, het typeplaatje of het scherm van de machine. " +
                    "De app leest de tekst op het toestel zelf — er gaat niets naar buiten.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("Camera toestaan") }
        }
        return
    }

    val scanner = remember(catalog) { Scanner(catalog) }
    var hits by remember { mutableStateOf<List<ScanHit>>(emptyList()) }
    var uitFoto by remember { mutableStateOf(false) }

    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var melding by remember { mutableStateOf("") }
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        melding = "Foto lezen…"
        runCatching { InputImage.fromFilePath(context, uri) }
            .onFailure { melding = "Kan de foto niet openen" }
            .onSuccess { image ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        val lines = result.textBlocks.flatMap { b -> b.lines.map { it.text } }
                        val found = scanner.scan(lines)
                        hits = found
                        uitFoto = true
                        melding = if (found.isEmpty()) {
                            if (lines.isEmpty()) "Geen tekst in de foto"
                            else "Tekst gelezen, niets herkend: " + lines.take(3).joinToString(" · ")
                        } else ""
                    }
                    .addOnFailureListener { melding = "Lezen mislukt: ${it.message}" }
            }
    }

    Box(Modifier.fillMaxSize()) {
        CameraPreview { lines ->
            val found = scanner.scan(lines)
            // Keep the last good result on screen: labels drift out of frame
            // while you are still reading them.
            if (found.isNotEmpty()) {
                hits = found
                uitFoto = false
                melding = ""
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
                        melding.isNotEmpty() -> melding
                        hits.isEmpty() -> "Richt op een label, typeplaatje of het scherm"
                        uitFoto -> "${hits.size} gevonden in de foto"
                        else -> "${hits.size} gevonden"
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
                Text("Uit foto")
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

@Composable
private fun HitCard(catalog: Catalog, hit: ScanHit, onOpen: (Route) -> Unit) {
    when (hit) {
        is ScanHit.Onderdeel -> HitRow(
            Icons.Filled.Build,
            hit.part.nummer,
            hit.part.omschrijving,
            "${catalog.machine(hit.part.machine)?.naam ?: hit.part.machine}  ·  ${hit.part.sectie}",
            hit.zekerheid,
            mono = true,
        ) { onOpen(Route.PartSection(hit.part.machine, hit.part.sectie)) }

        is ScanHit.Storing -> HitRow(
            Icons.Filled.WarningAmber,
            hit.groep.melding,
            hit.groep.eerste.nl,
            "Storing  ·  ${hit.groep.eerste.cat}",
            hit.zekerheid,
        ) { onOpen(Route.Fault(hit.groep.melding)) }

        is ScanHit.MachineHit -> HitRow(
            Icons.Filled.CoffeeMaker,
            hit.machine.naam,
            hit.machine.kort,
            "Machine  ·  ${hit.machine.serie}",
            hit.zekerheid,
        ) { onOpen(Route.Machine(hit.machine.id)) }

        is ScanHit.Typeplaat -> HitRow(
            Icons.Filled.Info,
            hit.machine?.naam ?: "Typeplaatje",
            "Serienummer ${hit.serienummer}",
            if (hit.code.isNotEmpty()) "Typecode ${hit.code}" else "Van het typeplaatje",
            hit.zekerheid,
        ) { hit.machine?.let { onOpen(Route.Machine(it.id)) } }
    }
}

@Composable
private fun HitRow(
    icon: ImageVector,
    titel: String,
    onder: String,
    context: String,
    zekerheid: Int,
    mono: Boolean = false,
    onClick: () -> Unit,
) {
    Card(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    titel,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                )
                if (onder.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(onder, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(context)
                    if (zekerheid < 90) Pill("$zekerheid%")
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
