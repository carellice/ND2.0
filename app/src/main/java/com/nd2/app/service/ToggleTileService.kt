package com.nd2.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
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
}
