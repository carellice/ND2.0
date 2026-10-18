package com.nd2.app.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Esito di un'importazione: quanti contatti e app sono stati aggiunti. */
data class ImportResult(val contacts: Int, val apps: Int)

/**
 * Backup su file (JSON) di contatti abilitati, "Altri nomi" e app sempre abilitate,
 * per portare la configurazione su un altro telefono.
 */
object Backup {
    private const val FORMAT = "nd2-backup"
    private const val VERSION = 1

    fun fileName(): String =
        "ND2.0-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.ITALY).format(Date())}.json"

    fun export(context: Context, uri: Uri) {
        val s = Store.current
        val contacts = JSONArray().apply {
            s.contacts.forEach { c ->
                put(
                    JSONObject()
                        .put("lookupKey", c.lookupKey)
                        .put("name", c.name)
                        .put("phones", JSONArray(c.phones))
                        .put("emails", JSONArray(c.emails))
                        .put("aliases", JSONArray(c.aliases))
                )
            }
        }
        val apps = JSONArray().apply {
            s.apps.forEach { put(JSONObject().put("pkg", it.pkg).put("label", it.label)) }
        }
        val json = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("contacts", contacts)
            .put("apps", apps)
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Impossibile scrivere il file")
        out.bufferedWriter().use { it.write(json.toString(2)) }
    }

    /**
     * Unisce il backup alla configurazione attuale (non toglie nulla).
     * Sul nuovo telefono gli id dei contatti sono diversi: ogni contatto viene ritrovato
     * in rubrica tramite lookup key, numero di telefono o nome.
     */
    fun import(context: Context, uri: Uri): ImportResult {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Impossibile leggere il file")
        val json = runCatching { JSONObject(raw) }.getOrNull()
        require(json != null && json.optString("format") == FORMAT) { "Il file non è un backup di ND2.0" }

        val book = runCatching { ContactsRepo.queryAll(context) }.getOrDefault(emptyList())
        // Contatti risultanti, indicizzati per id (quelli già abilitati restano com'erano).
        val merged = LinkedHashMap<Long, AllowedContact>()
        Store.current.contacts.forEach { merged[it.id] = it }
        // Id negativi per i contatti non trovati in rubrica: non collidono con quelli reali.
        var nextMissingId = minOf(0L, merged.keys.minOrNull() ?: 0L) - 1
        var addedContacts = 0

        val arr = json.optJSONArray("contacts") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name")
            if (name.isBlank()) continue
            val phones = o.optJSONArray("phones").toStringList()
            val emails = o.optJSONArray("emails").toStringList()
            val aliases = o.optJSONArray("aliases").toStringList()

            val entry = runCatching { resolve(context, book, o.optString("lookupKey"), name, phones) }.getOrNull()
            val existing = if (entry != null) merged[entry.id]
            else merged.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (existing != null) {
                // Già abilitato: aggiungiamo solo gli "Altri nomi" mancanti.
                merged[existing.id] = existing.copy(aliases = (existing.aliases + aliases).distinct())
                continue
            }
            val contact = if (entry != null) {
                runCatching { ContactsRepo.toAllowed(context, entry, aliases) }.getOrNull()
                    ?: AllowedContact(entry.id, entry.lookupKey, entry.name, phones, emails, aliases)
            } else {
                AllowedContact(nextMissingId--, "", name, phones, emails, aliases)
            }
            merged[contact.id] = contact
            addedContacts++
        }

        val apps = Store.current.apps.toMutableList()
        val appsArr = json.optJSONArray("apps") ?: JSONArray()
        var addedApps = 0
        for (i in 0 until appsArr.length()) {
            val o = appsArr.getJSONObject(i)
            val pkg = o.optString("pkg")
            if (pkg.isBlank() || apps.any { it.pkg == pkg }) continue
            apps += AllowedApp(pkg, o.optString("label", pkg))
            addedApps++
        }

        Store.update { it.copy(contacts = merged.values.toList(), apps = apps) }
        return ImportResult(addedContacts, addedApps)
    }

    /** Ritrova il contatto nella rubrica di questo telefono. */
    private fun resolve(
        context: Context,
        book: List<ContactEntry>,
        lookupKey: String,
        name: String,
        phones: List<String>,
    ): ContactEntry? {
        if (lookupKey.isNotEmpty()) {
            val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
            val id = runCatching {
                ContactsContract.Contacts.lookupContact(context.contentResolver, uri)?.lastPathSegment?.toLongOrNull()
            }.getOrNull()
            book.firstOrNull { it.id == id }?.let { return it }
        }
        for (phone in phones) {
            val ids = ContactsRepo.contactIdsForNumber(context, phone)
            book.firstOrNull { it.id in ids }?.let { return it }
        }
        return book.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }
}
