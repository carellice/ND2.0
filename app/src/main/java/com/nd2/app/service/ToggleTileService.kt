package com.nd2.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.nd2.app.R
import com.nd2.app.core.Nd2Mode
import com.nd2.app.data.Store
import com.nd2.app.ui.MainActivity

/** Pulsante nelle Impostazioni rapide per attivare/disattivare ND2.0. */
class ToggleTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        if (!Nd2Mode.isReady(this)) {
            openApp()
            return
        }
        Nd2Mode.toggle(this)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val active = Store.current.active
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "ND2.0"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (active) "Attiva" else "Disattivata"
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** Mostra la richiesta di sistema per aggiungere il pulsante alle Impostazioni rapide (Android 13+). */
        fun requestAdd(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(
                    context,
                    "Apri le Impostazioni rapide, tocca la matita e trascina il riquadro ND2.0",
                    Toast.LENGTH_LONG,
                ).show()
                return
            }
            val sbm = context.getSystemService(StatusBarManager::class.java) ?: return
            sbm.requestAddTileService(
                ComponentName(context, ToggleTileService::class.java),
                "ND2.0",
                Icon.createWithResource(context, R.drawable.ic_stat_nd2),
                context.mainExecutor,
            ) { result ->
                val msg = when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Pulsante ND2.0 aggiunto alle Impostazioni rapide"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Il pulsante ND2.0 è già nelle Impostazioni rapide"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> null
                    else -> "Apri le Impostazioni rapide, tocca la matita e trascina il riquadro ND2.0"
                }
                msg?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
            }
        }
    }
}
