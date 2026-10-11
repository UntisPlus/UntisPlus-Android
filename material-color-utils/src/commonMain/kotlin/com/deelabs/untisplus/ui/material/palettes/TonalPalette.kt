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

package com.deelabs.untisplus.ui.material.palettes

import com.deelabs.untisplus.ui.material.hct.Hct

/**
 * A convenience class for retrieving colors that are constant in hue and chroma, but vary in tone.
 */
class TonalPalette private constructor(
  private val hue: Double,
  private val chroma: Double,
) {
  private val cache = mutableMapOf<Int, Int>()

  /**
   * Create an ARGB color with HCT hue and chroma of this Tones instance, and the provided HCT tone.
   */
  fun tone(tone: Int): Int {
    val cached = cache[tone]
    if (cached != null) {
      return cached
    }
    val color = Hct.from(hue, chroma, tone.toDouble()).toInt()
    cache[tone] = color
    return color
  }

  companion object {
    /**
     * Create tones using the HCT hue and chroma from a color.
     */
    fun fromInt(argb: Int): TonalPalette {
      val hct = Hct.from(argb)
      return fromHueAndChroma(hct.hue, hct.chroma)
    }

    /**
     * Create tones from a defined HCT hue and chroma.
     */
    fun fromHueAndChroma(hue: Double, chroma: Double): TonalPalette {
      return TonalPalette(hue, chroma)
    }
  }
}
