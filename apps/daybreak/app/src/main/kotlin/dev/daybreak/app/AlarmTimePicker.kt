package dev.daybreak.app

import android.text.format.DateFormat
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** A presentation-only time editor. Scheduling still happens through CreateAlarm. */
@Composable
fun AlarmTimeField(
    time: LocalTime,
    enabled: Boolean = true,
    onTimeSelected: (LocalTime) -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val pattern = DateFormat.getBestDateTimePattern(configuration.locales[0], if (is24Hour) "Hm" else "hm")
    val formatted = time.format(DateTimeFormatter.ofPattern(pattern, configuration.locales[0]))
    Card(onClick = { showPicker = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = .14f),
                MaterialTheme.colorScheme.secondary.copy(alpha = .10f),
            ))).animateContentSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.alarm_time_label), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary)
            Text(formatted, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.change_alarm_time), style = MaterialTheme.typography.labelLarge)
        }
    }
    if (showPicker && enabled) AlarmTimePickerDialog(time, is24Hour,
        onDismiss = { showPicker = false },
        onConfirm = { selected -> onTimeSelected(selected); showPicker = false })
}

/** Picker changes are local until confirmed. Cancel/back/outside tap never modify the draft. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmTimePickerDialog(
    initialTime: LocalTime,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val picker = rememberTimePickerState(initialTime.hour, initialTime.minute, is24Hour)
    val colors = TimePickerDefaults.colors(
        selectorColor = MaterialTheme.colorScheme.primary,
        clockDialSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
        timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .20f),
        timeSelectorSelectedContentColor = MaterialTheme.colorScheme.primary,
        periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = .20f),
        periodSelectorSelectedContentColor = MaterialTheme.colorScheme.secondary,
    )
    // Short displays and large fonts start in the more compact keyboard mode.
    var showDial by rememberSaveable {
        mutableStateOf(windowHeight >= 600.dp && configuration.fontScale <= 1.3f)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp).widthIn(max = 384.dp).fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.select_alarm_time), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth())
                if (showDial) TimePicker(state = picker, colors = colors, layoutType = TimePickerLayoutType.Vertical)
                else TimeInput(state = picker, colors = colors)
                TextButton(onClick = { showDial = !showDial }) {
                    Text(stringResource(if (showDial) R.string.use_time_keyboard else R.string.use_time_dial))
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { onConfirm(LocalTime.of(picker.hour, picker.minute)) }) {
                        Text(stringResource(R.string.confirm_alarm_time))
                    }
                }
            }
        }
    }
}
