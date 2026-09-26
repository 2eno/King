package eu.zipf.zeno.monstermayor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.zipf.zeno.monstermayor.core.model.CharacterId
import eu.zipf.zeno.monstermayor.ui.theme.GameColors

/** Horizontal strip of monsters. Monsters in [taken] are shown dimmed and cannot be picked. */
@Composable
fun CharacterPicker(
    selected: CharacterId?,
    taken: Set<CharacterId>,
    onPick: (CharacterId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(CharacterId.entries, key = { it.name }) { character ->
            val isSelected = character == selected
            val available = isSelected || character !in taken
            Column(
                modifier = Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        BorderStroke(2.dp, if (isSelected) GameColors.Neon else Color.Transparent),
                        RoundedCornerShape(12.dp),
                    )
                    .clickable(enabled = available) { onPick(character) }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MonsterToken(character, size = 48.dp, dimmed = !available)
                Text(
                    character.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}
