package de.mediclever.tagebuch.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File

/** Teilt eine Datei aus dem Cache-Ordner "share" ueber das System-Menue. */
fun shareFile(context: Context, file: File, mime: String, title: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun shareDir(context: Context): File = File(context.cacheDir, "share").apply { mkdirs() }

/** Karte einer Auswertung, die sich als Bild teilen laesst. */
@Composable
fun SharePanel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val bg = MaterialTheme.colorScheme.surface
    Panel(
        modifier.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        },
        title = title, padding = 14.dp,
        action = {
            IconButton({
                scope.launch {
                    val bmp = layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
                    // Hintergrund unterlegen, damit die Karte auch ausserhalb der App lesbar ist
                    val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                    android.graphics.Canvas(out).apply { drawColor(android.graphics.Color.argb(255, (bg.red * 255).toInt(), (bg.green * 255).toInt(), (bg.blue * 255).toInt())); drawBitmap(bmp, 0f, 0f, null) }
                    val f = File(shareDir(context), "auswertung.png")
                    f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    shareFile(context, f, "image/png", "Auswertung teilen")
                }
            }, Modifier.size(40.dp)) { Icon(Icons.Rounded.Share, "$title als Bild teilen", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        content = content,
    )
}
