package com.nd2.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.nd2.app.core.Channels
import com.nd2.app.core.DndController
import com.nd2.app.core.Nd2Mode
import com.nd2.app.service.FilterListenerService

data class PermStatus(
    val listener: Boolean,
    val dnd: Boolean,
    val contacts: Boolean,
    val phone: Boolean,
    val postNotifications: Boolean,
    val channelsNotBypassing: List<String>,
) {
    val allGood get() = listener && dnd && contacts && phone && postNotifications && channelsNotBypassing.isEmpty()
}

object Perms {
    val PHONE = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.ANSWER_PHONE_CALLS,
    )

    fun status(context: Context) = PermStatus(
        listener = Nd2Mode.hasListenerAccess(context),
        dnd = DndController.hasAccess(context),
        contacts = granted(context, Manifest.permission.READ_CONTACTS),
        phone = PHONE.all { granted(context, it) },
        postNotifications = Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS),
        channelsNotBypassing = Channels.channelsNotBypassing(context),
    )

    fun granted(context: Context, perm: String) =
        context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    fun listenerSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, FilterListenerService::class.java).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

    fun dndSettings(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun channelSettings(context: Context, channelId: String): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)

    fun appSettings(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", context.packageName, null))
}
