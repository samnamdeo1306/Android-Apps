package dev.notesmaker.feature.capture

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.notesmaker.core.designsystem.theme.NotesMakerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class CaptureScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(state: CaptureUiState, onRecord: () -> Unit = {}, onImport: () -> Unit = {}) {
        compose.setContent {
            NotesMakerTheme(dynamicColor = false) { CaptureScreen(state, onRecord, onImport) }
        }
    }

    @Test
    fun offersRecordAndImport() {
        show(CaptureUiState(recordEnabled = true, importEnabled = true))
        compose.onNodeWithText("New session").assertIsDisplayed()
        compose.onNodeWithText("Record a session", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Import an audio file", substring = true).assertIsDisplayed()
    }

    @Test
    fun tapsReachTheirCallbacks() {
        var recorded = 0
        var imported = 0
        show(CaptureUiState(recordEnabled = true, importEnabled = true), onRecord = { recorded++ }, onImport = { imported++ })

        compose.onNodeWithText("Record a session", substring = true).assertIsEnabled().performClick()
        compose.onNodeWithText("Import an audio file", substring = true).performClick()

        assertEquals(1, recorded)
        assertEquals(1, imported)
    }

    @Test
    fun disabledActionsDoNothing() {
        var recorded = 0
        show(CaptureUiState(recordEnabled = false, importEnabled = false), onRecord = { recorded++ })
        compose.onNodeWithText("Record a session", substring = true).assertIsNotEnabled().performClick()
        assertEquals(0, recorded)
    }
}
