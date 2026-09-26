package eu.zipf.zeno.monstermayor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.zipf.zeno.monstermayor.core.cards.CardType
import eu.zipf.zeno.monstermayor.core.model.BoardLocation
import eu.zipf.zeno.monstermayor.core.model.CharacterId
import eu.zipf.zeno.monstermayor.core.model.DiceFace
import eu.zipf.zeno.monstermayor.core.presentation.MarketCardUi
import eu.zipf.zeno.monstermayor.core.presentation.PlayerUi
import eu.zipf.zeno.monstermayor.ui.theme.GameColors
import eu.zipf.zeno.monstermayor.ui.theme.color

/** A round monster token: body in the monster's colour, two eyes and a toothy grin. */
@Composable
fun MonsterToken(character: CharacterId, modifier: Modifier = Modifier, size: Dp = 40.dp, dimmed: Boolean = false) {
    Canvas(
        modifier
            .size(size)
            .alpha(if (dimmed) 0.35f else 1f)
            .semantics { contentDescription = character.displayName },
    ) {
        val r = this.size.minDimension / 2
        drawCircle(character.color)
        drawCircle(Color.Black.copy(alpha = 0.25f), radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.1f))
        val eyeY = center.y - r * 0.15f
        listOf(-1, 1).forEach { side ->
            val eye = Offset(center.x + side * r * 0.35f, eyeY)
            drawCircle(Color.White, radius = r * 0.22f, center = eye)
            drawCircle(GameColors.Night, radius = r * 0.1f, center = eye + Offset(side * r * 0.04f, r * 0.03f))
        }
        val mouthY = center.y + r * 0.38f
        drawLine(GameColors.Night, Offset(center.x - r * 0.45f, mouthY), Offset(center.x + r * 0.45f, mouthY), strokeWidth = r * 0.12f)
        listOf(-0.3f, 0f, 0.3f).forEach { dx ->
            drawLine(Color.White, Offset(center.x + dx * r, mouthY), Offset(center.x + dx * r, mouthY + r * 0.16f), strokeWidth = r * 0.12f)
        }
    }
}

@Composable
fun PlayerPanel(player: PlayerUi, victoryPointsToWin: Int, modifier: Modifier = Modifier) {
    val border = when {
        player.isActing -> BorderStroke(2.dp, GameColors.Neon)
        player.isCurrentTurn -> BorderStroke(2.dp, GameColors.Neon.copy(alpha = 0.5f))
        else -> null
    }
    Card(
        modifier = modifier
            .width(150.dp)
            .alpha(if (player.isAlive) 1f else 0.45f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = border,
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonsterToken(player.character, size = 30.dp, dimmed = !player.isAlive)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(player.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = when {
                            !player.isAlive -> "Destroyed"
                            player.location == BoardLocation.TOKYO_CITY -> "In Tokyo City"
                            player.location == BoardLocation.TOKYO_BAY -> "In Tokyo Bay"
                            player.isBot -> "CPU"
                            player.isLocal -> "You"
                            else -> player.character.displayName
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (player.inTokyo) GameColors.Neon else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StatRow(DiceFace.HEAL, "${player.health}/${player.maxHealth}")
            LinearProgressIndicator(
                progress = { player.health / player.maxHealth.toFloat() },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = GameColors.Health,
                trackColor = GameColors.Night,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VictoryStar()
                    Text(" ${player.victoryPoints}/$victoryPointsToWin", style = MaterialTheme.typography.bodyMedium)
                }
                StatRow(DiceFace.ENERGY, player.energy.toString())
            }
            if (player.cards.isNotEmpty()) {
                Text(
                    player.cards.joinToString { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatRow(icon: DiceFace, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatIcon(icon)
        Text(" $value", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun TokyoBoard(city: PlayerUi?, bay: PlayerUi?, bayEnabled: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TokyoSlot("Tokyo City", city, enabled = true, Modifier.weight(1f))
        TokyoSlot("Tokyo Bay", bay, enabled = bayEnabled, Modifier.weight(1f))
    }
}

@Composable
private fun TokyoSlot(title: String, occupant: PlayerUi?, enabled: Boolean, modifier: Modifier) {
    Card(
        modifier = modifier.alpha(if (enabled) 1f else 0.35f),
        colors = CardDefaults.cardColors(containerColor = GameColors.Panel),
        border = BorderStroke(1.dp, if (occupant != null) GameColors.Neon else Color.Transparent),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                if (occupant != null) MonsterToken(occupant.character, size = 36.dp)
                else Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, color = GameColors.Neon)
                Text(
                    text = when {
                        !enabled -> "5+ monsters only"
                        occupant == null -> "Empty"
                        else -> occupant.name
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun MarketCard(card: MarketCardUi, showBuy: Boolean, onBuy: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(150.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GameColors.Panel),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.card.name,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                StatIcon(DiceFace.ENERGY)
                Text(
                    card.card.cost.toString(),
                    fontWeight = FontWeight.Black,
                    color = if (card.affordable) GameColors.Energy else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (card.card.type == CardType.KEEP) "KEEP" else "DISCARD",
                style = MaterialTheme.typography.labelSmall,
                color = if (card.card.type == CardType.KEEP) GameColors.Held else GameColors.Attack,
            )
            Text(
                card.card.description,
                style = MaterialTheme.typography.bodySmall,
                minLines = 3,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (showBuy) {
                Button(onClick = onBuy, enabled = card.canBuy, modifier = Modifier.fillMaxWidth()) {
                    Text("Buy", textAlign = TextAlign.Center)
                }
            }
        }
    }
}
