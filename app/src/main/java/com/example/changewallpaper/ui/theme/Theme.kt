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
    accentStyle: AccentStyle = AccentStyle.FOREST,
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
        dark -> darkColorScheme(
            primary = ForestDarkPrimary,
            secondary = ForestDarkSecondary,
            tertiary = Color(0xFFA0CED3),
            primaryContainer = ForestDarkContainer,
            background = GalleryDarkBackground,
            surface = GalleryDarkSurface,
            surfaceVariant = GalleryDarkSurfaceVariant
        )
        else -> lightColorScheme(
            primary = ForestLightPrimary,
            secondary = ForestLightSecondary,
            tertiary = ForestLightTertiary,
            primaryContainer = ForestLightContainer,
            background = GalleryLightBackground,
            surface = GalleryLightSurface,
            surfaceVariant = GalleryLightSurfaceVariant
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
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)
