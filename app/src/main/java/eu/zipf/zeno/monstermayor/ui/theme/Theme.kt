package eu.zipf.zeno.monstermayor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import eu.zipf.zeno.monstermayor.core.model.CharacterId

object GameColors {
    val Night = Color(0xFF140C24)
    val Dusk = Color(0xFF231640)
    val Panel = Color(0xFF2E1F52)
    val Neon = Color(0xFFFFC107)
    val Health = Color(0xFFEF5350)
    val Energy = Color(0xFF66BB6A)
    val Victory = Color(0xFFFFD54F)
    val Attack = Color(0xFFFF7043)
    val DieBody = Color(0xFF1C1C1E)
    val DieEdge = Color(0xFF3A3A3C)
    val Held = Color(0xFF29B6F6)
}

val CharacterId.color: Color get() = Color(argbColor)

private val scheme = darkColorScheme(
    primary = GameColors.Neon,
    onPrimary = Color(0xFF231640),
    secondary = Color(0xFF7C4DFF),
    onSecondary = Color.White,
    tertiary = GameColors.Energy,
    background = GameColors.Night,
    onBackground = Color(0xFFF3EEFF),
    surface = GameColors.Dusk,
    onSurface = Color(0xFFF3EEFF),
    surfaceVariant = GameColors.Panel,
    onSurfaceVariant = Color(0xFFD1C4E9),
    error = GameColors.Health,
)

private val typography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = 1.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp),
)

@Composable
fun MonsterMayorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
