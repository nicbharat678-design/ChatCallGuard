package com.chatcallguard.ui

import com.chatcallguard.GoogleSafety
import com.chatcallguard.Issue
import com.chatcallguard.PrivacyScanner
import kotlin.math.roundToInt

/** Setup ki ek line (permission / access) */
data class SetupItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val done: Boolean,
    val button: String
)

/** Setup lines ke text ek hi jagah */
fun setupItem(id: String, done: Boolean): SetupItem = when (id) {
    "listener" -> SetupItem(
        id, "WhatsApp/Telegram guard",
        "Calls aur messages pe nazar ke liye Notification access chahiye", done, "ON karo"
    )
    "contacts" -> SetupItem(id, "Contacts", "Taaki apne logon ki call kabhi block na ho", done, "Allow")
    "notify" -> SetupItem(id, "Alerts", "Khatra milte hi turant notification aaye", done, "Allow")
    else -> SetupItem(id, "SIM call spam filter", "Normal SIM calls bhi jaanchi jaayein", done, "Enable")
}

/** "Kya-kya roka gaya" ki ek entry */
data class LogItem(val time: Long, val source: String, val who: String, val action: String, val reason: String)

/** Kila ka ek alarm */
data class AlarmItem(val time: Long, val title: String)

/** Gmail/Photos checklist ka ek kadam */
data class GoogleStepItem(val step: GoogleSafety.Step, val done: Boolean)

enum class Level { SAFE, WARN, DANGER }

enum class GuardRule { BLOCK_FOREIGN, BLOCK_ALL, HIDE_FOREIGN, SCAM_ALERTS }

/** Screen pe dikhne wali saari jaankari */
data class GuardState(
    val setup: List<SetupItem> = emptyList(),
    val issues: List<Issue> = emptyList(),
    val google: List<GoogleStepItem> = emptyList(),
    val apps: List<PrivacyScanner.Finding> = emptyList(),
    val blockForeign: Boolean = true,
    val blockAll: Boolean = false,
    val hideForeign: Boolean = true,
    val scamAlerts: Boolean = true,
    val homeCode: String = "+91",
    val blocklist: List<String> = emptyList(),
    val log: List<LogItem> = emptyList(),
    val alarms: List<AlarmItem> = emptyList(),
    val hasSim: Boolean = true,
    val listenerOn: Boolean = false,
    val loading: Boolean = false
) {
    val dangerCount: Int get() = issues.count { it.level >= 2 }
    val warnCount: Int get() = issues.count { it.level == 1 }
    val setupLeft: Int get() = setup.count { !it.done }
    val googleDone: Int get() = google.count { it.done }

    val blockedCalls: Int get() = log.count { it.action == "BLOCKED" }
    val hiddenMessages: Int get() = log.count { it.action == "MESSAGE HIDDEN" }
    val warnings: Int get() = log.count { it.action == "WARNING" || it.action == "SCAM MESSAGE" || it.action == "BLOCK FAILED" }

    val level: Level
        get() = when {
            !listenerOn || dangerCount > 0 -> Level.DANGER
            setupLeft > 0 || warnCount > 0 -> Level.WARN
            else -> Level.SAFE
        }

    /** 0-100: setup 60, hack scan 25, Google kadam 15 */
    val score: Int
        get() {
            val setupPart = if (setup.isEmpty()) 60.0 else 60.0 * setup.count { it.done } / setup.size
            val scanPart = (25 - dangerCount * 10 - warnCount * 3).coerceIn(0, 25)
            val googlePart = if (google.isEmpty()) 0.0 else 15.0 * googleDone / google.size
            val total = (setupPart + scanPart + googlePart).roundToInt().coerceIn(0, 100)
            return if (listenerOn) total else minOf(total, 40)
        }
}

/** Screen se aane wale kaam. Default kuch nahi karta (preview/screenshot ke liye). */
interface GuardActions {
    fun fixSetup(id: String) {}
    fun fixIssue(issue: Issue) {}
    fun openStep(target: String) {}
    fun toggleStep(id: String) {}
    fun openApp(pkg: String) {}
    fun openPrivacySettings() {}
    fun dial(code: String) {}
    fun openUrl(url: String) {}
    fun setRule(rule: GuardRule, on: Boolean) {}
    fun saveHomeCode(code: String) {}
    fun addBlock(entry: String) {}
    fun removeBlock(entry: String) {}
    fun clearLog() {}
}

object NoActions : GuardActions

/** "🔐 2-Step Verification ON karo" -> ("🔐", "2-Step Verification ON karo") */
fun splitEmoji(text: String): Pair<String?, String> {
    val t = text.trim()
    val i = t.indexOf(' ')
    if (i <= 0) return null to t
    val head = t.substring(0, i)
    return if (head.none { it.isLetterOrDigit() }) head to t.substring(i + 1).trim() else null to t
}
