package com.chatcallguard

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.chatcallguard.ui.GuardApp
import com.chatcallguard.ui.GuardState
import com.chatcallguard.ui.GuardTheme
import com.chatcallguard.ui.NoActions
import com.chatcallguard.ui.Sample
import org.junit.Rule
import org.junit.Test

/** Phone jitni screen pe har tab ki photo (nakli data ke saath) */
class PhoneScreensTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    private fun shot(name: String, tab: Int, state: GuardState = Sample.state, dark: Boolean = false) =
        paparazzi.snapshot(name) {
            GuardTheme(dark = dark) { GuardApp(state, NoActions, startTab = tab) }
        }

    @Test fun homeSafe() = shot("1_home_safe", 0, Sample.safe)
    @Test fun homeAlert() = shot("2_home_alert", 0)
    @Test fun kila() = shot("3_kila", 1)
    @Test fun rules() = shot("4_rules", 2)
    @Test fun log() = shot("5_log", 3)
    @Test fun madad() = shot("6_madad", 4)
    @Test fun homeDark() = shot("7_home_dark", 0, Sample.safe, dark = true)
}

/** Lambi screen: har tab ka poora content ek hi photo mein */
class FullScreensTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 7200),
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    private fun shot(name: String, tab: Int) =
        paparazzi.snapshot(name) {
            GuardTheme(dark = false) { GuardApp(Sample.state, NoActions, startTab = tab) }
        }

    @Test fun home() = shot("full_home", 0)
    @Test fun kila() = shot("full_kila", 1)
    @Test fun rules() = shot("full_rules", 2)
    @Test fun log() = shot("full_log", 3)
    @Test fun madad() = shot("full_madad", 4)
}
