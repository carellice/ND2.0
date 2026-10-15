package com.nd2.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.nd2.app.core.CallAlert
import com.nd2.app.core.Matcher
import com.nd2.app.data.Store

/** Fa squillare il telefono quando, a modalità attiva, chiama un contatto abilitato. */
class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!Store.current.active) return
                // Il numero è presente solo con il permesso READ_CALL_LOG (il broadcast arriva due volte).
                @Suppress("DEPRECATION")
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return
                val contact = Matcher.allowedContactForNumber(context, number) ?: return
                CallAlert.start(context, contact.name, number)
            }
            else -> CallAlert.stop(context)
        }
    }
}

/** Azioni della notifica di chiamata: rispondi, rifiuta, mostra. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ANSWER -> CallAlert.answer(context)
            ACTION_DECLINE -> CallAlert.decline(context)
            ACTION_SHOW -> CallAlert.showInCallScreen(context)
        }
        if (intent.action != ACTION_SHOW) CallAlert.stop(context)
    }

    companion object {
        const val ACTION_ANSWER = "com.nd2.app.ANSWER"
        const val ACTION_DECLINE = "com.nd2.app.DECLINE"
        const val ACTION_SHOW = "com.nd2.app.SHOW_CALL"
    }
}
