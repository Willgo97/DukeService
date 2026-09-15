package nl.dejongduke.service.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.dejongduke.service.data.SafetyNote
import nl.dejongduke.service.ui.theme.warnColor
import androidx.compose.runtime.getValue

/**
 * Section label in the service-menu idiom: a short gold marker, then the label
 * in small grey caps. The colour carries the structure, not the text.
 */
@Composable
fun SectionHeader(text: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 3.dp, height = 12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.4.sp,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
fun Pill(
    text: String,
    selected: Boolean = false,
    tone: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val bg = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        tone != null -> tone.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        tone != null -> tone
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Monospaced badge for part numbers — the thing engineers compare character by character. */
@Composable
fun PartNumber(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            // Tapping a part number puts it on the clipboard, ready for the
            // order system.
            .clickable { copyToClipboard(context, text) }
            .padding(horizontal = 7.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelLarge,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Card(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (highlight) Modifier.border(
                    2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)
                ) else Modifier
            )
            .then(
                when {
                    onLongClick != null -> Modifier.combinedClickable(
                        onClick = onClick ?: {}, onLongClick = onLongClick,
                    )
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                }
            ),
        color = if (highlight) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainer,
        content = { Box(Modifier.padding(14.dp)) { content() } },
    )
}

@Composable
fun WarnBanner(item: SafetyNote, modifier: Modifier = Modifier) {
    val tone = warnColor(item.level)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tone.copy(alpha = 0.10f))
            .border(1.dp, tone.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Icon(Icons.Filled.Warning, null, tint = tone, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                item.level.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = tone,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(item.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Horizontal row of filter chips that scrolls when there are more machines than fit. */
@Composable
fun ChipRow(
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
) {
    val state = rememberLazyListState()
    // With eleven machines the chosen one is often off the right edge, which
    // reads as nothing being chosen at all.
    LaunchedEffect(selected, options.size) {
        val index = options.indexOfFirst { it.first == selected }
        if (index >= 0) state.animateScrollToItem(index)
    }
    LazyRow(
        Modifier.fillMaxWidth(),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options.size) { index ->
            val (id, label) = options[index]
            Pill(label, selected = id == selected) { onSelect(id) }
        }
    }
}

@Composable
fun EmptyState(title: String, hint: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Decode an asset no larger than it is going to be shown.
 *
 * A drawing sheet is 1400 pixels wide and costs four bytes a pixel decoded; a
 * parts section with a dozen sheets would run the app out of memory long
 * before the engineer scrolled to the bottom of it.
 */
fun decodeAsset(context: android.content.Context, path: String, maxWidth: Int): ImageBitmap? =
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, maxWidth)
        }
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
            ?.asImageBitmap()
    }.getOrNull()

private fun sampleSize(width: Int, maxWidth: Int): Int {
    var sample = 1
    while (maxWidth > 0 && width / (sample * 2) >= maxWidth) sample *= 2
    return sample
}

/** An image from the app's assets, decoded off the main thread. */
@Composable
fun AssetPhoto(
    path: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    val screen = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current.density
    val bitmap by produceState<ImageBitmap?>(null, path, screen) {
        value = withContext(Dispatchers.IO) {
            decodeAsset(context, path, (screen * density).toInt())
        }
    }
    val image = bitmap
    if (image != null) {
        Image(image, contentDescription = null, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier)
    }
}
