package dev.daybreak.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.core.net.toUri
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.daybreak.domain.AlarmAction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Ink = Color(0xFF101C2B)
private val Muted = Color(0xFFABB8CB)
private val Peach = Color(0xFFFFB59A)
private val Mint = Color(0xFFB6E5D2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
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

    @Composable private fun DaybreakScreen(model: AlarmViewModel = viewModel()) {
        val alarms by model.alarms.collectAsStateWithLifecycle()
        val ringing by model.ringing.collectAsStateWithLifecycle()
        val message by model.message.collectAsStateWithLifecycle()
        val busy by model.busy.collectAsStateWithLifecycle()
        var showCreate by rememberSaveable { mutableStateOf(false) }
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
        val notificationAllowed = remember(permissionRevision) {
            getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        }
        val fullScreen = remember(permissionRevision) {
            Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(message) {
            message?.let { snackbar.showSnackbar(it); model.message.value = null }
        }
        Scaffold(containerColor = Ink, snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                if (ringing == null) ExtendedFloatingActionButton(onClick = { showCreate = true },
                    containerColor = Peach, contentColor = Ink) { Text("+  New alarm", fontWeight = FontWeight.Bold) }
            }) { padding ->
            LazyColumn(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF243447), Ink)))
                .padding(padding), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item {
                    Text("DAYBREAK   /   A MORE INTENTIONAL MORNING", color = Mint, fontSize = 10.sp,
                        letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    Text(if (ringing == null) "Rise with\npurpose." else "A little focus.\nA fresh start.",
                        fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Text(if (ringing == null) "An alarm that wakes your mind, too." else "Complete your challenge to finish or rest a little longer.", color = Muted)
                }
                if (ringing != null) {
                    item { ChallengeCard(requireNotNull(ringing), busy, model::submit) }
                } else {
                    if (!exact || !notificationAllowed || !fullScreen) item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF303A45))) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Give your morning a clear path", fontWeight = FontWeight.Bold)
                                Text("Exact alarms are required to schedule. Notifications and full-screen access help you reach the challenge when your phone is locked.", color = Muted)
                                if (!exact && Build.VERSION.SDK_INT >= 31) TextButton(onClick = {
                                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        "package:$packageName".toUri()))
                                }) { Text("Allow exact alarms") }
                                if (!notificationAllowed) TextButton(onClick = {
                                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                                }) { Text("Enable notifications") }
                                if (!fullScreen && Build.VERSION.SDK_INT >= 34) TextButton(onClick = {
                                    startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:$packageName".toUri()))
                                }) { Text("Allow lock-screen alarms") }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("YOUR ALARMS", color = Muted, letterSpacing = 2.sp, fontSize = 12.sp)
                            Text("${alarms.count { it.enabled }} scheduled", color = Mint, fontSize = 12.sp)
                        }
                    }
                    if (alarms.isEmpty()) item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Make room for a good morning.", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                                Text("Set a time. Choose your question or a quick number puzzle. Both snooze and dismiss need an answer.", color = Muted)
                                Text("01  Choose your time\n02  Pick a challenge\n03  Wake up with intention", lineHeight = 30.sp, color = Mint)
                            }
                        }
                    }
                    items(alarms, key = { it.id }) { alarm ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp)) {
                                Text(Instant.ofEpochMilli(alarm.triggerAtMillis).atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 42.sp, fontWeight = FontWeight.Light,
                                    color = if (alarm.enabled) Peach else Muted)
                                Text(alarm.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                Text("${if (alarm.enabled) "Scheduled" else "Completed / missed"} · " +
                                    Instant.ofEpochMilli(alarm.triggerAtMillis).atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("EEE, d MMM")), color = Muted)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(model.options.find { it.id == alarm.challengeId }?.title ?: "Unsupported challenge",
                                        color = Mint, modifier = Modifier.padding(top = 14.dp))
                                    TextButton(onClick = { model.delete(alarm) }, enabled = !busy) { Text("Delete") }
                                }
                            }
                        }
                    }
                    item {
                        Text("A small promise to yourself.\nOne-time alarms · 5-minute snooze · On-device only", color = Muted,
                            fontSize = 12.sp, lineHeight = 20.sp, modifier = Modifier.padding(bottom = 90.dp))
                    }
                }
            }
        }
        if (showCreate && ringing == null) CreateAlarmDialog(model, busy, exact) { showCreate = false }
    }
}

@Composable private fun ChallengeCard(current: RingingUi, busy: Boolean, submit: (AlarmAction, String) -> Unit) {
    var answer by rememberSaveable(current.alarm.id, current.session.prompt.text) { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("IT'S TIME  /  ${current.alarm.label}", color = Mint, fontSize = 12.sp)
            Text(current.session.prompt.text, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium)
            OutlinedTextField(answer, { answer = it }, label = { Text("Your answer") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType =
                    if (current.alarm.challengeId == "sum.v1") KeyboardType.Number else KeyboardType.Text))
            Button(onClick = { submit(AlarmAction.DISMISS, answer) }, enabled = !busy && answer.isNotBlank(),
                modifier = Modifier.fillMaxWidth()) { Text("Answer & start my day") }
            OutlinedButton(onClick = { submit(AlarmAction.SNOOZE, answer) }, enabled = !busy && answer.isNotBlank(),
                modifier = Modifier.fillMaxWidth()) { Text("Answer & snooze 5 min") }
            Text("A wrong answer keeps the alarm ringing. Your phone's alarm volume controls the sound.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable private fun CreateAlarmDialog(model: AlarmViewModel, busy: Boolean, exact: Boolean, close: () -> Unit) {
    var label by rememberSaveable { mutableStateOf("Morning ritual") }
    var time by rememberSaveable { mutableStateOf("07:00") }
    var type by rememberSaveable { mutableStateOf("sum.v1") }
    var question by rememberSaveable { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text("Your next fresh start") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("Alarm name") }, singleLine = true)
                OutlinedTextField(time, { time = it }, label = { Text("Time · 24-hour HH:mm") }, singleLine = true)
                Text("CHOOSE YOUR CHALLENGE", color = Mint, fontSize = 11.sp)
                model.options.forEach { option ->
                    FilterChip(selected = type == option.id, onClick = { type = option.id }, label = { Text(option.title) })
                }
                if (type == "question.v1") {
                    OutlinedTextField(question, { question = it }, label = { Text("Your agreed question") })
                    OutlinedTextField(answer, { answer = it }, label = { Text("Agreed answer") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                    Text("Answers ignore letter case and outer spaces. Use a memorable answer, never a password or sensitive information.", fontSize = 12.sp, color = Muted)
                } else Text("Solve a fresh addition puzzle each time the alarm rings.", color = Muted)
                if (!exact) Text("Enable exact alarms on the home screen before saving.", color = Peach)
            }
        }, confirmButton = {
            TextButton(enabled = !busy && exact, onClick = {
                model.create(label, time, type, question, answer, close)
            }) { Text(if (busy) "Saving…" else "Schedule") }
        }, dismissButton = { TextButton(onClick = close, enabled = !busy) { Text("Close") } })
}
