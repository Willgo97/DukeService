package nl.dejongduke.service.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.dejongduke.service.data.Hotspot

/**
 * The exploded drawing with its balloon numbers made tappable.
 *
 * The balloons sit in the same transformed layer as the drawing, so they stay
 * on their number while you pinch and drag.
 */
@Composable
fun DrawingView(
    path: String,
    balloons: List<Hotspot>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }

    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offsetX by remember(path) { mutableFloatStateOf(0f) }
    var offsetY by remember(path) { mutableFloatStateOf(0f) }
    val image = bitmap

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(
                if (image != null) Modifier.aspectRatio(image.width.toFloat() / image.height)
                else Modifier.height(240.dp)
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (image == null) {
            CircularProgressIndicator(Modifier.size(28.dp))
            return@Box
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .pointerInput(path) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
        ) {
            val breedte = maxWidth
            val hoogte = maxHeight
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offsetX, translationY = offsetY,
                    ),
            ) {
                Image(
                    bitmap = image,
                    contentDescription = "Explosietekening",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                balloons.forEach { spot ->
                    val active = spot.pos == selected
                    val diameter = (spot.r * 2.6f * breedte.value).coerceIn(22f, 60f).dp
                    Box(
                        Modifier
                            .offset(
                                x = breedte * spot.x - diameter / 2,
                                y = hoogte * spot.y - diameter / 2,
                            )
                            .size(diameter)
                            .clip(CircleShape)
                            .then(
                                if (active) Modifier.background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                ) else Modifier
                            )
                            .border(
                                width = if (active) 2.dp else 1.dp,
                                color = if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                shape = CircleShape,
                            )
                            .clickable { onSelect(spot.pos) },
                    )
                }
            }
        }

        if (selected != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    "pos $selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
