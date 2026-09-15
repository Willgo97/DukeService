package nl.dejongduke.service.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import nl.dejongduke.service.R

/**
 * The three things an engineer does with a find: pin it for later, copy the
 * number into an order, or send it to whoever is on the phone.
 */
@Composable
fun ActionRow(
    pinKey: String,
    pinned: Boolean,
    onPin: (String) -> Unit,
    shareText: String,
    copyText: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(onClick = { onPin(pinKey) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.PushPin, null, Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(if (pinned) stringResource(R.string.pinned) else stringResource(R.string.pin), style = MaterialTheme.typography.labelLarge)
        }
        if (copyText != null) {
            FilledTonalButton(
                onClick = { copyToClipboard(context, copyText) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.ContentCopy, null, Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text(stringResource(R.string.copy), style = MaterialTheme.typography.labelLarge)
            }
        }
        FilledTonalButton(
            onClick = { share(context, shareText) },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Filled.Share, null, Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(stringResource(R.string.share), style = MaterialTheme.typography.labelLarge)
        }
    }
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("DUKE Service", text))
    // Android 13 and up shows its own confirmation; older versions need one.
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, context.getString(R.string.copied, text), Toast.LENGTH_SHORT).show()
    }
}

fun share(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
}
