package com.chatcallguard

import android.Manifest
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as SysSettings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged

class MainActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private val settings by lazy { Settings(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.init(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        root.removeAllViews()

        heading("🛡️ Setup")
        statusRow(
            "WhatsApp/Telegram call guard",
            notificationAccessOn(),
            "Notification access do"
        ) { startActivity(Intent(SysSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

        statusRow("Contacts (apne logon ko block na kare)", has(Manifest.permission.READ_CONTACTS), "Allow") {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 1)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            statusRow("Alerts dikhane ki permission", has(Manifest.permission.POST_NOTIFICATIONS), "Allow") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            }
        }

        val roles = getSystemService(RoleManager::class.java)
        statusRow("SIM call spam filter", roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING), "Enable") {
            @Suppress("DEPRECATION")
            startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 3)
        }

        heading("⚙️ Rules")
        switch("Unknown VIDESH number ki call auto-decline", settings.blockForeignUnknown) {
            settings.blockForeignUnknown = it
        }
        switch("HAR unknown caller ki call auto-decline (strict)", settings.blockAllUnknown) {
            settings.blockAllUnknown = it
        }
        switch("Unknown VIDESH number ke messages chhupao", settings.hideForeignMessages) {
            settings.hideForeignMessages = it
        }
        switch("Scam message pe warning", settings.scamMessageAlerts) { settings.scamMessageAlerts = it }

        val code = EditText(this).apply {
            setText(settings.homeCode)
            hint = "Apna country code, jaise +91"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        root.addView(row(label("Mera desh code"), code))
        code.doAfterTextChanged {
            val v = it.toString().trim()
            if (Regex("^\\+\\d{1,4}$").matches(v)) settings.homeCode = v
        }

        heading("🔒 Meri jaankari kaun le raha hai")
        small(
            "Fake loan apps aapke contacts aur photos churaa kar family ko message bhejti hain. " +
                "Neeche wali apps aapki private jaankari padh sakti hain. 'Band karo' dabaao, " +
                "phir Permissions mein jaakar Contacts/SMS/Photos ko 'Don't allow' karo, " +
                "ya app uninstall kar do."
        )
        val findings = PrivacyScanner.scan(this)
        if (findings.isEmpty()) small("✅ Koi anjaan app aapki private jaankari nahi le rahi.")
        findings.forEach { f ->
            val icon = when (f.risk) { 2 -> "🔴"; 1 -> "🟠"; else -> "🟢" }
            val info = "$icon ${f.label}\n${f.perms.joinToString(", ")}" +
                if (f.why.isNotEmpty()) "\n${f.why}" else ""
            root.addView(row(label(info).apply { setPadding(0, dp(6), 0, dp(6)) }, Button(this).apply {
                text = if (f.risk > 0) "Band karo" else "Dekho"
                setOnClickListener {
                    startActivity(
                        Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${f.pkg}"))
                    )
                }
            }))
        }
        small(
            "WhatsApp mein bhi: Settings → Privacy → Calls → 'Silence unknown callers' ON karo, " +
                "aur Profile photo / About / Groups ko 'My contacts' pe rakho."
        )

        heading("🚫 Number report / block karo")
        val input = EditText(this).apply {
            hint = "+92 300 1234567  ya Telegram naam"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        root.addView(input)
        button("Block list mein daalo") {
            val v = input.text.toString()
            if (v.isNotBlank()) {
                settings.addToBlocklist(v)
                toast("Block kar diya")
                render()
            }
        }
        val list = settings.blocklist().sorted()
        if (list.isEmpty()) small("Abhi koi number block nahi hai.")
        list.forEach { entry ->
            small("• $entry   (hatane ke liye tap karo)").setOnClickListener {
                confirm("$entry ko block list se hatayein?") {
                    settings.removeFromBlocklist(entry); render()
                }
            }
        }

        heading("📋 Kya-kya roka gaya")
        val log = EventLog.lines(this)
        if (log.isEmpty()) small("Abhi tak kuch nahi. Jab koi call/message roka jaayega, yahan dikhega.")
        log.take(100).forEach { small(it).setPadding(0, dp(6), 0, dp(6)) }
        if (log.isNotEmpty()) button("Log saaf karo") { EventLog.clear(this); render() }

        heading("🆘 Fraud ho gaya to")
        small(
            "1. Turant 1930 pe call karein (National Cyber Crime Helpline).\n" +
                "2. cybercrime.gov.in pe complaint likhein.\n" +
                "3. Bank ko call karke account/UPI freeze karwaayein.\n" +
                "4. Fraud number ko 'Chakshu' (sancharsaathi.gov.in) pe report karein."
        )
        button("1930 pe call karo") { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))) }
        button("cybercrime.gov.in kholo") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cybercrime.gov.in")))
        }

        small(
            "\nPrivacy: Ye app aapke chats, calls ya data ko kahin nahi bhejti. " +
                "Sab kuch sirf aapke phone mein rehta hai. Internet permission bhi nahi li gayi."
        )
    }

    // ---------- chhote helpers ----------

    private fun notificationAccessOn(): Boolean {
        val enabled = SysSettings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return enabled.contains(ComponentName(this, ChatCallListener::class.java).flattenToString())
    }

    private fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun heading(t: String) = root.addView(TextView(this).apply {
        text = t; textSize = 20f; setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(8))
    })

    private fun label(t: String) = TextView(this).apply { text = t; textSize = 15f }

    private fun small(t: String): TextView = TextView(this).apply { text = t; textSize = 14f }.also { root.addView(it) }

    private fun button(t: String, onClick: () -> Unit) =
        root.addView(Button(this).apply { text = t; setOnClickListener { onClick() } })

    private fun row(left: View, right: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(right)
    }

    private fun statusRow(title: String, ok: Boolean, actionText: String, onFix: () -> Unit) {
        val left = label((if (ok) "✅ " else "❌ ") + title)
        if (ok) root.addView(left)
        else root.addView(row(left, Button(this).apply { text = actionText; setOnClickListener { onFix() } }))
    }

    @Suppress("UseSwitchCompatOrMaterialCode")
    private fun switch(t: String, value: Boolean, onChange: (Boolean) -> Unit) =
        root.addView(Switch(this).apply {
            text = t; isChecked = value; textSize = 15f
            setPadding(0, dp(6), 0, dp(6))
            setOnCheckedChangeListener { _, c -> onChange(c) }
        })

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun confirm(msg: String, yes: () -> Unit) =
        AlertDialog.Builder(this).setMessage(msg)
            .setPositiveButton("Haan") { _, _ -> yes() }
            .setNegativeButton("Nahi", null).show()
}
