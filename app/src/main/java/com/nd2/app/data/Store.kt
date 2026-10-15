package com.nd2.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Contatto dal quale si riceve tutto anche a modalità attiva. */
data class AllowedContact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    /** Altri nomi con cui il contatto compare nelle app (es. username Instagram, nome su Telegram). */
    val aliases: List<String> = emptyList(),
)

/** App le cui notifiche arrivano sempre. */
data class AllowedApp(val pkg: String, val label: String)

data class AppState(
    val active: Boolean = false,
    val contacts: List<AllowedContact> = emptyList(),
    val apps: List<AllowedApp> = emptyList(),
)

/** Stato persistente dell'app, condiviso tra UI, servizi e receiver (stesso processo). */
object Store {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state
    val current: AppState get() = _state.value

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("nd2", Context.MODE_PRIVATE)
        _state.value = load()
    }

    @Synchronized
    fun update(transform: (AppState) -> AppState) {
        val next = transform(_state.value)
        _state.value = next
        save(next)
    }

    var zenRuleId: String?
        get() = prefs.getString(KEY_RULE, null)
        set(value) = prefs.edit().putString(KEY_RULE, value).apply()

    private fun load(): AppState {
        val contacts = prefs.getString(KEY_CONTACTS, null)?.let { raw ->
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AllowedContact(
                    id = o.getLong("id"),
                    lookupKey = o.optString("lookupKey"),
                    name = o.getString("name"),
                    phones = o.optJSONArray("phones").toStringList(),
                    emails = o.optJSONArray("emails").toStringList(),
                    aliases = o.optJSONArray("aliases").toStringList(),
                )
            }
        } ?: emptyList()
        val apps = prefs.getString(KEY_APPS, null)?.let { raw ->
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AllowedApp(o.getString("pkg"), o.getString("label"))
            }
        } ?: emptyList()
        return AppState(prefs.getBoolean(KEY_ACTIVE, false), contacts, apps)
    }

    private fun save(s: AppState) {
        val contacts = JSONArray().apply {
            s.contacts.forEach { c ->
                put(
                    JSONObject()
                        .put("id", c.id)
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
        prefs.edit()
            .putBoolean(KEY_ACTIVE, s.active)
            .putString(KEY_CONTACTS, contacts.toString())
            .putString(KEY_APPS, apps.toString())
            .apply()
    }

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private const val KEY_ACTIVE = "active"
    private const val KEY_CONTACTS = "contacts"
    private const val KEY_APPS = "apps"
    private const val KEY_RULE = "zen_rule_id"
}
