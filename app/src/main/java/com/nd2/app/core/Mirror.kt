package com.nd2.app.core

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.os.BundleCompat
import com.nd2.app.R
import com.nd2.app.data.AppsRepo

/**
 * Ripropone una notifica bloccata dal Non disturbare sul canale di ND2.0 che lo ignora:
 * stesso titolo/testo, tap che apre l'app originale e stesse azioni (es. "Rispondi" di WhatsApp).
 */
object Mirror {
    private const val ID = 2001

    fun post(context: Context, sbn: StatusBarNotification) {
        val src = sbn.notification
        val ex = src.extras
        val appLabel = AppsRepo.label(context, sbn.packageName)
        val title = ex.getCharSequence(Notification.EXTRA_TITLE) ?: appLabel
        val text = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT)
            ?: lastMessageText(ex)
            ?: ""

        val largeIcon = src.getLargeIcon()?.takeIf {
            it.type == Icon.TYPE_BITMAP || it.type == Icon.TYPE_ADAPTIVE_BITMAP
        } ?: AppsRepo.icon(context, sbn.packageName)?.let { Icon.createWithBitmap(it) }

        val builder = Notification.Builder(context, Channels.MIRROR)
            .setSmallIcon(R.drawable.ic_stat_nd2)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSubText(appLabel)
            .setLargeIcon(largeIcon)
            .setContentIntent(src.contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(src.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
            .setCategory(src.category ?: Notification.CATEGORY_MESSAGE)
            .setWhen(src.`when`)
            .setShowWhen(true)
            .setGroup("nd2_" + sbn.packageName)
        src.actions?.forEach { builder.addAction(it) }

        nm(context).notify(sbn.key, ID, builder.build())
    }

    fun cancel(context: Context, originalKey: String) = nm(context).cancel(originalKey, ID)

    fun cancelAll(context: Context) {
        val nm = nm(context)
        nm.activeNotifications.filter { it.id == ID }.forEach { nm.cancel(it.tag, it.id) }
    }

    private fun lastMessageText(ex: Bundle): CharSequence? =
        BundleCompat.getParcelableArray(ex, Notification.EXTRA_MESSAGES, Bundle::class.java)
            ?.filterIsInstance<Bundle>()?.lastOrNull()?.getCharSequence("text")

    private fun nm(context: Context) = context.getSystemService(NotificationManager::class.java)
}
