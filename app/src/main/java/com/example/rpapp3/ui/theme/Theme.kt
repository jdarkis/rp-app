package com.example.rpapp3.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val ModernDarkScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = VoidBlack,
    primaryContainer = ElectricBlueContainer,
    onPrimaryContainer = NeonCyan,
    secondary = NeonCyan,
    onSecondary = VoidBlack,
    secondaryContainer = NeonCyanContainer,
    onSecondaryContainer = ElectricBlue,
    tertiary = Pink80,
    background = VoidBlack,
    surface = DarkSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = ErrorRed
)

private val CyberpunkScheme = darkColorScheme(
    primary = CyberYellow,
    onPrimary = CyberBlack,
    primaryContainer = CyberDarkHolo,
    onPrimaryContainer = CyberYellow,
    secondary = CyberPink,
    onSecondary = CyberBlack,
    secondaryContainer = CyberDarkHolo,
    onSecondaryContainer = CyberPink,
    background = CyberBlack,
    surface = CyberDarkHolo,
    onBackground = CyberYellow,
    onSurface = CyberPink,
    error = ErrorRed
)

private val NatureScheme = darkColorScheme(
    primary = LightLeaf,
    onPrimary = DarkPine,
    primaryContainer = DarkPine,
    onPrimaryContainer = LightLeaf,
    secondary = ForestGreen,
    onSecondary = SandBeige,
    background = EarthBrown,
    surface = DarkPine,
    onBackground = SandBeige,
    onSurface = SandBeige
)

private val ClassicScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val EclipseScheme = darkColorScheme(
    primary = SilverAccent,
    onPrimary = MatteBlack,
    primaryContainer = DarkGreySurface,
    onPrimaryContainer = SilverAccent,
    secondary = SilverAccent,
    background = MatteBlack,
    surface = DarkGreySurface,
    onBackground = SilverAccent,
    onSurface = SilverAccent
)

private val CloudScheme = lightColorScheme(
    primary = DeepSky,
    onPrimary = SoftWhite,
    primaryContainer = SkyBlue,
    onPrimaryContainer = DeepSky,
    secondary = SkyBlue,
    background = SoftWhite,
    surface = CloudGrey,
    onBackground = DeepSky,
    onSurface = DeepSky
)

@Composable
fun RPApp3Theme(
    selectedTheme: String = "MODERN_DARK",
    content: @Composable () -> Unit
) {
    val colorScheme = when (selectedTheme) {
        "CYBERPUNK" -> CyberpunkScheme
        "NATURE" -> NatureScheme
        "CLASSIC" -> ClassicScheme
        "ECLIPSE" -> EclipseScheme
        "CLOUD" -> CloudScheme
        else -> ModernDarkScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}