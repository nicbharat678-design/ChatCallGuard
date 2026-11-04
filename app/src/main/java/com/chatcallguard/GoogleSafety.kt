package com.chatcallguard

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.provider.Settings as SysSettings
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Gmail / Google Photos ki suraksha + phone/tablet ki buniyadi jaanch.
 * App internet use nahi karti: sirf phone pe jaanch karti hai aur Google ke asli pages kholti hai.
 */
object GoogleSafety {

    // ---------------- 1. Nakli Google link + code chori ----------------

    private val googleDomains = listOf(
        "google.com", "google.co.in", "gmail.com", "youtube.com", "android.com", "g.co", "goo.gl",
        "googleusercontent.com", "gstatic.com", "withgoogle.com", "google", "youtu.be"
    )

    private val urlRegex = Regex(
        "(?i)\\b((?:https?://|www\\.)[^\\s<>\"]+|[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\." +
            "(?:com|in|net|org|xyz|top|site|online|info|live|link|click|app|me|co|io|shop|vip|cc|tk|ml|ga|cf|gq|buzz|icu|cn|ru)" +
            "(?:/[^\\s<>\"]*)?)"
    )

    /** g00gle, goog1e, gmai1, y0utube jaise nakli naam */
    private val lookalike = Regex("g[o0]{2}g[l1i]e|g[o0]{2}gel|gma[i1l]l|gmall|y[o0]u?tub|accounts?-google|google-?(verify|secure|support|login)")
    private val googleTheme = Regex(
        "google|gmail|g-mail|youtube|play ?store|google ?photos|google ?pay|gpay|" +
            "account (suspend|band|block|disable|delete)|storage (full|bhar)"
    )

    private val codeAsk = Regex(
        "(send|share|forward|bhej\\w*|batao|bata do|de do|bolo).{0,30}(code|otp)|" +
            "(code|otp).{0,30}(send|share|forward|bhej\\w*|batao|bata do|de do|bolo)"
    )
    private val googleCode = Regex("\\bg-\\d{6}\\b")

    private fun host(url: String): String =
        url.lowercase()
            .substringAfter("://")
            .substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
            .removePrefix("www.")

    private fun isGoogle(h: String) = googleDomains.any { h == it || h.endsWith(".$it") }

    /** Message mein Gmail-chori ki koshish mili to wajah lautata hai, warna null */
    fun checkMessage(text: String): String? {
        val t = text.lowercase()

        if (googleCode.containsMatchIn(t) ||
            (codeAsk.containsMatchIn(t) && Regex("google|gmail|whatsapp|verification|6 ?digit").containsMatchIn(t))
        ) {
            return "🔑 Koi aapse Google/WhatsApp ka verification code maang raha hai. Ye code dete hi aapka " +
                "Gmail/WhatsApp unke haath chala jaayega. KABHI na dein — chahe dost ya rishtedaar ke number se aaye."
        }

        for (m in urlRegex.findAll(t)) {
            val h = host(m.value)
            if (h.isEmpty() || isGoogle(h)) continue
            if (lookalike.containsMatchIn(h)) {
                return "🎣 Nakli Google/Gmail link ($h)! Asli Google link sirf google.com pe hota hai. " +
                    "Is page pe password BILKUL na daalein."
            }
            if (googleTheme.containsMatchIn(t)) {
                return "🎣 Message Google/Gmail ke naam pe hai, lekin link '$h' Google ka nahi hai. " +
                    "Ye password churaane wala nakli page ho sakta hai."
            }
        }
        return null
    }

    // ---------------- 2. Phone / Tablet ki jaanch ----------------

    fun deviceIssues(ctx: Context): List<Issue> {
        val out = mutableListOf<Issue>()

        val km = ctx.getSystemService(KeyguardManager::class.java)
        if (!km.isDeviceSecure) {
            out += Issue(
                2, "🔓 Screen lock nahi hai",
                "Phone/tablet kisi ke haath laga to Gmail, Photos, WhatsApp sab khul jaayega. " +
                    "PIN/Password + fingerprint lagao (pattern se PIN zyada surakshit hai).",
                null, SysSettings.ACTION_SECURITY_SETTINGS
            )
        }

        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        val enc = dpm.storageEncryptionStatus
        if (enc == DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE || enc == DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED) {
            out += Issue(
                1, "💾 Storage encrypted nahi hai",
                "Phone chori hone pe data nikala ja sakta hai. Screen lock lagao aur Security settings mein Encryption dekho.",
                null, SysSettings.ACTION_SECURITY_SETTINGS
            )
        }

        runCatching {
            val patch = LocalDate.parse(Build.VERSION.SECURITY_PATCH)
            val months = ChronoUnit.MONTHS.between(patch, LocalDate.now())
            if (months >= 6) {
                out += Issue(
                    if (months >= 12) 2 else 1,
                    "🩹 Security update $months mahine purana hai",
                    "Purane phone mein hackers ke jaane-pehchaane raaste khule rehte hain. " +
                        "Settings → System/Software update mein update dhoondho aur install karo.",
                    null, "android.settings.SYSTEM_UPDATE_SETTINGS"
                )
            }
        }

        if (Build.VERSION.SDK_INT < 31) {
            out += Issue(
                1, "📵 Purana Android (${Build.VERSION.RELEASE})",
                "Android 12 se kam mein Camera/Mic master switch aur kai naye suraksha features nahi hote. " +
                    "Update mile to zaroor karo.",
                null, "android.settings.SYSTEM_UPDATE_SETTINGS"
            )
        }

        if (SysSettings.Global.getInt(ctx.contentResolver, SysSettings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1) {
            out += Issue(
                1, "🧑‍💻 Developer options ON hai",
                "Aam log ke liye zaroorat nahi. Developer options mein upar wala switch OFF kar do.",
                null, SysSettings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            )
        }
        return out
    }

    // ---------------- 3. Gmail / Google Photos Kila checklist ----------------

    /** open: "http..." = Google ka page, "pkg:..." = app kholo, baaki = Android settings action */
    data class Step(val id: String, val title: String, val why: String, val open: String)

    val steps = listOf(
        Step(
            "2sv", "🔐 2-Step Verification ON karo",
            "Password chori ho jaaye tab bhi koi login nahi kar payega. SABSE ZAROORI!",
            "https://myaccount.google.com/signinoptions/two-step-verification"
        ),
        Step(
            "passkey", "🔑 Passkey banao",
            "Fingerprint/face se login — password churaane ka sawaal hi khatam.",
            "https://myaccount.google.com/signinoptions/passkeys"
        ),
        Step(
            "devices", "📱 Kaun-kaun se phone/computer login hain",
            "Jo device aapka nahi hai, use turant 'Sign out' karo aur password badlo.",
            "https://myaccount.google.com/device-activity"
        ),
        Step(
            "apps", "🔌 Google account se jude apps",
            "Anjaan apps/games ki access hatao — ye aapki Gmail/Photos padh sakti hain.",
            "https://myaccount.google.com/connections"
        ),
        Step(
            "share", "🖼️ Google Photos: kya-kya share hai",
            "Purane shared albums aur links band karo. Link jiske paas hai, wo photos dekh sakta hai.",
            "https://photos.google.com/sharing"
        ),
        Step(
            "locked", "🔒 Family photos 'Locked Folder' mein rakho",
            "Google Photos app → Collections → Locked Folder. Fingerprint/PIN ke bina koi nahi dekh sakta, " +
                "aur ye photos search, albums aur sharing mein nahi dikhti.",
            "pkg:com.google.android.apps.photos"
        ),
        Step(
            "recovery", "📞 Recovery phone aur email sahi karo",
            "Account chhin jaaye to isi se wapas milta hai. Purana number ho to badlo.",
            "https://myaccount.google.com/security"
        ),
        Step(
            "passwords", "🗝️ Leak hue / kamzor passwords badlo",
            "Google batata hai kaun se password internet pe leak ho chuke hain.",
            "https://passwords.google.com/checkup"
        ),
        Step(
            "find", "📍 Find My Device ON karo",
            "Phone/tablet kho jaaye to door se lock ya poora saaf (erase) kar sakte ho.",
            "https://www.google.com/android/find"
        ),
        Step(
            "theft", "🚨 Theft protection ON karo",
            "Settings → Google → Theft protection. Phone chheen ke bhaage to screen apne aap lock ho jaati hai.",
            SysSettings.ACTION_SETTINGS
        ),
        Step(
            "checkup", "🛡️ Google Security Checkup chalao",
            "Google khud aapke account ki saari kamzoriyan dikhata hai.",
            "https://myaccount.google.com/security-checkup"
        ),
        Step(
            "app", "🏆 Advanced Protection (sabse oonchi suraksha)",
            "Google ka sabse mazboot lock — patrakaron aur netaon ke liye bana, sabke liye free. " +
                "Login ke liye passkey/security key zaroori ho jaata hai.",
            "https://landing.google.com/advancedprotection/"
        )
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("google_kila", Context.MODE_PRIVATE)

    fun isDone(ctx: Context, id: String) = prefs(ctx).getBoolean(id, false)

    fun toggle(ctx: Context, id: String) = prefs(ctx).edit().putBoolean(id, !isDone(ctx, id)).apply()

    fun score(ctx: Context) = steps.count { isDone(ctx, it.id) }
}
