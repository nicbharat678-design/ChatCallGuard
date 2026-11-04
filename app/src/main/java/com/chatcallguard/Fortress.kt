package com.chatcallguard

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings as SysSettings
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ek khatre ka nishaan */
data class Issue(
    val level: Int,          // 1 = dhyan do, 2 = khatra
    val title: String,
    val detail: String,
    val pkg: String? = null,
    val fix: String = SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS
) {
    val key get() = "$title|$pkg"
}

/** Kaun si app bharosemand hai, kahan se install hui */
object Trust {
    private val prefixes = listOf(
        "com.google.", "com.android.", "com.samsung.", "com.sec.", "com.miui.", "com.xiaomi.",
        "com.oneplus.", "com.coloros.", "com.oplus.", "com.heytap.", "com.vivo.", "com.bbk.", "com.oppo.",
        "com.realme.", "com.motorola.", "com.huawei.", "com.whatsapp", "org.telegram", "com.truecaller",
        "com.microsoft.", "com.chatcallguard"
    )

    private val stores = setOf(
        "com.android.vending", "com.sec.android.app.samsungapps", "com.huawei.appmarket",
        "com.xiaomi.market", "com.xiaomi.mipicks", "com.heytap.market", "com.oppo.market",
        "com.bbk.appstore", "com.amazon.venezia", "com.google.android.feedback"
    )

    fun isTrusted(pkg: String) = prefixes.any { pkg.startsWith(it) }

    fun isSystem(pm: PackageManager, pkg: String): Boolean = try {
        pm.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
    } catch (e: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    private fun installer(pm: PackageManager, pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
        else pm.getInstallerPackageName(pkg)
    } catch (e: Exception) {
        null
    }

    /** Play Store / company store ke bahar se aayi (APK file se) */
    fun isSideloaded(pm: PackageManager, pkg: String): Boolean {
        val i = installer(pm, pkg) ?: return true
        return i !in stores
    }

    fun label(pm: PackageManager, pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    fun granted(p: PackageInfo): Set<String> =
        p.requestedPermissions.orEmpty().filterIndexed { i, perm ->
            perm in PrivacyScanner.SENSITIVE &&
                (p.requestedPermissionsFlags?.getOrNull(i) ?: 0) and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
        }.map { PrivacyScanner.SENSITIVE.getValue(it) }.toSet()
}

/**
 * Hack ke nishaan dhoondhta hai.
 * 100% guarantee koi nahi de sakta, lekin asli fraud mein yahi nishaan sabse zyada milte hain.
 */
object HackScanner {
    private val remote = listOf("anydesk", "teamviewer", "rustdesk", "airdroid", "quicksupport", "screenshare")
    private val spySet = setOf("Camera", "Mic", "SMS", "Call log", "Contacts", "Location")

    private fun enabledPkgs(ctx: Context, key: String): Set<String> =
        (SysSettings.Secure.getString(ctx.contentResolver, key) ?: "")
            .split(':')
            .map { it.substringBefore('/').trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun scan(ctx: Context): List<Issue> {
        val pm = ctx.packageManager
        val me = ctx.packageName
        val out = mutableListOf<Issue>()
        fun suspicious(pkg: String) = pkg != me && !Trust.isTrusted(pkg) && !Trust.isSystem(pm, pkg)

        // 1. Accessibility: screen padhna + buttons khud dabana (hacker apps ka #1 hathiyar)
        enabledPkgs(ctx, SysSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES).filter { suspicious(it) }.forEach {
            out += Issue(
                2, "🕵️ '${Trust.label(pm, it)}' aapki screen padh aur chala sakti hai",
                "Iska Accessibility ON hai. Hacker apps isi se OTP, PIN aur password churaati hain. " +
                    "Agar aapne khud jaan-boojh kar ON nahi kiya, to turant OFF karo aur app uninstall karo.",
                it, SysSettings.ACTION_ACCESSIBILITY_SETTINGS
            )
        }

        // 2. Doosri app jo saari notifications (OTP bhi) padh rahi hai
        enabledPkgs(ctx, "enabled_notification_listeners").filter { suspicious(it) }.forEach {
            out += Issue(
                if (Trust.isSideloaded(pm, it)) 2 else 1,
                "📨 '${Trust.label(pm, it)}' aapke saare messages/OTP padh sakti hai",
                "Iske paas Notification access hai. Zaroorat na ho to OFF karo.",
                it, SysSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            )
        }

        // 3. Device admin: uninstall hone se bachti hai, phone lock kar sakti hai
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        dpm.activeAdmins.orEmpty().map { it.packageName }.distinct().filter { suspicious(it) }.forEach {
            out += Issue(
                2, "🔐 '${Trust.label(pm, it)}' ke paas Device Admin power hai",
                "Ye app phone lock kar sakti hai aur uninstall hone se bachti hai. " +
                    "Security settings → Device admin apps mein jaakar ise hatao, phir uninstall karo.",
                it, SysSettings.ACTION_SECURITY_SETTINGS
            )
        }

        // 4. Har app ki jaanch
        @Suppress("DEPRECATION")
        val pkgs = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        for (p in pkgs) {
            val pkg = p.packageName
            val ai = p.applicationInfo ?: continue
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 || pkg == me || Trust.isTrusted(pkg)) continue

            val label = pm.getApplicationLabel(ai).toString()
            val perms = Trust.granted(p)
            val side = Trust.isSideloaded(pm, pkg)
            val hidden = pm.getLaunchIntentForPackage(pkg) == null
            val name = (label + " " + pkg).lowercase()

            when {
                remote.any { name.contains(it) } -> out += Issue(
                    2, "🖥️ '$label' se koi aapka phone door se chala sakta hai",
                    "Fraudster 'support' ke naam pe AnyDesk/TeamViewer install karwate hain. " +
                        "Agar aapne khud kaam ke liye nahi daala, to uninstall karo.", pkg
                )
                side && ("SMS" in perms || "Call log" in perms) -> out += Issue(
                    2, "📦 '$label' Play Store se nahi aayi aur SMS/Call log padh rahi hai",
                    "Nakli 'bank update', 'KYC' ya 'bijli bill' APK aise hi OTP churaati hain. Uninstall karo.", pkg
                )
                hidden && perms.any { it in spySet } -> out += Issue(
                    2, "👻 '$label' chhupi hui hai (icon nahi dikhta)",
                    "Iske paas ${perms.joinToString(", ")} ki permission hai. Spy apps khud ko aise hi chhupaati hain.", pkg
                )
                side && perms.any { it == "Camera" || it == "Mic" || it == "Contacts" } -> out += Issue(
                    1, "⚠️ '$label' Play Store ke bahar se aayi hai",
                    "Iske paas ${perms.joinToString(", ")} hai. Bharosa na ho to permission hatao.", pkg
                )
            }
        }

        // 5. USB debugging: computer se phone mein ghusne ka raasta
        if (SysSettings.Global.getInt(ctx.contentResolver, SysSettings.Global.ADB_ENABLED, 0) == 1) {
            out += Issue(
                1, "🔌 USB debugging ON hai",
                "Isse computer se phone mein ghusa ja sakta hai. Developer ho to theek hai, warna OFF karo.",
                null, SysSettings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            )
        }

        return out.distinctBy { it.key }.sortedByDescending { it.level }
    }
}

/** Permission ka chowkidaar: kisi app ko nayi khatarnak permission mili to turant batata hai */
object Watchdog {
    private val watched = setOf("Camera", "Mic", "Call log", "SMS", "Contacts", "Location", "Photos")

    fun check(ctx: Context, notify: Boolean) {
        val pm = ctx.packageManager
        val p = ctx.getSharedPreferences("fortress", Context.MODE_PRIVATE)
        val oldJson = p.getString("perms", null)
        val old = mutableMapOf<String, Set<String>>()
        if (oldJson != null) {
            val o = JSONObject(oldJson)
            o.keys().forEach { k ->
                val a = o.getJSONArray(k)
                old[k] = (0 until a.length()).map { a.getString(it) }.toSet()
            }
        }

        val now = JSONObject()
        @Suppress("DEPRECATION")
        val pkgs = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        for (pi in pkgs) {
            val pkg = pi.packageName
            val ai = pi.applicationInfo ?: continue
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 || Trust.isTrusted(pkg)) continue

            val g = Trust.granted(pi).filter { it in watched }.toSet()
            now.put(pkg, JSONArray(g.toList()))
            if (oldJson == null || !notify) continue

            val added = g - (old[pkg] ?: emptySet())
            if (added.isNotEmpty()) {
                Fortress.alert(
                    ctx,
                    "👮 '${Trust.label(pm, pkg)}' ko abhi ${added.joinToString(", ")} ki permission mili",
                    "Kya aapne khud di? Agar kisi ke kehne pe ya galti se di hai, to ChatCall Guard kholo → " +
                        "🏰 Suraksha Kila → 'Meri jaankari kaun le raha hai' mein us app pe 'Band karo' dabaao."
                )
            }
        }
        p.edit().putString("perms", now.toString()).apply()
    }
}

/** Kila: background mein chowkidaari (notification listener service ke saath chalta hai) */
object Fortress {
    private const val INTERVAL = 3 * 60 * 1000L     // har 3 minute
    private val handler = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private val camInUse = mutableSetOf<String>()
    private val lastAlarm = mutableMapOf<String, Long>()

    private val tick = object : Runnable {
        override fun run() {
            val c = app ?: return
            Thread { runCatching { check(c, true) } }.start()
            handler.postDelayed(this, INTERVAL)
        }
    }

    /** Nayi app install hote hi jaanch */
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            val pkg = i.data?.schemeSpecificPart ?: return
            val ctx = c.applicationContext
            val pm = ctx.packageManager
            if (Trust.isTrusted(pkg) || Trust.isSystem(pm, pkg)) return
            if (Trust.isSideloaded(pm, pkg)) {
                alert(
                    ctx, "📦 Nayi app install hui: ${Trust.label(pm, pkg)}",
                    "Ye Play Store se nahi aayi. Agar kisi ne call/WhatsApp pe bol kar install karwayi hai " +
                        "(KYC, bank update, bijli bill, AnyDesk...), to turant uninstall karo — ye hacker app ho sakti hai."
                )
            }
            handler.postDelayed({ Thread { runCatching { check(ctx, true) } }.start() }, 5000)
        }
    }

    /** Screen band ho aur camera chalu ho jaaye */
    private val camCb = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) {
            camInUse.add(cameraId)
            handler.postDelayed({
                val c = app
                if (c != null && cameraId in camInUse && screenOff(c)) alarm(c, "CAMERA")
            }, 2500)
        }

        override fun onCameraAvailable(cameraId: String) {
            camInUse.remove(cameraId)
        }
    }

    /** Screen band ho aur mic chalu ho jaaye (call ke dauraan nahi) */
    private val micCb = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>?) {
            if (configs.isNullOrEmpty()) return
            handler.postDelayed({
                val c = app ?: return@postDelayed
                val am = c.getSystemService(AudioManager::class.java)
                if (am.activeRecordingConfigurations.isNotEmpty() && screenOff(c) && !inCall(am)) alarm(c, "MIC")
            }, 2500)
        }
    }

    fun start(ctx: Context) {
        if (app != null) return
        val c = ctx.applicationContext
        app = c
        runCatching { c.getSystemService(CameraManager::class.java).registerAvailabilityCallback(camCb, handler) }
        runCatching { c.getSystemService(AudioManager::class.java).registerAudioRecordingCallback(micCb, handler) }
        runCatching {
            val f = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
            ContextCompat.registerReceiver(c, pkgReceiver, f, ContextCompat.RECEIVER_EXPORTED)
        }
        handler.post(tick)
    }

    fun stop() {
        val c = app ?: return
        app = null
        handler.removeCallbacksAndMessages(null)
        runCatching { c.getSystemService(CameraManager::class.java).unregisterAvailabilityCallback(camCb) }
        runCatching { c.getSystemService(AudioManager::class.java).unregisterAudioRecordingCallback(micCb) }
        runCatching { c.unregisterReceiver(pkgReceiver) }
    }

    /** Permission watchdog + naye hack-nishaan ki jaanch */
    @Synchronized
    fun check(ctx: Context, notify: Boolean) {
        Watchdog.check(ctx, notify)
        val p = ctx.getSharedPreferences("fortress", Context.MODE_PRIVATE)
        val seen = p.getStringSet("seenIssues", null)
        val issues = HackScanner.scan(ctx).filter { it.level >= 2 }
        if (seen != null && notify) {
            issues.filter { it.key !in seen }.forEach { alert(ctx, "🚨 " + it.title, it.detail) }
        }
        p.edit().putStringSet("seenIssues", issues.map { it.key }.toSet()).apply()
    }

    private fun screenOff(c: Context) = !c.getSystemService(PowerManager::class.java).isInteractive

    private fun inCall(am: AudioManager) =
        am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION

    private fun alarm(c: Context, what: String) {
        val now = System.currentTimeMillis()
        if (now - (lastAlarm[what] ?: 0L) < 60_000) return
        lastAlarm[what] = now
        alert(
            c, "🚨 Screen band hai, lekin $what chalu hua!",
            "Kisi app ne aapki jaankari ke bina $what chalu kiya. Agar aap khud kuch record nahi kar rahe the, " +
                "to ChatCall Guard kholo aur dekho kis app ke paas $what ki permission hai. " +
                "Sabse surakshit: Quick Settings mein Camera/Mic access OFF rakho."
        )
    }

    @Synchronized
    fun alert(c: Context, title: String, text: String) {
        Notifier.show(c, title, text)
        val p = c.getSharedPreferences("fortress", Context.MODE_PRIVATE)
        val arr = JSONArray(p.getString("log", "[]"))
        val out = JSONArray().put(JSONObject().put("t", System.currentTimeMillis()).put("title", title))
        for (i in 0 until minOf(arr.length(), 99)) out.put(arr.get(i))
        p.edit().putString("log", out.toString()).apply()
    }

    fun log(c: Context): List<String> {
        val arr = JSONArray(c.getSharedPreferences("fortress", Context.MODE_PRIVATE).getString("log", "[]"))
        val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            "${fmt.format(Date(o.getLong("t")))}  •  ${o.getString("title")}"
        }
    }
}
