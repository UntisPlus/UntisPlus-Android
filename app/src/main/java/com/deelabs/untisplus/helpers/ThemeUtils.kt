package com.deelabs.untisplus.helpers

import android.app.Activity
import android.os.Build
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.sapuseven.compose.protostore.ui.preferences.materialColors
import com.deelabs.untisplus.ui.material.scheme.Scheme
import com.deelabs.untisplus.ui.theme.animated
import com.deelabs.untisplus.ui.theme.toColorScheme

@Composable
fun AppTheme(
	darkTheme: ThemeMode = ThemeMode.FollowSystem,
	darkThemeOled: Boolean? = null,
	themeColor: Color? = null,
	content: @Composable () -> Unit
) {
	val defaultThemeColor = materialColors[0]
	val isDarkTheme = when (darkTheme) {
		ThemeMode.AlwaysLight -> false
		ThemeMode.AlwaysDark -> true
		ThemeMode.FollowSystem -> isSystemInDarkTheme()
	}
	Log.d("AppTheme", "Theme mode: $darkTheme, isDarkTheme: $isDarkTheme")

	val colorScheme = when {
		themeColor == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
			val context = LocalContext.current
			if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
		}

		isDarkTheme -> Scheme.dark((themeColor ?: defaultThemeColor).toArgb()).toColorScheme()
		else -> Scheme.light((themeColor ?: defaultThemeColor).toArgb()).toColorScheme()
	}.run {
		if (isDarkTheme && (darkThemeOled ?: isSystemInDarkTheme()))
			copy(background = Color.Black, surface = Color.Black)
		else
			this
	}

	val darkIcons = colorScheme.background.luminance() > .5f
	val view = LocalView.current
	if (!view.isInEditMode) {
		SideEffect {
			val window = (view.context as Activity).window
			WindowCompat.getInsetsController(window, view).apply {
				isAppearanceLightStatusBars = darkIcons
				isAppearanceLightNavigationBars = darkIcons
			}
		}
	}

	MaterialTheme(
		colorScheme = colorScheme.animated(),
		content = content
	)
}

enum class ThemeMode {
	AlwaysLight,
	AlwaysDark,
	FollowSystem
}
