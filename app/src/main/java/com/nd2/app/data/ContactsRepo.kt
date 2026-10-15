package com.nd2.app.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone

data class ContactEntry(val id: Long, val lookupKey: String, val name: String)

object ContactsRepo {

    fun queryAll(context: Context): List<ContactEntry> {
        val out = mutableListOf<ContactEntry>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ),
            null, null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(2) ?: continue
                out += ContactEntry(c.getLong(0), c.getString(1) ?: "", name)
            }
        }
        return out
    }

    /** Carica numeri ed email di un contatto e costruisce l'[AllowedContact]. */
    fun toAllowed(context: Context, entry: ContactEntry, aliases: List<String> = emptyList()): AllowedContact {
        val cr = context.contentResolver
        val phones = mutableListOf<String>()
        cr.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.NUMBER, Phone.NORMALIZED_NUMBER),
            "${Phone.CONTACT_ID} = ?", arrayOf(entry.id.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                (c.getString(1) ?: c.getString(0))?.let { phones += it }
            }
        }
        val emails = mutableListOf<String>()
        cr.query(
            Email.CONTENT_URI,
            arrayOf(Email.ADDRESS),
            "${Email.CONTACT_ID} = ?", arrayOf(entry.id.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) c.getString(0)?.let { emails += it.lowercase() }
        }
        return AllowedContact(entry.id, entry.lookupKey, entry.name, phones.distinct(), emails.distinct(), aliases)
    }

    /**
     * Aggiorna numeri/email dei contatti abilitati (possono essere cambiati nella rubrica).
     * L'id del contatto può cambiare dopo una sincronizzazione: lo risolviamo tramite la lookup key.
     */
    fun refresh(context: Context, contacts: List<AllowedContact>): List<AllowedContact> =
        contacts.map { c ->
            runCatching {
                val lookupUri = ContactsContract.Contacts.getLookupUri(c.id, c.lookupKey)
                // Contatto eliminato dalla rubrica: teniamo i dati salvati.
                val resolved = ContactsContract.Contacts.lookupContact(context.contentResolver, lookupUri)
                    ?: return@map c
                val id = resolved.lastPathSegment?.toLongOrNull() ?: c.id
                val name = queryName(context, id) ?: c.name
                toAllowed(context, ContactEntry(id, c.lookupKey, name), c.aliases)
            }.getOrDefault(c)
        }

    private fun queryName(context: Context, id: Long): String? =
        context.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id.toString()),
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    /** Id dei contatti di rubrica associati a un numero di telefono. */
    fun contactIdsForNumber(context: Context, number: String): Set<Long> {
        if (number.isBlank()) return emptySet()
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val ids = mutableSetOf<Long>()
        runCatching {
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                ?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        }
        return ids
    }
}
