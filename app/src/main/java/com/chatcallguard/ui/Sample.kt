package com.chatcallguard.ui

import com.chatcallguard.GoogleSafety
import com.chatcallguard.Issue
import com.chatcallguard.PrivacyScanner

/** Design dekhne ke liye nakli data (screenshot aur preview mein kaam aata hai) */
object Sample {
    private const val NOW = 1_793_800_000_000L
    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN

    private val apps = listOf(
        PrivacyScanner.Finding(
            "com.quickloan.cash", "Quick Loan Cash", listOf("Contacts", "SMS", "Photos"), 2,
            "Loan app ke paas contacts/SMS/photos — ise turant band karo ya uninstall karo"
        ),
        PrivacyScanner.Finding(
            "com.photoeditor.pro", "Photo Editor Pro", listOf("Contacts", "Camera"), 1,
            "Aapke contacts/SMS padh sakti hai — zaroorat na ho to band karo"
        ),
        PrivacyScanner.Finding("com.instagram.android", "Instagram", listOf("Camera", "Mic", "Photos"), 0, ""),
        PrivacyScanner.Finding("in.swiggy.android", "Swiggy", listOf("Location"), 0, ""),
        PrivacyScanner.Finding("com.spotify.music", "Spotify", listOf("Mic"), 0, "")
    )

    val state = GuardState(
        setup = listOf(
            setupItem("listener", true),
            setupItem("contacts", true),
            setupItem("notify", true),
            setupItem("sim", false)
        ),
        issues = listOf(
            Issue(
                2, "🕵️ 'Quick Loan Cash' aapki screen padh aur chala sakti hai",
                "Iska Accessibility ON hai. Hacker apps isi se OTP, PIN aur password churaati hain. " +
                    "Agar aapne khud jaan-boojh kar ON nahi kiya, to turant OFF karo aur app uninstall karo.",
                "com.quickloan.cash"
            ),
            Issue(
                1, "🔌 USB debugging ON hai",
                "Isse computer se phone mein ghusa ja sakta hai. Developer ho to theek hai, warna OFF karo."
            )
        ),
        google = GoogleSafety.steps.mapIndexed { i, s -> GoogleStepItem(s, i < 4) },
        apps = apps,
        homeCode = "+91",
        blocklist = listOf("+923001234567", "loan recovery dept"),
        log = listOf(
            LogItem(NOW - 8 * MIN, "WhatsApp", "+92 300 1234567", "BLOCKED", "Unknown videsh number (high-risk code)"),
            LogItem(
                NOW - 45 * MIN, "WhatsApp", "+84 912 345 678", "MESSAGE HIDDEN",
                "Unknown videsh number (high-risk code) | Fake trading group scam"
            ),
            LogItem(
                NOW - 3 * HOUR, "Telegram", "Loan Recovery Dept", "SCAM MESSAGE",
                "Family/contacts ko message karne ki dhamki — loan app blackmail"
            ),
            LogItem(NOW - 26 * HOUR, "SIM call", "+1 202 555 0147", "BLOCKED", "Unknown videsh number"),
            LogItem(NOW - 50 * HOUR, "WhatsApp", "+91 98765 01234", "WARNING", "Unknown caller — savdhan rahein")
        ),
        alarms = listOf(
            AlarmItem(NOW - 2 * HOUR, "📦 Nayi app install hui: Quick Loan Cash"),
            AlarmItem(NOW - 30 * HOUR, "👮 'Photo Editor Pro' ko abhi Contacts ki permission mili")
        ),
        hasSim = true,
        listenerOn = true
    )

    val safe = state.copy(
        setup = state.setup.map { it.copy(done = true) },
        issues = emptyList(),
        google = GoogleSafety.steps.mapIndexed { i, s -> GoogleStepItem(s, i < 9) },
        apps = apps.filter { it.risk == 0 }
    )
}
