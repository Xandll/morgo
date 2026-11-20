package io.qzz.xandll.morgo.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val Peach = Color(0xFFF9A86B)
private val Orange = Color(0xFFF7934C)
private val Sun = Color(0xFFFFC56B)
private val Navy = Color(0xFF1E2350)
private val Indigo = Color(0xFF2E3470)
private val Cream = Color(0xFFFFF6EE)
private val Ink = Color(0xFF1A1C2C)

private val LightColorScheme = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = Peach,
    onPrimaryContainer = Navy,
    secondary = Indigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9DCF5),
    onSecondaryContainer = Navy,
    tertiary = Sun,
    onTertiary = Navy,
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFFFF1E6),
    onSurfaceVariant = Color(0xFF5C5148),
    outline = Color(0xFFE7D0C0),
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Peach,
    onPrimary = Navy,
    primaryContainer = Color(0xFF8A4E28),
    onPrimaryContainer = Color(0xFFFFE6D2),
    secondary = Color(0xFFB7BEEC),
    onSecondary = Navy,
    secondaryContainer = Indigo,
    onSecondaryContainer = Color(0xFFE4E7FF),
    tertiary = Sun,
    onTertiary = Navy,
    background = Color(0xFF12142C),
    onBackground = Color(0xFFF6EFE8),
    surface = Color(0xFF1E2350),
    onSurface = Color(0xFFF6EFE8),
    surfaceVariant = Color(0xFF2A3168),
    outline = Color(0xFF3C4478),
    onSurfaceVariant = Color(0xFFD5C8BC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val MorgoTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Light,
        fontSize = 84.sp,
        lineHeight = 88.sp,
        letterSpacing = (-2).sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Light,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1).sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )
)

private val MorgoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MorgoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MorgoTypography,
        shapes = MorgoShapes,
        content = content
    )
}
