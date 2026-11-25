package com.glassdialer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

val Pill = RoundedCornerShape(percent = 50)

val DIAL_KEYS = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to "",
)

/** Slowly drifting color background that the glass blurs. */
@Composable
fun AnimatedBlobs(modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    val t = rememberInfiniteTransition(label = "blobs")
    val a by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(14000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a"
    )
    val b by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(17000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b"
    )
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        fun blob(c: Color, x: Float, y: Float, r: Float) {
            val center = Offset(x, y)
            drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = g.blobAlpha), Color.Transparent), center, r),
                r, center,
            )
        }
        blob(Color(0xFF7B5CFF), w * (0.1f + 0.25f * a), h * (0.55f - 0.08f * a), w * 0.75f)
        blob(Color(0xFF12C2B0), w * (0.95f - 0.3f * b), h * (0.68f + 0.05f * b), w * 0.7f)
        blob(Color(0xFFFF8A4C), w * (0.4f + 0.2f * b), h * (0.88f - 0.06f * a), w * 0.6f)
        blob(Color(0xFF4C9DFF), w * (0.9f - 0.1f * a), h * 0.08f, w * 0.5f)
    }
}

/** Frosted glass surface. Real blur on Android 12+, translucent fallback below. */
fun Modifier.glass(state: HazeState, shape: Shape, g: GlassColors): Modifier = this
    .clip(shape)
    .hazeEffect(state) {
        backgroundColor = g.bg
        tints = listOf(HazeTint(g.glass))
        blurRadius = 28.dp
        noiseFactor = 0f
    }
    .border(0.5.dp, g.border, shape)

fun avatarColor(seed: String): Color {
    val palette = listOf(
        Color(0xFF6B5BD6), Color(0xFF1F9E8A), Color(0xFFD0702F),
        Color(0xFFC2457A), Color(0xFF3B82C4), Color(0xFF5F8F2A),
    )
    return palette[(seed.hashCode() and 0x7fffffff) % palette.size]
}

fun initials(n: String) =
    n.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

@Composable
fun Avatar(name: String?, size: Dp = 44.dp) {
    val g = LocalGlass.current
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (name != null) avatarColor(name) else g.avatarBg),
        contentAlignment = Alignment.Center,
    ) {
        if (name != null) {
            Text(initials(name), color = Color.White, fontSize = (size.value / 2.9f).sp, fontWeight = FontWeight.Medium)
        } else {
            Icon(Icons.Rounded.Person, null, tint = g.text2, modifier = Modifier.size(size * 0.55f))
        }
    }
}

/** Pill-shaped glass key with a springy press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PillKey(
    modifier: Modifier = Modifier,
    height: Dp = 54.dp,
    color: Color? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val g = LocalGlass.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "press"
    )
    Box(
        modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(Pill)
            .background(color ?: if (pressed) g.keyPressed else g.key)
            .border(0.5.dp, g.border, Pill)
            .combinedClickable(
                interactionSource = src, indication = null,
                onLongClick = onLongClick, onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
