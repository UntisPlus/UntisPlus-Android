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

package com.deelabs.untisplus.ui.material.score

import com.deelabs.untisplus.ui.material.hct.Cam16
import com.deelabs.untisplus.ui.material.utils.ColorUtils
import com.deelabs.untisplus.ui.material.utils.MathUtils
import kotlin.math.roundToInt

/**
 * Given a large set of colors, remove colors that are unsuitable for a UI theme, and rank the rest
 * based on suitability.
 *
 * <p>Enables use of a high cluster count for image quantization, thus ensuring colors aren't
 * muddied, while curating the high cluster count to a much smaller number of appropriate choices.
 */
object Score {
  private const val CUTOFF_CHROMA = 15.0
  private const val CUTOFF_EXCITED_PROPORTION = 0.01
  private const val CUTOFF_TONE = 10.0
  private const val TARGET_CHROMA = 48.0
  private const val WEIGHT_PROPORTION = 0.7
  private const val WEIGHT_CHROMA_ABOVE = 0.3
  private const val WEIGHT_CHROMA_BELOW = 0.1

  /**
   * Given a map with keys of colors and values of how often the color appears, rank the colors
   * based on suitability for being used for a UI theme.
   */
  fun score(colorsToPopulation: Map<Int, Int>): List<Int> {
    var populationSum = 0.0
    for (entry in colorsToPopulation.entries) {
      populationSum += entry.value
    }

    val colorsToCam = HashMap<Int, Cam16>()
    val hueProportions = DoubleArray(361)
    for (entry in colorsToPopulation.entries) {
      val color = entry.key
      val population = entry.value.toDouble()
      val proportion = population / populationSum

      val cam = Cam16.fromInt(color)
      colorsToCam[color] = cam

      val hue = cam.hue.roundToInt()
      hueProportions[hue] += proportion
    }

    val colorsToExcitedProportion = HashMap<Int, Double>()
    for (entry in colorsToCam.entries) {
      val color = entry.key
      val cam = entry.value
      val hue = cam.hue.roundToInt()

      var excitedProportion = 0.0
      for (j in (hue - 15) until (hue + 15)) {
        val neighborHue = MathUtils.sanitizeDegreesInt(j)
        excitedProportion += hueProportions[neighborHue]
      }

      colorsToExcitedProportion[color] = excitedProportion
    }

    val colorsToScore = HashMap<Int, Double>()
    for (entry in colorsToCam.entries) {
      val color = entry.key
      val cam = entry.value

      val proportion = colorsToExcitedProportion.getValue(color)
      val proportionScore = proportion * 100.0 * WEIGHT_PROPORTION

      val chromaWeight = if (cam.chroma < TARGET_CHROMA) WEIGHT_CHROMA_BELOW else WEIGHT_CHROMA_ABOVE
      val chromaScore = (cam.chroma - TARGET_CHROMA) * chromaWeight

      colorsToScore[color] = proportionScore + chromaScore
    }

    val filteredColors = filter(colorsToExcitedProportion, colorsToCam)
    val filteredColorsToScore = HashMap<Int, Double>()
    for (color in filteredColors) {
      filteredColorsToScore[color] = colorsToScore.getValue(color)
    }

    val entryList = filteredColorsToScore.entries.sortedByDescending { it.value }
    val colorsByScoreDescending = ArrayList<Int>()
    for (entry in entryList) {
      val color = entry.key
      val cam = colorsToCam.getValue(color)
      var duplicateHue = false

      for (alreadyChosenColor in colorsByScoreDescending) {
        val alreadyChosenCam = colorsToCam.getValue(alreadyChosenColor)
        if (MathUtils.differenceDegrees(cam.hue, alreadyChosenCam.hue) < 15) {
          duplicateHue = true
          break
        }
      }

      if (duplicateHue) {
        continue
      }
      colorsByScoreDescending.add(entry.key)
    }

    if (colorsByScoreDescending.isEmpty()) {
      colorsByScoreDescending.add(0xff4285F4.toInt()) // Google Blue
    }
    return colorsByScoreDescending
  }

  private fun filter(
    colorsToExcitedProportion: Map<Int, Double>,
    colorsToCam: Map<Int, Cam16>,
  ): List<Int> {
    val filtered = ArrayList<Int>()
    for (entry in colorsToCam.entries) {
      val color = entry.key
      val cam = entry.value
      val proportion = colorsToExcitedProportion.getValue(color)

      if (cam.chroma >= CUTOFF_CHROMA &&
        ColorUtils.lstarFromArgb(color) >= CUTOFF_TONE &&
        proportion >= CUTOFF_EXCITED_PROPORTION
      ) {
        filtered.add(color)
      }
    }
    return filtered
  }
}
