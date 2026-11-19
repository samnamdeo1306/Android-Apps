package dev.notesmaker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.notesmaker.core.designsystem.theme.NotesMakerTheme
import dev.notesmaker.feature.capture.CaptureRoute

/** The single activity (ADR-09). Navigation 3 and the other screens arrive with LLD Section 12 step 7. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NotesMakerTheme {
                CaptureRoute()
            }
        }
    }
}
