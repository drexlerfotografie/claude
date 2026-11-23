package app.fotosortierer.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.fotosortierer.core.MediaItem
import coil.compose.AsyncImage
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMAN)
val DATETIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.GERMAN)
val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)

fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.GERMAN, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.GERMAN, "%.1f MB", bytes / (1L shl 20).toDouble())
    else -> String.format(Locale.GERMAN, "%.0f KB", bytes / 1024.0)
}

/** Pfad kürzer anzeigen: /storage/emulated/0/DCIM/Camera -> DCIM/Camera */
fun shortPath(path: String, root: String): String =
    if (path.startsWith("$root/")) path.removePrefix("$root/") else if (path == root) "Interner Speicher" else path

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Thumb(
    item: MediaItem,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    selected: Boolean = false,
    dimmed: Boolean = false,
    badge: String? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(6.dp)
    val base = if (size != null) modifier.size(size) else modifier.aspectRatio(1f)
    Box(
        base
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = File(item.path),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(if (dimmed) 0.3f else 1f),
        )
        if (item.isVideo) {
            Icon(Icons.Default.PlayCircle, "Video", tint = Color.White, modifier = Modifier.align(Alignment.Center).size(24.dp))
        }
        if (!item.source.reliable) {
            Icon(
                Icons.Default.Warning, "Datum unsicher", tint = Color(0xFFFFB300),
                modifier = Modifier.align(Alignment.TopStart).padding(2.dp).size(16.dp),
            )
        }
        if (selected) {
            Icon(
                Icons.Default.CheckCircle, "Ausgewählt", tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(18.dp).background(Color.White, RoundedCornerShape(50)),
            )
        }
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 3.dp),
            )
        }
    }
}

fun openExternally(ctx: Context, item: MediaItem) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(item.path))
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, if (item.isVideo) "video/*" else "image/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "Keine passende App gefunden", Toast.LENGTH_SHORT).show()
    }
}

/** Großansicht mit Infos zur Datei. */
@Composable
fun DetailDialog(item: MediaItem, root: String, extra: String? = null, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
        dismissButton = { TextButton(onClick = { openExternally(ctx, item) }) { Text("Öffnen") } },
        title = { Text(item.name, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    AsyncImage(
                        model = File(item.path),
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    )
                }
                InfoRow("Aufnahme", item.taken.format(DATETIME_FORMAT))
                InfoRow("Quelle", item.source.label)
                InfoRow("Ordner", shortPath(item.folder, root))
                InfoRow("Größe", formatSize(item.size))
                if (extra != null) Text(extra, style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
