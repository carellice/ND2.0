package com.nd2.app.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager

/**
 * Canali di notifica di ND2.0. Vengono creati solo dopo che l'app ha ottenuto l'accesso al
 * Non disturbare: solo così il sistema accetta il flag "ignora Non disturbare".
 */
object Channels {
    const val MIRROR = "nd2_allowed"
    const val CALL = "nd2_call"

    fun ensure(context: Context) {
        if (!DndController.hasAccess(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(MIRROR) == null) {
            nm.createNotificationChannel(
                NotificationChannel(MIRROR, "Notifiche abilitate", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Notifiche di contatti e app abilitate mentre ND2.0 è attiva"
                    setBypassDnd(true)
                    enableVibration(true)
                    setShowBadge(true)
                }
            )
        }
        if (nm.getNotificationChannel(CALL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CALL, "Chiamate abilitate", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Suoneria per le chiamate dei contatti abilitati mentre ND2.0 è attiva"
                    setBypassDnd(true)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 800, 600, 800, 600)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                        AudioAttributes.Builder()
                            // USAGE_NOTIFICATION: la suoneria "chiamate" è silenziata dal Non disturbare.
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                }
            )
        }
    }

    /** Canali che l'utente o il sistema hanno lasciato senza "ignora Non disturbare". */
    fun channelsNotBypassing(context: Context): List<String> {
        val nm = context.getSystemService(NotificationManager::class.java)
        return listOf(MIRROR, CALL).filter { id ->
            nm.getNotificationChannel(id)?.let { !it.canBypassDnd() } ?: false
        }
    }
}
