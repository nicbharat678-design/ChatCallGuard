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

        // Tablet mein SIM nahi hota, isliye SIM wale features sirf phone pe
        val hasSim = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val roles = getSystemService(RoleManager::class.java)
        if (hasSim && roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            statusRow("SIM call spam filter", roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING), "Enable") {
                @Suppress("DEPRECATION")
                startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 3)
            }
        }

        heading("🏰 Suraksha Kila")
        if (!notificationAccessOn()) small("⚠️ Kila ke alarm tabhi chalenge jab upar 'Notification access' ON ho.")
        val issues = (HackScanner.scan(this) + GoogleSafety.deviceIssues(this)).sortedByDescending { it.level }
        when {
            issues.isEmpty() -> small("🟢 Koi hack ka nishaan nahi mila.")
            issues.any { it.level >= 2 } ->
                small("🔴 ${issues.count { it.level >= 2 }} khatre ke nishaan mile! Turant 'Theek karo' dabaao.")
            else -> small("🟠 Kuch cheezon pe dhyan do:")
        }
        issues.forEach { i ->
            val icon = if (i.level >= 2) "🔴" else "🟠"
            root.addView(row(label("$icon ${i.title}\n${i.detail}").apply { setPadding(0, dp(6), 0, dp(6)) },
                Button(this).apply { text = "Theek karo"; setOnClickListener { openFix(i) } }))
        }

        small(
            "\n📷🎤 Camera/Mic ka MASTER SWITCH (Android 12+):\n" +
                "Upar se notification panel neeche kheencho → 'Camera access' aur 'Microphone access' " +
                "tiles OFF karo. OFF hone pe KOI bhi app — permission ho tab bhi — camera/mic nahi chala sakti. " +
                "Zaroorat ho tab ON karo. (Tile na dikhe to panel mein ✏️ edit dabake jodo.)"
        )
        button("Privacy settings kholo") { open(Intent(SysSettings.ACTION_PRIVACY_SETTINGS)) }

        if (hasSim) {
            small("\n📞 Call forwarding: fraudster aapke OTP wale calls apne number pe forward kar lete hain.")
            button("Forwarding check karo (*#21#)") { dial("*#21#") }
            button("Saari forwarding band karo (##002#)") { dial("##002#") }
        }

        small(
            "\n💬 WhatsApp hack check: WhatsApp → ⋮ → Linked devices. Jo computer/phone aapka nahi hai, " +
                "use 'Log out' karo. Aur Settings → Account → Two-step verification ON karo."
        )

        val alerts = Fortress.log(this)
        if (alerts.isNotEmpty()) {
            small("\n🚨 Kila ke pichhle alarm:")
            alerts.take(20).forEach { small(it) }
        }

        heading("🔐 Gmail aur Google Photos Kila")
        val done = GoogleSafety.score(this)
        val total = GoogleSafety.steps.size
        small(
            (if (done == total) "🟢" else if (done >= total / 2) "🟠" else "🔴") +
                " Suraksha score: $done / $total\n" +
                "Har step ke saamne 'Kholo' dabao, Google ka asli page khulega. Kaam ho jaaye to " +
                "us step pe tap karke ✅ laga do. (Ye app aapka password kabhi nahi maangti.)"
        )
        GoogleSafety.steps.forEach { st ->
            val ok = GoogleSafety.isDone(this, st.id)
            val item = label((if (ok) "✅ " else "⬜ ") + st.title + "\n" + st.why).apply {
                setPadding(0, dp(6), 0, dp(6))
                setOnClickListener { GoogleSafety.toggle(this@MainActivity, st.id); render() }
            }
            root.addView(row(item, Button(this).apply { text = "Kholo"; setOnClickListener { openStep(st.open) } }))
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

        // Desh code ab har akshar pe nahi, sirf "Save" dabane pe aur sirf sahi code hi save hota hai
        val code = EditText(this).apply {
            setText(settings.homeCode)
            hint = "+91"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        root.addView(row(label("Mera desh code\n(sirf code, phone number nahi)"), code))
        small("Abhi chal raha code: ${settings.homeCode}  (India ke liye +91)")
        button("Desh code save karo") { saveHomeCode(code) }

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
        if (hasSim) button("1930 pe call karo") { dial("1930") }
        button("cybercrime.gov.in kholo") { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://cybercrime.gov.in"))) }

        small(
            "\nPrivacy: Ye app aapke chats, calls ya data ko kahin nahi bhejti. " +
                "Sab kuch sirf aapke phone mein rehta hai. Internet permission bhi nahi li gayi."
        )
    }

    private fun saveHomeCode(field: EditText) {
        val raw = field.text.toString().trim()
        val typed = Phone.cleanCode(raw)
        // "+91..." ya "0091..." se shuru poora number ho to usmein se code nikaal sakte hain
        val fromNumber = if (raw.startsWith("+") || raw.startsWith("00")) Phone.countryCodeOf(raw) else null

        fun save(cc: String) {
            settings.homeCode = cc
            toast("✅ Desh code save ho gaya: $cc")
            render()
        }

        when {
            Phone.isCountryCode(typed) -> save(typed)
            fromNumber != null -> confirm(
                "Ye poora phone number lag raha hai. Yahan sirf desh code chahiye.\n\n'$fromNumber' save karun?"
            ) { save(fromNumber) }
            else -> field.error = "Ye sahi desh code nahi hai. Sirf code likho, jaise India ke liye +91."
        }
    }

    // ---------- chhote helpers ----------

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(SysSettings.ACTION_SETTINGS))
        }
    }

    private fun openFix(i: Issue) = open(
        if (i.fix == SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS && i.pkg != null)
            Intent(i.fix, Uri.parse("package:${i.pkg}"))
        else Intent(i.fix)
    )

    private fun openStep(target: String) {
        when {
            target.startsWith("http") -> open(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
            target.startsWith("pkg:") -> {
                val launch = packageManager.getLaunchIntentForPackage(target.removePrefix("pkg:"))
                if (launch != null) open(launch) else toast("Ye app phone mein nahi hai")
            }
            else -> open(Intent(target))
        }
    }

    private fun dial(code: String) = open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code))))

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
