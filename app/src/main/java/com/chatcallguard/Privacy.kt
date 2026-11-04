package com.chatcallguard

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager

/**
 * Company check:
 * Agar message kisi company/bank ke naam se aaya hai, lekin us company ka app
 * aapke phone mein hai hi nahi, to aapka us company se koi loan/account ho hi nahi sakta.
 */
object Brands {
    private val brands = mapOf(
        "sbi|state bank|yono" to listOf("sbi", "yono"),
        "hdfc" to listOf("hdfc"),
        "icici|imobile" to listOf("icici"),
        "axis bank" to listOf("axis"),
        "kotak" to listOf("kotak"),
        "pnb|punjab national" to listOf("pnb"),
        "bank of baroda|bob world" to listOf("baroda", "bob"),
        "paytm" to listOf("paytm"),
        "phonepe" to listOf("phonepe"),
        "google pay|gpay" to listOf("google pay", "nbu.paisa"),
        "bajaj finserv|bajaj finance" to listOf("bajaj"),
        "kreditbee" to listOf("kreditbee"),
        "moneyview|money view" to listOf("moneyview", "money view"),
        "navi loan|navi app" to listOf("navi"),
        "slice" to listOf("slice"),
        "cred" to listOf("cred"),
        "amazon" to listOf("amazon"),
        "flipkart" to listOf("flipkart"),
        "airtel" to listOf("airtel"),
        "jio" to listOf("jio")
    ).map { (k, v) -> Regex("\\b(?:$k)\\b", RegexOption.IGNORE_CASE) to v }

    fun mentionedButNotInstalled(ctx: Context, text: String): String? {
        val hits = brands.mapNotNull { (r, keys) -> r.find(text)?.value?.let { it to keys } }
        if (hits.isEmpty()) return null
        val installed = installedApps(ctx).map { (it.label + " " + it.pkg).lowercase() }
        return hits.firstOrNull { (_, keys) -> installed.none { app -> keys.any { app.contains(it) } } }?.first
    }

    data class App(val pkg: String, val label: String)

    fun installedApps(ctx: Context): List<App> {
        val pm = ctx.packageManager
        return pm.getInstalledApplications(0).map {
            App(it.packageName, pm.getApplicationLabel(it).toString())
        }
    }
}

/**
 * Kaun-kaun si apps aapki private jaankari (contacts, SMS, photos, location...) le sakti hain.
 */
object PrivacyScanner {

    val SENSITIVE = linkedMapOf(
        Manifest.permission.READ_CONTACTS to "Contacts",
        Manifest.permission.READ_SMS to "SMS",
        Manifest.permission.READ_CALL_LOG to "Call log",
        Manifest.permission.ACCESS_FINE_LOCATION to "Location",
        Manifest.permission.CAMERA to "Camera",
        Manifest.permission.RECORD_AUDIO to "Mic",
        Manifest.permission.READ_MEDIA_IMAGES to "Photos",
        Manifest.permission.READ_EXTERNAL_STORAGE to "Files/Photos"
    )

    private val remoteControl = listOf(
        "anydesk", "teamviewer", "rustdesk", "airdroid", "quicksupport", "screenshare", "remote"
    )

    private val loanWords = Regex("loan|cash|credit|rupee|paisa|money|kredit|lend|udhaar|emi", RegexOption.IGNORE_CASE)

    private val trusted = listOf(
        "com.whatsapp", "org.telegram", "com.google.", "com.android.", "com.samsung.",
        "com.miui.", "com.oneplus.", "com.coloros.", "com.vivo.", "com.oppo.", "com.realme.",
        "com.truecaller", "com.chatcallguard"
    )

    data class Finding(
        val pkg: String,
        val label: String,
        val perms: List<String>,
        val risk: Int,
        val why: String
    )

    fun scan(ctx: Context): List<Finding> {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val pkgs: List<PackageInfo> = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

        return pkgs.mapNotNull { p ->
            val ai = p.applicationInfo ?: return@mapNotNull null
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) return@mapNotNull null
            if (p.packageName == ctx.packageName) return@mapNotNull null

            val granted = p.requestedPermissions.orEmpty().filterIndexed { i, perm ->
                perm in SENSITIVE &&
                    (p.requestedPermissionsFlags?.getOrNull(i) ?: 0) and
                    PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
            }.map { SENSITIVE.getValue(it) }.distinct()

            val label = pm.getApplicationLabel(ai).toString()
            val name = (label + " " + p.packageName).lowercase()
            val isRemote = remoteControl.any { name.contains(it) }
            val isLoan = loanWords.containsMatchIn(name)
            val isTrusted = trusted.any { p.packageName.startsWith(it) }

            val (risk, why) = when {
                isRemote -> 2 to "Screen-share/remote app — fraudster isse aapka phone chala sakte hain"
                isLoan && ("Contacts" in granted || "SMS" in granted || "Photos" in granted) ->
                    2 to "Loan app ke paas contacts/SMS/photos — ise turant band karo ya uninstall karo"
                !isTrusted && ("Contacts" in granted || "SMS" in granted || "Call log" in granted) ->
                    1 to "Aapke contacts/SMS padh sakti hai — zaroorat na ho to band karo"
                granted.isEmpty() -> return@mapNotNull null
                else -> 0 to ""
            }
            Finding(p.packageName, label, granted, risk, why)
        }.sortedWith(compareByDescending<Finding> { it.risk }.thenBy { it.label.lowercase() })
    }
}
