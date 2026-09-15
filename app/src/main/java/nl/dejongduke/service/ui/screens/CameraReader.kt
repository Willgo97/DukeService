package nl.dejongduke.service.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import nl.dejongduke.service.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * A screen message read at arm's length. The camera's own default for analysis
 * is 640x480, on which the letters of a machine display are a few pixels high
 * and the reader returns nothing at all.
 */
val SCREEN_TEXT = Size(1280, 720)

/** Engraved, small, and usually photographed at an angle: the type plate. */
val ENGRAVED_TEXT = Size(1920, 1080)

/**
 * The camera, with every frame handed to the text reader.
 *
 * [onText] is called for every frame that was read, empty list included: a
 * frame without text is how a result the camera has left behind expires.
 */
@Composable
fun CameraReader(resolution: Size, onText: (List<String>) -> Unit) {
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    // The analyser is handed one callback when the camera is bound and keeps
    // it for the life of the screen. Reading the latest one per frame is what
    // keeps it from working with state from before the parts table arrived.
    val callback by rememberUpdatedState(onText)
    // A frame can still be on its way in when the screen closes; handing it to
    // a reader that has been shut is a crash.
    val open = remember { AtomicBoolean(true) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = view.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    resolution,
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                )
                            )
                            .build()
                    )
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    if (open.get()) analyse(proxy, recognizer) { callback(it) } else proxy.close()
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            open.set(false)
            executor.shutdown()
            recognizer.close()
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun analyse(proxy: ImageProxy, recognizer: TextRecognizer, onText: (List<String>) -> Unit) {
    val media = proxy.image
    if (media == null) {
        proxy.close()
        return
    }
    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    recognizer.process(image)
        .addOnSuccessListener { result ->
            onText(result.textBlocks.flatMap { block -> block.lines.map { it.text } })
        }
        .addOnCompleteListener { proxy.close() }
}

/**
 * Asks for the camera, and says what to do when the answer is no.
 *
 * Reading a photo from the gallery needs no camera at all, so that way out
 * stays on screen whatever the answer was.
 */
@Composable
fun CameraAccess(
    explanation: String,
    onPickPhoto: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var asked by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    // Android stops showing the dialog after a second refusal; then the only
    // way back is the settings screen.
    val refusedForGood = asked && !granted &&
        !ActivityCompat.shouldShowRequestPermissionRationale(
            context as Activity, Manifest.permission.CAMERA,
        )

    if (granted) {
        content()
        return
    }
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
            if (refusedForGood) stringResource(R.string.the_camera_was_turned_off_for_this_app_you_c)
            else explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (refusedForGood) {
            Button(onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                )
            }) { Text(stringResource(R.string.open_settings)) }
        } else {
            Button(onClick = { ask.launch(Manifest.permission.CAMERA) }) {
                Text(stringResource(R.string.allow_camera))
            }
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onPickPhoto) {
            Icon(Icons.Filled.PhotoLibrary, null, modifier = Modifier.height(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.from_photo))
        }
    }
}
