package app.fotosortierer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Address
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import app.fotosortierer.core.Label
import app.fotosortierer.core.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/** Ergebnis der Analyse einer Datei. */
data class FileInfo(val labels: List<Label>, val lat: Double?, val lon: Double?)

/** Austauschbare Bausteine, damit Tests ohne ML Kit und ohne Ortsdienst laufen. */
object AnalysisBackend {
    var labeler: (suspend (Context, Bitmap) -> List<Label>)? = null
    var geocoder: (suspend (Context, Double, Double) -> String?)? = null
}

class Analyzer(private val context: Context) {
    private val cacheFile = File(context.filesDir, "analysis.tsv")
    private val cache = ConcurrentHashMap<String, Pair<String, FileInfo>>() // path -> (stamp, info)
    private val places = ConcurrentHashMap<String, String>() // "48.14,11.58" -> Ort
    private var loaded = false

    private val mlLabeler by lazy {
        com.google.mlkit.vision.label.ImageLabeling.getClient(
            com.google.mlkit.vision.label.defaults.ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build()
        )
    }

    suspend fun analyze(item: MediaItem): FileInfo = withContext(Dispatchers.IO) {
        load()
        val stamp = "${item.size}:${item.lastModified}"
        cache[item.path]?.let { (s, info) -> if (s == stamp) return@withContext info }
        val (lat, lon) = location(item)
        val labels = runCatching { thumbnail(item)?.let { label(it) } }.getOrNull().orEmpty()
        val info = FileInfo(labels, lat, lon)
        cache[item.path] = stamp to info
        info
    }

    fun relocate(from: String, to: String) {
        cache[from]?.let { cache[to] = it }
    }

    private suspend fun label(bmp: Bitmap): List<Label> {
        AnalysisBackend.labeler?.let { return it(context, bmp) }
        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bmp, 0)
        return mlLabeler.process(image).await().map { Label(it.text, it.confidence) }
    }

    /** Kleines Vorschaubild (~400 px) – reicht für die Erkennung und ist schnell. */
    private fun thumbnail(item: MediaItem): Bitmap? {
        if (item.isVideo) {
            val r = MediaMetadataRetriever()
            return try {
                r.setDataSource(item.path)
                r.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 400, 400)
            } catch (t: Throwable) {
                null
            } finally {
                runCatching { r.release() }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(item.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 400 && bounds.outHeight / (sample * 2) >= 400) sample *= 2
        return BitmapFactory.decodeFile(item.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun location(item: MediaItem): Pair<Double?, Double?> = try {
        if (item.isVideo) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(item.path)
                val loc = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION) // "+48.1234+011.5678/"
                val m = loc?.let { Regex("""([+-]\d+\.\d+)([+-]\d+\.\d+)""").find(it) }
                if (m != null) m.groupValues[1].toDouble() to m.groupValues[2].toDouble() else null to null
            } finally {
                runCatching { r.release() }
            }
        } else {
            val ll = ExifInterface(item.path).latLong
            if (ll != null && !(ll[0] == 0.0 && ll[1] == 0.0)) ll[0] to ll[1] else null to null
        }
    } catch (t: Throwable) {
        null to null
    }

    /** Ortsname für Koordinaten. Nutzt den Ortsdienst des Systems (Google); Ergebnisse werden gecacht. */
    suspend fun place(lat: Double, lon: Double): String? {
        val key = String.format(Locale.ROOT, "%.2f,%.2f", lat, lon)
        places[key]?.let { return it.ifEmpty { null } }
        val name = runCatching {
            AnalysisBackend.geocoder?.let { return@runCatching it(context, lat, lon) }
            if (!Geocoder.isPresent()) return@runCatching null
            val address = geocode(lat, lon)
            address?.locality ?: address?.subAdminArea ?: address?.adminArea
        }.getOrNull()
        places[key] = name ?: ""
        return name
    }

    private suspend fun geocode(lat: Double, lon: Double): Address? {
        val geocoder = Geocoder(context, Locale.GERMANY)
        return if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                    override fun onError(errorMessage: String?) = cont.resume(null)
                })
            }
        } else withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
        }
    }

    @Synchronized
    private fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            if (!cacheFile.exists()) return
            cacheFile.forEachLine { line ->
                val p = line.split('\t')
                when {
                    p.size == 3 && p[0] == "P" -> places[p[1]] = p[2]
                    p.size == 5 -> {
                        val labels = if (p[2].isEmpty()) emptyList() else p[2].split(';').mapNotNull {
                            val (t, c) = it.split('=').let { x -> if (x.size == 2) x[0] to x[1] else return@mapNotNull null }
                            Label(t, c.toFloatOrNull() ?: return@mapNotNull null)
                        }
                        cache[p[0]] = p[1] to FileInfo(labels, p[3].toDoubleOrNull(), p[4].toDoubleOrNull())
                    }
                }
            }
        }
    }

    @Synchronized
    fun persist() {
        runCatching {
            val tmp = File(cacheFile.path + ".tmp")
            tmp.bufferedWriter().use { w ->
                for ((path, v) in cache) {
                    if (path.contains('\t') || path.contains('\n')) continue
                    val (stamp, info) = v
                    val labels = info.labels.joinToString(";") { "${it.text.replace(Regex("[;=\t\n]"), " ")}=${it.confidence}" }
                    w.write("$path\t$stamp\t$labels\t${info.lat ?: ""}\t${info.lon ?: ""}\n")
                }
                for ((k, v) in places) w.write("P\t$k\t$v\n")
            }
            tmp.renameTo(cacheFile)
        }
    }
}
