package com.glassdialer.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassdialer.data.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun MainApp(
    repo: PhoneRepository,
    refresh: Int,
    initialNumber: String,
    isDefault: Boolean,
    onMakeDefault: () -> Unit,
) {
    val g = LocalGlass.current
    val ctx = LocalContext.current
    val haze = remember { HazeState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var number by rememberSaveable(initialNumber) { mutableStateOf(initialNumber) }
    var sheetOpen by rememberSaveable { mutableStateOf(true) }
    var calls by remember { mutableStateOf(emptyList<CallEntry>()) }
    var contacts by remember { mutableStateOf(emptyList<Contact>()) }
    var tick by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { tick++ }
        }
        runCatching { ctx.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer) }
        onDispose { ctx.contentResolver.unregisterContentObserver(observer) }
    }
    LaunchedEffect(refresh, tick) {
        calls = repo.calls()
        contacts = repo.contacts()
    }

    Box(Modifier.fillMaxSize().background(g.bg)) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            AnimatedBlobs()
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                if (!isDefault) DefaultBanner(onMakeDefault)
                when (tab) {
                    0 -> CallsPage(
                        calls = calls, contacts = contacts, query = number, sheetOpen = sheetOpen,
                        onSearch = { tab = 1 }, onCall = { placeCall(ctx, it) },
                    )
                    1 -> ContactsPage(contacts, onCall = { placeCall(ctx, it) })
                    else -> RecordingsPage(repo)
                }
                }
            }
        }

        if (tab == 0) {
            AnimatedVisibility(
                visible = sheetOpen,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = 300f)) { it },
                exit = slideOutVertically(tween(250)) { it },
            ) {
                KeypadSheet(
                    haze = haze,
                    number = number,
                    matchCount = if (number.isEmpty()) 0 else
                        contacts.count { T9.matches(number, it.name, it.number) },
                    onNumber = { number = it },
                    onHide = { sheetOpen = false },
                    onCall = {
                        if (number.isEmpty()) {
                            number = calls.firstOrNull { it.type == CallLog.Calls.OUTGOING_TYPE }
                                ?.number?.filter { it.isDigit() || it in "+*#" }.orEmpty()
                        } else placeCall(ctx, number)
                    },
                )
            }
            AnimatedVisibility(
                visible = !sheetOpen,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                    .padding(end = 22.dp, bottom = 96.dp),
                enter = scaleIn(), exit = scaleOut(),
            ) {
                Box(
                    Modifier.size(60.dp).glass(haze, CircleShape, g).clickable { sheetOpen = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Dialpad, "Show keypad", tint = CallGreen) }
            }
        }

        BottomNav(haze, tab, Modifier.align(Alignment.BottomCenter)) { tab = it }
    }
}

@Composable
private fun DefaultBanner(onClick: () -> Unit) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
            .clip(shape).background(g.key).border(0.5.dp, g.border, shape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Make this your default phone app to handle calls",
            fontSize = 13.sp, color = g.text2, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Set default", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(Pill).background(CallGreen).clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CallsPage(
    calls: List<CallEntry>,
    contacts: List<Contact>,
    query: String,
    sheetOpen: Boolean,
    onSearch: () -> Unit,
    onCall: (String) -> Unit,
) {
    val g = LocalGlass.current
    var missedOnly by rememberSaveable { mutableStateOf(false) }
    val shownCalls = remember(calls, query, missedOnly) {
        calls.filter {
            (!missedOnly || it.type == CallLog.Calls.MISSED_TYPE || it.type == CallLog.Calls.REJECTED_TYPE) &&
                T9.matches(query, it.name, it.number)
        }
    }
    val shownContacts = remember(contacts, query) {
        if (query.isEmpty()) emptyList()
        else contacts.filter { T9.matches(query, it.name, it.number) }.take(5)
    }

    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Calls", fontSize = 26.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Row(Modifier.clip(Pill).background(g.key).border(0.5.dp, g.border, Pill).padding(3.dp)) {
                FilterChip("All", !missedOnly) { missedOnly = false }
                FilterChip("Missed", missedOnly) { missedOnly = true }
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(g.key)
                    .border(0.5.dp, g.border, CircleShape).clickable(onClick = onSearch),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Search, "Search contacts", Modifier.size(20.dp)) }
        }

        LazyColumn(contentPadding = PaddingValues(bottom = if (sheetOpen) 480.dp else 130.dp)) {
            if (shownContacts.isNotEmpty()) {
                item { SectionLabel("Contacts") }
                items(shownContacts, key = { "c${it.id}" }) { c ->
                    ListRow(name = c.name, title = c.name, subtitle = c.number) { onCall(c.number) }
                }
                item { SectionLabel("Recent calls") }
            }
            if (shownCalls.isEmpty()) {
                item {
                    Text(
                        if (query.isEmpty()) "No calls yet" else "No matching calls",
                        color = g.text2, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    )
                }
            }
            items(shownCalls, key = { it.id }) { CallRow(it) { onCall(it.number) } }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val g = LocalGlass.current
    Text(
        label, fontSize = 14.sp, color = if (selected) g.text else g.text2,
        modifier = Modifier.clip(Pill).background(if (selected) g.chipOn else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = 12.sp, color = LocalGlass.current.text2,
        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun CallRow(c: CallEntry, onClick: () -> Unit) {
    val g = LocalGlass.current
    val missed = c.type == CallLog.Calls.MISSED_TYPE || c.type == CallLog.Calls.REJECTED_TYPE
    val (icon, tint) = when {
        c.type == CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Rounded.CallMade to g.text2
        missed -> Icons.AutoMirrored.Rounded.CallMissed to EndRed
        else -> Icons.AutoMirrored.Rounded.CallReceived to g.text2
    }
    Column(Modifier.clickable(onClick = onClick).padding(horizontal = 18.dp)) {
        Row(Modifier.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.name)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    c.name ?: c.number.ifBlank { "Unknown" }, fontSize = 16.sp,
                    color = if (missed) EndRed else g.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(14.dp), tint = tint)
                    Spacer(Modifier.width(4.dp))
                    Text(c.location ?: if (c.name != null) c.number else "Phone", fontSize = 12.sp, color = g.text2, maxLines = 1)
                }
            }
            Text(formatTime(c.date), fontSize = 12.sp, color = g.text2)
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Outlined.Info, "Details", Modifier.size(20.dp), tint = g.text2)
        }
        Box(Modifier.fillMaxWidth().padding(start = 58.dp).height(0.5.dp).background(g.line))
    }
}

@Composable
fun ListRow(name: String?, title: String, subtitle: String, onClick: () -> Unit) {
    val g = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name, 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 13.sp, color = g.text2)
        }
        Icon(Icons.Rounded.Call, "Call", Modifier.size(20.dp), tint = g.text2)
    }
}

@Composable
private fun KeypadSheet(
    haze: HazeState,
    number: String,
    matchCount: Int,
    onNumber: (String) -> Unit,
    onHide: () -> Unit,
    onCall: () -> Unit,
) {
    val g = LocalGlass.current
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    Column(
        Modifier.fillMaxWidth().glass(haze, shape, g).navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 84.dp),
    ) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp).size(40.dp, 4.dp)
                .clip(CircleShape).background(g.text2.copy(alpha = 0.4f)).clickable(onClick = onHide)
        )
        AnimatedVisibility(number.isNotEmpty()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(number, fontSize = 32.sp, maxLines = 1, letterSpacing = 1.sp)
                Text(
                    if (matchCount > 0) "$matchCount matching contact${if (matchCount > 1) "s" else ""}"
                    else "Add to contacts",
                    fontSize = 12.sp, color = CallGreen,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        DIAL_KEYS.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { (digit, letters) ->
                    PillKey(
                        Modifier.weight(1f),
                        onLongClick = if (digit == "0") ({ onNumber(number + "+") }) else null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (number.length < 20) onNumber(number + digit)
                        },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(digit, fontSize = 26.sp, lineHeight = 28.sp)
                            if (digit == "1") {
                                Icon(Icons.Rounded.Voicemail, null, Modifier.size(12.dp), tint = g.text2)
                            } else {
                                Text(letters, fontSize = 10.sp, lineHeight = 12.sp, color = g.text2, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillKey(Modifier.weight(1f), onClick = onHide) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Hide keypad", tint = g.text2)
            }
            PillKey(Modifier.weight(1f), color = CallGreen, onClick = onCall) {
                Icon(Icons.Rounded.Call, "Call", tint = Color.White)
            }
            PillKey(
                Modifier.weight(1f),
                onLongClick = { onNumber("") },
                onClick = { onNumber(number.dropLast(1)) },
            ) { Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete", tint = g.text2) }
        }
    }
}

@Composable
private fun BottomNav(haze: HazeState, tab: Int, modifier: Modifier, onTab: (Int) -> Unit) {
    val g = LocalGlass.current
    Row(
        modifier.navigationBarsPadding().padding(bottom = 14.dp)
            .glass(haze, Pill, g).padding(4.dp),
    ) {
        NavItem(Icons.Rounded.Call, "Calls", tab == 0) { onTab(0) }
        NavItem(Icons.Rounded.People, "Contacts", tab == 1) { onTab(1) }
        NavItem(Icons.Rounded.Mic, "Recordings", tab == 2) { onTab(2) }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val g = LocalGlass.current
    val color = if (selected) g.text else g.text2
    Column(
        Modifier.clip(Pill).background(if (selected) g.chipOn else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = color)
        Text(label, fontSize = 11.sp, color = color)
    }
}