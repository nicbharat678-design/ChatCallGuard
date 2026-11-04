package com.chatcallguard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Kis app se call aayi */
enum class Source(val label: String) { WHATSAPP("WhatsApp"), TELEGRAM("Telegram"), SIM("SIM call") }

/** Call ke baare mein faisla */
data class Decision(val block: Boolean, val warn: Boolean, val reason: String)

/** User ki settings (SharedPreferences mein) */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var homeCode: String
        get() = p.getString("homeCode", "+91")!!
        set(v) = p.edit().putString("homeCode", v).apply()

    var blockForeignUnknown: Boolean
        get() = p.getBoolean("blockForeign", true)
        set(v) = p.edit().putBoolean("blockForeign", v).apply()

    var blockAllUnknown: Boolean
        get() = p.getBoolean("blockAllUnknown", false)
        set(v) = p.edit().putBoolean("blockAllUnknown", v).apply()

    var hideForeignMessages: Boolean
        get() = p.getBoolean("hideForeignMsgs", true)
        set(v) = p.edit().putBoolean("hideForeignMsgs", v).apply()

    var scamMessageAlerts: Boolean
        get() = p.getBoolean("scamAlerts", true)
        set(v) = p.edit().putBoolean("scamAlerts", v).apply()

    fun blocklist(): Set<String> = p.getStringSet("blocklist", emptySet())!!

    fun addToBlocklist(entry: String) {
        val key = Phone.normalize(entry) ?: entry.trim().lowercase()
        if (key.isBlank()) return
        p.edit().putStringSet("blocklist", blocklist() + key).apply()
    }

    fun removeFromBlocklist(entry: String) {
        p.edit().putStringSet("blocklist", blocklist() - entry).apply()
    }
}

object Phone {
    /** Fraud mein zyada use hone wale country codes (in deshon ke sab log fraud nahi hote) */
    val HIGH_RISK_CODES = listOf(
        "+92", "+84", "+62", "+60", "+63", "+855", "+856", "+95",
        "+234", "+254", "+255", "+256", "+251", "+233", "+880", "+237", "+243", "+225"
    )

    private val numberLike = Regex("^[+0-9][0-9\\s\\-()]{6,}$")

    fun looksLikeNumber(s: String) = numberLike.matches(s.trim())

    fun normalize(raw: String?): String? {
        if (raw == null || !looksLikeNumber(raw)) return null
        val digits = raw.filter { it.isDigit() || it == '+' }
        return when {
            digits.startsWith("+") -> digits
            digits.startsWith("00") -> "+" + digits.drop(2)
            else -> digits
        }
    }

    fun isForeign(number: String, homeCode: String): Boolean =
        number.startsWith("+") && !number.startsWith(homeCode)

    fun isHighRisk(raw: String): Boolean {
        val n = normalize(raw) ?: return false
        return HIGH_RISK_CODES.any { n.startsWith(it) }
    }
}

object Contacts {
    private fun allowed(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun hasNumber(ctx: Context, number: String): Boolean {
        if (!allowed(ctx)) return false
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
            ?.use { it.count > 0 } ?: false
    }

    fun hasName(ctx: Context, name: String): Boolean {
        if (!allowed(ctx)) return false
        return ctx.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID),
            "${ContactsContract.Contacts.DISPLAY_NAME} = ?",
            arrayOf(name.trim()), null
        )?.use { it.count > 0 } ?: false
    }
}

/** Saari rules yahan ek jagah */
object Rules {
    fun isKnown(ctx: Context, who: String): Boolean {
        val number = Phone.normalize(who)
        return if (number != null) Contacts.hasNumber(ctx, number) else Contacts.hasName(ctx, who)
    }

    fun isUnknownForeign(ctx: Context, who: String): Boolean {
        val number = Phone.normalize(who) ?: return false
        return Phone.isForeign(number, Settings(ctx).homeCode) && !Contacts.hasNumber(ctx, number)
    }

    fun isBlocked(ctx: Context, who: String): Boolean =
        (Phone.normalize(who) ?: who.trim().lowercase()) in Settings(ctx).blocklist()

    fun decide(ctx: Context, caller: String, source: Source): Decision {
        val s = Settings(ctx)
        val number = Phone.normalize(caller)
        val key = number ?: caller.trim().lowercase()

        if (key in s.blocklist()) return Decision(true, true, "Aapne ise report/block kiya hai")

        val known = if (number != null) Contacts.hasNumber(ctx, number) else Contacts.hasName(ctx, caller)
        if (known) return Decision(false, false, "Contact mein hai")

        if (number != null && Phone.isForeign(number, s.homeCode)) {
            val risky = Phone.isHighRisk(caller)
            val why = if (risky) "Unknown videsh number (high-risk code)" else "Unknown videsh number"
            return Decision(s.blockForeignUnknown || s.blockAllUnknown, true, why)
        }

        if (s.blockAllUnknown) return Decision(true, true, "Unknown caller (contact mein nahi)")

        return Decision(false, true, "Unknown caller — savdhan rahein")
    }
}

/** Scam messages ke pattern (Hindi + English) */
object ScamDetector {
    private val patterns = listOf(
        "(settle|repay|clear|complete) (your |the )?loan|loan (overdue|due|settle)" to
            "Fake loan recovery dhamki — loan app scam",
        "(contact|message|call)\\w* (your )?(family|relatives|friends|contacts|leaders)" to
            "Family/contacts ko message karne ki dhamki — loan app blackmail",
        "your data is ready|morph\\w* (photo|pic)|photo viral|nude" to
            "Photo/data viral karne ki dhamki — blackmail scam",
        "(loan|karz|udhaar).{0,40}(chukao|bharo|warna)" to "Loan recovery dhamki",
        "kyc" to "KYC update ke naam pe fraud",
        "(send|share|bhej\\w*|batao|bata do) (the |your |apna )?otp|otp (send|share|bhej\\w*|batao|bata do)" to
            "OTP maangna — kabhi kisi ko OTP na dein",
        "digital arrest" to "'Digital arrest' naam ki koi cheez nahi hoti",
        "cbi" to "CBI/police ke naam pe darana",
        "police (case|complaint|station)|arrest warrant|fir (darj|registered)" to "Police ke naam pe darana",
        "customs|illegal parcel|parcel (seized|mein drugs)" to "Customs/parcel scam",
        "fedex" to "FedEx/courier scam",
        "lottery" to "Lottery/inaam scam",
        "lucky draw" to "Lucky draw scam",
        "kbc" to "KBC lottery scam",
        "work from home" to "Fake job/task scam",
        "part time job" to "Fake part-time job scam",
        "youtube like" to "Like karke paise kamao scam",
        "prepaid task|daily task|task (complete|karo)" to "Task/rating scam ho sakta hai",
        "guaranteed (return|profit)|double (your )?money|paisa double" to "Fake investment scam",
        "trading (group|tips)|stock tips" to "Fake trading group scam",
        "crypto (investment|profit)|usdt" to "Crypto scam ho sakta hai",
        "anydesk" to "Screen-share app install karwana",
        "teamviewer" to "Screen-share app install karwana",
        "\\.apk|apk (download|install)" to "Anjaan APK install karwana",
        "electricity (bill|connection).{0,40}(disconnect|cut|kat)|bijli.{0,30}(kat|band)" to
            "Bijli connection katne ka scam",
        "account (will be )?(block|blocked|freeze|band)" to "Account block hone ka darr",
        "sim (will be )?(block|blocked|band)" to "SIM block hone ka darr",
        "refund (process|ke liye)" to "Fake refund scam",
        "upi pin" to "UPI PIN maangna — PIN sirf paise BHEJNE ke liye lagta hai",
        "scan qr" to "QR scan karke paise milte nahi, kat'te hain",
        "sextortion" to "Blackmail scam",
        "video call record" to "Video call blackmail scam"
    )

    private val compiled = patterns.map { (p, why) -> Regex("\\b(?:$p)\\b") to why }

    fun check(text: String): String? {
        val t = text.lowercase()
        return compiled.firstOrNull { (r, _) -> r.containsMatchIn(t) }?.second
    }
}

/** Block/warning ka itihaas (sirf phone mein, kahin bheja nahi jaata) */
object EventLog {
    private const val MAX = 300

    fun add(ctx: Context, source: Source, who: String, action: String, reason: String) {
        val p = ctx.getSharedPreferences("log", Context.MODE_PRIVATE)
        val arr = JSONArray(p.getString("items", "[]"))
        val item = JSONObject()
            .put("t", System.currentTimeMillis())
            .put("src", source.label).put("who", who)
            .put("action", action).put("reason", reason)
        val out = JSONArray().put(item)
        for (i in 0 until minOf(arr.length(), MAX - 1)) out.put(arr.get(i))
        p.edit().putString("items", out.toString()).apply()
    }

    fun lines(ctx: Context): List<String> {
        val arr = JSONArray(ctx.getSharedPreferences("log", Context.MODE_PRIVATE).getString("items", "[]"))
        val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            "${fmt.format(Date(o.getLong("t")))}  •  ${o.getString("src")}\n" +
                "${o.getString("action")}: ${o.getString("who")}\n${o.getString("reason")}"
        }
    }

    fun clear(ctx: Context) =
        ctx.getSharedPreferences("log", Context.MODE_PRIVATE).edit().clear().apply()
}

object Notifier {
    private const val CH = "alerts"

    fun init(ctx: Context) {
        val ch = NotificationChannel(CH, "Fraud alerts", NotificationManager.IMPORTANCE_HIGH)
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun show(ctx: Context, title: String, text: String) {
        init(ctx)
        val n = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify((title + text).hashCode(), n)
        } catch (e: SecurityException) {
            // notification permission nahi mili
        }
    }
}
