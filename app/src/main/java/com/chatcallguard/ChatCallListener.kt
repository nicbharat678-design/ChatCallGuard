package com.chatcallguard

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * WhatsApp / Telegram ki notifications dekhta hai.
 *
 * Encryption nahi todta: sirf wahi padhta hai jo notification mein
 * already phone ki screen pe dikh raha hai (caller ka naam/number, message preview).
 * Kuch bhi internet pe nahi bheja jaata.
 */
class ChatCallListener : NotificationListenerService() {

    private val apps = mapOf(
        "com.whatsapp" to Source.WHATSAPP,
        "com.whatsapp.w4b" to Source.WHATSAPP,
        "org.telegram.messenger" to Source.TELEGRAM,
        "org.telegram.messenger.web" to Source.TELEGRAM,
        "org.thunderdog.challegram" to Source.TELEGRAM
    )

    private val declineWords = Regex("decline|reject|अस्वीकार|मना|रद्द", RegexOption.IGNORE_CASE)
    private val callWords = Regex(
        "incoming (voice |video )?call|calling…|calling\\.\\.\\.|ringing|इनकमिंग|कॉल आ रहा",
        RegexOption.IGNORE_CASE
    )

    /** Ek hi call/message pe baar-baar alert na aaye */
    private val seen = LinkedHashSet<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = apps[sbn.packageName] ?: return
        val n = sbn.notification ?: return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isEmpty()) return

        if (isIncomingCall(n, text)) handleCall(sbn, n, source, title)
        else handleMessage(sbn, source, title, text)
    }

    private fun isIncomingCall(n: Notification, text: String): Boolean {
        if (n.category == Notification.CATEGORY_CALL) return true
        if (callWords.containsMatchIn(text)) return true
        return n.actions?.any { declineWords.containsMatchIn(it.title ?: "") } == true &&
            n.actions?.any { Regex("answer|accept|उत्तर", RegexOption.IGNORE_CASE).containsMatchIn(it.title ?: "") } == true
    }

    private fun handleCall(sbn: StatusBarNotification, n: Notification, source: Source, caller: String) {
        val id = "call:${sbn.key}:${sbn.postTime}"
        if (!remember(id)) return

        val d = Rules.decide(this, caller, source)
        if (!d.block && !d.warn) return

        if (d.block) {
            val declined = decline(n)
            EventLog.add(this, source, caller, if (declined) "BLOCKED" else "BLOCK FAILED", d.reason)
            Notifier.show(
                this,
                if (declined) "🛡️ ${source.label} call block ki gayi" else "⚠️ ${source.label}: khatarnak call",
                if (declined) "$caller\n${d.reason}"
                else "$caller\n${d.reason}\nCall khud se kaat dein — uthaayein nahi."
            )
        } else {
            EventLog.add(this, source, caller, "WARNING", d.reason)
            Notifier.show(
                this, "⚠️ ${source.label}: anjaan caller",
                "$caller\n${d.reason}\nBank/police/courier wale WhatsApp pe call nahi karte. " +
                    "OTP, PIN, screen-share kabhi na dein."
            )
        }
    }

    /** Notification ka "Decline" button khud dabata hai */
    private fun decline(n: Notification): Boolean {
        val action = n.actions?.firstOrNull { declineWords.containsMatchIn(it.title ?: "") } ?: return false
        return try {
            action.actionIntent.send()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun handleMessage(sbn: StatusBarNotification, source: Source, sender: String, text: String) {
        if (text.isBlank()) return
        val s = Settings(this)
        if (!remember("msg:${sender}:${text.hashCode()}")) return

        val unknown = !Rules.isKnown(this, sender)
        val blocked = Rules.isBlocked(this, sender)
        val foreign = Rules.isUnknownForeign(this, sender)

        val scam = if (s.scamMessageAlerts) ScamDetector.check(text) else null
        val fakeBrand = if (unknown) Brands.mentionedButNotInstalled(this, text) else null

        val reasons = buildList {
            if (blocked) add("Aapne ise block kiya hai")
            if (foreign) add("Unknown videsh number" + if (Phone.isHighRisk(sender)) " (high-risk code)" else "")
            if (scam != null) add(scam)
            if (fakeBrand != null) add(
                "Message '$fakeBrand' ke naam se hai, lekin aapke phone mein '$fakeBrand' ka app hi nahi hai. " +
                    "Asli company apne app/SMS se baat karti hai, anjaan WhatsApp number se nahi."
            )
        }
        if (reasons.isEmpty()) return

        val hide = blocked || (foreign && s.hideForeignMessages)
        if (hide) cancelNotification(sbn.key)

        val why = reasons.joinToString("\n• ", prefix = "• ")
        EventLog.add(this, source, sender, if (hide) "MESSAGE HIDDEN" else "SCAM MESSAGE", reasons.joinToString(" | "))
        Notifier.show(
            this,
            if (hide) "🛡️ ${source.label}: khatarnak message chhupaya" else "🚨 ${source.label}: scam message ho sakta hai",
            "$sender: \"${text.take(80)}\"\n\n$why\n\n" +
                "Reply na karein, link na kholein, paise/OTP na bhejein. " +
                "Dhamki mile to darein nahi — 1930 pe call karein. Chat mein jaake Block + Report karein."
        )
    }
/** Kila (hack scan, permission chowkidaar, camera/mic alarm) isi service ke saath chalta hai */
    override fun onListenerConnected() {
        Fortress.start(this)
    }

    override fun onListenerDisconnected() {
        Fortress.stop()
    }
    private fun remember(id: String): Boolean {
        if (!seen.add(id)) return false
        while (seen.size > 200) seen.remove(seen.first())
        return true
    }
}
