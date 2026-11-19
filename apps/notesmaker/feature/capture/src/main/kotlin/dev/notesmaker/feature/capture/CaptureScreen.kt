package dev.notesmaker.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.notesmaker.core.designsystem.component.ActionCard
import dev.notesmaker.core.designsystem.theme.NotesMakerTheme

/** What the Capture screen shows. Grows with recording and import (LLD Section 8.1). */
@Immutable
internal data class CaptureUiState(val recordEnabled: Boolean, val importEnabled: Boolean)

/**
 * Entry point of the Capture screen, the app's start destination (ADR-09). The only public
 * declaration of this module; it becomes a Navigation 3 entry builder when navigation lands (ADR-29).
 */
@Composable
fun CaptureRoute(modifier: Modifier = Modifier) {
    // Recording (LLD Section 12 step 6) and import (step 5) are wired to use cases in later milestones.
    CaptureScreen(
        state = CaptureUiState(recordEnabled = false, importEnabled = false),
        onRecord = {},
        onImport = {},
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CaptureScreen(
    state: CaptureUiState,
    onRecord: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.capture_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.capture_intro), style = MaterialTheme.typography.bodyLarge)
                ActionCard(
                    title = stringResource(R.string.capture_record_title),
                    description = stringResource(R.string.capture_record_description),
                    icon = ImageVector.vectorResource(R.drawable.ic_capture_mic),
                    onClick = onRecord,
                    enabled = state.recordEnabled,
                )
                ActionCard(
                    title = stringResource(R.string.capture_import_title),
                    description = stringResource(R.string.capture_import_description),
                    icon = ImageVector.vectorResource(R.drawable.ic_capture_audio_file),
                    onClick = onImport,
                    enabled = state.importEnabled,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CaptureScreenPreview() {
    NotesMakerTheme(dynamicColor = false) {
        CaptureScreen(CaptureUiState(recordEnabled = true, importEnabled = true), onRecord = {}, onImport = {})
    }
}
