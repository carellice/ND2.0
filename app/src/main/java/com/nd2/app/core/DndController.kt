package com.nd2.app.core

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import com.nd2.app.data.Store
import com.nd2.app.ui.MainActivity

/**
 * Gestisce una regola "Non disturbare" di sistema dedicata (visibile in Impostazioni > Non disturbare
 * come "ND2.0"). Quando è attiva silenzia e nasconde tutto; ND2.0 poi fa passare (riproponendole
 * su un canale che ignora il Non disturbare) solo le notifiche dei contatti e delle app abilitate.
 * Le notifiche bloccate non vengono cancellate: ricompaiono quando la modalità si disattiva.
 */
object DndController {

    private val CONDITION_ID: Uri = Uri.parse("condition://com.nd2.app/active")

    fun hasAccess(context: Context): Boolean = nm(context).isNotificationPolicyAccessGranted

    private fun nm(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun policy(): ZenPolicy = ZenPolicy.Builder()
        .disallowAllSounds()
        .allowAlarms(true)
        .allowMedia(true)
        .allowCalls(ZenPolicy.PEOPLE_TYPE_NONE)
        .allowMessages(ZenPolicy.PEOPLE_TYPE_NONE)
        .allowRepeatCallers(false)
        .allowEvents(false)
        .allowReminders(false)
        .allowSystem(false)
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                allowConversations(ZenPolicy.CONVERSATION_SENDERS_NONE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                // Necessario perché i canali di ND2.0 (che ignorano il Non disturbare) suonino.
                allowPriorityChannels(true)
            }
        }
        .hideAllVisualEffects()
        .build()

    private fun buildRule(context: Context) = AutomaticZenRule(
        "ND2.0",
        null,
        ComponentName(context, MainActivity::class.java),
        CONDITION_ID,
        policy(),
        NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        true,
    )

    private fun ensureRule(context: Context): String {
        val nm = nm(context)
        val existing = Store.zenRuleId
        if (existing != null && nm.getAutomaticZenRule(existing) != null) {
            nm.updateAutomaticZenRule(existing, buildRule(context))
            return existing
        }
        return nm.addAutomaticZenRule(buildRule(context)).also { Store.zenRuleId = it }
    }

    fun setActive(context: Context, active: Boolean) {
        if (!hasAccess(context)) return
        val id = if (active) ensureRule(context) else (Store.zenRuleId ?: return)
        val state = if (active) Condition.STATE_TRUE else Condition.STATE_FALSE
        val summary = if (active) "Attiva" else "Disattivata"
        runCatching { nm(context).setAutomaticZenRuleState(id, Condition(CONDITION_ID, summary, state)) }
    }
}
