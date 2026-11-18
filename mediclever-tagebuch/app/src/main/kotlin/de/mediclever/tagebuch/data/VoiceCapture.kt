package de.mediclever.tagebuch.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * Nimmt eine Sprachnotiz auf und laesst sie zugleich vom Geraet in Text umsetzen. Der Ton wird
 * selbst aufgezeichnet (16 kHz, mono, als WAV-Datei) und ab Android 13 als Strom an die
 * Spracherkennung des Geraets weitergereicht; so entstehen Aufnahme und Text aus derselben Quelle.
 * Auf aelteren Geraeten oder ohne Spracherkennung bleibt es bei der Aufnahme, der Text wird getippt.
 * Inhalte landen nie in Logs.
 */
class VoiceCapture(private val context: Context, private val target: File) {
    data class State(val recording: Boolean = false, val text: String = "", val recognising: Boolean = false, val recognitionAvailable: Boolean = false, val failed: Boolean = false, val done: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    @Volatile private var running = false
    private var record: AudioRecord? = null
    private var worker: Thread? = null
    private var recognizer: SpeechRecognizer? = null
    private var pipeOut: OutputStream? = null
    private val finalParts = mutableListOf<String>()
    private var startedAt = 0L
    var durationMs = 0L; private set

    /** Auf dem Hauptfaden aufrufen, die Mikrofon-Erlaubnis muss erteilt sein. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val rec = runCatching { AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4) }.getOrNull()
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) { rec?.release(); return false }
        val canRecognise = Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isRecognitionAvailable(context)
        if (canRecognise) startRecognizer()
        runCatching { rec.startRecording() }.onFailure { rec.release(); stopRecognizer(); return false }
        record = rec; running = true; startedAt = System.currentTimeMillis()
        _state.value = State(recording = true, recognising = pipeOut != null, recognitionAvailable = pipeOut != null)
        worker = thread(name = "sprachnotiz") {
            val buffer = ByteArray(min)
            var bytes = 0L
            runCatching {
                RandomAccessFile(target, "rw").use { file ->
                    file.setLength(0); file.write(ByteArray(44))
                    while (running) {
                        val n = rec.read(buffer, 0, buffer.size)
                        if (n <= 0) continue
                        file.write(buffer, 0, n); bytes += n
                        // die Erkennung darf die Aufnahme nie aufhalten oder abbrechen
                        pipeOut?.let { out -> runCatching { out.write(buffer, 0, n) }.onFailure { pipeOut = null } }
                    }
                    file.seek(0); file.write(wavHeader(bytes))
                }
            }
            runCatching { rec.stop() }; rec.release()
            runCatching { pipeOut?.close() }; pipeOut = null
        }
        return true
    }

    private fun startRecognizer() {
        if (Build.VERSION.SDK_INT < 33) return
        runCatching {
            val pipe = ParcelFileDescriptor.createPipe()
            val r = SpeechRecognizer.createSpeechRecognizer(context)
            r.setRecognitionListener(object : RecognitionListener {
                override fun onPartialResults(partialResults: Bundle?) { show(first(partialResults)) }
                override fun onSegmentResults(segmentResults: Bundle) { first(segmentResults).takeIf { it.isNotBlank() }?.let { finalParts += it }; show("") }
                override fun onResults(results: Bundle?) { first(results).takeIf { it.isNotBlank() }?.let { finalParts += it }; finish(false) }
                override fun onEndOfSegmentedSession() { finish(false) }
                override fun onError(error: Int) { finish(finalParts.isEmpty()) }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, RATE)
                .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
            r.startListening(intent)
            recognizer = r
            pipeOut = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        }.onFailure { stopRecognizer() }
    }

    private fun first(b: Bundle?): String = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private fun show(partial: String) {
        _state.value = _state.value.copy(text = (finalParts + partial).filter { it.isNotBlank() }.joinToString(" "))
    }

    private fun finish(failed: Boolean) {
        show("")
        _state.value = _state.value.copy(recognising = false, failed = failed, done = !running)
        if (!running) stopRecognizer()
    }

    private fun stopRecognizer() {
        runCatching { recognizer?.destroy() }; recognizer = null
        runCatching { pipeOut?.close() }; pipeOut = null
    }

    /** Beendet die Aufnahme. Der Text kann kurz danach noch vollstaendiger werden (state.recognising). */
    fun stop() {
        if (!running) return
        running = false
        durationMs = System.currentTimeMillis() - startedAt
        runCatching { worker?.join(1500) }
        val waiting = recognizer != null && _state.value.recognising
        _state.value = _state.value.copy(recording = false, done = !waiting)
    }

    /** Aufraeumen. discard loescht die Datei. */
    fun release(discard: Boolean) {
        running = false
        runCatching { worker?.join(1500) }
        stopRecognizer()
        if (discard) target.delete()
    }

    private fun wavHeader(dataBytes: Long): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt((36 + dataBytes).toInt()); put("WAVE".toByteArray())
        put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(dataBytes.toInt())
    }.array()

    companion object { const val RATE = 16_000 }
}
