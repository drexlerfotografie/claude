package de.mediclever.tagebuch.cloud

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import de.mediclever.tagebuch.BuildConfig
import de.mediclever.tagebuch.core.model.SyncRow
import de.mediclever.tagebuch.core.model.Table
import de.mediclever.tagebuch.core.model.UserSettings
import de.mediclever.tagebuch.core.sync.Pulled
import de.mediclever.tagebuch.core.sync.RemoteStore
import de.mediclever.tagebuch.core.sync.RowCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

data class Account(val uid: String, val email: String)

/** Fehler mit einer Meldung, die man dem Nutzer zeigen kann. */
class CloudException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Anmeldung ueber Firebase Authentication. Ohne google-services.json (und ohne Emulator-Schalter)
 * ist available false; die App laeuft dann nur lokal.
 */
class AuthManager(private val context: Context) {
    val available: Boolean = initFirebase(context)
    private val auth: FirebaseAuth? = if (available) FirebaseAuth.getInstance() else null
    private val _account = MutableStateFlow(auth?.currentUser?.toAccount())
    val account: StateFlow<Account?> = _account

    init { auth?.addAuthStateListener { _account.value = it.currentUser?.toAccount() } }

    private fun com.google.firebase.auth.FirebaseUser.toAccount() = if (isAnonymous) null else Account(uid, email ?: "")
    private fun need(): FirebaseAuth = auth ?: throw CloudException("Die Anmeldung ist in dieser Version der App noch nicht eingerichtet.")

    suspend fun signIn(email: String, password: String) = translate { need().signInWithEmailAndPassword(email.trim(), password).await() }
    suspend fun register(email: String, password: String) = translate { need().createUserWithEmailAndPassword(email.trim(), password).await().user?.sendEmailVerification() }
    suspend fun sendPasswordReset(email: String) = translate { need().sendPasswordResetEmail(email.trim()).await() }

    /** Google-Anmeldung ueber den Credential Manager. Braucht die Web-Client-ID aus der Firebase-Konsole. */
    suspend fun signInWithGoogle(activity: Context) = translate {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.ifEmpty {
            val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (id != 0) context.getString(id) else ""
        }
        if (clientId.isEmpty()) throw CloudException("Die Google-Anmeldung ist noch nicht eingerichtet (Web-Client-ID fehlt).")
        val option = GetGoogleIdOption.Builder().setServerClientId(clientId).setFilterByAuthorizedAccounts(false).build()
        val result = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val cred = result.credential
        if (cred !is CustomCredential || cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) throw CloudException("Die Google-Anmeldung hat keine gültige Antwort geliefert.")
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        need().signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
    }

    suspend fun signOut() {
        auth?.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    /** Loescht das Konto bei Firebase. Die Daten muessen vorher entfernt sein, siehe FirestoreRemote.wipeAll. */
    suspend fun deleteAccount() = translate { need().currentUser?.delete()?.await() }

    private suspend fun <T> translate(block: suspend () -> T): T = try { block() } catch (e: Exception) {
        throw when (e) {
            is CloudException -> e
            is FirebaseAuthWeakPasswordException -> CloudException("Das Passwort ist zu kurz. Nimm mindestens 8 Zeichen.", e)
            is FirebaseAuthUserCollisionException -> CloudException("Zu dieser E-Mail-Adresse gibt es schon ein Konto. Melde dich an oder setze das Passwort zurück.", e)
            is FirebaseAuthInvalidUserException, is FirebaseAuthInvalidCredentialsException -> CloudException("E-Mail-Adresse oder Passwort stimmen nicht.", e)
            is FirebaseAuthRecentLoginRequiredException -> CloudException("Aus Sicherheitsgründen musst du dich dafür frisch anmelden. Melde dich ab, wieder an und versuche es erneut.", e)
            is androidx.credentials.exceptions.GetCredentialCancellationException -> CloudException("Anmeldung abgebrochen.", e)
            is androidx.credentials.exceptions.NoCredentialException -> CloudException("Auf diesem Gerät ist kein Google-Konto verfügbar.", e)
            is com.google.firebase.FirebaseNetworkException -> CloudException("Keine Verbindung. Prüfe dein Netz und versuche es noch einmal.", e)
            else -> CloudException("Das hat nicht geklappt. Versuche es später noch einmal.", e)
        }
    }

    companion object {
        /** Liefert true, wenn Firebase nutzbar ist. Im Emulator-Modus wird ein Demo-Projekt eingerichtet. */
        fun initFirebase(context: Context): Boolean = runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) return@runCatching true
            if (!BuildConfig.FIREBASE_EMULATOR) return@runCatching false
            FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-mediclever-tagebuch")
                .setApplicationId("1:1:android:1").setApiKey("demo-key").build())
            // 10.0.2.2 ist aus dem Android-Emulator der eigene Rechner
            FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
            FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
            true
        }.getOrDefault(false)
    }
}

/**
 * Firestore-Seite des Abgleichs: users/{uid}/{sammlung}/{id}. Jedes Schreiben setzt syncedAt
 * auf die Serverzeit; danach wird beim Holen gefiltert. Der Merker zaehlt in Mikrosekunden.
 */
class FirestoreRemote(private val uid: String) : RemoteStore {
    private val db = FirebaseFirestore.getInstance()
    private val user get() = db.collection("users").document(uid)

    private fun micros(t: Timestamp): Long = t.seconds * 1_000_000 + t.nanoseconds / 1000
    private fun timestamp(micros: Long) = Timestamp(micros / 1_000_000, ((micros % 1_000_000) * 1000).toInt())

    override suspend fun pull(table: Table, cursor: Long): Pulled {
        val rows = mutableListOf<SyncRow>()
        var newest = cursor
        var last: DocumentSnapshot? = null
        while (true) {
            var q = user.collection(table.path).whereGreaterThan("syncedAt", timestamp(cursor)).orderBy("syncedAt").limit(PAGE)
            last?.let { q = q.startAfter(it) }
            val snap = q.get(Source.SERVER).await()
            for (doc in snap.documents) {
                val data = doc.data ?: continue
                (data["syncedAt"] as? Timestamp)?.let { newest = maxOf(newest, micros(it)) }
                // Ein einzelnes kaputtes Dokument darf den Abgleich nicht aufhalten
                runCatching { RowCodec.fromMap(table, data) }.onSuccess { rows += it }
            }
            if (snap.size() < PAGE) break
            last = snap.documents.last()
        }
        return Pulled(rows, newest)
    }

    override suspend fun push(table: Table, rows: List<SyncRow>) {
        val batch = db.batch()
        for (row in rows) batch.set(user.collection(table.path).document(row.id), RowCodec.toMap(table, row) + ("syncedAt" to FieldValue.serverTimestamp()))
        batch.commit().await()
    }

    override suspend fun pullSettings(): UserSettings? =
        user.collection("settings").document("main").get(Source.SERVER).await().data?.let { runCatching { RowCodec.settingsFromMap(it) }.getOrNull() }

    override suspend fun pushSettings(settings: UserSettings) {
        user.collection("settings").document("main").set(RowCodec.settingsToMap(settings) + ("syncedAt" to FieldValue.serverTimestamp())).await()
    }

    /** Legt das Profil beim ersten Abgleich an und haelt Einwilligung und App-Version fest. */
    suspend fun touchProfile(consentAt: Long, appVersion: String) {
        val doc = user.get(Source.SERVER).await()
        val fields = mutableMapOf<String, Any>("appVersion" to appVersion, "consentVersion" to CONSENT_VERSION)
        if (consentAt > 0) fields["consentDiaryAt"] = Timestamp(consentAt / 1000, 0)
        if (doc.exists()) user.update(fields).await() else user.set(fields + ("createdAt" to FieldValue.serverTimestamp())).await()
    }

    /** Entfernt alle Daten des Nutzers vom Server. */
    suspend fun wipeAll() {
        for (path in Table.entries.map { it.path } + "settings") {
            while (true) {
                val snap = user.collection(path).limit(400).get(Source.SERVER).await()
                if (snap.isEmpty) break
                val batch = db.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
        user.delete().await()
    }

    companion object {
        const val PAGE = 500L
        const val CONSENT_VERSION = "2026-11"
    }
}
