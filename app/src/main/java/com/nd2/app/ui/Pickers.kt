package com.nd2.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nd2.app.core.Matcher
import com.nd2.app.data.AllowedApp
import com.nd2.app.data.AppEntry
import com.nd2.app.data.AppsRepo
import com.nd2.app.data.ContactEntry
import com.nd2.app.data.ContactsRepo
import com.nd2.app.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ContactPickerScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<ContactEntry>?>(null) }
    var selected by remember { mutableStateOf(Store.current.contacts.map { it.id }.toSet()) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        all = withContext(Dispatchers.IO) { runCatching { ContactsRepo.queryAll(context) }.getOrDefault(emptyList()) }
    }

    PickerScaffold(
        title = "Scegli i contatti",
        subtitle = "Da loro riceverai tutto, sempre",
        items = all,
        key = { it.id },
        label = { it.name },
        section = { e -> e.name.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#" },
        selectedCount = selected.size,
        isSelected = { it.id in selected },
        onToggle = { e -> selected = if (e.id in selected) selected - e.id else selected + e.id },
        leading = { e, sel -> Avatar(e.name, selected = sel) },
        saving = saving,
        onBack = onDone,
        onSave = {
            saving = true
            scope.launch {
                val existing = Store.current.contacts.associateBy { it.id }
                val chosen = all.orEmpty().filter { it.id in selected }
                val result = withContext(Dispatchers.IO) {
                    chosen.map { e -> existing[e.id] ?: ContactsRepo.toAllowed(context, e) }
                }
                Store.update { it.copy(contacts = result) }
                onDone()
            }
        },
    )
}

@Composable
fun AppPickerScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var all by remember { mutableStateOf<List<AppEntry>?>(null) }
    var selected by remember { mutableStateOf(Store.current.apps.map { it.pkg }.toSet()) }

    LaunchedEffect(Unit) {
        all = withContext(Dispatchers.IO) { AppsRepo.queryLaunchable(context) }
    }

    PickerScaffold(
        title = "Scegli le app",
        subtitle = "Le loro notifiche arriveranno sempre",
        items = all,
        key = { it.pkg },
        label = { it.label },
        section = null,
        selectedCount = selected.size,
        isSelected = { it.pkg in selected },
        onToggle = { e -> selected = if (e.pkg in selected) selected - e.pkg else selected + e.pkg },
        leading = { e, sel ->
            val bmp = remember(e.pkg) { e.icon?.asImageBitmap() }
            Box(Modifier.size(44.dp)) {
                if (bmp != null) Image(bmp, contentDescription = null, modifier = Modifier.size(44.dp)) else Avatar(e.label)
                SelectionBadge(sel, Modifier.align(Alignment.BottomEnd))
            }
        },
        saving = false,
        onBack = onDone,
        onSave = {
            val apps = all.orEmpty().filter { it.pkg in selected }.map { AllowedApp(it.pkg, it.label) }
            // Mantiene anche app selezionate in passato e non più presenti nel launcher.
            val kept = Store.current.apps.filter { old -> old.pkg in selected && apps.none { it.pkg == old.pkg } }
            Store.update { it.copy(apps = apps + kept) }
            onDone()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun <T> PickerScaffold(
    title: String,
    subtitle: String,
    items: List<T>?,
    key: (T) -> Any,
    label: (T) -> String,
    section: ((T) -> String)?,
    selectedCount: Int,
    isSelected: (T) -> Boolean,
    onToggle: (T) -> Unit,
    leading: @Composable (T, Boolean) -> Unit,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 8.dp) {
                Button(
                    onClick = onSave,
                    enabled = items != null && !saving,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp).height(54.dp),
                ) {
                    Text(
                        when {
                            saving -> "Salvataggio…"
                            selectedCount == 0 -> "Salva"
                            else -> "Salva · $selectedCount selezionat${if (selectedCount == 1) "o" else "i"}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Cancella") }
                    }
                },
                placeholder = { Text("Cerca") },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (items == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            val q = Matcher.normalize(query)
            val filtered = if (q.isEmpty()) items else items.filter { Matcher.normalize(label(it)).contains(q) }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp)) {
                    EmptyState(Icons.Filled.Search, if (q.isEmpty()) "Nessun elemento" else "Nessun risultato per \"$query\"")
                }
                return@Column
            }
            val groups = if (section == null) mapOf("" to filtered) else filtered.groupBy(section)
            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                groups.forEach { (letter, group) ->
                    if (letter.isNotEmpty()) {
                        stickyHeader(key = "h_$letter") {
                            Text(
                                letter,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                    items(group, key = key) { item ->
                        PickerRow(label(item), isSelected(item), { onToggle(item) }) { leading(item, it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: @Composable (Boolean) -> Unit,
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent,
        label = "row",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading(selected)
        Spacer(Modifier.width(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}
