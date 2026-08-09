package uz.ex.sip2go.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF0A1929),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color(0xFF00201C),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF1A2332),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF243044),
    onSurfaceVariant = Color(0xFFB8C5D6),
    outline = Color(0xFF3D5166),
    error = Color(0xFFFF8A80),
)

@Composable
fun SipTgTheme(content: @Composable () -> Unit) {
    val colorScheme = DarkColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
