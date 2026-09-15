package nl.dejongduke.service.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.dejongduke.service.R
import nl.dejongduke.service.data.Hotspot
import nl.dejongduke.service.ui.decodeAsset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
    // Twice the screen width: sharp when pinched open, half the memory of the
    // full sheet when it is just sitting in the list.
    val screen = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current.density
    val bitmap by produceState<ImageBitmap?>(null, path, screen) {
        value = withContext(Dispatchers.IO) {
            decodeAsset(context, path, (screen * density * 2).toInt())
        }
    }

    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offsetX by remember(path) { mutableFloatStateOf(0f) }
    var offsetY by remember(path) { mutableFloatStateOf(0f) }
    var box by remember(path) { mutableStateOf(IntSize.Zero) }
    val image = bitmap

    /** Keep the drawing inside its frame; dragging it off screen helps nobody. */
    fun clamp() {
        val maxX = (box.width * (scale - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (box.height * (scale - 1f) / 2f).coerceAtLeast(0f)
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }

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
                .onSizeChanged { box = it }
                .pointerInput(path) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                                // zoom towards the point that was tapped
                                offsetX = (box.width / 2f - tap.x) * (scale - 1f)
                                offsetY = (box.height / 2f - tap.y) * (scale - 1f)
                                clamp()
                            }
                        },
                    )
                }
                .pointerInput(path) {
                    // Pinch and drag are handled by hand rather than with
                    // detectTransformGestures, because that consumes every
                    // drag — including the one-finger swipe meant for the list
                    // this drawing sits in. Only a real two-finger gesture, or
                    // a drag while zoomed in, belongs to the drawing.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val mine = pressed >= 2 || scale > 1f
                            if (mine) {
                                if (zoom != 1f) scale = (scale * zoom).coerceIn(1f, 6f)
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                    clamp()
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            val frameWidth = maxWidth
            val frameHeight = maxHeight
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
                    contentDescription = stringResource(R.string.exploded_drawing),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                balloons.forEach { spot ->
                    val active = spot.pos == selected
                    val diameter = (spot.r * 2.6f * frameWidth.value).coerceIn(22f, 60f).dp
                    Box(
                        Modifier
                            .offset(
                                x = frameWidth * spot.x - diameter / 2,
                                y = frameHeight * spot.y - diameter / 2,
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
                    stringResource(R.string.pos, selected),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
