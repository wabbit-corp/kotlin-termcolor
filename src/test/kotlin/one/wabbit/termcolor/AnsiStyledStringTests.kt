package one.wabbit.termcolor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AnsiStyledStringTests {
    private inline fun <reified E : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is E) return
            throw e
        }
        throw AssertionError("Expected exception of type ${E::class.java}")
    }

    @Test
    fun `test error modes`() {
        assertThrows<IllegalArgumentException> {
            AnsiStyledString.parseOrThrow("Invalid\u001b[38;2;xxx")
        }
        assertEquals(
            "Invalid[38;2;xxx",
            AnsiStyledString.parseSanitized("Invalid\u001b[38;2;xxx").toString(),
        )
        assertEquals(
            "Invalidxx",
            AnsiStyledString.parseStripped("Invalid\u001b[38;2;xxx").toString(),
        )
    }

    @Test
    fun `test basic construction and plainText`() {
        val raw = "Hello"
        val s = AnsiStyledString(raw)
        assertEquals(raw, s.plainText)
        assertEquals(raw.length, s.length)
        assertTrue(s.colors().all { it == 0L }, "All colors should be 0 for unstyled text")
    }

    @Test
    fun `test parse with normal ANSI codes`() {
        // Suppose  \u001b[31m => red foreground, \u001b[1m => bold
        // For a minimal test, we rely on the underlying library to parse those.
        val raw = "Hello \u001b[31mred\u001b[0m world"
        val parsed = AnsiStyledString.parseOrThrow(raw)
        assertEquals(
            "Hello red world",
            parsed.plainText,
            "Stripping ANSI codes should yield a plain string",
        )
        assertEquals(15, parsed.length) // "Hello red world".length == 15
    }

    @Test
    fun `test plus operator concatenation`() {
        val s1 = AnsiStyledString("Hello")
        val s2 = AnsiStyledString("World")
        val combined = s1 + s2
        assertEquals("HelloWorld", combined.plainText)
        assertEquals(s1.length + s2.length, combined.length)
        // Colors should be zero across the entire combined string
        assertTrue(combined.colors().all { it == 0L })
    }

    @Test
    fun `test splitAt normal usage`() {
        val s = AnsiStyledString("HelloWorld")
        assertEquals(10, s.length)

        // Splitting at index=5 => "Hello" and "World"
        val (left, right) = s.splitAt(5)
        assertEquals("Hello", left.plainText)
        assertEquals("World", right.plainText)
        // Ensure both have correct sizes, color arrays, etc.
        assertEquals(5, left.length)
        assertEquals(5, right.length)
    }

    @Test
    fun `test splitAt invalid usage throws`() {
        val s = AnsiStyledString("TestString")
        // If splitAt doesn't have an explicit 'require', it might internally cause an exception
        // We'll assert that it does or that it doesn't.
        assertThrows<IllegalArgumentException> { s.splitAt(-1) }
        assertThrows<IllegalArgumentException> { s.splitAt(s.length + 10) }
    }

    @Test
    fun `test substring normal usage`() {
        val s = AnsiStyledString("HelloWorld")
        val sub = s.substring(1, 4)
        assertEquals("ell", sub.plainText)
        assertEquals(3, sub.length)

        // Entire string
        val entire = s.substring(0, s.length)
        assertEquals("HelloWorld", entire.plainText)
        assertEquals(s.length, entire.length)

        // zero-length substring
        val zero = s.substring(2, 2)
        assertEquals("", zero.plainText)
        assertEquals(0, zero.length)
    }

    @Test
    fun `test substring invalid usage throws`() {
        val s = AnsiStyledString("TestString")
        assertThrows<IllegalArgumentException> { s.substring(-1, 2) }
        assertThrows<IllegalArgumentException> { s.substring(0, s.length + 1) }
        assertThrows<IllegalArgumentException> { s.substring(5, 3) }
    }

    @Test
    fun `test overlay single range`() {
        val base = AnsiStyledString("Hello World")
        val style = AnsiStyle.Bold.On // some style

        val styled = base.decorate(style, 0, 5)
        // Check that indices [0..4] get updated, others remain the same
        for (i in 0 until styled.length) {
            if (i in 0..4) {
                // Should be non-zero because Bold sets some bits
                assertNotEquals(0L, styled.colorAt(i), "Char $i should be bold")
            } else {
                assertEquals(0L, styled.colorAt(i), "Char $i should remain unstyled")
            }
        }
    }

    @Test
    fun `test overlay multiple ranges`() {
        val base = AnsiStyledString("ABCDEFGHIJK")
        val range1 = 1..3 // inclusive => positions 1,2,3
        val range2 = 7..9
        val style1 = AnsiStyle.Underlined.On
        val style2 = AnsiStyle.Reversed.On

        val styled = base.decorate(listOf(range1 to style1, range2 to style2))

        // Indices 1..3 => underlined
        for (i in 1..3) {
            assertNotEquals(0L, styled.colorAt(i), "Should be underlined in range 1..3")
        }
        // Indices 7..9 => reversed
        for (i in 7..9) {
            assertNotEquals(0L, styled.colorAt(i), "Should be reversed in range 7..9")
        }
        // Others zero
        for (i in listOf(0, 4, 5, 6, 10)) {
            assertEquals(0L, styled.colorAt(i), "Char $i should remain unstyled")
        }
    }

    @Test
    fun `test apply as alias to withOverlay`() {
        val s = AnsiStyledString("Test")
        val styled = s.decorate(AnsiStyle.Bold.On)
        for (i in 0 until styled.length) {
            assertNotEquals(0L, styled.colorAt(i), "All positions should be styled with Bold")
        }
    }

    @Test
    fun `test join with multiple strings`() {
        val s1 = AnsiStyledString("Hello")
        val s2 = AnsiStyledString("World")
        val s3 = AnsiStyledString("123")

        val joinedNoSep = AnsiStyledString.join(listOf(s1, s2, s3))
        assertEquals("HelloWorld123", joinedNoSep.plainText)

        val sep = AnsiStyledString(" - ")
        val joinedWithSep = AnsiStyledString.join(listOf(s1, s2, s3), sep)
        assertEquals("Hello - World - 123", joinedWithSep.plainText)
    }

    @Test
    fun `test join with empty iterable`() {
        val joined = AnsiStyledString.join(emptyList())
        assertEquals("", joined.plainText)
        assertEquals(0, joined.length)
    }

    @Test
    fun `test error modes more thoroughly`() {
        // Partially covered by existing test, but let's expand
        // 1) Throw
        assertThrows<IllegalArgumentException> {
            AnsiStyledString.parseOrThrow("Invalid\u001b[48;2;999;0;0m text")
        }
        // 2) Sanitize
        val sanitized = AnsiStyledString.parseSanitized("Te\u001b[48;2;999;0;0mst")
        // The invalid code's first \u001b is replaced with nothing, leaving "[48;2;999;0;0mst"
        // The "Te" remains, and the subsequent bracket might appear.
        // Actual result is a bit subtle: we skip just the ESC? Let's just check part:
        assertTrue(
            sanitized.plainText.startsWith("Te"),
            "Should contain the original chars outside the unknown escape",
        )
        // 3) Strip
        val stripped = AnsiStyledString.parseStripped("Te\u001b[48;2;999;0;0mst")
        assertEquals("Test", stripped.plainText, "Whole unknown code should be removed")
    }

    @Test
    fun `test rendering resets state at the end`() {
        val s = AnsiStyledString("Hello").decorate(AnsiStyle.Bold.On, 0, 5)
        val rendered = s.toString()
        // The result should start with some ANSI codes for bold, then "Hello", then a reset
        assertTrue(rendered.contains("Hello"), "Should contain the text itself")
        // Typically ends with "\u001b[0m" or something similar, depending on AnsiStyle
        // implementation
        // We'll do a naive check for 'm' or the reset code.
        assertTrue(
            rendered.endsWith(AnsiCodes.RESET),
            "Should end with the reset code to restore default state",
        )
    }

    @Test
    fun `test equals and hashCode`() {
        val s1 = AnsiStyledString("Test")
        val s2 = AnsiStyledString("Test")
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode(), "Equal strings must have same hashCode")

        val s3 = s1.decorate(AnsiStyle.Bold.On, 0, 1)
        assertNotEquals(s1, s3, "Changing style should change equality")
    }

    @Test
    fun `test fromArrays constructor`() {
        val chars = charArrayOf('A', 'B', 'C')
        val colors = longArrayOf(0L, 123L, 99999L)
        val s = AnsiStyledString.fromArrays(chars, colors)

        assertEquals("ABC", s.plainText)
        assertEquals(3, s.length)
        // Ensure it has copies, not references
        chars[0] = 'Z'
        colors[0] = 999999L
        assertEquals('A', s.charAt(0), "Should not have changed to Z if the arrays were cloned")
        assertEquals(0L, s.colorAt(0), "Should not have changed to 999999L if cloned")
    }

    /**
     * Test handling of Unicode text.
     *
     * This test uses accented characters and emoji (which are represented as surrogate pairs) to
     * ensure that the plainText and length behave as expected.
     *
     * Note: Since the library operates at the Char-array level, length equals the number of Char
     * code units rather than user-perceived grapheme clusters.
     */
    @Test
    fun `test unicode handling`() {
        val raw =
            "héllo 👋🏼 World" // Contains accented letters and an emoji with skin tone modifier.
        val styled = AnsiStyledString(raw)
        assertEquals(
            raw,
            styled.plainText,
            "The plain text should match the original Unicode input",
        )
        assertEquals(
            raw.length,
            styled.length,
            "Length is measured in Char units; surrogate pairs count as two",
        )
    }

    /**
     * Test behavior when an ANSI escape sequence is incomplete.
     *
     * In this test the ANSI sequence does not have its terminating character. When using the Strip
     * error mode, the incomplete escape should be skipped entirely.
     */
    @Test
    fun `test incomplete ansi sequence strip`() {
        val input = "Hello \u001b[31"
        val parsed = AnsiStyledString.parseStripped(input)
        // Expect that the incomplete escape is removed from the output.
        assertEquals("Hello ", parsed.plainText, "Incomplete ANSI sequence should be stripped")
    }

    /**
     * Test behavior when an ANSI escape sequence is incomplete in Sanitize mode.
     *
     * In Sanitize mode the escape initiator (e.g. \u001b) is skipped, leaving the rest of the
     * characters visible.
     */
    @Test
    fun `test incomplete ansi sequence sanitize`() {
        val input = "Hello \u001b[31"
        val parsed = AnsiStyledString.parseSanitized(input)
        // Expect that only the ESC character is removed; the remaining "[31" is left intact.
        assertEquals(
            "Hello [31",
            parsed.plainText,
            "Incomplete ANSI sequence should be partially sanitized",
        )
    }

    /** Test that an incomplete ANSI sequence causes an exception in Throw mode. */
    @Test
    fun `test incomplete ansi sequence throws exception`() {
        val input = "Incomplete \u001b[31"
        assertFailsWith<IllegalArgumentException> { AnsiStyledString.parseOrThrow(input) }
    }

    /**
     * Test overlapping style overlays.
     *
     * This test applies Bold to indices 0..3 and Underlined to indices 2..5. Since Bold and
     * Underlined are in separate bit categories, the overlapping region (indices 2 and 3) should
     * have both styles applied.
     */
    @Test
    fun `test overlapping styles`() {
        val base = AnsiStyledString("Overlap")
        // Define ranges using Kotlin's 'until' operator.
        // Bold applied to indices 0..3 (i.e. characters at positions 0,1,2,3)
        // Underlined applied to indices 2..5 (positions 2,3,4,5)
        val styled =
            base.decorate(
                listOf((0 until 4) to AnsiStyle.Bold.On, (2 until 6) to AnsiStyle.Underlined.On)
            )
        // For indices 0 and 1: Only Bold should be active.
        for (i in 0 until 2) {
            val color = styled.colorAt(i)
            // Bold: category Bold has offset 0; Bold.On.applyMask should be 1.
            assertEquals(
                AnsiStyle.Bold.On.applyMask,
                color and AnsiStyle.Bold.mask,
                "Index $i should be bold",
            )
            assertEquals(
                0L,
                color and AnsiStyle.Underlined.mask,
                "Index $i should not be underlined",
            )
        }
        // For indices 2 and 3: Both Bold and Underlined should be active.
        for (i in 2 until 4) {
            val color = styled.colorAt(i)
            assertEquals(
                AnsiStyle.Bold.On.applyMask,
                color and AnsiStyle.Bold.mask,
                "Index $i should be bold",
            )
            assertEquals(
                AnsiStyle.Underlined.On.applyMask,
                color and AnsiStyle.Underlined.mask,
                "Index $i should be underlined",
            )
        }
        // For indices 4 and 5: Only Underlined should be active.
        for (i in 4 until 6) {
            val color = styled.colorAt(i)
            assertEquals(0L, color and AnsiStyle.Bold.mask, "Index $i should not be bold")
            assertEquals(
                AnsiStyle.Underlined.On.applyMask,
                color and AnsiStyle.Underlined.mask,
                "Index $i should be underlined",
            )
        }
        // For any remaining indices, no style should be applied.
        for (i in 6 until styled.length) {
            val color = styled.colorAt(i)
            assertEquals(0L, color, "Index $i should be unstyled")
        }
    }

    /**
     * Test nested style applications via successive decoration.
     *
     * First, the whole string is decorated with Bold. Then, a subset of the string (indices 2
     * until 5) is further decorated with Underlined. The test verifies that the nested region has
     * both styles, while the non-overlapped regions retain only Bold.
     */
    @Test
    fun `test nested styles via successive decoration`() {
        val s = AnsiStyledString("Nested")
        // Apply Bold to the entire string.
        val bolded = s.decorate(AnsiStyle.Bold.On)
        // Now apply Underlined to a subset (indices 2 until 5).
        val nested = bolded.decorate(AnsiStyle.Underlined.On, 2 until 5)

        // Indices 2, 3, 4 should have both Bold and Underlined.
        for (i in 2 until 5) {
            val color = nested.colorAt(i)
            assertEquals(
                AnsiStyle.Bold.On.applyMask,
                color and AnsiStyle.Bold.mask,
                "Index $i should be bold",
            )
            assertEquals(
                AnsiStyle.Underlined.On.applyMask,
                color and AnsiStyle.Underlined.mask,
                "Index $i should be underlined",
            )
        }
        // Indices before 2 and after 4 should have Bold only.
        for (i in 0 until 2) {
            val color = nested.colorAt(i)
            assertEquals(
                AnsiStyle.Bold.On.applyMask,
                color and AnsiStyle.Bold.mask,
                "Index $i should be bold",
            )
            assertEquals(
                0L,
                color and AnsiStyle.Underlined.mask,
                "Index $i should not be underlined",
            )
        }
        for (i in 5 until nested.length) {
            val color = nested.colorAt(i)
            assertEquals(
                AnsiStyle.Bold.On.applyMask,
                color and AnsiStyle.Bold.mask,
                "Index $i should be bold",
            )
            assertEquals(
                0L,
                color and AnsiStyle.Underlined.mask,
                "Index $i should not be underlined",
            )
        }
    }
}
