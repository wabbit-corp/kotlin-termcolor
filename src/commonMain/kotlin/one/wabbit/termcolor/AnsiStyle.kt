package one.wabbit.termcolor

/**
 * Represents one or more [[AnsiStyle]]s, that can be passed around as a set or combined with other
 * sets of [[AnsiStyle]]s.
 */
sealed interface AnsiStyle {
    /**
     * Which bits of the [[EncodedAnsiStyle]] integer these [[AnsiStyle]] will override when it is
     * applied
     */
    val resetMask: EncodedAnsiStyle

    /**
     * Which bits of the [[EncodedAnsiStyle]] integer these [[AnsiStyle]] will set to `1` when it is
     * applied
     */
    val applyMask: EncodedAnsiStyle

    /**
     * Apply the current [[AnsiStyle]] to the [[EncodedAnsiStyle]] integer, modifying it to
     * represent the state after all changes have taken effect
     */
    fun transform(state: EncodedAnsiStyle): EncodedAnsiStyle =
        (state and resetMask.inv()) or applyMask

    /**
     * Combine this [[AnsiStyle]] with other [[AnsiStyle]]s, returning one which when applied is
     * equivalent to applying this one and then the `other` one in series.
     */
    operator fun plus(other: AnsiStyle): AnsiStyle =
        when (this) {
            is Combined ->
                when (other) {
                    is Combined -> AnsiStyle(this.attrs + other.attrs)
                    is Single -> AnsiStyle(this.attrs + other)
                }
            is Single ->
                when (other) {
                    is Combined -> AnsiStyle(listOf(this) + other.attrs)
                    is Single -> AnsiStyle(listOf(this, other))
                }
    }

    companion object {
        val Empty = AnsiStyle(emptyList())

        /**
         * Represents the removal of all ansi text decoration. Doesn't fit into any convenient
         * category, since it applies to them all.
         */
        val Reset: AnsiStyle =
            EscapeAnsiStyle(AnsiCodes.RESET, Long.MAX_VALUE, 0, "Escape")

        /** A list of possible categories */
        val categories = listOf(ForegroundColor, BackgroundColor, Bold, Underlined, Reversed)

        /**
         * Emit the ansi escapes necessary to transition between two states, if necessary, as a
         * `java.lang.String`
         */
        fun emitAnsiCodes(currentState: EncodedAnsiStyle, nextState: EncodedAnsiStyle): String {
            val output = StringBuilder()
            val categoryArray = categories.toTypedArray()
            emitAnsiCodes0(currentState, nextState, output, categoryArray)
            return output.toString()
        }

        /**
         * Messy-but-fast version of [[emitAnsiCodes]] that avoids allocating things unnecessarily.
         * Reads its category listing from a fast Array version of [[categories]] and writes it's
         * output to a mutable `StringBuilder`
         */
        fun emitAnsiCodes0(
            currentState: EncodedAnsiStyle,
            nextState: EncodedAnsiStyle,
            output: StringBuilder,
            categoryArray: Array<Category>,
        ) {
            if (currentState != nextState) {
                val hardOffMask = Bold.mask
                // Any of these transitions from 1 to 0 within the hardOffMask
                // categories cannot be done with a single ansi escape, and need
                // you to emit a RESET followed by re-building whatever ansi state
                // you previous had from scratch
                val currentState2 =
                    if ((currentState and nextState.inv() and hardOffMask) != 0L) {
                        output.append(AnsiCodes.RESET)
                        0L
                    } else {
                        currentState
                    }

                var categoryIndex = 0
                while (categoryIndex < categoryArray.size) {
                    val cat = categoryArray[categoryIndex]
                    if ((cat.mask and currentState2) != (cat.mask and nextState)) {
                        val escape = cat.lookupEscape(nextState and cat.mask)
                        output.append(escape)
                    }
                    categoryIndex += 1
                }
            }
        }

        operator fun invoke(attrs: List<Single>): AnsiStyle {
            val output = mutableListOf<Single>()
            var resetMask = 0L
            var applyMask = 0L

            // Walk the list of attributes backwards, and aggregate only those whose
            // `resetMask` is not going to get totally covered by the union of all
            // `resetMask`s that come after it.
            //
            // Simultaneously build up the `applyMask`, which is the `applyMask` of
            // all aggregated `attr`s whose own `applyMask` is not totally covered by
            // the union of all `resetMask`s that come after.
            for (attr in attrs.reversed()) {
                if ((attr.resetMask and resetMask.inv()) == 0L) continue
                if ((attr.applyMask and resetMask) == 0L) {
                    applyMask = applyMask or attr.applyMask
                }
                resetMask = resetMask or attr.resetMask
                output.add(attr)
            }

            return if (output.size == 1) {
                output.first()
            } else {
                Combined(resetMask, applyMask, output.reversed())
            }
        }
    }

    @ConsistentCopyVisibility
    private data class Combined
    internal constructor(
        override val resetMask: Long,
        override val applyMask: Long,
        val attrs: List<Single>,
    ) : AnsiStyle {
        init {
            require(attrs.size != 1)
        }

        override fun toString(): String = "Attr(${attrs.joinToString(",")})"
    }

    /**
     * Represents a single, atomic ANSI escape sequence that results in a color, background or
     * decoration being added to the output. May or may not have an escape sequence (`escapeOpt`),
     * as some attributes (e.g. [[Bold.Off]]) are not widely/directly supported by terminals and so
     * Str supports them by rendering a hard [[AnsiStyle.Reset]] and then re-rendering other
     * [[AnsiStyle]]s that are active.
     *
     * Many of the codes were stolen shamelessly from
     *
     * http://misc.flogisoft.com/bash/tip_colors_and_formatting
     */
    sealed interface Single : AnsiStyle {
        /** escapeOpt the actual ANSI escape sequence corresponding to this Attr */
        val escapeOpt: String?

        val name: String
    }

    /** An [[AnsiStyle]] represented by an escape sequence */
    class EscapeAnsiStyle
    internal constructor(
        override val escapeOpt: String,
        override val resetMask: Long,
        override val applyMask: Long,
        override val name: String,
    ) : Single {
        override fun toString(): String = escapeOpt + name + AnsiCodes.RESET
    }

    /** An [[AnsiStyle]] for which no escape sequence exists */
    class ResetAnsiStyle
    internal constructor(
        override val resetMask: Long,
        override val applyMask: Long,
        override val name: String,
    ) : Single {
        override val escapeOpt = null

        override fun toString(): String = name
    }

    /** Represents a set of [[AnsiStyle]]s all occupying the same bit-space in the state `Int` */
    sealed class Category(val offset: Int, val width: Int, val catName: String) {
        val mask: Long = ((1L shl width) - 1) shl offset
        abstract val all: List<Single>

        open fun lookupEscape(applyState: Long): String {
            val escapeOpt = lookupAttr(applyState).escapeOpt
            return escapeOpt ?: ""
        }

        open fun lookupAttr(applyState: Long): Single =
            lookupAttrTable[(applyState shr offset).toInt()]!!

        // Allows fast lookup of categories based on the desired applyState
        internal open val lookupTableWidth = 1 shl width

        internal val lookupAttrTable by lazy {
            val arr = Array<Single?>(lookupTableWidth) { null }
            for (attr in all) {
                arr[(attr.applyMask shr offset).toInt()] = attr
            }
            arr
        }

        fun makeAttr(s: String, applyValue: Long, name: String) =
            EscapeAnsiStyle(s, mask, applyValue shl offset, "$catName.$name")

        fun makeNoneAttr(applyValue: Long, name: String) =
            ResetAnsiStyle(mask, applyValue shl offset, "$catName.$name")
    }

    /** [[AnsiStyle]]s to turn text bold/bright or disable it. */
    object Bold : Category(offset = 0, width = 1, catName = "Bold") {
        val On = makeAttr(AnsiCodes.BOLD, 1, "On")
        val Off = makeNoneAttr(0, "Off")
        override val all = listOf(On, Off)
    }

    /**
     * [[AnsiStyle]]s to reverse the background/foreground colors of your text, or un-reverse them.
     */
    object Reversed : Category(offset = 1, width = 1, "Reversed") {
        val On = makeAttr(AnsiCodes.REVERSED, 1, "On")
        val Off = makeAttr("\u001b[27m", 0, "Off")
        override val all = listOf(On, Off)
    }

    /** [[AnsiStyle]]s to enable or disable underlined text. */
    object Underlined : Category(offset = 2, width = 1, "Underlined") {
        val On = makeAttr(AnsiCodes.UNDERLINED, 1, "On")
        val Off = makeAttr("\u001b[24m", 0, "Off")
        override val all = listOf(On, Off)
    }

    /** [[AnsiStyle]]s to set or reset the color of your foreground text */
    object ForegroundColor : ColorCategory(offset = 3, width = 25, colorCode = 38, "Color") {
        val Reset = makeAttr("\u001b[39m", 0, "Reset")
        val Black = makeAttr(AnsiCodes.BLACK, 1, "Black")
        val Red = makeAttr(AnsiCodes.RED, 2, "Red")
        val Green = makeAttr(AnsiCodes.GREEN, 3, "Green")
        val Yellow = makeAttr(AnsiCodes.YELLOW, 4, "Yellow")
        val Blue = makeAttr(AnsiCodes.BLUE, 5, "Blue")
        val Magenta = makeAttr(AnsiCodes.MAGENTA, 6, "Magenta")
        val Cyan = makeAttr(AnsiCodes.CYAN, 7, "Cyan")
        val LightGray = makeAttr("\u001b[37m", 8, "LightGray")
        val DarkGray = makeAttr("\u001b[90m", 9, "DarkGray")
        val LightRed = makeAttr("\u001b[91m", 10, "LightRed")
        val LightGreen = makeAttr("\u001b[92m", 11, "LightGreen")
        val LightYellow = makeAttr("\u001b[93m", 12, "LightYellow")
        val LightBlue = makeAttr("\u001b[94m", 13, "LightBlue")
        val LightMagenta = makeAttr("\u001b[95m", 14, "LightMagenta")
        val LightCyan = makeAttr("\u001b[96m", 15, "LightCyan")
        val White = makeAttr("\u001b[97m", 16, "White")

        override val all =
            listOf(
                Reset,
                Black,
                Red,
                Green,
                Yellow,
                Blue,
                Magenta,
                Cyan,
                LightGray,
                DarkGray,
                LightRed,
                LightGreen,
                LightYellow,
                LightBlue,
                LightMagenta,
                LightCyan,
                White,
            ) + Full
    }

    /** [[AnsiStyle]]s to set or reset the color of your background */
    object BackgroundColor : ColorCategory(offset = 28, width = 25, colorCode = 48, "Back") {
        val Reset = makeAttr("\u001b[49m", 0, "Reset")
        val Black = makeAttr(AnsiCodes.BLACK_B, 1, "Black")
        val Red = makeAttr(AnsiCodes.RED_B, 2, "Red")
        val Green = makeAttr(AnsiCodes.GREEN_B, 3, "Green")
        val Yellow = makeAttr(AnsiCodes.YELLOW_B, 4, "Yellow")
        val Blue = makeAttr(AnsiCodes.BLUE_B, 5, "Blue")
        val Magenta = makeAttr(AnsiCodes.MAGENTA_B, 6, "Magenta")
        val Cyan = makeAttr(AnsiCodes.CYAN_B, 7, "Cyan")
        val LightGray = makeAttr("\u001b[47m", 8, "LightGray")
        val DarkGray = makeAttr("\u001b[100m", 9, "DarkGray")
        val LightRed = makeAttr("\u001b[101m", 10, "LightRed")
        val LightGreen = makeAttr("\u001b[102m", 11, "LightGreen")
        val LightYellow = makeAttr("\u001b[103m", 12, "LightYellow")
        val LightBlue = makeAttr("\u001b[104m", 13, "LightBlue")
        val LightMagenta = makeAttr("\u001b[105m", 14, "LightMagenta")
        val LightCyan = makeAttr("\u001b[106m", 15, "LightCyan")
        val White = makeAttr("\u001b[107m", 16, "White")

        override val all =
            listOf(
                Reset,
                Black,
                Red,
                Green,
                Yellow,
                Blue,
                Magenta,
                Cyan,
                LightGray,
                DarkGray,
                LightRed,
                LightGreen,
                LightYellow,
                LightBlue,
                LightMagenta,
                LightCyan,
                White,
            ) + Full
    }

    /**
     * * Color encoded on 25 bit as follow : 0 : reset value 1 - 16 : 3 bit colors 17 - 272 : 8 bit
     *   colors 273 - 16 777 388 : 24 bit colors
     */
    abstract class ColorCategory(offset: Int, width: Int, val colorCode: Int, catName: String) :
        Category(offset, width, catName) {
        /** 256 color [[AnsiStyle]]s, for those terminals that support it */
        val Full = (0..<256).map { x -> makeAttr("\u001b[$colorCode;5;${x}m", 17L + x, "Full($x)") }

        internal fun True0(r: Int, g: Int, b: Int, index: Int): EscapeAnsiStyle =
            makeAttr(trueRgbEscape(r, g, b), 273L + index, "True($r,$g,$b)")

        fun trueRgbEscape(r: Int, g: Int, b: Int) = "\u001b[$colorCode;2;$r;$g;${b}m"

        /**
         * Create a TrueColor color, from a given index within the 16-million-color TrueColor range
         */
        fun True(index: Int): EscapeAnsiStyle {
            require(0 <= index && index <= (1 shl 24)) {
                "True parameter `index` must be 273 <= index <= 16777488, not $index"
            }
            val r = index shr 16
            val g = (index and 0x00FF00) shr 8
            val b = index and 0x0000FF
            return True0(r, g, b, index)
        }

        /**
         * Create a TrueColor color, from a given (r, g, b) within the 16-million-color TrueColor
         * range
         */
        fun True(r: Int, g: Int, b: Int) = True0(r, g, b, trueIndex(r, g, b))

        fun trueIndex(r: Int, g: Int, b: Int): Int {
            require(r in 0..255) { "True parameter `r` must be 0 <= r < 256, not $r" }
            require(g in 0..255) { "True parameter `g` must be 0 <= r < 256, not $g" }
            require(b in 0..255) { "True parameter `b` must be 0 <= r < 256, not $b" }
            return (r shl 16) or (g shl 8) or b
        }

        override fun lookupEscape(applyState: Long): String {
            val rawIndex = (applyState shr offset).toInt()
            if (rawIndex < 273) return super.lookupEscape(applyState)
            val index = rawIndex - 273
            return trueRgbEscape(
                r = index shr 16,
                g = (index and 0x00FF00) shr 8,
                b = index and 0x0000FF,
            )
        }

        override fun lookupAttr(applyState: Long): Single {
            val index = (applyState shr offset).toInt()
            return if (index < 273) {
                lookupAttrTable[index]!!
            } else {
                True(index - 273)
            }
        }

        internal override val lookupTableWidth = 273
    }
}
