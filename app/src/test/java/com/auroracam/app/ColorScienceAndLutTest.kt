package com.auroracam.app

import com.auroracam.app.capture.LookUniforms
import com.auroracam.app.capture.LutManager
import com.auroracam.app.gl.lut.AuroraWarmLut
import com.auroracam.app.gl.lut.ChromeLut
import com.auroracam.app.gl.lut.CubeParser
import com.auroracam.app.gl.lut.FujiClassicChromeLut
import com.auroracam.app.gl.lut.HasselbladNaturalLut
import com.auroracam.app.gl.lut.KodakPortra400Lut
import com.auroracam.app.gl.lut.LeicaCharacterLut
import com.auroracam.app.gl.lut.MonoLut
import com.auroracam.app.gl.lut.OklabColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ColorScienceAndLutTest {

    @Test
    fun testOklabColorMathRoundTrip() {
        // Test primary and neutral colors roundtrip accuracy
        val testColors = listOf(
            Triple(1.0f, 0.0f, 0.0f),
            Triple(0.0f, 1.0f, 0.0f),
            Triple(0.0f, 0.0f, 1.0f),
            Triple(0.5f, 0.5f, 0.5f),
            Triple(0.9f, 0.7f, 0.5f),
            Triple(0.1f, 0.2f, 0.3f)
        )

        for ((r, g, b) in testColors) {
            val linR = OklabColor.srgbToLinear(r)
            val linG = OklabColor.srgbToLinear(g)
            val linB = OklabColor.srgbToLinear(b)

            val oklab = OklabColor.linearSrgbToOklab(linR, linG, linB)
            val oklch = OklabColor.oklabToOklch(oklab)
            val recLab = OklabColor.oklchToOklab(oklch)
            val (recLinR, recLinG, recLinB) = OklabColor.oklabToLinearSrgb(recLab)

            val recR = OklabColor.linearToSrgb(recLinR)
            val recG = OklabColor.linearToSrgb(recLinG)
            val recB = OklabColor.linearToSrgb(recLinB)

            assertTrue("Red roundtrip mismatch for ($r, $g, $b): got $recR", abs(r - recR) < 0.002f)
            assertTrue("Green roundtrip mismatch for ($r, $g, $b): got $recG", abs(g - recG) < 0.002f)
            assertTrue("Blue roundtrip mismatch for ($r, $g, $b): got $recB", abs(b - recB) < 0.002f)
        }
    }

    @Test
    fun testAllSevenProceduralLutsGenerateValid33Cube() {
        val lutGenerators = listOf(
            HasselbladNaturalLut.LUT_NAME to { HasselbladNaturalLut.generate() },
            LeicaCharacterLut.LUT_NAME to { LeicaCharacterLut.generate() },
            FujiClassicChromeLut.LUT_NAME to { FujiClassicChromeLut.generate() },
            KodakPortra400Lut.LUT_NAME to { KodakPortra400Lut.generate() },
            AuroraWarmLut.LUT_NAME to { AuroraWarmLut.generate() },
            ChromeLut.LUT_NAME to { ChromeLut.generate() },
            MonoLut.LUT_NAME to { MonoLut.generate() }
        )

        assertEquals("Expected exactly 7 built-in procedural LUTs", 7, lutGenerators.size)

        for ((name, generator) in lutGenerators) {
            val cube = generator()
            assertEquals("LUT '$name' must be size 33", 33, cube.size)
            assertEquals("LUT '$name' domainMin must be 0", 0.0f, cube.domainMin[0])
            assertEquals("LUT '$name' domainMax must be 1", 1.0f, cube.domainMax[0])

            val expectedBytes = 33 * 33 * 33 * 4
            assertEquals("LUT '$name' data capacity mismatch", expectedBytes, cube.data.capacity())
            assertTrue("LUT '$name' data must be direct buffer", cube.data.isDirect)
        }
    }

    @Test
    fun testLutManagerPresetsAndUniformsIntegrity() {
        val builtInNames = LutManager.BUILTIN_PRESETS
        assertEquals(7, builtInNames.size)

        for (name in builtInNames) {
            val uniforms = LutManager.DEFAULT_LOOK_UNIFORMS[name]
            assertNotNull("Uniforms for look '$name' must be defined", uniforms)
            assertTrue("Intensity must be > 0 for look '$name'", uniforms!!.intensity > 0.0f)
            assertTrue("Halation must be >= 0 for look '$name'", uniforms.halation >= 0.0f)
            assertTrue("HalationThreshold must be in [0.5, 0.95] for look '$name'", uniforms.halationThreshold in 0.5f..0.95f)
            assertTrue("Grain must be >= 0 for look '$name'", uniforms.grain >= 0.0f)
            assertTrue("Vignette must be >= 0 for look '$name'", uniforms.vignette >= 0.0f)
        }
    }

    @Test
    fun testCubeParser() {
        val sampleCubeContent = """
            # Adobe Cube sample
            TITLE "Custom Test LUT"
            LUT_3D_SIZE 2
            DOMAIN_MIN 0.0 0.0 0.0
            DOMAIN_MAX 1.0 1.0 1.0
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
        """.trimIndent()

        val parsed = CubeParser.parse(sampleCubeContent.byteInputStream())
        assertEquals(2, parsed.size)
        assertEquals(2 * 2 * 2 * 4, parsed.data.capacity())
        assertEquals(0.0f, parsed.domainMin[0])
        assertEquals(1.0f, parsed.domainMax[0])
    }
}

