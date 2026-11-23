package app.fotosortierer.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel

/** Austauschbar, damit Tests ohne echte Systemberechtigung laufen. */
object StorageAccess {
    var granted: () -> Boolean = { Environment.isExternalStorageManager() }
}

enum class Tab(val label: String, val icon: ImageVector) {
    SORT("Sortieren", Icons.Default.Sort),
    BROWSE("Suchen", Icons.Default.Search),
    DUPES("Duplikate", Icons.Default.ContentCopy),
    HISTORY("Verlauf", Icons.Default.History),
    FOLDERS("Ordner", Icons.Default.Folder),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: AppViewModel = viewModel()) {
    var hasAccess by remember { mutableStateOf(StorageAccess.granted()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasAccess = StorageAccess.granted() }

    if (!hasAccess) {
        PermissionScreen()
        return
    }

    var tab by rememberSaveable { mutableStateOf(if (vm.sources.isEmpty()) Tab.FOLDERS else Tab.SORT) }
    val snackbar = remember { SnackbarHostState() }
    val progress by vm.progress.collectAsState()

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.plan != null && tab == Tab.SORT) "Vorschau" else tab.label) },
                actions = {
                    if (vm.sources.isNotEmpty()) {
                        IconButton(onClick = vm::scan) { Icon(Icons.Default.Refresh, "Neu einlesen") }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label, maxLines = 1) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val m = Modifier.padding(padding).fillMaxSize()
        when (tab) {
            Tab.SORT -> if (vm.plan != null) PreviewScreen(vm, m) else SortScreen(vm, m, openFolders = { tab = Tab.FOLDERS })
            Tab.BROWSE -> BrowseScreen(vm, m)
            Tab.DUPES -> DuplicatesScreen(vm, m)
            Tab.HISTORY -> HistoryScreen(vm, m)
            Tab.FOLDERS -> FoldersScreen(vm, m)
        }
    }

    progress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(p.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (p.total > 0) {
                        LinearProgressIndicator(progress = { p.done.toFloat() / p.total }, modifier = Modifier.fillMaxWidth())
                        Text("${p.done} von ${p.total}")
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
        )
    }
}

@Composable
private fun PermissionScreen() {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Zugriff auf Dateien nötig", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(
            "Damit die App Fotos und Videos umbenennen und in neue Ordner verschieben kann, braucht sie " +
                "die Berechtigung „Zugriff auf alle Dateien“. Die App hat keine Internetberechtigung – " +
                "deine Bilder verlassen das Handy nicht.",
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${ctx.packageName}"),
            )
            runCatching { ctx.startActivity(intent) }
                .onFailure { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }) { Text("Berechtigung erteilen") }
    }
}
