package de.mediclever.tagebuch

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mediclever.tagebuch.data.LocalState
import de.mediclever.tagebuch.ui.AppNav
import de.mediclever.tagebuch.ui.LocalEnv
import de.mediclever.tagebuch.ui.LockScreen
import de.mediclever.tagebuch.ui.OnboardingScreen
import de.mediclever.tagebuch.ui.UiEnv
import de.mediclever.tagebuch.ui.more.LocalContainer
import de.mediclever.tagebuch.ui.theme.TagebuchTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val route = mutableStateOf<String?>(null)
    private val level = mutableStateOf<Int?>(null)
    /** Entsperrt bis die App laenger im Hintergrund war. */
    private val unlocked = mutableStateOf(false)
    private var backgroundAt = 0L

    private fun read(intent: Intent?) {
        route.value = intent?.getStringExtra("route")
        level.value = intent?.getIntExtra("level", 0)?.takeIf { it in 1..5 }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); read(intent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) read(intent)
        val c = (application as TagebuchApp).container

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { backgroundAt = System.currentTimeMillis() }
            override fun onStart(owner: LifecycleOwner) {
                if (backgroundAt > 0 && System.currentTimeMillis() - backgroundAt > LOCK_AFTER_MS) unlocked.value = false
                c.scope.launch { c.sync.syncNow() }
            }
        })

        setContent {
            val diary by c.repo.diary.collectAsStateWithLifecycle()
            val local by c.prefs.state.collectAsStateWithLifecycle(initialValue = null as LocalState?)
            val d = diary
            val l = local
            // Mit PIN erscheint der Inhalt weder in Screenshots noch in der App-Uebersicht
            LaunchedEffect(l?.pinHash) {
                if (l?.pinHash?.isNotEmpty() == true) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            TagebuchTheme(d?.settings ?: de.mediclever.tagebuch.core.model.UserSettings()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (d == null || l == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else {
                        val env = remember(d, l) { UiEnv(d, l, act = { block -> c.scope.launch { c.repo.block() } }, assetFile = { a -> c.assets.file(a) }) }
                        CompositionLocalProvider(LocalEnv provides env, LocalContainer provides c) {
                            when {
                                l.pinHash.isNotEmpty() && !unlocked.value -> LockScreen(l.pinSalt, l.pinHash) { unlocked.value = true }
                                !l.onboardingDone || l.consentAt == 0L -> OnboardingScreen()
                                else -> AppNav(route.value, level.value) { route.value = null }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object { const val LOCK_AFTER_MS = 30_000L }
}
