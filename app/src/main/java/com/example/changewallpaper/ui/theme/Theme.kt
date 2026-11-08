package com.example.changewallpaper.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.example.changewallpaper.AccentStyle
import com.example.changewallpaper.AppThemeMode

@Composable
fun ChangeWallpaperTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    accentStyle: AccentStyle = AccentStyle.LAVENDER,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        accentStyle == AccentStyle.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        accentStyle == AccentStyle.LAVENDER && dark -> darkColorScheme(
            primary = LavenderDarkPrimary,
            onPrimary = Color(0xFF381E72),
            primaryContainer = LavenderDarkContainer,
            onPrimaryContainer = Color(0xFFEADDFF),
            secondary = LavenderDarkSecondary,
            onSecondary = Color(0xFF332D41),
            secondaryContainer = Color(0xFF4A4458),
            onSecondaryContainer = Color(0xFFE8DEF8),
            tertiary = LavenderDarkTertiary,
            tertiaryContainer = Color(0xFF633B58),
            background = LavenderDarkBackground,
            surface = LavenderDarkSurface,
            surfaceVariant = LavenderDarkSurfaceVariant,
            outline = Color(0xFF948F9E),
            outlineVariant = Color(0xFF494452)
        )
        accentStyle == AccentStyle.LAVENDER -> lightColorScheme(
            primary = LavenderLightPrimary,
            onPrimary = Color.White,
            primaryContainer = LavenderLightContainer,
            onPrimaryContainer = Color(0xFF260058),
            secondary = LavenderLightSecondary,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE8DEF8),
            onSecondaryContainer = Color(0xFF1E192B),
            tertiary = LavenderLightTertiary,
            tertiaryContainer = Color(0xFFFFD8E7),
            background = LavenderLightBackground,
            surface = LavenderLightSurface,
            surfaceVariant = LavenderLightSurfaceVariant,
            outline = Color(0xFF7A7584),
            outlineVariant = Color(0xFFCBC4D3)
        )
        accentStyle == AccentStyle.OCEAN && dark -> darkColorScheme(
            primary = OceanDarkPrimary,
            secondary = Color(0xFFB7C9D7),
            tertiary = Color(0xFFC2C2EB),
            primaryContainer = OceanDarkContainer,
            background = GalleryDarkBackground,
            surface = GalleryDarkSurface,
            surfaceVariant = GalleryDarkSurfaceVariant
        )
        accentStyle == AccentStyle.OCEAN -> lightColorScheme(
            primary = OceanLightPrimary,
            secondary = OceanLightSecondary,
            tertiary = Color(0xFF65587B),
            primaryContainer = OceanLightContainer,
            background = GalleryLightBackground,
            surface = GalleryLightSurface,
            surfaceVariant = GalleryLightSurfaceVariant
        )
        accentStyle == AccentStyle.SUNSET && dark -> darkColorScheme(
            primary = SunsetDarkPrimary,
            secondary = Color(0xFFE7BDBF),
            tertiary = Color(0xFFE8C084),
            primaryContainer = SunsetDarkContainer,
            background = GalleryDarkBackground,
            surface = GalleryDarkSurface,
            surfaceVariant = GalleryDarkSurfaceVariant
        )
        accentStyle == AccentStyle.SUNSET -> lightColorScheme(
            primary = SunsetLightPrimary,
            secondary = SunsetLightSecondary,
            tertiary = Color(0xFF765A2B),
            primaryContainer = SunsetLightContainer,
            background = GalleryLightBackground,
            surface = GalleryLightSurface,
            surfaceVariant = GalleryLightSurfaceVariant
        )
        accentStyle == AccentStyle.FOREST && dark -> darkColorScheme(
            primary = ForestDarkPrimary,
            secondary = ForestDarkSecondary,
            tertiary = Color(0xFFA0CED3),
            primaryContainer = ForestDarkContainer,
            background = GalleryDarkBackground,
            surface = GalleryDarkSurface,
            surfaceVariant = GalleryDarkSurfaceVariant
        )
        accentStyle == AccentStyle.FOREST -> lightColorScheme(
            primary = ForestLightPrimary,
            secondary = ForestLightSecondary,
            tertiary = ForestLightTertiary,
            primaryContainer = ForestLightContainer,
            background = GalleryLightBackground,
            surface = GalleryLightSurface,
            surfaceVariant = GalleryLightSurfaceVariant
        )
        dark -> darkColorScheme(
            primary = LavenderDarkPrimary,
            secondary = LavenderDarkSecondary,
            tertiary = LavenderDarkTertiary,
            primaryContainer = LavenderDarkContainer,
            background = LavenderDarkBackground,
            surface = LavenderDarkSurface,
            surfaceVariant = LavenderDarkSurfaceVariant
        )
        else -> lightColorScheme(
            primary = LavenderLightPrimary,
            secondary = LavenderLightSecondary,
            tertiary = LavenderLightTertiary,
            primaryContainer = LavenderLightContainer,
            background = LavenderLightBackground,
            surface = LavenderLightSurface,
            surfaceVariant = LavenderLightSurfaceVariant
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        shapes = GalleryShapes,
        content = content
    )
}

private val GalleryShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(38.dp)
)
