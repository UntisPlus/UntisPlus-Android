package com.deelabs.untisplus.ui.material.scheme

import com.deelabs.untisplus.ui.material.scheme.Scheme
import kotlin.test.Test
import kotlin.test.assertEquals

class SchemeGoldenTest {
  private fun parseHex(s: String): Int = s.toLong(16).toInt()

  @Test
  fun golden_matches_java_implementation() {
    val seeds = intArrayOf(0xFF6750A4.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF00FF00.toInt(), 0xFFFF0000.toInt(), 0xFF123456.toInt(), 0xFF808080.toInt(), 0xFF4CAF50.toInt())
    val lines = javaClass.classLoader.getResource("golden.txt")?.readText() ?: error("missing golden.txt")
    val map = mutableMapOf<String, MutableMap<String, Int>>()
    var currentLabel: String? = null
    for (raw in lines.lines()) {
      val line = raw.trim()
      if (line.startsWith("## ")) {
        currentLabel = line.substring(3)
        map[currentLabel] = mutableMapOf()
      } else if (currentLabel != null && line.contains("=")) {
        val parts = line.split("=", limit = 2)
        if (parts.size == 2) {
          map.getValue(currentLabel)[parts[0]] = parseHex(parts[1])
        }
      }
    }
    for (seed in seeds) {
      val hex = String.format("%08X", seed)
      fun assertScheme(label: String, s: Scheme) {
        val m = map["$label:$hex"] ?: error("missing $label:$hex")
        assertEquals(m["primary"], s.primary)
        assertEquals(m["onPrimary"], s.onPrimary)
        assertEquals(m["primaryContainer"], s.primaryContainer)
        assertEquals(m["onPrimaryContainer"], s.onPrimaryContainer)
        assertEquals(m["secondary"], s.secondary)
        assertEquals(m["onSecondary"], s.onSecondary)
        assertEquals(m["secondaryContainer"], s.secondaryContainer)
        assertEquals(m["onSecondaryContainer"], s.onSecondaryContainer)
        assertEquals(m["tertiary"], s.tertiary)
        assertEquals(m["onTertiary"], s.onTertiary)
        assertEquals(m["tertiaryContainer"], s.tertiaryContainer)
        assertEquals(m["onTertiaryContainer"], s.onTertiaryContainer)
        assertEquals(m["error"], s.error)
        assertEquals(m["onError"], s.onError)
        assertEquals(m["errorContainer"], s.errorContainer)
        assertEquals(m["onErrorContainer"], s.onErrorContainer)
        assertEquals(m["background"], s.background)
        assertEquals(m["onBackground"], s.onBackground)
        assertEquals(m["surface"], s.surface)
        assertEquals(m["onSurface"], s.onSurface)
        assertEquals(m["surfaceVariant"], s.surfaceVariant)
        assertEquals(m["onSurfaceVariant"], s.onSurfaceVariant)
        assertEquals(m["outline"], s.outline)
        assertEquals(m["outlineVariant"], s.outlineVariant)
        assertEquals(m["shadow"], s.shadow)
        assertEquals(m["scrim"], s.scrim)
        assertEquals(m["inverseSurface"], s.inverseSurface)
        assertEquals(m["inverseOnSurface"], s.inverseOnSurface)
        assertEquals(m["inversePrimary"], s.inversePrimary)
        assertEquals(m["surfaceBright"], s.surfaceBright)
        assertEquals(m["surfaceDim"], s.surfaceDim)
        assertEquals(m["surfaceContainer"], s.surfaceContainer)
        assertEquals(m["surfaceContainerHigh"], s.surfaceContainerHigh)
        assertEquals(m["surfaceContainerHighest"], s.surfaceContainerHighest)
        assertEquals(m["surfaceContainerLow"], s.surfaceContainerLow)
        assertEquals(m["surfaceContainerLowest"], s.surfaceContainerLowest)
      }
      assertScheme("light", Scheme.light(seed))
      assertScheme("dark", Scheme.dark(seed))
      assertScheme("lightContent", Scheme.lightContent(seed))
      assertScheme("darkContent", Scheme.darkContent(seed))
    }
  }
}
