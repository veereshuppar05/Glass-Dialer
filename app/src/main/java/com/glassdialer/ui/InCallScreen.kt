package com.glassdialer.ui

import android.annotation.SuppressLint
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassdialer.data.PhoneRepository
import com.glassdialer.data.formatDuration
import com.glassdialer.recording.CallRecorder
import com.glassdialer.telecom.CallManager
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun InCallScreen(repo: PhoneRepository, onClose: () -> Unit) {
    val g = LocalGlass.current
    val call by CallManager.call.collectAsState()
    val state by CallManager.state.collectAsState()
    val audio by CallManager.audio.collectAsState()
    var number by remember { mutableStateOf("") }

    LaunchedEffect(call) {
        call?.details?.handle?.schemeSpecificPart?.let { number = it }
        if (call == null) { delay(900); onClose() }
    }
    val name by produceState<String?>(null, number) {
        value = withContext(Dispatchers.IO) { repo.lookupName(number) }
    }
    val haze = remember { HazeState() }

    Box(Modifier.fillMaxSize().background(g.bg)) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) { AnimatedBlobs() }
        when {
            call == null || state == Call.STATE_DISCONNECTED ->
                Text("Call ended", Modifier.align(Alignment.Center), color = g.text2, fontSize = 16.sp)
            state == Call.STATE_SELECT_PHONE_ACCOUNT -> SimPicker(haze, name ?: number)
            state == Call.STATE_RINGING -> Incoming(haze, name, number)
            else -> Ongoing(haze, name, number, state, audio)
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun Incoming(haze: HazeState, name: String?, number: String) {
    val g = LocalGlass.current
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier.glass(haze, Pill, g).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.CallReceived, null, Modifier.size(14.dp), tint = g.text2)
            Spacer(Modifier.width(6.dp))
            Text("Incoming call", fontSize = 12.sp, color = g.text2)
        }
        Spacer(Modifier.height(48.dp))
        PulsingAvatar(name)
        Spacer(Modifier.height(22.dp))
        Text(name ?: number.ifBlank { "Unknown" }, fontSize = 30.sp, textAlign = TextAlign.Center)
        if (name != null) Text(number, fontSize = 14.sp, color = g.text2)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassChip(haze, Icons.Rounded.Sms, "Message") {
                CallManager.rejectWithMessage("Can't talk right now. I'll call you back.")
            }
            GlassChip(haze, Icons.Rounded.NotificationsOff, "Silence") {
                runCatching { ctx.getSystemService(TelecomManager::class.java).silenceRinger() }
            }
        }
        Spacer(Modifier.height(26.dp))
        SwipeToAnswer(haze, onAnswer = CallManager::answer, onDecline = CallManager::hangup)
        Spacer(Modifier.height(10.dp))
        Text("Swipe right to answer, left to decline", fontSize = 12.sp, color = g.text2)
        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun PulsingAvatar(name: String?) {
    val color = name?.let(::avatarColor) ?: LocalGlass.current.text2
    val p by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = LinearOutSlowInEasing)), label = "p"
    )
    Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
        listOf(p, (p + 0.5f) % 1f).forEach { v ->
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer {
                        scaleX = 1f + v * 0.7f; scaleY = 1f + v * 0.7f; alpha = 0.5f * (1f - v)
                    }
                    .clip(CircleShape).background(color)
            )
        }
        Avatar(name, 110.dp)
    }
}

@Composable
private fun SwipeToAnswer(haze: HazeState, onAnswer: () -> Unit, onDecline: () -> Unit) {
    val g = LocalGlass.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offset = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    val wiggle by rememberInfiniteTransition(label = "w").animateFloat(
        -1f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wv"
    )
    val knob = 60.dp

    BoxWithConstraints(Modifier.fillMaxWidth().height(76.dp).glass(haze, Pill, g).padding(8.dp)) {
        val range = with(density) { ((maxWidth - knob) / 2).toPx() }
        val frac = (offset.value / range).coerceIn(-1f, 1f)
        Icon(Icons.Rounded.CallEnd, null, tint = EndRed, modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp))
        Icon(Icons.Rounded.Call, null, tint = CallGreen, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
        val knobColor = when {
            frac > 0f -> lerp(g.keyPressed, CallGreen, frac)
            frac < 0f -> lerp(g.keyPressed, EndRed, -frac)
            else -> g.keyPressed
        }
        Box(
            Modifier.align(Alignment.Center)
                .offset {
                    val idle = if (dragging || offset.isRunning) 0f else wiggle * with(density) { 6.dp.toPx() }
                    IntOffset((offset.value + idle).roundToInt(), 0)
                }
                .size(knob).clip(CircleShape).background(knobColor).border(0.5.dp, g.border, CircleShape)
                .pointerInput(range) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            scope.launch {
                                when {
                                    offset.value > range * 0.7f -> { offset.animateTo(range); onAnswer() }
                                    offset.value < -range * 0.7f -> { offset.animateTo(-range); onDecline() }
                                    else -> offset.animateTo(0f, spring(dampingRatio = 0.5f))
                                }
                            }
                        },
                        onDragCancel = { dragging = false; scope.launch { offset.animateTo(0f) } },
                    ) { _, delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-range, range)) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Call, "Swipe right to answer, left to decline",
                tint = if (abs(frac) > 0.3f) Color.White else g.text,
            )
        }
    }
}

@Composable
private fun Ongoing(haze: HazeState, name: String?, number: String, state: Int, audio: CallAudioState?) {
    val g = LocalGlass.current
    val ctx = LocalContext.current
    var keypad by remember { mutableStateOf(false) }
    var dtmf by remember { mutableStateOf("") }
    val muted = audio?.isMuted == true
    val speaker = audio?.route == CallAudioState.ROUTE_SPEAKER
    val held = state == Call.STATE_HOLDING
    val rec by CallRecorder.status.collectAsState()
    var autoStarted by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var silentTicks by remember { mutableIntStateOf(0) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    LaunchedEffect(state) {
        if (state == Call.STATE_ACTIVE && !autoStarted && !rec.recording &&
            CallRecorder.autoRecord(ctx)
        ) {
            autoStarted = true
            CallRecorder.start(ctx, number)
        }
    }
    LaunchedEffect(rec.recording) {
        silentTicks = 0
        while (CallRecorder.status.value.recording) {
            val amplitude = CallRecorder.amplitude()
            level = (amplitude / 12000f).coerceIn(0f, 1f)
            silentTicks = if (amplitude < 200) silentTicks + 1 else 0
            delay(250)
        }
        level = 0f
    }

    val connect = CallManager.call.value?.details?.connectTimeMillis ?: 0L
    val callStatus = when (state) {
        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW -> "Calling…"
        Call.STATE_HOLDING -> "On hold"
        Call.STATE_ACTIVE -> if (connect > 0) formatDuration((now - connect) / 1000) else "00:00"
        else -> ""
    }
    val topScale by animateFloatAsState(
        if (keypad) 0.8f else 1f,
        spring(dampingRatio = 0.7f),
        label = "top",
    )

    Column(Modifier.fillMaxSize().systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.graphicsLayer {
                scaleX = topScale
                scaleY = topScale
                translationY = (topScale - 1f) * 300f
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            Avatar(name, 96.dp)
            Spacer(Modifier.height(16.dp))
            Text(name ?: number.ifBlank { "Unknown" }, fontSize = 28.sp)
            if (name != null) Text(number, fontSize = 14.sp, color = g.text2)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.glass(haze, Pill, g).padding(horizontal = 14.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = if (held) TagOrange else CallGreen
                Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(callStatus, fontSize = 14.sp, color = color)
            }

            AnimatedVisibility(rec.recording) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.glass(haze, Pill, g).padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(EndRed))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "REC " + formatDuration(((now - rec.startedAt) / 1000).coerceAtLeast(0)),
                            fontSize = 13.sp,
                            color = EndRed,
                        )
                        Spacer(Modifier.width(10.dp))
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf(0.35f, 0.7f, 1f, 0.6f).forEach { factor ->
                                Box(
                                    Modifier.width(3.dp).height((4 + 12 * level * factor).dp)
                                        .clip(Pill).background(g.text2),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (rec.mode == CallRecorder.Mode.CALL_AUDIO) "Call audio" else "Mic",
                            fontSize = 12.sp,
                            color = g.text2,
                        )
                    }
                    if (silentTicks > 20) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "No sound detected. Your phone may be blocking call recording.",
                            fontSize = 12.sp,
                            color = EndRed,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp), contentAlignment = Alignment.BottomCenter) {
            OverlayVisibility(
                !keypad,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Control(Icons.Rounded.MicOff, "Mute", muted) { CallManager.mute(!muted) }
                        Control(Icons.Rounded.VolumeUp, "Speaker", speaker) { CallManager.speaker(!speaker) }
                        Control(
                            Icons.Rounded.FiberManualRecord,
                            if (rec.recording) "Stop rec" else "Record",
                            rec.recording,
                        ) {
                            if (rec.recording) CallRecorder.stop(ctx)
                            else CallRecorder.start(ctx, number)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Control(Icons.Rounded.Dialpad, "Keypad", false) { keypad = true }
                        Control(Icons.Rounded.Pause, "Hold", held) { CallManager.hold(!held) }
                    }
                }
            }
            OverlayVisibility(
                keypad,
                enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = 400f)) { it / 3 } + fadeIn(),
                exit = slideOutVertically { it / 3 } + fadeOut(),
            ) {
                Column(Modifier.fillMaxWidth().glass(haze, RoundedCornerShape(30.dp), g).padding(12.dp)) {
                    Text(
                        dtmf.takeLast(14), fontSize = 24.sp, letterSpacing = 2.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().height(34.dp),
                    )
                    DIAL_KEYS.chunked(3).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { (digit, letters) ->
                                PillKey(Modifier.weight(1f), height = 50.dp, onClick = {
                                    dtmf += digit
                                    CallManager.dtmf(digit[0])
                                }) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(digit, fontSize = 24.sp, lineHeight = 26.sp)
                                        Text(letters, fontSize = 9.sp, lineHeight = 11.sp, color = g.text2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                if (keypad) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).clickable { keypad = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "Hide keypad", tint = g.text2)
                    }
                }
            }
            Spacer(Modifier.width(18.dp))
            Box(
                Modifier.size(132.dp, 60.dp).clip(Pill).background(EndRed).clickable { CallManager.hangup() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CallEnd, "End call", Modifier.size(28.dp), tint = Color.White)
            }
            Spacer(Modifier.width(18.dp))
            Spacer(Modifier.size(52.dp))
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Control(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val g = LocalGlass.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PillKey(
            Modifier.size(68.dp), height = 68.dp,
            color = if (on) g.toggleOn else null, onClick = onClick,
        ) { Icon(icon, label, tint = if (on) g.toggleOnText else g.text) }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, color = g.text2)
    }
}

@Composable
private fun GlassChip(haze: HazeState, icon: ImageVector, label: String, onClick: () -> Unit) {
    val g = LocalGlass.current
    Row(
        Modifier.glass(haze, Pill, g).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = g.text)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp)
    }
}

/** Shown on dual-SIM phones set to "ask every time". */
@SuppressLint("MissingPermission")
@Composable
private fun SimPicker(haze: HazeState, target: String) {
    val g = LocalGlass.current
    val ctx = LocalContext.current
    val accounts = remember {
        val tm = ctx.getSystemService(TelecomManager::class.java)
        runCatching {
            tm.callCapablePhoneAccounts.map { it to (tm.getPhoneAccount(it)?.label?.toString() ?: "SIM") }
        }.getOrDefault(emptyList())
    }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Call $target with", fontSize = 18.sp)
        Spacer(Modifier.height(20.dp))
        accounts.forEachIndexed { i, (handle, label) ->
            Row(
                Modifier.fillMaxWidth().glass(haze, RoundedCornerShape(22.dp), g)
                    .clickable { CallManager.call.value?.phoneAccountSelected(handle, false) }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.SimCard, null, tint = CallGreen)
                Spacer(Modifier.width(12.dp))
                Text("SIM ${i + 1} · $label", fontSize = 16.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Cancel", color = EndRed, fontSize = 15.sp,
            modifier = Modifier.clip(Pill).clickable { CallManager.hangup() }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun OverlayVisibility(
    visible: Boolean,
    enter: EnterTransition,
    exit: ExitTransition,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit,
        content = content,
    )
}
