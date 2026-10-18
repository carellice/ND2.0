package com.nd2.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nd2.app.core.ReleaseInfo
import com.nd2.app.core.UpdateState
import com.nd2.app.core.Updater
import com.nd2.app.data.Backup
import com.nd2.app.service.ToggleTileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(resumeTick: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by Updater.state.collectAsStateWithLifecycle()
    val version = remember { Updater.currentVersion(context) }
    // APK in attesa che l'utente consenta a ND2.0 di installare app.
    var pendingInstall by remember { mutableStateOf<File?>(null) }

    fun install(file: File) {
        if (Updater.canInstall(context)) {
            Updater.install(context, file)
        } else {
            pendingInstall = file
            context.startActivity(Updater.unknownSourcesSettings(context))
        }
    }

    val scope = rememberCoroutineScope()
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { Backup.export(context, uri) } }
            toast(if (result.isSuccess) "Backup salvato" else "Backup non riuscito: ${result.exceptionOrNull()?.message}")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { Backup.import(context, uri) } }
            result.onSuccess { r ->
                toast(
                    if (r.contacts == 0 && r.apps == 0) "Backup importato: era già tutto presente"
                    else "Backup importato: ${r.contacts} contatti e ${r.apps} app aggiunti",
                )
            }.onFailure { toast(it.message ?: "Importazione non riuscita") }
        }
    }

    // Ogni volta che si entra nelle Impostazioni si controlla se c'è una nuova versione.
    LaunchedEffect(Unit) { Updater.check(context) }
    // Al ritorno dalla schermata "Installa app sconosciute" si riprende l'installazione.
    LaunchedEffect(resumeTick) {
        val file = pendingInstall ?: return@LaunchedEffect
        if (Updater.canInstall(context)) {
            pendingInstall = null
            Updater.install(context, file)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SectionLabel("Aggiornamenti") }
            item {
                UpdateCard(
                    version = version,
                    state = state,
                    onCheck = { Updater.check(context) },
                    onDownload = { Updater.download(context, it) },
                    onInstall = { install(it) },
                )
            }

            item { SectionLabel("Backup") }
            item {
                GroupedList(2) { i ->
                    when (i) {
                        0 -> LinkRow(Icons.Filled.Share, "Esporta backup", "Salva contatti, altri nomi e app su un file") {
                            exportLauncher.launch(Backup.fileName())
                        }
                        else -> LinkRow(Icons.Filled.Refresh, "Importa backup", "Ripristina da un file, ad esempio su un nuovo telefono") {
                            importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                        }
                    }
                }
            }

            item { SectionLabel("Generale") }
            item {
                GroupedList(4) { i ->
                    when (i) {
                        0 -> LinkRow(Icons.Filled.Add, "Pulsante nelle Impostazioni rapide", "Attiva ND2.0 dalla tendina delle notifiche") {
                            ToggleTileService.requestAdd(context)
                        }
                        1 -> LinkRow(Icons.Filled.Settings, "Impostazioni di sistema dell'app", "Permessi, notifiche, batteria") {
                            context.startActivity(Perms.appSettings(context))
                        }
                        2 -> LinkRow(Icons.Filled.Star, "Progetto su GitHub", "Codice sorgente e tutte le versioni") {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.REPO_URL)))
                        }
                        else -> LinkRow(Icons.Filled.Info, "Versione", "ND2.0 $version", showArrow = false) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun UpdateCard(
    version: String,
    state: UpdateState,
    onCheck: () -> Unit,
    onDownload: (ReleaseInfo) -> Unit,
    onInstall: (File) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo(52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("ND2.0", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Versione installata $version",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "update",
            ) { s ->
                when (s) {
                    UpdateState.Idle, UpdateState.Checking -> StatusLine(
                        leading = { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp) },
                        title = "Controllo aggiornamenti…",
                        subtitle = "Cerco una nuova versione su GitHub",
                    )
                    UpdateState.UpToDate -> Column {
                        StatusLine(
                            leading = { StatusIcon(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primary) },
                            title = "Hai l'ultima versione",
                            subtitle = "ND2.0 è aggiornata",
                        )
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Controlla di nuovo")
                        }
                    }
                    is UpdateState.Available -> Column {
                        NewVersionBanner(s.release)
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { onDownload(s.release) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Text("Scarica e installa · ${formatSize(s.release.size)}", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    is UpdateState.Downloading -> Column {
                        NewVersionBanner(s.release)
                        Spacer(Modifier.height(16.dp))
                        val p by animateFloatAsState(s.progress, label = "dl")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Download in corso…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            strokeCap = StrokeCap.Round,
                            drawStopIndicator = {},
                        )
                    }
                    is UpdateState.Ready -> Column {
                        NewVersionBanner(s.release)
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { onInstall(s.file) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Text("Installa ND2.0 ${s.release.version}", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "Download completato. Android ti chiederà di confermare l'installazione.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    is UpdateState.Error -> Column {
                        StatusLine(
                            leading = { StatusIcon(Icons.Filled.Warning, MaterialTheme.colorScheme.error) },
                            title = "Qualcosa non ha funzionato",
                            subtitle = s.message,
                        )
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Riprova")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewVersionBanner(release: ReleaseInfo) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "NUOVA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "ND2.0 ${release.version} disponibile",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        if (release.notes.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                release.notes.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun StatusLine(leading: @Composable () -> Unit, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, showArrow: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = showArrow, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, 40.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showArrow) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatSize(bytes: Long): String =
    if (bytes <= 0) "" else String.format(Locale.ITALY, "%.1f MB", bytes / 1_048_576.0)
