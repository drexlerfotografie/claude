package app.fotosortierer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import app.fotosortierer.core.DateSource
import app.fotosortierer.core.ExifDate
import app.fotosortierer.core.FilenameDate
import app.fotosortierer.core.MediaItem
import app.fotosortierer.core.MediaTypes
import app.fotosortierer.core.VideoDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Ordner, die nie durchsucht werden. */
const val DUPLICATE_DIR = "_Duplikate"

class MediaRepository(context: Context) {
    private val cacheFile = File(context.filesDir, "datecache.tsv")
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private var cacheLoaded = false

    private data class CacheEntry(val size: Long, val modified: Long, val taken: LocalDateTime, val source: DateSource)

    suspend fun scan(
        folders: List<String>,
        recursive: Boolean,
        progress: (done: Int, total: Int) -> Unit,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        loadCache()
        val files = LinkedHashMap<String, File>()
        for (f in folders) collect(File(f), recursive, files)
        val total = files.size
        val done = AtomicInteger()
        progress(0, total)

        val io = Dispatchers.IO.limitedParallelism(4)
        val items = coroutineScope {
            files.values.chunked(200).map { chunk ->
                async(io) {
                    chunk.mapNotNull { file ->
                        val item = runCatching { read(file) }.getOrNull()
                        val n = done.incrementAndGet()
                        if (n % 50 == 0 || n == total) progress(n, total)
                        item
                    }
                }
            }.awaitAll().flatten()
        }
        saveCache()
        items
    }

    private fun collect(dir: File, recursive: Boolean, out: MutableMap<String, File>) {
        val children = dir.listFiles() ?: return
        for (c in children) {
            if (c.name.startsWith(".")) continue // .thumbnails, .trashed-…, .nomedia
            if (c.isDirectory) {
                if (recursive && c.name != DUPLICATE_DIR && c.path != "/storage/emulated/0/Android/data") collect(c, true, out)
            } else if (MediaTypes.isMedia(c.extension)) {
                out.putIfAbsent(c.canonicalPath, c)
            }
        }
    }

    private fun read(file: File): MediaItem {
        val size = file.length()
        val modified = file.lastModified()
        val isVideo = MediaTypes.isVideo(file.extension)
        val cached = cache[file.path]
        val (taken, source) = if (cached != null && cached.size == size && cached.modified == modified) {
            cached.taken to cached.source
        } else {
            detectDate(file, isVideo, modified).also { (t, s) -> cache[file.path] = CacheEntry(size, modified, t, s) }
        }
        return MediaItem(file.path, size, modified, isVideo, taken, source)
    }

    private fun detectDate(file: File, isVideo: Boolean, modified: Long): Pair<LocalDateTime, DateSource> {
        if (isVideo) videoDate(file)?.let { return it to DateSource.VIDEO }
        else exifDate(file)?.let { return it to DateSource.EXIF }
        FilenameDate.parse(file.name)?.let { return it to DateSource.FILENAME }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(modified), ZoneId.systemDefault()) to DateSource.MODIFIED
    }

    private fun exifDate(file: File): LocalDateTime? = try {
        val exif = ExifInterface(file)
        ExifDate.parse(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            ?: ExifDate.parse(exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED))
            ?: ExifDate.parse(exif.getAttribute(ExifInterface.TAG_DATETIME))
    } catch (t: Throwable) {
        null
    }

    private fun videoDate(file: File): LocalDateTime? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.path)
            VideoDate.parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE))
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** Nach Verschieben den Cache-Eintrag mitnehmen, damit nichts neu gelesen werden muss. */
    fun relocate(from: String, to: String, copy: Boolean) {
        val e = cache[from] ?: return
        cache[to] = e
        if (!copy) cache.remove(from)
    }

    fun persist() = saveCache()

    @Synchronized
    private fun loadCache() {
        if (cacheLoaded) return
        cacheLoaded = true
        if (!cacheFile.exists()) return
        runCatching {
            cacheFile.forEachLine { line ->
                val p = line.split('\t')
                if (p.size == 5) {
                    val src = runCatching { DateSource.valueOf(p[4]) }.getOrNull() ?: return@forEachLine
                    cache[p[0]] = CacheEntry(p[1].toLong(), p[2].toLong(), LocalDateTime.parse(p[3]), src)
                }
            }
        }
    }

    @Synchronized
    private fun saveCache() {
        runCatching {
            val tmp = File(cacheFile.path + ".tmp")
            tmp.bufferedWriter().use { w ->
                for ((path, e) in cache) {
                    if (path.contains('\t') || path.contains('\n')) continue
                    w.write("$path\t${e.size}\t${e.modified}\t${e.taken}\t${e.source.name}\n")
                }
            }
            tmp.renameTo(cacheFile)
        }
    }
}
