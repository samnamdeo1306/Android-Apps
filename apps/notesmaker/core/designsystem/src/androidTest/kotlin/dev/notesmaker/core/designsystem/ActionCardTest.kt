package dev.notesmaker.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.notesmaker.core.designsystem.component.ActionCard
import dev.notesmaker.core.designsystem.theme.NotesMakerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ActionCardTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theWholeCardIsOneButtonThatReadsTitleAndDescription() {
        var clicks = 0
        compose.setContent {
            NotesMakerTheme(dynamicColor = false) {
                ActionCard(title = "Record", description = "Capture a live class", icon = Icons.Filled.Add, onClick = { clicks++ })
            }
        }

        compose.onNodeWithText("Record", substring = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher("merges title and description") { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } == listOf("Record", "Capture a live class")
            })
            .assertIsEnabled()
            .performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun aDisabledCardIgnoresClicks() {
        var clicks = 0
        compose.setContent {
            NotesMakerTheme(dynamicColor = false) {
                ActionCard(title = "Import", description = "Pick a file", icon = Icons.Filled.Add, onClick = { clicks++ }, enabled = false)
            }
        }

        compose.onNodeWithText("Import", substring = true).assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }
}
