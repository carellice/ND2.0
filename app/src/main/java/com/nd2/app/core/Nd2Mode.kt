package com.nd2.app.core

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.quicksettings.TileService
import com.nd2.app.data.ContactsRepo
import com.nd2.app.data.Store
import com.nd2.app.service.FilterListenerService
import com.nd2.app.service.ToggleTileService

/** Punto unico per attivare/disattivare ND2.0 (da app, tile delle impostazioni rapide, ecc.). */
object Nd2Mode {

    /** Requisiti minimi per poter attivare la modalità. */
    fun isReady(context: Context) = DndController.hasAccess(context) && hasListenerAccess(context)

    fun hasListenerAccess(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(context, FilterListenerService::class.java)
        return flat.split(':').mapNotNull { ComponentName.unflattenFromString(it) }.any { it == me }
    }

    fun setActive(context: Context, active: Boolean): Boolean {
        if (active && !isReady(context)) return false
        Channels.ensure(context)
        if (active) {
            // Aggiorna numeri ed email dei contatti abilitati prima di attivare.
            runCatching {
                val refreshed = ContactsRepo.refresh(context, Store.current.contacts)
                Store.update { it.copy(contacts = refreshed) }
            }
        }
        Store.update { it.copy(active = active) }
        DndController.setActive(context, active)
        if (!active) {
            Mirror.cancelAll(context)
            CallAlert.stop(context)
        }
        TileService.requestListeningState(context, ComponentName(context, ToggleTileService::class.java))
        return true
    }

    fun toggle(context: Context): Boolean = setActive(context, !Store.current.active)
}
