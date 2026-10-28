package dev.daybreak.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.core.net.toUri
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dev.daybreak.app.alarm.AlarmNotifications
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import dev.daybreak.domain.AlarmAction
import java.util.Date

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalConfiguration

private val Ink = Color(0xFF101C2B)
private val Muted = Color(0xFFABB8CB)
private val Peach = Color(0xFFFFB59A)
private val Mint = Color(0xFFB6E5D2)

class MainActivity : ComponentActivity() {
    private val listModel: AlarmListViewModel by viewModels { graph.screenViewModelFactory }
    private val editorModel: AlarmEditorViewModel by viewModels { graph.screenViewModelFactory }
    private val ringingModel: RingingViewModel by viewModels { graph.screenViewModelFactory }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyAlarmWindowMode(graph.activeId.value != null)
        lifecycleScope.launch { graph.activeId.collect { applyAlarmWindowMode(it != null) } }
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Peach, onPrimary = Ink,
                secondary = Mint, background = Ink, surface = Color(0xFF1B2B3E),
                onSurface = Color(0xFFF4F2EE), onBackground = Color(0xFFF4F2EE)),
                shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))) {
                DaybreakScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            graph.refreshSchedules()
            graph.startScheduleSync()
            if (graph.activeId.value != null && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                try {
                    androidx.core.content.ContextCompat.startForegroundService(this@MainActivity,
                        Intent(this@MainActivity, dev.daybreak.app.alarm.RingingService::class.java)
                            .putExtra("alarmId", graph.activeId.value).putExtra("resumeRinging", true))
                } catch (error: RuntimeException) { android.util.Log.e("DaybreakDelivery", "Could not resume ringing occurrence", error) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyAlarmWindowMode(graph.activeId.value != null)
    }

    private fun applyAlarmWindowMode(ringing: Boolean) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(ringing)
            setTurnScreenOn(ringing)
        } else {
            @Suppress("DEPRECATION")
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (ringing) window.addFlags(flags) else window.clearFlags(flags)
        }
    }

    @Composable private fun DaybreakScreen() {
        val listState by listModel.state.collectAsStateWithLifecycle()
        val editorState by editorModel.state.collectAsStateWithLifecycle()
        val ringingState by ringingModel.state.collectAsStateWithLifecycle()
        val alarms = listState.alarms
        val ringing = ringingState.ringing
        val active = ringingState.active
        val busy = listState.busy
        val scheduleIssue by graph.scheduleSyncIssue.collectAsStateWithLifecycle()
        var permissionRevision by remember { mutableIntStateOf(0) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) permissionRevision++
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissionRevision++
        }
        val exact = remember(permissionRevision) { graph.scheduler.canSchedule() }
        val deliveryAccess = remember(permissionRevision) { AlarmNotifications(this).access() }
        val notificationAllowed = deliveryAccess.notifications
        val fullScreen = deliveryAccess.fullScreen
        val channelHigh = deliveryAccess.highImportance
        val snackbar = remember { SnackbarHostState() }
        val defaultLabel = stringResource(R.string.default_alarm_name)
        val locale = LocalConfiguration.current.locales[0]
        val listMessage = listState.message?.let { stringResource(it) }
        val editorMessage = editorState.message?.let { stringResource(it) }
        val ringingMessage = ringingState.message?.let { stringResource(it) }
        LaunchedEffect(listState.message, listMessage) {
            listState.message?.let { snackbar.showSnackbar(requireNotNull(listMessage)); listModel.messageShown(it) }
        }
        LaunchedEffect(editorState.message, editorMessage) {
            editorState.message?.let { snackbar.showSnackbar(requireNotNull(editorMessage)); editorModel.messageShown(it) }
        }
        LaunchedEffect(ringingState.message, ringingMessage) {
            ringingState.message?.let { snackbar.showSnackbar(requireNotNull(ringingMessage)); ringingModel.messageShown(it) }
        }
        LaunchedEffect(editorState.saved) {
            if (editorState.saved) { editorModel.saveHandled() }
        }
        Scaffold(containerColor = Ink, snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                if (!active) ExtendedFloatingActionButton(onClick = { editorModel.begin(defaultLabel) },
                    containerColor = Peach, contentColor = Ink) { Text(stringResource(R.string.new_alarm), fontWeight = FontWeight.Bold) }
            }) { padding ->
            LazyColumn(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF243447), Ink)))
                .padding(padding), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item {
                    Text(stringResource(R.string.brand_tagline), color = Mint, fontSize = 10.sp,
                        letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    Text(if (!active) stringResource(R.string.home_heading) else stringResource(R.string.ringing_heading),
                        fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Text(if (!active) stringResource(R.string.home_subtitle) else stringResource(R.string.ringing_subtitle), color = Muted)
                }
                if (active) {
                    item {
                        if (ringing != null) ChallengeCard(requireNotNull(ringing), ringingState.busy, ringingModel::submit)
                        else Text(stringResource(R.string.challenge_loading))
                    }
                } else {
                    if (!exact || !notificationAllowed || !fullScreen || !channelHigh || scheduleIssue) item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF303A45))) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.permission_heading), fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.permission_explanation), color = Muted)
                                if (scheduleIssue) {
                                    Text(stringResource(R.string.schedule_sync_pending), color = Muted)
                                    TextButton(onClick = { graph.retrySchedules() }) { Text(stringResource(R.string.retry_schedules)) }
                                }
                                if (!exact && Build.VERSION.SDK_INT >= 31) TextButton(onClick = {
                                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        "package:$packageName".toUri()))
                                }) { Text(stringResource(R.string.allow_exact)) }
                                if (!notificationAllowed) TextButton(onClick = {
                                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                                }) { Text(stringResource(R.string.enable_notifications)) }
                                if (!channelHigh) {
                                    Text(stringResource(R.string.channel_access_help), color = Muted)
                                    TextButton(onClick = {
                                        startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                            .putExtra(Settings.EXTRA_CHANNEL_ID, AlarmNotifications.CHANNEL_ID))
                                    }) { Text(stringResource(R.string.open_alarm_channel)) }
                                }
                                if (!fullScreen && Build.VERSION.SDK_INT >= 34) TextButton(onClick = {
                                    startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:$packageName".toUri()))
                                }) { Text(stringResource(R.string.allow_full_screen)) }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.your_alarms), color = Muted, letterSpacing = 2.sp, fontSize = 12.sp)
                            Text(pluralStringResource(R.plurals.scheduled_count, alarms.count { it.enabled }, alarms.count { it.enabled }), color = Mint, fontSize = 12.sp)
                        }
                    }
                    if (alarms.isEmpty()) item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.empty_heading), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                                Text(stringResource(R.string.empty_explanation), color = Muted)
                                Text(stringResource(R.string.empty_steps), lineHeight = 30.sp, color = Mint)
                            }
                        }
                    }
                    items(alarms, key = { it.id }) { alarm ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp)) {
                                Text(android.text.format.DateFormat.getTimeFormat(this@MainActivity).format(Date(alarm.triggerAtMillis)), fontSize = 42.sp, fontWeight = FontWeight.Light,
                                    color = if (alarm.enabled) Peach else Muted)
                                Text(repeatSummary(alarm.repeat), color = Mint, style = MaterialTheme.typography.labelMedium)
                                Text(alarm.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.alarm_status_date,
                                    stringResource(if (alarm.enabled) R.string.status_scheduled else R.string.status_completed),
                                    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, locale)
                                        .format(Date(alarm.triggerAtMillis))), color = Muted)
                                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(challengeTitle(alarm.challengeId)),
                                        color = Mint, modifier = Modifier.padding(top = 14.dp))
                                    TextButton(onClick = { editorModel.edit(alarm) }, enabled = !busy && !editorState.busy) {
                                        Text(stringResource(R.string.edit_alarm))
                                    }
                                    TextButton(onClick = { listModel.delete(alarm) }, enabled = !busy) { Text(stringResource(R.string.delete_alarm)) }
                                }
                            }
                        }
                    }
                    item {
                        Text(stringResource(R.string.home_footer), color = Muted,
                            fontSize = 12.sp, lineHeight = 20.sp, modifier = Modifier.padding(bottom = 90.dp))
                    }
                }
            }
        }
        if (editorState.visible && !active) AlarmEditorDialog(editorState, graph.challenges.options, exact,
            editorModel::updateDraft, editorModel::save) { editorModel.close() }
    }
}

@Composable private fun ChallengeCard(current: RingingUi, busy: Boolean, submit: (AlarmAction, String) -> Unit) {
    var answer by rememberSaveable(current.alarm.id, current.prompt) { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(R.string.ringing_label, current.alarm.label), color = Mint, fontSize = 12.sp)
            Text(promptText(current.prompt.content), fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium)
            OutlinedTextField(answer, { answer = it }, label = { Text(stringResource(R.string.your_answer)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType =
                    if (current.alarm.challengeId == "sum.v1") KeyboardType.Number else KeyboardType.Text))
            Button(onClick = { submit(AlarmAction.DISMISS, answer) }, enabled = !busy && answer.isNotBlank(),
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.answer_dismiss)) }
            OutlinedButton(onClick = { submit(AlarmAction.SNOOZE, answer) }, enabled = !busy && answer.isNotBlank(),
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.answer_snooze)) }
            Text(stringResource(R.string.answer_help), color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable private fun AlarmEditorDialog(
    state: AlarmEditorUiState,
    options: List<dev.daybreak.domain.Challenge>,
    exact: Boolean,
    update: (AlarmDraft) -> Unit,
    save: () -> Unit,
    close: () -> Unit,
) {
    val draft = state.draft
    val busy = state.busy
    val label = draft.label
    val time = draft.time
    val type = draft.challengeId
    val question = draft.question
    val answer = draft.answer
    AlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text(stringResource(if (state.original == null) R.string.create_title else R.string.edit_alarm_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(label, { update(draft.copy(label = it)) }, label = { Text(stringResource(R.string.alarm_name)) }, singleLine = true)
                AlarmTimeField(java.time.LocalTime.parse(time), enabled = !busy,
                    onTimeSelected = { update(draft.copy(time = it.toString())) })
                Text(stringResource(R.string.repeat_days_label), color = Mint, style = MaterialTheme.typography.labelMedium)
                val locale = LocalConfiguration.current.locales[0]
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = draft.repeatDays.isEmpty(), enabled = !busy,
                        onClick = { update(draft.copy(repeatDays = emptySet())) },
                        label = { Text(stringResource(R.string.repeat_once)) })
                    val firstDay = java.time.temporal.WeekFields.of(locale).firstDayOfWeek
                    (0L..6L).map { firstDay.plus(it) }.forEach { day ->
                        val fullName = day.getDisplayName(java.time.format.TextStyle.FULL, locale)
                        FilterChip(selected = day in draft.repeatDays, enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = fullName },
                            onClick = { update(draft.copy(repeatDays = if (day in draft.repeatDays)
                                draft.repeatDays - day else draft.repeatDays + day)) },
                            label = { Text(day.getDisplayName(java.time.format.TextStyle.SHORT, locale)) })
                    }
                }
                Text(stringResource(R.string.repeat_help), color = Muted, fontSize = 12.sp)
                Text(stringResource(R.string.choose_challenge), color = Mint, fontSize = 11.sp)
                options.forEach { option ->
                    FilterChip(selected = type == option.id, onClick = { update(draft.copy(challengeId = option.id)) }, label = { Text(stringResource(challengeTitle(option.id))) })
                }
                if (type == "question.v1") {
                    OutlinedTextField(question, { update(draft.copy(question = it)) }, label = { Text(stringResource(R.string.agreed_question)) })
                    OutlinedTextField(answer, { update(draft.copy(answer = it)) }, label = { Text(stringResource(R.string.agreed_answer)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                    Text(stringResource(if (state.original?.challengeId == "question.v1") R.string.edit_answer_help else R.string.question_help), fontSize = 12.sp, color = Muted)
                } else Text(stringResource(R.string.puzzle_help), color = Muted)
                if (state.original != null) Text(stringResource(R.string.edit_schedule_help), fontSize = 12.sp, color = Muted)
                state.message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                if (!exact) Text(stringResource(R.string.exact_required_hint), color = Peach)
            }
        }, confirmButton = {
            TextButton(enabled = !busy && exact, onClick = {
                save()
            }) { Text(if (busy) stringResource(R.string.saving) else stringResource(if (state.original == null) R.string.schedule else R.string.save_and_schedule)) }
        }, dismissButton = { TextButton(onClick = close, enabled = !busy) { Text(stringResource(R.string.close)) } })
}

@Composable private fun repeatSummary(rule: dev.daybreak.domain.RepeatRule?): String {
    if (rule == null) return stringResource(R.string.repeat_once)
    val locale = LocalConfiguration.current.locales[0]
    val firstDay = java.time.temporal.WeekFields.of(locale).firstDayOfWeek
    val days = rule.days.sortedBy { (it.value - firstDay.value + 7) % 7 }.map { it.getDisplayName(java.time.format.TextStyle.SHORT, locale) }
    val list = android.icu.text.ListFormatter.getInstance(locale).format(days)
    return stringResource(R.string.repeat_summary, list)
}
