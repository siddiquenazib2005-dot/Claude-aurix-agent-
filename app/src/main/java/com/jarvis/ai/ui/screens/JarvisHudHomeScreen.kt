package com.jarvis.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.ai.missions.MissionProgress
import com.jarvis.ai.ui.components.GlowBackground
import com.jarvis.ai.ui.components.JarvisOrb
import com.jarvis.ai.viewmodel.JarvisViewModel

private val HudInk = Color(0xFF060607)
private val HudPanel = Color(0xE6160F12)
private val HudStroke = Color(0xFF4E1D27)
private val HudRed = Color(0xFFFF1744)
private val HudOrange = Color(0xFFFF6B00)
private val HudGreen = Color(0xFF28D17C)
private val HudPurple = Color(0xFF9C4DFF)
private val HudText = Color(0xFFF7F3F4)
private val HudMuted = Color(0xFFA89FA2)

/**
 * Single canonical assistant phase for the HUD. Priority is deliberate:
 * WORKING (device action) outranks everything so the deck never reports
 * READY mid-automation — the exact masking bug the old boolean-soup UI had
 * (speaking hid an in-flight action).
 */
enum class HudPhase { IDLE, LISTENING, THINKING, SPEAKING, WORKING }

/** Pure phase derivation — unit-tested on the JVM (see JarvisHudTest). */
internal fun hudPhase(
    isActing: Boolean,
    isLoading: Boolean,
    isSpeaking: Boolean,
    isListening: Boolean,
    handsFree: Boolean
): HudPhase = when {
    isActing -> HudPhase.WORKING
    isLoading -> HudPhase.THINKING
    isSpeaking -> HudPhase.SPEAKING
    isListening || handsFree -> HudPhase.LISTENING
    else -> HudPhase.IDLE
}

/** Pure status line for a phase — unit-tested on the JVM. */
internal fun hudStatusLine(phase: HudPhase): String = when (phase) {
    HudPhase.WORKING -> "Working on it…"
    HudPhase.THINKING -> "Thinking…"
    HudPhase.SPEAKING -> "Speaking…"
    HudPhase.LISTENING -> "Listening…"
    HudPhase.IDLE -> "How can I assist you today?"
}

private fun HudPhase.accent(): Color = when (this) {
    HudPhase.WORKING -> HudOrange
    HudPhase.THINKING -> HudRed
    HudPhase.SPEAKING -> HudPurple
    HudPhase.LISTENING -> HudGreen
    HudPhase.IDLE -> HudMuted
}

private data class HudSystem(
    val icon: String,
    val title: String,
    val status: String,
    val live: Boolean,
    val accent: Color,
    val onTap: () -> Unit
)

private data class HudChip(val label: String, val command: String)

private val HUD_CHIPS = listOf(
    HudChip("Flashlight", "flashlight on"),
    HudChip("Battery", "battery status"),
    HudChip("Read screen", "what's on my screen"),
    HudChip("Notifications", "read my notifications"),
    HudChip("Camera", "open camera"),
    HudChip("Missions", "list missions")
)

/**
 * Unique JARVIS command deck (Phase-2).
 *
 * Not a copy of any reference mock: a vertical reactor-dial layout in the
 * existing red/black identity — wordmark header, one canonical phase badge,
 * central orb with a CORE readout, a live-systems grid where every status
 * string is derived from real ViewModel/mission state (never faked), quick
 * chips and a mic dock. Execution stays in JarvisViewModel; this screen only
 * renders and forwards intent.
 */
@Composable
fun JarvisHudHomeScreen(
    viewModel: JarvisViewModel,
    missionProgress: MissionProgress,
    savedMissionCount: Int,
    onOpenChat: () -> Unit,
    onOpenMissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onCommand: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val orbLevel by viewModel.orbLevel.collectAsState(initial = 0f)
    val handsFree by viewModel.handsFreeActive.collectAsState()
    val wakeArmed by viewModel.wakeWordEnabled.collectAsState()

    val phase = hudPhase(
        isActing = state.isActing,
        isLoading = state.isLoading,
        isSpeaking = state.isSpeaking,
        isListening = state.isListening,
        handsFree = handsFree
    )
    val accent = phase.accent()
    val active = phase != HudPhase.IDLE

    val systems = listOf(
        HudSystem(
            icon = "◉",
            title = "VOICE",
            status = when {
                handsFree -> "Hands-free live"
                wakeArmed -> "Wake-word armed"
                else -> "Tap mic to talk"
            },
            live = handsFree,
            accent = HudGreen,
            onTap = { viewModel.toggleHandsFreeMode() }
        ),
        HudSystem(
            icon = "⬢",
            title = "BRAIN",
            status = if (state.backendOnline) {
                state.activeProvider.takeIf { it.isNotBlank() }?.let { "Online · $it" } ?: "Online"
            } else {
                "On-device"
            },
            live = state.backendOnline,
            accent = HudRed,
            onTap = onOpenChat
        ),
        HudSystem(
            icon = "⬣",
            title = "GUARD",
            status = if (state.hasPendingConfirmation) "CONFIRM WAITING" else "Gates armed",
            live = state.hasPendingConfirmation,
            accent = HudOrange,
            onTap = onOpenChat
        ),
        HudSystem(
            icon = "⬔",
            title = "MISSIONS",
            status = if (missionProgress.isActive) {
                "${missionProgress.completedSteps}/${missionProgress.totalSteps} running"
            } else {
                "$savedMissionCount saved"
            },
            live = missionProgress.isActive,
            accent = HudPurple,
            onTap = onOpenMissions
        ),
        HudSystem(
            icon = "⬒",
            title = "VISION",
            status = if (state.isActing) "Acting on screen" else "OCR + control ready",
            live = state.isActing,
            accent = HudOrange,
            onTap = { onCommand("what's on my screen") }
        ),
        HudSystem(
            icon = "⬓",
            title = "CORE",
            status = state.latency.totalMs?.let { "Last reply ${it}ms" } ?: "Idle",
            live = state.isLoading,
            accent = HudRed,
            onTap = onOpenChat
        )
    )

    Box(modifier = Modifier.fillMaxSize().background(HudInk)) {
        GlowBackground(modifier = Modifier.fillMaxSize(), level = orbLevel)

        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("JARVIS", color = HudText, fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Text(
                            "COMMAND DECK",
                            color = HudRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp
                        )
                    }
                    HudPhaseBadge(phase = phase, accent = accent)
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(HudPanel)
                        .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(26.dp))
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "CORE · ${phase.name}",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    JarvisOrb(size = 168.dp, active = active, level = orbLevel)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        hudStatusLine(phase),
                        color = HudText,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    state.notice?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = HudOrange, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.horizontalGradient(listOf(HudRed, HudOrange)))
                            .clickable(onClick = onOpenChat)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "ASK JARVIS",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            item {
                Column {
                    Text(
                        "LIVE SYSTEMS",
                        color = HudText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text("Real status — never faked", color = HudMuted, fontSize = 11.5.sp)
                }
            }

            systems.chunked(2).forEach { pair ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { system ->
                            HudSystemCard(system, Modifier.weight(1f))
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(HUD_CHIPS) { chip ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(HudPanel)
                                .border(1.dp, HudStroke, RoundedCornerShape(20.dp))
                                .clickable { onCommand(chip.command) }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(chip.label, color = HudText, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        HudDock(
            handsFree = handsFree,
            onChat = onOpenChat,
            onMic = { viewModel.toggleHandsFreeMode() },
            onMissions = onOpenMissions,
            onSettings = onOpenSettings,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun HudPhaseBadge(phase: HudPhase, accent: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(HudPanel)
            .border(1.dp, accent, RoundedCornerShape(20.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.size(7.dp))
        Text(phase.name, color = HudText, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun HudSystemCard(system: HudSystem, modifier: Modifier) {
    Column(
        modifier = modifier
            .height(118.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(HudPanel)
            .border(1.dp, system.accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .clickable(onClick = system.onTap)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(system.icon, color = system.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(8.dp))
            Text(system.title, color = HudText, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(6.dp).clip(CircleShape)
                    .background(if (system.live) HudGreen else HudMuted)
            )
            Spacer(Modifier.size(7.dp))
            Text(system.status, color = HudMuted, fontSize = 11.5.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun HudDock(
    handsFree: Boolean,
    onChat: () -> Unit,
    onMic: () -> Unit,
    onMissions: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xF20B0809))
            .border(1.dp, HudStroke)
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HudDockEntry("⌂", "Home", selected = true, onClick = {})
        HudDockEntry("◉", "Chat", selected = false, onClick = onChat)
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(HudOrange, HudRed)))
                .border(
                    2.dp,
                    Color.White.copy(alpha = if (handsFree) 0.45f else 0.18f),
                    CircleShape
                )
                .clickable(onClick = onMic),
            contentAlignment = Alignment.Center
        ) {
            Text(if (handsFree) "■" else "🎙", fontSize = 22.sp, color = Color.White)
        }
        HudDockEntry("⚡", "Runs", selected = false, onClick = onMissions)
        HudDockEntry("⚙", "Setup", selected = false, onClick = onSettings)
    }
}

@Composable
private fun HudDockEntry(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, color = if (selected) HudRed else HudMuted, fontSize = 20.sp)
        Text(label, color = if (selected) HudRed else HudMuted, fontSize = 10.sp)
    }
}
