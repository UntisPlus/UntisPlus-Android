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

package com.deelabs.untisplus.ui.material.blend

import com.deelabs.untisplus.ui.material.hct.Cam16
import com.deelabs.untisplus.ui.material.hct.Hct
import com.deelabs.untisplus.ui.material.utils.ColorUtils
import com.deelabs.untisplus.ui.material.utils.MathUtils
import kotlin.math.min

/** Functions for blending in HCT and CAM16. */
object Blend {
  /**
   * Blend the design color's HCT hue towards the key color's HCT hue.
   */
  fun harmonize(designColor: Int, sourceColor: Int): Int {
    val fromHct = Hct.from(designColor)
    val toHct = Hct.from(sourceColor)
    val differenceDegrees = MathUtils.differenceDegrees(fromHct.hue, toHct.hue)
    val rotationDegrees = minOf(differenceDegrees * 0.5, 15.0)
    val outputHue =
      MathUtils.sanitizeDegreesDouble(
        fromHct.hue + rotationDegrees * MathUtils.rotationDirection(fromHct.hue, toHct.hue),
      )
    return Hct.from(outputHue, fromHct.chroma, fromHct.tone).toInt()
  }

  /**
   * Blends hue from one color into another. The chroma and tone of the original color are maintained.
   */
  fun hctHue(from: Int, to: Int, amount: Double): Int {
    val ucs = cam16Ucs(from, to, amount)
    val ucsCam = Cam16.fromInt(ucs)
    val fromCam = Cam16.fromInt(from)
    val blended = Hct.from(ucsCam.hue, fromCam.chroma, ColorUtils.lstarFromArgb(from))
    return blended.toInt()
  }

  /**
   * Blend in CAM16-UCS space.
   */
  fun cam16Ucs(from: Int, to: Int, amount: Double): Int {
    val fromCam = Cam16.fromInt(from)
    val toCam = Cam16.fromInt(to)
    val fromJ = fromCam.jStar
    val fromA = fromCam.aStar
    val fromB = fromCam.bStar
    val toJ = toCam.jStar
    val toA = toCam.aStar
    val toB = toCam.bStar
    val jstar = fromJ + (toJ - fromJ) * amount
    val astar = fromA + (toA - fromA) * amount
    val bstar = fromB + (toB - fromB) * amount
    return Cam16.fromUcs(jstar, astar, bstar).toInt()
  }
}
