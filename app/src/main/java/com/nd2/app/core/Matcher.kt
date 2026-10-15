package com.nd2.app.core

import android.app.Notification
import android.app.Person
import android.content.Context
import android.os.Bundle
import androidx.core.os.BundleCompat
import com.nd2.app.data.AllowedContact
import com.nd2.app.data.ContactsRepo
import com.nd2.app.data.Store
import java.text.Normalizer

/**
 * Riconosce se una notifica (di qualsiasi app) o una chiamata proviene da un contatto abilitato.
 *
 * Usa, in ordine di affidabilità: il Person associato (URI tel:/mailto:/contatto di rubrica),
 * il mittente dell'ultimo messaggio (MessagingStyle), il titolo e il prefisso "Nome: testo".
 */
object Matcher {

    private class Index(contacts: List<AllowedContact>) {
        val ids = contacts.map { it.id.toString() }.toSet()
        val lookupKeys = contacts.map { it.lookupKey }.filter { it.isNotEmpty() }.toSet()
        val phones = contacts.flatMap { it.phones }.map { digits(it) }.filter { it.isNotEmpty() }
        val emails = contacts.flatMap { it.emails }.map { it.lowercase() }.toSet()
        val names = contacts.flatMap { listOf(it.name) + it.aliases }
            .map { normalize(it) }.filter { it.isNotEmpty() }.toSet()
        val aliases = contacts.flatMap { it.aliases }
            .map { normalize(it) }.filter { it.length >= 3 }.toSet()
    }

    @Volatile private var cache: Pair<List<AllowedContact>, Index>? = null

    private fun index(): Index {
        val contacts = Store.current.contacts
        cache?.let { (src, idx) -> if (src === contacts) return idx }
        return Index(contacts).also { cache = contacts to it }
    }

    // ---------- Notifiche ----------

    fun isFromAllowedContact(n: Notification): Boolean {
        val idx = index()
        if (idx.names.isEmpty() && idx.phones.isEmpty()) return false
        val ex = n.extras ?: return false
        val isGroup = ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)

        // 1) Conversazioni (WhatsApp, Telegram, SMS...): conta il mittente dell'ultimo messaggio.
        val messages = BundleCompat.getParcelableArray(ex, Notification.EXTRA_MESSAGES, Bundle::class.java)
            ?.filterIsInstance<Bundle>().orEmpty()
        if (messages.isNotEmpty()) {
            val last = messages.last()
            val person = BundleCompat.getParcelable(last, "sender_person", Person::class.java)
            val sender = last.getCharSequence("sender")
            if (person != null && matchesPerson(idx, person)) return true
            if (matchesName(idx, sender)) return true
            if (isGroup) return false
            // In una chat 1:1 il mittente può mancare (es. ultimo messaggio inviato da me): usiamo il titolo.
        }

        // 2) Persone associate alla notifica (solo chat singole: nei gruppi ci sono tutti i partecipanti).
        if (!isGroup) {
            val people = BundleCompat.getParcelableArrayList(ex, Notification.EXTRA_PEOPLE_LIST, Person::class.java)
            if (people?.any { matchesPerson(idx, it) } == true) return true
            @Suppress("DEPRECATION")
            ex.getStringArray(Notification.EXTRA_PEOPLE)?.forEach { if (matchesUri(idx, it)) return true }
        }

        // 3) Titolo (es. "Mamma", "Mamma (2 messaggi)", "Famiglia: Mamma").
        if (matchesName(idx, ex.getCharSequence(Notification.EXTRA_TITLE))) return true
        if (matchesName(idx, ex.getCharSequence(Notification.EXTRA_TITLE_BIG))) return true
        if (!isGroup && matchesName(idx, ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))) return true

        // 4) Testo nella forma "Mamma: ciao" oppure che inizia con un alias ("mamma_rossi ti ha inviato...").
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val colon = text.indexOf(':')
        if (colon in 1..40 && matchesName(idx, text.substring(0, colon))) return true
        val normText = normalize(text)
        if (idx.aliases.any { normText == it || normText.startsWith("$it ") }) return true

        return false
    }

    private fun matchesPerson(idx: Index, p: Person): Boolean =
        (p.uri != null && matchesUri(idx, p.uri!!)) || matchesName(idx, p.name)

    private fun matchesUri(idx: Index, uri: String): Boolean {
        val u = uri.trim()
        return when {
            u.startsWith("tel:", true) -> matchesNumber(idx, u.substring(4))
            u.startsWith("mailto:", true) -> u.substring(7).lowercase() in idx.emails
            u.contains("com.android.contacts", true) -> {
                val segs = android.net.Uri.parse(u).pathSegments
                val i = segs.indexOf("lookup")
                if (i >= 0) {
                    (segs.getOrNull(i + 1)?.let { it in idx.lookupKeys } == true) ||
                        (segs.getOrNull(i + 2)?.let { it in idx.ids } == true)
                } else {
                    segs.lastOrNull()?.let { it in idx.ids } == true
                }
            }
            else -> false
        }
    }

    private fun matchesName(idx: Index, raw: CharSequence?): Boolean {
        if (raw.isNullOrBlank()) return false
        val s = raw.toString()
        val variants = mutableSetOf(s)
        // Rimuove suffissi come "(3 messaggi)"
        variants += s.replace(Regex("\\s*\\(.*\\)\\s*$"), "")
        // "Gruppo: Mamma", "Mamma @ Gruppo", "Mamma - Telegram"
        s.split(':', '@', '·', '|').forEach { variants += it }
        s.split(" - ").forEach { variants += it }
        return variants.any { normalize(it) in idx.names }
    }

    // ---------- Chiamate ----------

    /** Restituisce il contatto abilitato che corrisponde al numero, se presente. */
    fun allowedContactForNumber(context: Context, number: String): AllowedContact? {
        val contacts = Store.current.contacts
        if (contacts.isEmpty()) return null
        val ids = ContactsRepo.contactIdsForNumber(context, number)
        contacts.firstOrNull { it.id in ids }?.let { return it }
        val d = digits(number)
        return contacts.firstOrNull { c -> c.phones.any { numbersMatch(digits(it), d) } }
    }

    private fun matchesNumber(idx: Index, number: String): Boolean {
        val d = digits(number)
        return idx.phones.any { numbersMatch(it, d) }
    }

    // ---------- Utilità ----------

    private fun digits(s: String) = s.filter { it.isDigit() }

    /** Confronta le ultime 9 cifre, così "+39 333 1234567" e "3331234567" coincidono. */
    private fun numbersMatch(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val n = minOf(9, a.length, b.length)
        if (n < 6) return a == b
        return a.takeLast(n) == b.takeLast(n)
    }

    /** Minuscolo, senza accenti/emoji/punteggiatura: "Papà ❤️" -> "papa". */
    fun normalize(s: CharSequence?): String {
        if (s == null) return ""
        val noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return noAccents.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}._ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
