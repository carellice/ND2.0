package com.nd2.app.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import com.nd2.app.R
import com.nd2.app.service.CallActionReceiver

/**
 * Il Non disturbare silenzia tutte le chiamate; per quelle dei contatti abilitati ND2.0 fa
 * squillare il telefono con una notifica "insistente" (la suoneria si ripete finché non rispondi
 * o la chiamata termina) e porta in primo piano la schermata di chiamata.
 */
object CallAlert {
    private const val ID = 3001

    fun start(context: Context, name: String, number: String) {
        val builder = Notification.Builder(context, Channels.CALL)
            .setSmallIcon(R.drawable.ic_stat_nd2)
            .setContentTitle("Chiamata da $name")
            .setContentText(number)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(action(context, CallActionReceiver.ACTION_SHOW, 0))
            .addAction(
                Notification.Action.Builder(null, "Rispondi", action(context, CallActionReceiver.ACTION_ANSWER, 1)).build()
            )
            .addAction(
                Notification.Action.Builder(null, "Rifiuta", action(context, CallActionReceiver.ACTION_DECLINE, 2)).build()
            )
        val n = builder.build().apply { flags = flags or Notification.FLAG_INSISTENT }
        context.getSystemService(NotificationManager::class.java).notify(ID, n)
        showInCallScreen(context)
    }

    fun stop(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    @SuppressLint("MissingPermission")
    fun showInCallScreen(context: Context) {
        if (!granted(context, Manifest.permission.READ_PHONE_STATE)) return
        runCatching { context.getSystemService(TelecomManager::class.java).showInCallScreen(false) }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun answer(context: Context) {
        if (!granted(context, Manifest.permission.ANSWER_PHONE_CALLS)) return showInCallScreen(context)
        runCatching { context.getSystemService(TelecomManager::class.java).acceptRingingCall() }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun decline(context: Context) {
        if (!granted(context, Manifest.permission.ANSWER_PHONE_CALLS)) return showInCallScreen(context)
        runCatching { context.getSystemService(TelecomManager::class.java).endCall() }
    }

    private fun granted(context: Context, perm: String) =
        context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private fun action(context: Context, action: String, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, req,
            Intent(context, CallActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
