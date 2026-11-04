package com.chatcallguard

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as SysSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chatcallguard.ui.GoogleStepItem
import com.chatcallguard.ui.GuardActions
import com.chatcallguard.ui.GuardApp
import com.chatcallguard.ui.GuardRule
import com.chatcallguard.ui.GuardState
import com.chatcallguard.ui.GuardTheme
import com.chatcallguard.ui.setupItem

/** App ki main screen (naya design: ui/ folder mein) */
class MainActivity : ComponentActivity(), GuardActions {

    private val settings by lazy { Settings(this) }
    private var state by mutableStateOf(GuardState(loading = true))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Notifier.init(this)
        setContent {
            GuardTheme { GuardApp(state, this@MainActivity) }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** Saari jaanch background mein, taaki screen atke nahi */
    private fun refresh() {
        Thread {
            val fresh = runCatching { load() }.getOrNull()
            runOnUiThread { state = fresh ?: state.copy(loading = false) }
        }.start()
    }

    private fun load(): GuardState {
        // Tablet mein SIM nahi hota, isliye SIM wale features sirf phone pe
        val hasSim = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val roles = getSystemService(RoleManager::class.java)
        val listener = listenerOn()
        val setup = buildList {
            add(setupItem("listener", listener))
            add(setupItem("contacts", has(Manifest.permission.READ_CONTACTS)))
            if (Build.VERSION.SDK_INT >= 33) add(setupItem("notify", has(Manifest.permission.POST_NOTIFICATIONS)))
            if (hasSim && roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                add(setupItem("sim", roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)))
            }
        }
        return GuardState(
            setup = setup,
            issues = (HackScanner.scan(this) + GoogleSafety.deviceIssues(this)).sortedByDescending { it.level },
            google = GoogleSafety.steps.map { GoogleStepItem(it, GoogleSafety.isDone(this, it.id)) },
            apps = PrivacyScanner.scan(this),
            blockForeign = settings.blockForeignUnknown,
            blockAll = settings.blockAllUnknown,
            hideForeign = settings.hideForeignMessages,
            scamAlerts = settings.scamMessageAlerts,
            homeCode = settings.homeCode,
            blocklist = settings.blocklist().sorted(),
            log = EventLog.entries(this),
            alarms = Fortress.alarms(this),
            hasSim = hasSim,
            listenerOn = listener,
            loading = false
        )
    }

    // ---------------- Screen ke button ----------------

    override fun fixSetup(id: String) {
        when (id) {
            "listener" -> open(Intent(SysSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            "contacts" -> askPermission(Manifest.permission.READ_CONTACTS, 1)
            "notify" -> if (Build.VERSION.SDK_INT >= 33) askPermission(Manifest.permission.POST_NOTIFICATIONS, 2)
            "sim" -> {
                val roles = getSystemService(RoleManager::class.java)
                @Suppress("DEPRECATION")
                startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 3)
            }
        }
    }

    override fun fixIssue(issue: Issue) = open(
        if (issue.fix == SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS && issue.pkg != null)
            Intent(issue.fix, Uri.parse("package:${issue.pkg}"))
        else Intent(issue.fix)
    )

    override fun openStep(target: String) {
        when {
            target.startsWith("http") -> openUrl(target)
            target.startsWith("pkg:") -> {
                val launch = packageManager.getLaunchIntentForPackage(target.removePrefix("pkg:"))
                if (launch != null) open(launch) else toast("Ye app phone mein nahi hai")
            }
            else -> open(Intent(target))
        }
    }

    override fun toggleStep(id: String) {
        GoogleSafety.toggle(this, id)
        state = state.copy(google = state.google.map { if (it.step.id == id) it.copy(done = !it.done) else it })
    }

    override fun openApp(pkg: String) =
        open(Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))

    override fun openPrivacySettings() = open(Intent(SysSettings.ACTION_PRIVACY_SETTINGS))

    override fun dial(code: String) = open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code))))

    override fun openUrl(url: String) = open(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    override fun setRule(rule: GuardRule, on: Boolean) {
        state = when (rule) {
            GuardRule.BLOCK_FOREIGN -> {
                settings.blockForeignUnknown = on
                state.copy(blockForeign = on)
            }
            GuardRule.BLOCK_ALL -> {
                settings.blockAllUnknown = on
                state.copy(blockAll = on)
            }
            GuardRule.HIDE_FOREIGN -> {
                settings.hideForeignMessages = on
                state.copy(hideForeign = on)
            }
            GuardRule.SCAM_ALERTS -> {
                settings.scamMessageAlerts = on
                state.copy(scamAlerts = on)
            }
        }
    }

    override fun saveHomeCode(code: String) {
        settings.homeCode = code
        state = state.copy(homeCode = settings.homeCode)
        toast("✅ Desh code save ho gaya: ${settings.homeCode}")
    }

    override fun addBlock(entry: String) {
        if (entry.isBlank()) return
        settings.addToBlocklist(entry)
        state = state.copy(blocklist = settings.blocklist().sorted())
        toast("Block kar diya")
    }

    override fun removeBlock(entry: String) {
        settings.removeFromBlocklist(entry)
        state = state.copy(blocklist = settings.blocklist().sorted())
    }

    override fun clearLog() {
        EventLog.clear(this)
        state = state.copy(log = emptyList())
    }

    // ---------------- chhote helpers ----------------

    /** Permission do baar mana ho chuki ho to Android dialog nahi dikhata, tab app settings kholo */
    private fun askPermission(perm: String, code: Int) {
        val p = getSharedPreferences("ui", MODE_PRIVATE)
        if (p.getBoolean("asked_$perm", false) && !shouldShowRequestPermissionRationale(perm)) {
            openApp(packageName)
            toast("Permissions mein jaakar Allow karo")
        } else {
            p.edit().putBoolean("asked_$perm", true).apply()
            requestPermissions(arrayOf(perm), code)
        }
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            runCatching { startActivity(Intent(SysSettings.ACTION_SETTINGS)) }
        }
    }

    private fun listenerOn(): Boolean {
        val enabled = SysSettings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return enabled.contains(ComponentName(this, ChatCallListener::class.java).flattenToString())
    }

    private fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
