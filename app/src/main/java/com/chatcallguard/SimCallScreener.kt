package com.chatcallguard

import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * Normal SIM calls ke liye (Truecaller jaisa).
 * App ko "Caller ID & spam app" banane par hi Android ise chalata hai.
 */
class SimCallScreener : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        val number = details.handle?.schemeSpecificPart.orEmpty()

        if (!incoming || number.isBlank()) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }

        val d = Rules.decide(this, number, Source.SIM)
        val response = CallResponse.Builder()
        if (d.block) {
            response.setDisallowCall(true).setRejectCall(true)
                .setSkipCallLog(false).setSkipNotification(true)
            EventLog.add(this, Source.SIM, number, "BLOCKED", d.reason)
            Notifier.show(this, "🛡️ SIM call block ki gayi", "$number\n${d.reason}")
        } else if (d.warn && Phone.isForeign(Phone.normalize(number) ?: "", Settings(this).homeCode)) {
            EventLog.add(this, Source.SIM, number, "WARNING", d.reason)
            Notifier.show(this, "⚠️ Videsh se call", "$number\n${d.reason}")
        }
        respondToCall(details, response.build())
    }
}
