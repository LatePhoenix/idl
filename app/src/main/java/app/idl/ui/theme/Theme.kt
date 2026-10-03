package app.idl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Warm, cozy palette: iris primary, peach secondary, soft lagoon tertiary. No alarm reds.
private val Light = lightColorScheme(
    primary = Color(0xFF6656D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E0FF),
    onPrimaryContainer = Color(0xFF1F1360),
    secondary = Color(0xFFB8643F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC8),
    onSecondaryContainer = Color(0xFF3D1805),
    tertiary = Color(0xFF2E8792),
    tertiaryContainer = Color(0xFFC6EEF2),
    background = Color(0xFFFFFBF7),
    onBackground = Color(0xFF221C26),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF221C26),
    surfaceVariant = Color(0xFFF1E9EE),
    onSurfaceVariant = Color(0xFF5B5260),
    surfaceContainer = Color(0xFFF7F0F2),
    surfaceContainerHigh = Color(0xFFF1EAEE),
    error = Color(0xFFB3434F),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFC9BFFF),
    onPrimary = Color(0xFF30209A),
    primaryContainer = Color(0xFF4A3BBE),
    onPrimaryContainer = Color(0xFFE6E0FF),
    secondary = Color(0xFFFFB68F),
    onSecondary = Color(0xFF5A2A0F),
    secondaryContainer = Color(0xFF7A3E1F),
    onSecondaryContainer = Color(0xFFFFDCC8),
    tertiary = Color(0xFF8FD3DC),
    tertiaryContainer = Color(0xFF115E67),
    background = Color(0xFF17131B),
    onBackground = Color(0xFFEBE1EC),
    surface = Color(0xFF17131B),
    onSurface = Color(0xFFEBE1EC),
    surfaceVariant = Color(0xFF3B3340),
    onSurfaceVariant = Color(0xFFCEC3D2),
    surfaceContainer = Color(0xFF221D27),
    surfaceContainerHigh = Color(0xFF2C2632),
    error = Color(0xFFFFB3B8),
)

private val IdlShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun IdlTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, shapes = IdlShapes, content = content)
}
