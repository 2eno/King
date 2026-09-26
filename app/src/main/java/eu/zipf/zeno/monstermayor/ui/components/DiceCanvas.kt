package eu.zipf.zeno.monstermayor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.zipf.zeno.monstermayor.core.model.DiceFace
import eu.zipf.zeno.monstermayor.core.presentation.DieUi
import eu.zipf.zeno.monstermayor.ui.theme.GameColors
import kotlin.random.Random

/**
 * The dice tray. Dice that were not held tumble whenever [rollSequence] changes, cycling through
 * random faces before settling on the host's result.
 */
@Composable
fun DiceTray(
    dice: List<DieUi>,
    slots: Int,
    rollSequence: Int,
    canToggle: Boolean,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dieSize: Dp = 48.dp,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (dice.isEmpty()) {
            repeat(slots) { DieView(face = null, held = false, rollSequence = 0, size = dieSize, onClick = null) }
        } else {
            dice.forEach { die ->
                DieView(
                    face = die.face,
                    held = die.held,
                    rollSequence = rollSequence,
                    size = dieSize,
                    onClick = if (canToggle) ({ onToggle(die.index) }) else null,
                )
            }
        }
    }
}

@Composable
fun DieView(face: DiceFace?, held: Boolean, rollSequence: Int, size: Dp, onClick: (() -> Unit)?) {
    val spin = remember { Animatable(0f) }
    var shownFace by remember { mutableStateOf(face) }
    val lift by animateDpAsState(if (held) (-8).dp else 0.dp, label = "die-lift")

    LaunchedEffect(rollSequence, face) {
        if (face == null || held || rollSequence == 0) {
            shownFace = face
            return@LaunchedEffect
        }
        spin.snapTo(0f)
        repeat(TUMBLE_FRAMES) { frame ->
            shownFace = DiceFace.entries[Random.nextInt(DiceFace.entries.size)]
            spin.animateTo((frame + 1) * 360f / TUMBLE_FRAMES, tween(TUMBLE_FRAME_MS, easing = FastOutSlowInEasing))
        }
        shownFace = face
        spin.snapTo(0f)
    }

    val description = when {
        face == null -> "Die not rolled yet"
        held -> "${face.label}, held"
        else -> face.label
    }
    Box(
        modifier = Modifier
            .offset(y = lift)
            .size(size)
            .graphicsLayer { rotationZ = spin.value }
            .clip(RoundedCornerShape(size * 0.2f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
    ) {
        DieFaceCanvas(shownFace, held, Modifier.size(size))
    }
}

@Composable
fun DieFaceCanvas(face: DiceFace?, held: Boolean, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val corner = CornerRadius(size.minDimension * 0.2f)
        drawRoundRect(color = GameColors.DieBody, cornerRadius = corner)
        drawRoundRect(
            color = if (held) GameColors.Held else GameColors.DieEdge,
            cornerRadius = corner,
            style = Stroke(width = size.minDimension * if (held) 0.09f else 0.05f),
        )
        when (face) {
            null -> drawCircle(GameColors.DieEdge, radius = size.minDimension * 0.08f)
            DiceFace.ONE, DiceFace.TWO, DiceFace.THREE -> {
                val layout = measurer.measure(
                    text = face.numberValue.toString(),
                    style = TextStyle(color = Color.White, fontSize = (size.minDimension * 0.42f).toSp(), fontWeight = FontWeight.Black),
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset((size.width - layout.size.width) / 2f, (size.height - layout.size.height) / 2f),
                )
            }
            DiceFace.ATTACK -> drawClaw(GameColors.Attack)
            DiceFace.HEAL -> drawHeart(GameColors.Health)
            DiceFace.ENERGY -> drawBolt(GameColors.Energy)
        }
    }
}

private fun DrawScope.drawClaw(color: Color) {
    val w = size.width
    val h = size.height
    rotate(degrees = -20f) {
        for (i in -1..1) {
            val x = w / 2 + i * w * 0.17f
            val path = Path().apply {
                moveTo(x - w * 0.02f, h * 0.24f)
                cubicTo(x + w * 0.08f, h * 0.38f, x + w * 0.06f, h * 0.60f, x - w * 0.04f, h * 0.76f)
            }
            drawPath(path, color, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawHeart(color: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.5f, h * 0.78f)
        cubicTo(w * 0.12f, h * 0.55f, w * 0.18f, h * 0.22f, w * 0.38f, h * 0.24f)
        cubicTo(w * 0.45f, h * 0.25f, w * 0.49f, h * 0.30f, w * 0.5f, h * 0.35f)
        cubicTo(w * 0.51f, h * 0.30f, w * 0.55f, h * 0.25f, w * 0.62f, h * 0.24f)
        cubicTo(w * 0.82f, h * 0.22f, w * 0.88f, h * 0.55f, w * 0.5f, h * 0.78f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawBolt(color: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.58f, h * 0.16f)
        lineTo(w * 0.30f, h * 0.54f)
        lineTo(w * 0.48f, h * 0.54f)
        lineTo(w * 0.40f, h * 0.84f)
        lineTo(w * 0.70f, h * 0.44f)
        lineTo(w * 0.52f, h * 0.44f)
        close()
    }
    drawPath(path, color)
}

/** Small inline icons drawn with the same shapes as the dice (used in the HUD). */
@Composable
fun StatIcon(face: DiceFace, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier) {
        when (face) {
            DiceFace.ATTACK -> drawClaw(GameColors.Attack)
            DiceFace.HEAL -> drawHeart(GameColors.Health)
            DiceFace.ENERGY -> drawBolt(GameColors.Energy)
            else -> drawStar(GameColors.Victory)
        }
    }
}

fun DrawScope.drawStar(color: Color) {
    val center = Offset(size.width / 2, size.height / 2)
    val outer = size.minDimension * 0.46f
    val inner = outer * 0.45f
    val path = Path()
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) outer else inner
        val angle = Math.toRadians((-90 + i * 36).toDouble())
        val point = Offset(center.x + radius * kotlin.math.cos(angle).toFloat(), center.y + radius * kotlin.math.sin(angle).toFloat())
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, color)
}

@Composable
fun VictoryStar(modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier) { drawStar(GameColors.Victory) }
}

val DiceFace.label: String
    get() = when (this) {
        DiceFace.ONE -> "One"
        DiceFace.TWO -> "Two"
        DiceFace.THREE -> "Three"
        DiceFace.ATTACK -> "Attack"
        DiceFace.HEAL -> "Heal"
        DiceFace.ENERGY -> "Energy"
    }

private const val TUMBLE_FRAMES = 6
private const val TUMBLE_FRAME_MS = 70

