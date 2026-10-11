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

package com.deelabs.untisplus.ui.material.hct

import com.deelabs.untisplus.ui.material.utils.ColorUtils

/**
 * A color system built using CAM16 hue and chroma, and L* from L*a*b*.
 */
class Hct private constructor(argb: Int) {
  var hue = 0.0
    private set
  var chroma = 0.0
    private set
  var tone = 0.0
    private set
  private var argbValue = argb

  init {
    setInternalState(argb)
  }

  fun toInt(): Int = argbValue

  fun setHue(newHue: Double) {
    setInternalState(HctSolver.solveToInt(newHue, chroma, tone))
  }

  fun setChroma(newChroma: Double) {
    setInternalState(HctSolver.solveToInt(hue, newChroma, tone))
  }

  fun setTone(newTone: Double) {
    setInternalState(HctSolver.solveToInt(hue, chroma, newTone))
  }

  private fun setInternalState(argb: Int) {
    this.argbValue = argb
    val cam = Cam16.fromInt(argb)
    hue = cam.hue
    chroma = cam.chroma
    tone = ColorUtils.lstarFromArgb(argb)
  }

  companion object {
    /**
     * Create an HCT color from hue, chroma, and tone.
     */
    fun from(hue: Double, chroma: Double, tone: Double): Hct {
      val argb = HctSolver.solveToInt(hue, chroma, tone)
      return Hct(argb)
    }

    /**
     * Create an HCT color from a color.
     */
    fun from(argb: Int): Hct = Hct(argb)
  }
}
