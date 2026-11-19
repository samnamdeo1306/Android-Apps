package dev.notesmaker.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Smoke test: the app starts through Hilt and shows the Capture screen as its start destination (ADR-09). */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    private val hilt = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hilt).around(compose)

    @Test
    fun startsOnTheCaptureScreen() {
        compose.onNodeWithText("New session").assertIsDisplayed()
        compose.onNodeWithText("Record a session", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Import an audio file", substring = true).assertIsDisplayed()
    }
}
