package com.chatcallguard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chatcallguard.Issue
import com.chatcallguard.Phone
import com.chatcallguard.PrivacyScanner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ======================= App ka dhaancha: 5 tabs =======================

private data class TabDef(val label: String, val icon: ImageVector, val badge: (GuardState) -> Int)

private val tabs: List<TabDef> by lazy {
    listOf(
        TabDef("Home", Icons.Filled.Home) { it.setupLeft },
        TabDef("Kila", GuardIcons.Shield) { it.dangerCount },
        TabDef("Rules", GuardIcons.Tune) { 0 },
        TabDef("Log", GuardIcons.History) { 0 },
        TabDef("Madad", GuardIcons.Help) { 0 }
    )
}

@Composable
fun GuardApp(state: GuardState, actions: GuardActions, startTab: Int = 0) {
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                tabs.forEachIndexed { i, t ->
                    val count = t.badge(state)
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            BadgedBox(badge = { if (count > 0) Badge { Text("$count") } }) {
                                Icon(t.icon, contentDescription = t.label)
                            }
                        },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else when (tab) {
                0 -> HomeScreen(state, actions) { tab = it }
                1 -> KilaScreen(state, actions)
                2 -> RulesScreen(state, actions)
                3 -> LogScreen(state, actions)
                else -> MadadScreen(state, actions)
            }
        }
    }
}

// ======================= Home =======================

@Composable
fun HomeScreen(state: GuardState, actions: GuardActions, goTo: (Int) -> Unit) {
    ScreenList {
        item { HeroCard(state) { goTo(1) } }
        item {
            SectionTitle(if (state.setupLeft > 0) "Setup poora karo" else "Setup") {
                if (state.setupLeft > 0) StatusPill("${state.setupLeft} baaki", StatusColors.Warn)
                else StatusPill("Sab ON", StatusColors.Safe)
            }
        }
        item {
            GCard(padding = 12.dp) {
                state.setup.forEachIndexed { i, s ->
                    if (i > 0) RowDivider()
                    SetupRow(s) { actions.fixSetup(s.id) }
                }
            }
        }
        item { SectionTitle("Ab tak ki suraksha") }
        item { StatsRow(state) }
        item { SectionTitle("Turant jaanch") }
        item {
            GCard(padding = 12.dp) {
                if (state.hasSim) {
                    ActionRow(
                        Icons.Filled.Call, MaterialTheme.colorScheme.primary,
                        "Call forwarding check karo",
                        "Fraudster OTP wali calls apne number pe forward kar lete hain (*#21#)"
                    ) { actions.dial("*#21#") }
                    RowDivider()
                    ActionRow(
                        Icons.Filled.Close, StatusColors.Danger,
                        "Saari forwarding band karo", "##002# dial hoga"
                    ) { actions.dial("##002#") }
                    RowDivider()
                }
                ActionRow(
                    GuardIcons.VerifiedUser, StatusColors.Safe,
                    "WhatsApp hack check",
                    "⋮ → Linked devices: jo device aapka nahi, use Log out karo. Two-step verification bhi ON karo."
                ) { actions.openStep("pkg:com.whatsapp") }
            }
        }
    }
}

private fun heroText(s: GuardState): Pair<String, String> = when {
    !s.listenerOn -> "Guard band hai" to
        "Neeche 'WhatsApp/Telegram guard' ON karo, warna koi call ya message nahi roka jaayega."
    s.dangerCount > 0 -> (if (s.dangerCount == 1) "1 khatra mila!" else "${s.dangerCount} khatre mile!") to
        "Kila mein jaakar 'Theek karo' dabao."
    s.setupLeft > 0 -> "Thoda setup baaki" to
        "${s.setupLeft} cheez ON karni hai, phir suraksha poori hogi."
    s.warnCount > 0 -> "Lagbhag surakshit" to "${s.warnCount} cheezon pe dhyan do. Kila tab dekho."
    else -> "Aapka phone surakshit hai" to "Calls, messages aur apps pe nazar rakhi ja rahi hai."
}

@Composable
private fun HeroCard(state: GuardState, onOpenKila: () -> Unit) {
    val (title, sub) = heroText(state)
    val colors = when (state.level) {
        Level.SAFE -> listOf(Color(0xFF12B37E), Color(0xFF05704F))
        Level.WARN -> listOf(Color(0xFFF08C00), Color(0xFFB45309))
        Level.DANGER -> listOf(Color(0xFFEF4444), Color(0xFF9F1D1D))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(colors))
    ) {
        Icon(
            GuardIcons.Shield, contentDescription = null,
            tint = Color.White.copy(alpha = 0.08f),
            modifier = Modifier.size(200.dp).align(Alignment.CenterEnd).offset(x = 60.dp, y = 20.dp)
        )
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(GuardIcons.VerifiedUser, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("ChatCall Guard", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.92f))
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
                }
                Spacer(Modifier.width(16.dp))
                ScoreRing(state.score)
            }
            if (state.listenerOn && (state.dangerCount > 0 || state.warnCount > 0)) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onOpenKila,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = colors.last())
                ) { Text("Kila mein dekho") }
            }
        }
    }
}

@Composable
private fun ScoreRing(score: Int) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
        CircularProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.25f),
            strokeWidth = 8.dp,
            strokeCap = StrokeCap.Round
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score%", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("Suraksha", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

private fun setupIcon(id: String): ImageVector = when (id) {
    "listener" -> GuardIcons.Shield
    "contacts" -> Icons.Filled.Person
    "notify" -> Icons.Filled.Notifications
    else -> Icons.Filled.Phone
}

@Composable
private fun SetupRow(item: SetupItem, onFix: () -> Unit) {
    val color = if (item.done) StatusColors.Safe else StatusColors.Warn
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBubble(setupIcon(item.id), color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall)
            Text(
                item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        if (item.done) {
            StatusPill("ON", StatusColors.Safe)
        } else {
            Button(onClick = onFix, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                Text(item.button)
            }
        }
    }
}

@Composable
private fun StatsRow(state: GuardState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(state.blockedCalls, "Calls\nkaati gayi", GuardIcons.Block, StatusColors.Danger, Modifier.weight(1f))
        StatCard(state.hiddenMessages, "Messages\nchhupaye", Icons.Filled.Lock, StatusColors.Warn, Modifier.weight(1f))
        StatCard(state.warnings, "Warnings\ndi gayi", Icons.Filled.Warning, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(value: Int, label: String, icon: ImageVector, color: Color, modifier: Modifier) {
    GCard(modifier, padding = 14.dp) {
        IconBubble(icon, color, 34.dp)
        Spacer(Modifier.height(10.dp))
        Text("$value", style = MaterialTheme.typography.headlineSmall)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ======================= Kila =======================

@Composable
fun KilaScreen(state: GuardState, actions: GuardActions) {
    var showAllApps by rememberSaveable { mutableStateOf(false) }
    ScreenList {
        item { ScreenHeader("Suraksha Kila", "Hack ke nishaan, apps ki permissions aur Google account") }
        if (!state.listenerOn) {
            item {
                Banner(
                    StatusColors.Danger,
                    "Kila ke alarm tabhi chalenge jab Home par 'WhatsApp/Telegram guard' ON hoga."
                )
            }
        }
        item {
            SectionTitle("Hack scan") {
                when {
                    state.dangerCount > 0 -> StatusPill("${state.dangerCount} khatra", StatusColors.Danger)
                    state.warnCount > 0 -> StatusPill("${state.warnCount} dhyan do", StatusColors.Warn)
                    else -> StatusPill("Saaf", StatusColors.Safe)
                }
            }
        }
        if (state.issues.isEmpty()) {
            item {
                EmptyCard(
                    GuardIcons.VerifiedUser, StatusColors.Safe,
                    "Koi hack ka nishaan nahi mila",
                    "Background mein har 3 minute mein jaanch hoti rehti hai."
                )
            }
        } else {
            items(state.issues) { issue -> IssueCard(issue) { actions.fixIssue(issue) } }
        }
        item {
            TipCard(
                Icons.Filled.Lock, MaterialTheme.colorScheme.primary,
                "Camera aur Mic ka master switch",
                "Notification panel neeche kheencho → 'Camera access' aur 'Microphone access' OFF karo. " +
                    "OFF hone par koi bhi app camera ya mic nahi chala sakti, permission ho tab bhi. " +
                    "Zaroorat ho tab ON karo. (Tile na dikhe to panel mein ✏️ edit dabake jodo.)"
            ) {
                OutlinedButton(
                    onClick = { actions.openPrivacySettings() },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Privacy settings kholo") }
            }
        }
        item {
            val g = state.google
            val color = when {
                g.isNotEmpty() && state.googleDone == g.size -> StatusColors.Safe
                state.googleDone * 2 >= g.size -> StatusColors.Warn
                else -> StatusColors.Danger
            }
            SectionTitle("Gmail aur Google Photos") { StatusPill("${state.googleDone}/${g.size}", color) }
        }
        item { GoogleCard(state, actions) }
        item { SectionTitle("Meri jaankari kaun le raha hai") }
        item { AppsCard(state.apps, showAllApps, { showAllApps = !showAllApps }, actions) }
    }
}

@Composable
private fun IssueCard(issue: Issue, onFix: () -> Unit) {
    val (emoji, title) = splitEmoji(issue.title)
    val color = StatusColors.ofIssue(issue.level)
    GCard {
        Row(verticalAlignment = Alignment.Top) {
            EmojiBubble(emoji ?: "⚠️", color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                StatusPill(if (issue.level >= 2) "Khatra" else "Dhyan do", color)
                Spacer(Modifier.height(6.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    issue.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onFix,
            modifier = Modifier.align(Alignment.End),
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
        ) { Text("Theek karo") }
    }
}

@Composable
private fun GoogleCard(state: GuardState, actions: GuardActions) {
    val total = state.google.size.coerceAtLeast(1)
    val color = when {
        state.googleDone == state.google.size -> StatusColors.Safe
        state.googleDone * 2 >= state.google.size -> StatusColors.Warn
        else -> StatusColors.Danger
    }
    GCard(padding = 12.dp) {
        Column(Modifier.padding(4.dp)) {
            Text("${state.googleDone} / ${state.google.size} kadam poore", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { state.googleDone / total.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "'Kholo' dabao, Google ka asli page khulega. Kaam ho jaaye to ✓ laga do. " +
                    "Ye app aapka password kabhi nahi maangti.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        state.google.forEach { g ->
            RowDivider(start = 48.dp)
            GoogleStepRow(g, actions)
        }
    }
}

@Composable
private fun GoogleStepRow(g: GoogleStepItem, actions: GuardActions) {
    val (_, title) = splitEmoji(g.step.title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { actions.toggleStep(g.step.id) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = g.done, onCheckedChange = { actions.toggleStep(g.step.id) })
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (g.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            Text(
                g.step.why,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = { actions.openStep(g.step.open) }) { Text("Kholo") }
    }
}

@Composable
private fun AppsCard(
    apps: List<PrivacyScanner.Finding>,
    showAll: Boolean,
    onToggle: () -> Unit,
    actions: GuardActions
) {
    val risky = apps.filter { it.risk > 0 }
    val rest = apps.filter { it.risk == 0 }
    GCard(padding = 12.dp) {
        Text(
            "Fake loan apps contacts aur photos churaa kar family ko message bhejti hain. 'Band karo' dabao, " +
                "phir Permissions mein Contacts/SMS/Photos ko 'Don't allow' karo, ya app uninstall kar do.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(4.dp)
        )
        if (risky.isEmpty()) {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = StatusColors.Safe)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Koi anjaan app aapki private jaankari nahi le rahi.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        risky.forEach {
            RowDivider(start = 0.dp)
            AppRow(it, actions)
        }
        if (rest.isNotEmpty()) {
            if (showAll) {
                rest.forEach {
                    RowDivider(start = 0.dp)
                    AppRow(it, actions)
                }
            }
            RowDivider(start = 0.dp)
            TextButton(onClick = onToggle, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (showAll) "Kam dikhao" else "Baaki ${rest.size} apps bhi dekho")
            }
        }
    }
}

@Composable
private fun AppRow(f: PrivacyScanner.Finding, actions: GuardActions) {
    val color = when (f.risk) {
        2 -> StatusColors.Danger
        1 -> StatusColors.Warn
        else -> StatusColors.Safe
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        LetterBubble(f.label, color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(f.perms.joinToString(" • "), style = MaterialTheme.typography.labelMedium, color = color)
            if (f.why.isNotEmpty()) {
                Text(
                    f.why,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (f.risk > 0) {
            FilledTonalButton(
                onClick = { actions.openApp(f.pkg) },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = color.copy(alpha = 0.14f),
                    contentColor = color
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("Band karo") }
        } else {
            TextButton(onClick = { actions.openApp(f.pkg) }) { Text("Dekho") }
        }
    }
}

// ======================= Rules =======================

@Composable
fun RulesScreen(state: GuardState, actions: GuardActions) {
    var removing by remember { mutableStateOf<String?>(null) }
    ScreenList {
        item { ScreenHeader("Rules", "Kaun si call aur message roke jaayein") }
        item {
            GCard(padding = 12.dp) {
                RuleRow(
                    "Videsh ke anjaan number ki call kaato",
                    "Jo number contacts mein nahi aur ${state.homeCode} ke bahar ka hai",
                    state.blockForeign
                ) { actions.setRule(GuardRule.BLOCK_FOREIGN, it) }
                RowDivider(start = 4.dp)
                RuleRow(
                    "Har anjaan caller ki call kaato",
                    "Strict mode: sirf contacts ki call aayegi. Naye asli log bhi block honge.",
                    state.blockAll
                ) { actions.setRule(GuardRule.BLOCK_ALL, it) }
                RowDivider(start = 4.dp)
                RuleRow(
                    "Videsh ke anjaan messages chhupao",
                    "Notification hata kar warning dikhayi jaati hai",
                    state.hideForeign
                ) { actions.setRule(GuardRule.HIDE_FOREIGN, it) }
                RowDivider(start = 4.dp)
                RuleRow(
                    "Scam message pe warning",
                    "KYC, OTP, loan dhamki, nakli link wale messages pakdo",
                    state.scamAlerts
                ) { actions.setRule(GuardRule.SCAM_ALERTS, it) }
            }
        }
        item { SectionTitle("Mera desh code") }
        item { GCard { HomeCodeEditor(state.homeCode, actions) } }
        item {
            SectionTitle("Number block karo") {
                if (state.blocklist.isNotEmpty()) StatusPill("${state.blocklist.size} blocked", StatusColors.Danger)
            }
        }
        item { GCard(padding = 12.dp) { BlockEditor(state.blocklist, actions) { removing = it } } }
        item {
            TipCard(
                Icons.Filled.Info, MaterialTheme.colorScheme.primary,
                "WhatsApp mein bhi ye karo",
                "Settings → Privacy → Calls → 'Silence unknown callers' ON karo. " +
                    "Profile photo, About aur Groups ko 'My contacts' pe rakho."
            )
        }
    }
    removing?.let { entry ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Block hataayein?") },
            text = { Text("$entry ko block list se hata dein?") },
            confirmButton = {
                TextButton(onClick = {
                    actions.removeBlock(entry)
                    removing = null
                }) { Text("Haan, hatao") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Nahi") } }
        )
    }
}

@Composable
private fun RuleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun HomeCodeEditor(homeCode: String, actions: GuardActions) {
    var text by rememberSaveable(homeCode) { mutableStateOf(homeCode) }
    var error by remember { mutableStateOf<String?>(null) }
    var ask by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val raw = text.trim()
        val typed = Phone.cleanCode(raw)
        // "+91..." ya "0091..." se shuru poora number ho to usmein se code nikaal sakte hain
        val fromNumber = if (raw.startsWith("+") || raw.startsWith("00")) Phone.countryCodeOf(raw) else null
        when {
            Phone.isCountryCode(typed) -> {
                actions.saveHomeCode(typed)
                text = typed
                error = null
            }
            fromNumber != null -> ask = fromNumber
            else -> error = "Ye sahi desh code nahi hai. India ke liye +91 likho."
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBubble(Icons.Filled.Phone, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Abhi chal raha code",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(homeCode, style = MaterialTheme.typography.titleLarge)
        }
        StatusPill("Active", StatusColors.Safe)
    }
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.Top) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                error = null
            },
            modifier = Modifier.weight(1f),
            label = { Text("Desh code") },
            placeholder = { Text("+91") },
            singleLine = true,
            isError = error != null,
            supportingText = { Text(error ?: "Sirf code likho, phone number nahi") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() })
        )
        Spacer(Modifier.width(10.dp))
        Button(onClick = { submit() }, modifier = Modifier.padding(top = 8.dp).height(52.dp)) { Text("Save") }
    }

    ask?.let { cc ->
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text("Poora number likha hai") },
            text = { Text("Yahan sirf desh code chahiye, phone number nahi.\n\n'$cc' save karun?") },
            confirmButton = {
                TextButton(onClick = {
                    actions.saveHomeCode(cc)
                    text = cc
                    ask = null
                }) { Text("Haan, $cc save karo") }
            },
            dismissButton = { TextButton(onClick = { ask = null }) { Text("Nahi") } }
        )
    }
}

@Composable
private fun BlockEditor(list: List<String>, actions: GuardActions, onRemove: (String) -> Unit) {
    var entry by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = entry,
        onValueChange = { entry = it },
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        label = { Text("Number ya Telegram naam") },
        placeholder = { Text("+92 300 1234567") },
        singleLine = true
    )
    Button(
        onClick = {
            actions.addBlock(entry)
            entry = ""
        },
        enabled = entry.isNotBlank(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = StatusColors.Danger, contentColor = Color.White)
    ) {
        Icon(GuardIcons.Block, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Block list mein daalo")
    }
    Spacer(Modifier.height(8.dp))
    if (list.isEmpty()) {
        Text(
            "Abhi koi number block nahi hai.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(4.dp)
        )
    }
    list.forEach { e ->
        RowDivider(start = 4.dp)
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(GuardIcons.Block, StatusColors.Danger, 34.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                e,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = { onRemove(e) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Hatao", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ======================= Log =======================

@Composable
fun LogScreen(state: GuardState, actions: GuardActions) {
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    ScreenList {
        item {
            ScreenHeader("Kya-kya roka gaya", "Calls, messages aur Kila ke alarm") {
                if (state.log.isNotEmpty()) TextButton(onClick = { actions.clearLog() }) { Text("Saaf karo") }
            }
        }
        item { StatsRow(state) }
        item { SectionTitle("Calls aur messages") }
        if (state.log.isEmpty()) {
            item {
                EmptyCard(
                    GuardIcons.History, MaterialTheme.colorScheme.primary,
                    "Abhi tak kuch nahi roka",
                    "Jab koi call ya message roka jaayega, yahan dikhega."
                )
            }
        } else {
            item {
                GCard(padding = 12.dp) {
                    state.log.take(100).forEachIndexed { i, e ->
                        if (i > 0) RowDivider(start = 48.dp)
                        LogRow(e, fmt)
                    }
                }
            }
        }
        item { SectionTitle("Kila ke alarm") }
        if (state.alarms.isEmpty()) {
            item {
                EmptyCard(
                    GuardIcons.VerifiedUser, StatusColors.Safe,
                    "Koi alarm nahi",
                    "Camera/mic chupke se chalu ho ya nayi khatarnak app aaye, to yahan dikhega."
                )
            }
        } else {
            item {
                GCard(padding = 12.dp) {
                    state.alarms.take(20).forEachIndexed { i, a ->
                        if (i > 0) RowDivider(start = 48.dp)
                        AlarmRow(a, fmt)
                    }
                }
            }
        }
    }
}

private data class LogStyle(val icon: ImageVector, val color: Color, val label: String)

private fun logStyle(action: String): LogStyle = when (action) {
    "BLOCKED" -> LogStyle(GuardIcons.Block, StatusColors.Danger, "Call kaati gayi")
    "BLOCK FAILED" -> LogStyle(Icons.Filled.Warning, StatusColors.Danger, "Call nahi kat payi")
    "MESSAGE HIDDEN" -> LogStyle(Icons.Filled.Lock, StatusColors.Warn, "Message chhupaya")
    "SCAM MESSAGE" -> LogStyle(Icons.Filled.Warning, StatusColors.Danger, "Scam message")
    else -> LogStyle(Icons.Filled.Info, StatusColors.Warn, "Warning")
}

@Composable
private fun LogRow(e: LogItem, fmt: SimpleDateFormat) {
    val (icon, color, label) = logStyle(e.action)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        IconBubble(icon, color, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.who,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    fmt.format(Date(e.time)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("$label • ${e.source}", style = MaterialTheme.typography.labelMedium, color = color)
            Spacer(Modifier.height(2.dp))
            Text(
                e.reason.replace(" | ", "\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlarmRow(a: AlarmItem, fmt: SimpleDateFormat) {
    val (emoji, title) = splitEmoji(a.title)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        EmojiBubble(emoji ?: "🚨", StatusColors.Warn, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                fmt.format(Date(a.time)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ======================= Madad =======================

private val tips = listOf(
    "OTP aur UPI PIN kabhi kisi ko na dein. Bank wale bhi nahi maangte.",
    "UPI PIN sirf paise BHEJNE ke liye lagta hai, paane ke liye kabhi nahi.",
    "Bank, police, CBI ya courier wale WhatsApp pe call nahi karte.",
    "'Digital arrest' naam ki koi cheez nahi hoti.",
    "Kisi ke kehne pe AnyDesk, TeamViewer ya anjaan APK install na karein.",
    "Dhamki mile to darein nahi. Chat mein jaakar Block + Report karein."
)

@Composable
fun MadadScreen(state: GuardState, actions: GuardActions) {
    ScreenList {
        item { ScreenHeader("Madad", "Fraud ho jaaye to ghabraiye nahi") }
        item { EmergencyCard(state.hasSim, actions) }
        item { SectionTitle("Fraud ke baad ye kadam") }
        item {
            GCard(padding = 12.dp) {
                StepRow(1, "Turant 1930 pe call karein", "National Cyber Crime Helpline. Jitni jaldi, utna achha.")
                RowDivider(start = 44.dp)
                StepRow(2, "cybercrime.gov.in pe complaint", "Screenshot, number aur transaction ID saath rakhein.", "Kholo") {
                    actions.openUrl("https://cybercrime.gov.in")
                }
                RowDivider(start = 44.dp)
                StepRow(3, "Bank ko call karein", "Account aur UPI turant freeze karwaayein.")
                RowDivider(start = 44.dp)
                StepRow(4, "Number 'Chakshu' pe report karein", "Sanchar Saathi portal par, taaki ye number band ho.", "Kholo") {
                    actions.openUrl("https://sancharsaathi.gov.in")
                }
            }
        }
        item { SectionTitle("Hamesha yaad rakhein") }
        item {
            GCard(padding = 12.dp) {
                tips.forEachIndexed { i, t ->
                    if (i > 0) RowDivider(start = 44.dp)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Filled.CheckCircle, StatusColors.Safe, 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(t, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            TipCard(
                Icons.Filled.Lock, StatusColors.Safe,
                "Aapka data aapke phone mein hi rehta hai",
                "Ye app aapke chats, calls ya data ko kahin nahi bhejti. Internet permission bhi nahi li gayi."
            )
        }
    }
}

@Composable
private fun EmergencyCard(hasSim: Boolean, actions: GuardActions) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = StatusColors.Danger.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, StatusColors.Danger.copy(alpha = 0.25f))
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Cyber fraud helpline", style = MaterialTheme.typography.labelLarge, color = StatusColors.Danger)
            Text("1930", style = MaterialTheme.typography.displaySmall, color = StatusColors.Danger)
            Spacer(Modifier.height(4.dp))
            Text(
                "Paise kat gaye hon to turant call karein. Pehle kuch ghante sabse keemti hote hain.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            if (hasSim) {
                Button(
                    onClick = { actions.dial("1930") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusColors.Danger, contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("1930 pe call karo")
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = { actions.openUrl("https://cybercrime.gov.in") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("cybercrime.gov.in kholo") }
        }
    }
}

@Composable
private fun StepRow(n: Int, title: String, sub: String, button: String? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text("$n", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (button != null && onClick != null) {
            TextButton(onClick = onClick) { Text(button) }
        }
    }
}
