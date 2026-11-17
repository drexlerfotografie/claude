package de.mediclever.tagebuch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.mediclever.tagebuch.data.StarterPacks
import de.mediclever.tagebuch.ui.icons.MoodFace

/**
 * Erster Start in zwei Schritten: Einwilligung, dann Startpaket. Danach ist das Tagebuch
 * benutzbar. Anmelden und Importieren gehen spaeter ueber "Mehr".
 */
@Composable
fun OnboardingScreen(startStep: Int = 0) {
    val env = LocalEnv.current
    var step by rememberSaveable { mutableIntStateOf(startStep) }
    var consent by rememberSaveable { mutableStateOf(false) }
    var adult by rememberSaveable { mutableStateOf(false) }
    var pack by rememberSaveable { mutableStateOf("schichtdienst") }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (step == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for (l in 1..5) MoodFace(l, "a", 40.dp) }
                Text("Dein Tagebuch für Stimmung und Alltag", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text("Halte in wenigen Sekunden fest, wie es dir geht und was du gemacht hast. Mit der Zeit siehst du, was dir guttut.", style = MaterialTheme.typography.bodyLarge)
                Panel(title = "Bevor es losgeht", padding = 14.dp) {
                    Text("Stimmung, Schlaf oder Schmerzen sind Gesundheitsdaten. Deshalb vorab, was mit ihnen geschieht:", style = MaterialTheme.typography.bodyMedium)
                    Text("• Deine Einträge liegen auf diesem Gerät.\n• Nur wenn du dich später anmeldest, werden Texte, Stimmungen und Einstellungen zusätzlich bei Google Firebase in der EU gespeichert, damit deine Geräte denselben Stand haben.\n• Fotos und Sprachnotizen bleiben immer auf dem Gerät.\n• Du kannst jederzeit alles exportieren, alles löschen und diese Einwilligung widerrufen (Mehr › MediClever-Konto).", style = MaterialTheme.typography.bodyMedium)
                    CheckRow("Ich bin einverstanden, dass die App meine Tagebuchdaten einschließlich Gesundheitsangaben wie beschrieben verarbeitet.", consent) { consent = it }
                    CheckRow("Ich bin mindestens 18 Jahre alt.", adult) { adult = it }
                }
                Panel(padding = 14.dp) { Text("Bitte trage keine Patienten- und Einsatzdaten ein. Das Tagebuch ist für dich, nicht für die Dokumentation deiner Arbeit.", style = MaterialTheme.typography.bodyMedium) }
                Button({ step = 1 }, Modifier.fillMaxWidth().height(52.dp), enabled = consent && adult) { Text("Weiter") }
            } else {
                Text("Womit möchtest du starten?", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text("Ein Paket ist nur ein Vorschlag. Du kannst danach alles umbenennen, ergänzen oder entfernen.", style = MaterialTheme.typography.bodyLarge)
                for (p in StarterPacks.packs) {
                    val sel = pack == p.key
                    Surface(shape = MaterialTheme.shapes.medium, color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(role = Role.RadioButton) { pack = p.key }.semantics { selected = sel }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RadioButton(sel, null)
                            Column { Text(p.title, style = MaterialTheme.typography.titleMedium); Text(p.description, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
                Text("Du hast schon ein Tagebuch in einer anderen App? Starte leer und importiere es danach unter Mehr › Daten.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button({
                    env.act {
                        val d = StarterPacks.build(pack)
                        // vorhandene Stimmungen (z. B. nach einem Abgleich) nicht verdoppeln
                        val have = snapshot().moods.any { !it.deleted }
                        save((if (have) emptyList() else d.moods) + d.groups + d.tags + d.scales + d.templates + d.reminders)
                        prefs.setConsent(System.currentTimeMillis())
                        prefs.setOnboardingDone(true)
                    }
                }, Modifier.fillMaxWidth().height(52.dp)) { Text("Tagebuch einrichten") }
                TextButton({ step = 0 }) { Text("Zurück") }
            }
        }
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Checkbox) { onChange(!checked) }.heightIn(min = 48.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Checkbox(checked, null)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Sperrbildschirm der PIN-Sperre. */
@Composable
fun LockScreen(salt: String, hash: String, onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var tries by remember { mutableIntStateOf(0) }
    var blockedUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(blockedUntil) { while (System.currentTimeMillis() < blockedUntil) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(500) }; now = System.currentTimeMillis() }
    val blocked = now < blockedUntil
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MediClever Tagebuch", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text("Gib deine PIN ein, um das Tagebuch zu öffnen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            de.mediclever.tagebuch.ui.more.PinField("PIN", pin) { pin = it; wrong = false }
            if (wrong) Text("Die PIN stimmt nicht.", color = MaterialTheme.colorScheme.error)
            if (blocked) Text("Zu viele Versuche. Bitte warte ${((blockedUntil - now) / 1000 + 1)} Sekunden.", color = MaterialTheme.colorScheme.error)
            Button({
                if (de.mediclever.tagebuch.data.Pin.matches(pin, salt, hash)) onUnlock()
                else { wrong = true; pin = ""; tries++; if (tries >= 5) { blockedUntil = System.currentTimeMillis() + 30_000L * (tries - 4); now = System.currentTimeMillis() } }
            }, Modifier.fillMaxWidth().height(52.dp), enabled = pin.length >= 4 && !blocked) { Text("Entsperren") }
        }
    }
}
