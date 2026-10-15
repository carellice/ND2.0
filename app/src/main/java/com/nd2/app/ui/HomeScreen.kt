package com.nd2.app.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nd2.app.R
import com.nd2.app.core.Nd2Mode
import com.nd2.app.data.AllowedApp
import com.nd2.app.data.AllowedContact
import com.nd2.app.data.AppsRepo
import com.nd2.app.data.Store
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(resumeTick: Int, onPickContacts: () -> Unit, onPickApps: () -> Unit) {
    val context = LocalContext.current
    val state by Store.state.collectAsStateWithLifecycle()
    var permTick by remember { mutableIntStateOf(0) }
    val perms = remember(resumeTick, permTick) { Perms.status(context) }
    val asked = remember { mutableStateOf(setOf<String>()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick++
    }
    fun request(vararg p: String) {
        val key = p.joinToString()
        // Se l'utente ha già negato, Android non mostra più il dialogo: apriamo le impostazioni dell'app.
        if (key in asked.value && p.any { !Perms.granted(context, it) }) {
            context.startActivity(Perms.appSettings(context))
        } else {
            asked.value = asked.value + key
            launcher.launch(arrayOf(*p))
        }
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<AllowedContact?>(null) }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Header() }

            item {
                HeroCard(
                    active = state.active,
                    contacts = state.contacts.size,
                    apps = state.apps.size,
                    onToggle = { want ->
                        if (!Nd2Mode.setActive(context, want)) {
                            scope.launch {
                                snackbar.showSnackbar("Concedi l'accesso alle notifiche e a Non disturbare per attivare ND2.0")
                            }
                        }
                    },
                )
            }

            if (!perms.allGood) {
                item {
                    SetupCard(
                        perms, context,
                        onRequestContacts = { request(Manifest.permission.READ_CONTACTS) },
                        onRequestPhone = { request(*Perms.PHONE) },
                        onRequestNotifications = {
                            if (Build.VERSION.SDK_INT >= 33) request(Manifest.permission.POST_NOTIFICATIONS)
                        },
                    )
                }
            }

            item {
                SectionHeader(
                    icon = Icons.Filled.Favorite,
                    title = "Contatti abilitati",
                    count = state.contacts.size,
                    subtitle = "Da loro ricevi chiamate, SMS e notifiche di qualsiasi app, anche con ND2.0 attiva.",
                    onAdd = { if (perms.contacts) onPickContacts() else request(Manifest.permission.READ_CONTACTS) },
                )
            }
            item {
                if (state.contacts.isEmpty()) {
                    EmptyState(Icons.Filled.Person, "Nessun contatto ancora.\nAggiungi ad esempio mamma, papà, nonni.")
                } else {
                    GroupedList(state.contacts.size) { i ->
                        val c = state.contacts[i]
                        ContactRow(
                            contact = c,
                            onEdit = { editing = c },
                            onRemove = { Store.update { s -> s.copy(contacts = s.contacts.filter { it.id != c.id }) } },
                        )
                    }
                }
            }

            item {
                SectionHeader(
                    icon = Icons.Filled.Notifications,
                    title = "App sempre abilitate",
                    count = state.apps.size,
                    subtitle = "Le loro notifiche arrivano sempre, che ND2.0 sia attiva o no.",
                    onAdd = onPickApps,
                )
            }
            item {
                if (state.apps.isEmpty()) {
                    EmptyState(Icons.Filled.Notifications, "Nessuna app ancora.\nAggiungi ad esempio Gmail o Calendar.")
                } else {
                    GroupedList(state.apps.size) { i ->
                        val app = state.apps[i]
                        AppRow(app, onRemove = { Store.update { s -> s.copy(apps = s.apps.filter { it.pkg != app.pkg }) } })
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)); HowItWorks() }
        }
    }

    editing?.let { c ->
        AliasDialog(
            contact = c,
            onDismiss = { editing = null },
            onSave = { aliases ->
                Store.update { s ->
                    s.copy(contacts = s.contacts.map { if (it.id == c.id) it.copy(aliases = aliases) else it })
                }
                editing = null
            },
        )
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        AppLogo(44.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text("ND2.0", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Non disturbare, ma con intelligenza",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroCard(active: Boolean, contacts: Int, apps: Int, onToggle: (Boolean) -> Unit) {
    val inactiveBg = MaterialTheme.colorScheme.surfaceContainerHigh
    AnimatedContent(
        targetState = active,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "hero",
    ) { on ->
        val fg = if (on) Color.White else MaterialTheme.colorScheme.onSurface
        val fgSoft = if (on) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant
        Box(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .then(if (on) Modifier.background(Brand.activeGradient) else Modifier.background(inactiveBg))
                .animateContentSize(),
        ) {
            // Luna decorativa sullo sfondo della card attiva.
            if (on) {
                Icon(
                    painterResource(R.drawable.ic_stat_nd2),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.07f),
                    modifier = Modifier.size(190.dp).align(Alignment.TopEnd).offset(x = 40.dp, y = (-30).dp),
                )
            }
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoonBadge(on)
                    Spacer(Modifier.weight(1f))
                    StatusPill(on)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    if (on) "Sei in modalità\nnon disturbare" else "Modalità disattivata",
                    style = MaterialTheme.typography.headlineSmall,
                    color = fg,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (on) "Arriva solo ciò che conta. Tutto il resto ti aspetta quando la disattivi."
                    else "Tutte le notifiche e le chiamate arrivano normalmente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = fgSoft,
                )
                if (on) {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroChip(Icons.Filled.Favorite, plural(contacts, "contatto", "contatti"))
                        HeroChip(Icons.Filled.Notifications, plural(apps, "app", "app"))
                    }
                }
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = { onToggle(!on) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = if (on) {
                        ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.Night)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    Text(if (on) "Disattiva" else "Attiva ND2.0", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun MoonBadge(active: Boolean) {
    val bg = if (active) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primaryContainer
    val tint = if (active) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
    Box(Modifier.size(56.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_stat_nd2), contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun StatusPill(active: Boolean) {
    val bg = if (active) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val dot = if (active) Brand.Moon else MaterialTheme.colorScheme.outline
    Row(
        Modifier.clip(CircleShape).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(if (active) "ATTIVA" else "SPENTA", style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
private fun HeroChip(icon: ImageVector, text: String) {
    Row(
        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

private fun plural(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"

@Composable
private fun SetupCard(
    perms: PermStatus,
    context: Context,
    onRequestContacts: () -> Unit,
    onRequestPhone: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val steps = listOf(perms.listener, perms.dnd, perms.contacts, perms.phone, perms.postNotifications)
    val done = steps.count { it }
    val progress by animateFloatAsState(done / steps.size.toFloat(), label = "setup")
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Filled.Settings, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Completa la configurazione", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text("$done di ${steps.size} passaggi", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f))
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
            )
            SetupRow("Accesso alle notifiche", "Per filtrare le notifiche delle app", perms.listener) {
                context.startActivity(Perms.listenerSettings(context))
            }
            SetupRow("Accesso a Non disturbare", "Per silenziare tutto il resto", perms.dnd) {
                context.startActivity(Perms.dndSettings())
            }
            SetupRow("Contatti", "Per scegliere e riconoscere i contatti", perms.contacts, onRequestContacts)
            SetupRow("Telefono e chiamate", "Per far squillare i contatti abilitati", perms.phone, onRequestPhone)
            SetupRow("Notifiche", "Per mostrarti ciò che passa il filtro", perms.postNotifications, onRequestNotifications)
            perms.channelsNotBypassing.forEach { id ->
                SetupRow("Ignora Non disturbare", "Attivalo per il canale di ND2.0", false) {
                    context.startActivity(Perms.channelSettings(context, id))
                }
            }
        }
    }
}

@Composable
private fun SetupRow(title: String, subtitle: String, ok: Boolean, onClick: () -> Unit) {
    val onC = MaterialTheme.colorScheme.onTertiaryContainer
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (ok) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Fatto", tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(22.dp))
        } else {
            Box(Modifier.size(22.dp).clip(CircleShape).background(onC.copy(alpha = 0.12f)))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                color = if (ok) onC.copy(alpha = 0.6f) else onC)
            if (!ok) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = onC.copy(alpha = 0.75f))
        }
        if (!ok) {
            FilledTonalButton(
                onClick = onClick,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
            ) { Text("Concedi") }
        }
    }
}

@Composable
private fun ContactRow(contact: AllowedContact, onEdit: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(contact.name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                contact.phones.firstOrNull() ?: "Nessun numero",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (contact.aliases.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    contact.aliases.take(3).forEach { AliasChip(it) }
                    if (contact.aliases.size > 3) AliasChip("+${contact.aliases.size - 3}")
                }
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Altri nomi", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Rimuovi", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AliasChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun AppRow(app: AllowedApp, onRemove: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(app.pkg) { AppsRepo.icon(context, app.pkg)?.asImageBitmap() }
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(44.dp)) else Avatar(app.label)
        Spacer(Modifier.width(14.dp))
        Text(app.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Rimuovi", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AliasDialog(contact: AllowedContact, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var text by remember { mutableStateOf(contact.aliases.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Avatar(contact.name, 56.dp) },
        title = { Text(contact.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Se nelle app questa persona compare con un altro nome (es. username Instagram " +
                        "o nome diverso su Telegram), aggiungilo qui. Separa i nomi con una virgola.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Altri nomi") },
                    placeholder = { Text("es. mamma.rossi, Mami") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct())
            }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun HowItWorks() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Come funziona", style = MaterialTheme.typography.titleMedium)
            HowRow(Icons.Filled.Lock, "Chiamate e notifiche degli altri vengono silenziate e nascoste.")
            HowRow(Icons.Filled.Favorite, "Passano solo i contatti e le app che hai abilitato.")
            HowRow(Icons.Filled.Refresh, "Quando la disattivi ritrovi tutto, non si perde nulla.")
            Text(
                "Suggerimento: aggiungi il pulsante \"ND2.0\" nelle Impostazioni rapide per attivarla al volo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HowRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.primary, 32.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
