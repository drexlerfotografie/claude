package de.mediclever.schichtclever.focus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mediclever.schichtclever.data.Repository
import de.mediclever.schichtclever.ui.screens.longName
import de.mediclever.schichtclever.ui.components.Cleverle
import de.mediclever.schichtclever.ui.components.McCard
import de.mediclever.schichtclever.ui.durationHm
import de.mediclever.schichtclever.ui.theme.LocalExtra
import de.mediclever.schichtclever.ui.theme.SchichtCleverTheme
import de.mediclever.schichtclever.ui.time

/** Wird angezeigt, wenn während des Fokus-Modus eine pausierte App geöffnet wird. */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: run { finish(); return }
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault("Diese App")
        val data = Repository.data.value
        val shift = data.activeShift

        setContent {
            SchichtCleverTheme(dark = true) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Cleverle(Modifier.size(64.dp, 75.dp), dark = true)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        (if (shift != null) "Fokus · ${shift.type.code}" else "Fokus-Modus").uppercase(),
                        style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.accent,
                    )
                    Text(
                        "$label ist während deiner ${shift?.type?.longName ?: "Fokuszeit"} pausiert.",
                        style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "Du hast das selbst so eingestellt, und dein Kopf wird es dir danken.",
                        style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp),
                    )
                    if (shift != null) {
                        McCard(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                            Text("WIEDER VERFÜGBAR IN", style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted, modifier = Modifier.align(Alignment.CenterHorizontally))
                            Text("${durationHm(shift.plannedEndAt - System.currentTimeMillis())} h", style = MaterialTheme.typography.displayMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                            Text("Schichtende um ${shift.plannedEndAt.time()}", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = { goHome() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.small) {
                        Text("ZURÜCK", style = MaterialTheme.typography.titleLarge)
                    }
                    OutlinedButton(
                        onClick = {
                            FocusService.allowedUntil[pkg] = System.currentTimeMillis() + 5 * 60_000
                            packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp), shape = MaterialTheme.shapes.small,
                    ) { Text("5 Minuten freigeben") }
                }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object { const val EXTRA_PACKAGE = "pkg" }
}
