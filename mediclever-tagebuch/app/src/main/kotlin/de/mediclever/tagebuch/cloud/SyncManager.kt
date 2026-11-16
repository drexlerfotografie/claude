package de.mediclever.tagebuch.cloud

import de.mediclever.tagebuch.BuildConfig
import de.mediclever.tagebuch.core.sync.SyncEngine
import de.mediclever.tagebuch.data.DiaryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stoesst den Abgleich an: nach Aenderungen (mit kurzer Verzoegerung), beim Oeffnen der App
 * und nach der Anmeldung. Gleicht nur ab, wenn das angemeldete Konto zum Geraet passt.
 */
class SyncManager(private val repo: DiaryRepository, private val auth: AuthManager, private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private var pending: Job? = null
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    /** Konto angemeldet, aber das Geraet haengt noch an einem anderen: Der Nutzer muss entscheiden. */
    suspend fun needsDecision(): Boolean {
        val uid = auth.account.value?.uid ?: return false
        val last = repo.prefs.current().lastUid
        return last.isNotEmpty() && last != uid
    }

    fun requestSoon() {
        pending?.cancel()
        pending = scope.launch { delay(2500); syncNow() }
    }

    suspend fun syncNow(): String? {
        val uid = auth.account.value?.uid ?: return null
        if (needsDecision()) return null
        return mutex.withLock {
            _running.value = true
            val error = try {
                val remote = FirestoreRemote(uid)
                val local = repo.prefs.current()
                if (local.lastUid != uid) repo.prefs.setLastUid(uid)
                remote.touchProfile(local.consentAt, BuildConfig.VERSION_NAME)
                SyncEngine(repo, remote).syncOnce()
                null
            } catch (e: Exception) {
                when (e) {
                    is com.google.firebase.FirebaseNetworkException -> "Keine Verbindung"
                    is com.google.firebase.firestore.FirebaseFirestoreException ->
                        if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE) "Keine Verbindung"
                        else if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) "Der Server hat den Zugriff abgelehnt"
                        else "Abgleich fehlgeschlagen (${e.code.name})"
                    else -> "Abgleich fehlgeschlagen"
                }
            }
            repo.prefs.setSyncResult(System.currentTimeMillis(), error ?: "")
            _running.value = false
            error
        }
    }

    /** Lokale Daten in das neu angemeldete Konto uebernehmen. */
    suspend fun adoptLocalData() {
        val uid = auth.account.value?.uid ?: return
        repo.markEverythingDirty()
        repo.prefs.setLastUid(uid)
        syncNow()
    }

    /** Geraet leeren und den Bestand des angemeldeten Kontos laden. Einwilligung und Einrichtung bleiben erledigt. */
    suspend fun replaceLocalData() {
        val uid = auth.account.value?.uid ?: return
        val keep = repo.prefs.current()
        repo.wipeLocal()
        repo.prefs.setConsent(keep.consentAt)
        repo.prefs.setOnboardingDone(true)
        repo.prefs.setLastUid(uid)
        syncNow()
    }

    /** Konto samt aller Daten auf dem Server und auf dem Geraet loeschen. */
    suspend fun deleteEverything() {
        val uid = auth.account.value?.uid
        if (uid != null) {
            try { FirestoreRemote(uid).wipeAll() } catch (e: Exception) { throw CloudException("Die Daten auf dem Server konnten nicht gelöscht werden. Prüfe dein Netz und versuche es noch einmal.", e) }
            auth.deleteAccount()
        }
        repo.wipeLocal()
    }
}
