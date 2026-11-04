package com.chatcallguard

import androidx.compose.runtime.CompositionLocalProvider
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.chatcallguard.ui.GuardApp
import com.chatcallguard.ui.GuardState
import com.chatcallguard.ui.GuardTheme
import com.chatcallguard.ui.LocalStartItem
import com.chatcallguard.ui.NoActions
import com.chatcallguard.ui.Sample
import org.junit.Rule
import org.junit.Test

/**
 * Har tab ki photo, phone jitni screen pe (nakli data ke saath).
 * startItem = list ko us item tak scroll karke photo lo (neeche ka hissa dekhne ke liye).
 */
class ScreensTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    private fun shot(
        name: String,
        tab: Int,
        state: GuardState = Sample.state,
        startItem: Int = 0,
        dark: Boolean = false
    ) = paparazzi.snapshot(name) {
        CompositionLocalProvider(LocalStartItem provides startItem) {
            GuardTheme(dark = dark) { GuardApp(state, NoActions, startTab = tab) }
        }
    }

    @Test fun homeSafe() = shot("01_home_safe", 0, Sample.safe)
    @Test fun homeAlert() = shot("02_home_alert", 0)
    @Test fun homeOff() = shot("03_home_off", 0, Sample.off)
    @Test fun homeBottom() = shot("04_home_bottom", 0, startItem = 4)
    @Test fun kila() = shot("05_kila", 1)
    @Test fun kilaGoogle() = shot("06_kila_google", 1, startItem = 6)
    @Test fun kilaApps() = shot("07_kila_apps", 1, startItem = 7)
    @Test fun rules() = shot("08_rules", 2)
    @Test fun rulesBottom() = shot("09_rules_bottom", 2, startItem = 4)
    @Test fun log() = shot("10_log", 3)
    @Test fun logBottom() = shot("11_log_bottom", 3, startItem = 4)
    @Test fun madad() = shot("12_madad", 4)
    @Test fun madadBottom() = shot("13_madad_bottom", 4, startItem = 4)
    @Test fun homeDark() = shot("14_home_dark", 0, Sample.safe, dark = true)
    @Test fun kilaDark() = shot("15_kila_dark", 1, dark = true)
}
