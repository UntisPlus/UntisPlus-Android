/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.deelabs.untisplus.ui.material.scheme

import com.deelabs.untisplus.ui.material.palettes.CorePalette

data class Scheme(
  val primary: Int = 0,
  val onPrimary: Int = 0,
  val primaryContainer: Int = 0,
  val onPrimaryContainer: Int = 0,
  val secondary: Int = 0,
  val onSecondary: Int = 0,
  val secondaryContainer: Int = 0,
  val onSecondaryContainer: Int = 0,
  val tertiary: Int = 0,
  val onTertiary: Int = 0,
  val tertiaryContainer: Int = 0,
  val onTertiaryContainer: Int = 0,
  val error: Int = 0,
  val onError: Int = 0,
  val errorContainer: Int = 0,
  val onErrorContainer: Int = 0,
  val background: Int = 0,
  val onBackground: Int = 0,
  val surface: Int = 0,
  val onSurface: Int = 0,
  val surfaceVariant: Int = 0,
  val onSurfaceVariant: Int = 0,
  val outline: Int = 0,
  val outlineVariant: Int = 0,
  val shadow: Int = 0,
  val scrim: Int = 0,
  val inverseSurface: Int = 0,
  val inverseOnSurface: Int = 0,
  val inversePrimary: Int = 0,
  val surfaceBright: Int = 0,
  val surfaceDim: Int = 0,
  val surfaceContainer: Int = 0,
  val surfaceContainerHigh: Int = 0,
  val surfaceContainerHighest: Int = 0,
  val surfaceContainerLow: Int = 0,
  val surfaceContainerLowest: Int = 0,
) {
  companion object {
    fun light(argb: Int): Scheme = lightFromCorePalette(CorePalette.of(argb))

    fun dark(argb: Int): Scheme = darkFromCorePalette(CorePalette.of(argb))

    fun lightContent(argb: Int): Scheme = lightFromCorePalette(CorePalette.contentOf(argb))

    fun darkContent(argb: Int): Scheme = darkFromCorePalette(CorePalette.contentOf(argb))

    private fun lightFromCorePalette(core: CorePalette): Scheme {
      return Scheme(
        primary = core.a1.tone(40),
        onPrimary = core.a1.tone(100),
        primaryContainer = core.a1.tone(90),
        onPrimaryContainer = core.a1.tone(10),
        secondary = core.a2.tone(40),
        onSecondary = core.a2.tone(100),
        secondaryContainer = core.a2.tone(90),
        onSecondaryContainer = core.a2.tone(10),
        tertiary = core.a3.tone(40),
        onTertiary = core.a3.tone(100),
        tertiaryContainer = core.a3.tone(90),
        onTertiaryContainer = core.a3.tone(10),
        error = core.error.tone(40),
        onError = core.error.tone(100),
        errorContainer = core.error.tone(90),
        onErrorContainer = core.error.tone(10),
        background = core.n1.tone(99),
        onBackground = core.n1.tone(10),
        surface = core.n1.tone(99),
        onSurface = core.n1.tone(10),
        surfaceVariant = core.n2.tone(90),
        onSurfaceVariant = core.n2.tone(30),
        outline = core.n2.tone(50),
        outlineVariant = core.n2.tone(80),
        shadow = core.n1.tone(0),
        scrim = core.n1.tone(0),
        inverseSurface = core.n1.tone(20),
        inverseOnSurface = core.n1.tone(95),
        inversePrimary = core.a1.tone(80),
        surfaceBright = core.n1.tone(98),
        surfaceDim = core.n1.tone(87),
        surfaceContainer = core.n1.tone(94),
        surfaceContainerHigh = core.n1.tone(92),
        surfaceContainerHighest = core.n1.tone(90),
        surfaceContainerLow = core.n1.tone(96),
        surfaceContainerLowest = core.n1.tone(100),
      )
    }

    private fun darkFromCorePalette(core: CorePalette): Scheme {
      return Scheme(
        primary = core.a1.tone(80),
        onPrimary = core.a1.tone(20),
        primaryContainer = core.a1.tone(30),
        onPrimaryContainer = core.a1.tone(90),
        secondary = core.a2.tone(80),
        onSecondary = core.a2.tone(20),
        secondaryContainer = core.a2.tone(30),
        onSecondaryContainer = core.a2.tone(90),
        tertiary = core.a3.tone(80),
        onTertiary = core.a3.tone(20),
        tertiaryContainer = core.a3.tone(30),
        onTertiaryContainer = core.a3.tone(90),
        error = core.error.tone(80),
        onError = core.error.tone(20),
        errorContainer = core.error.tone(30),
        onErrorContainer = core.error.tone(80),
        background = core.n1.tone(10),
        onBackground = core.n1.tone(90),
        surface = core.n1.tone(10),
        onSurface = core.n1.tone(90),
        surfaceVariant = core.n2.tone(30),
        onSurfaceVariant = core.n2.tone(80),
        outline = core.n2.tone(60),
        outlineVariant = core.n2.tone(30),
        shadow = core.n1.tone(0),
        scrim = core.n1.tone(0),
        inverseSurface = core.n1.tone(90),
        inverseOnSurface = core.n1.tone(20),
        inversePrimary = core.a1.tone(40),
        surfaceBright = core.n1.tone(24),
        surfaceDim = core.n1.tone(6),
        surfaceContainer = core.n1.tone(12),
        surfaceContainerHigh = core.n1.tone(17),
        surfaceContainerHighest = core.n1.tone(22),
        surfaceContainerLow = core.n1.tone(10),
        surfaceContainerLowest = core.n1.tone(4),
      )
    }
  }
}
