package com.nd2.app.service

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.service.quicksettings.TileService
import com.nd2.app.core.CallAlert
import com.nd2.app.core.Matcher
import com.nd2.app.core.Mirror
import com.nd2.app.data.Store

/**
 * Riceve tutte le notifiche del telefono. A modalità attiva, quelle bloccate dal Non disturbare
 * che arrivano da un'app sempre abilitata o da un contatto abilitato vengono fatte passare.
 */
class FilterListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        val state = Store.current
        if (!state.active || sbn.packageName == packageName) return

        val n = sbn.notification
        val skipFlags = Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT or
            Notification.FLAG_FOREGROUND_SERVICE
        if (n.flags and skipFlags != 0) return
        // Le chiamate in arrivo le gestisce CallStateReceiver.
        if (n.category == Notification.CATEGORY_CALL) return

        // Se il sistema la lascia già passare (es. sveglie) non serve fare nulla.
        val ranking = Ranking()
        if (rankingMap.getRanking(sbn.key, ranking) && ranking.matchesInterruptionFilter()) return

        val allowed = state.apps.any { it.pkg == sbn.packageName } || Matcher.isFromAllowedContact(n)
        if (allowed) runCatching { Mirror.post(this, sbn) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != packageName) Mirror.cancel(this, sbn.key)
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        // Se l'utente spegne il Non disturbare dal sistema, consideriamo ND2.0 disattivata.
        if (interruptionFilter == INTERRUPTION_FILTER_ALL && Store.current.active) {
            Store.update { it.copy(active = false) }
            Mirror.cancelAll(this)
            CallAlert.stop(this)
            TileService.requestListeningState(this, ComponentName(this, ToggleTileService::class.java))
        }
    }
}
