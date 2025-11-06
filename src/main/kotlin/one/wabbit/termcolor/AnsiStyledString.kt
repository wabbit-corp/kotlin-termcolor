package one.wabbit.termcolor

import java.util.Arrays

typealias EncodedAnsiStyle = Long

/**
 * Represents a string with associated ANSI colors and text decorations.
 *
 * The [AnsiStyledString] class is the primary data type for working with colored strings. It
 * provides methods for manipulating and rendering the string with ANSI escape codes.
 *
 * @property chars The character array representing the string.
 * @property colors The color array representing the colors associated with each character.
 */
class AnsiStyledString
internal constructor(private val chars: CharArray, private val colors: LongArray) {
    init {
        require(chars.size == colors.size)
    }

    /**
     * An [AnsiStyledString]'s `color`s array is filled with Long, each representing the ANSI state
     * of one character encoded in its bits. Each [AnsiStyle] belongs to a [AnsiStyle.Category] that
     * occupies a range of bits within each long:
     *
     * 61... 55 54 53 52 51 .... 31 30 29 28 27 26 25 ..... 6 5 4 3 2 1 0 |--------|
     * |-----------------------| |-----------------------| | | |bold | | | | |reversed | | |
     * |underlined | | |foreground-color | |background-color |unused
     *
     * The `0000 0000 0000 0000` long corresponds to plain text with no decoration
     */

    /** Returns the plain-text length of the string. */
    val length: Int
        get() = chars.size

    /**
     * Returns the plain-text representation of this string without ANSI escape codes.
     *
     * @return The plain-text string.
     */
    val plainText by lazy { String(chars) }

    /**
     * Returns the color of the string at the specified index.
     *
     * @param i The index of the character.
     * @return The color of the character at the specified index.
     */
    fun colorAt(i: Int): EncodedAnsiStyle = colors[i]

    /**
     * Returns a copy of the color array backing this string.
     *
     * @return A copy of the color array.
     */
    fun colors(): LongArray = colors.clone()

    /**
     * Returns a copy of the character array backing this string.
     *
     * @return A copy of the character array.
     */
    fun chars(): CharArray = chars.clone()

    /**
     * Returns the character at the specified index.
     *
     * @param i The index of the character.
     * @return The character at the specified index.
     */
    fun charAt(i: Int): Char = chars[i]

    override fun hashCode(): Int = chars.contentHashCode() * 31 + colors.contentHashCode()

    override fun equals(other: Any?): Boolean =
        when (other) {
            is AnsiStyledString ->
                chars.contentEquals(other.chars) && colors.contentEquals(other.colors)
            else -> false
        }

    /**
     * Concatenates this string with another string.
     *
     * @param other The string to concatenate.
     * @return A new string that is the concatenation of this string and the other string.
     */
    operator fun plus(other: AnsiStyledString): AnsiStyledString {
        val chars2 = CharArray(length + other.length)
        val colors2 = LongArray(length + other.length)
        System.arraycopy(chars, 0, chars2, 0, length)
        System.arraycopy(other.chars, 0, chars2, length, other.length)
        System.arraycopy(colors, 0, colors2, 0, length)
        System.arraycopy(other.colors, 0, colors2, length, other.length)
        return AnsiStyledString(chars2, colors2)
    }

    /**
     * Splits the string into two substrings at the specified index.
     *
     * @param index The index at which to split the string.
     * @return A pair of strings representing the substrings before and after the split index.
     */
    fun splitAt(index: Int): Pair<AnsiStyledString, AnsiStyledString> {
        require(index in 0..length) { "split index [$index] must be between 0 and length:$length" }
        val left =
            AnsiStyledString(
                Arrays.copyOfRange(chars, 0, index),
                Arrays.copyOfRange(colors, 0, index),
            )
        val right =
            AnsiStyledString(
                Arrays.copyOfRange(chars, index, length),
                Arrays.copyOfRange(colors, index, length),
            )
        return Pair(left, right)
    }

    /**
     * Returns a substring of this string.
     *
     * @param start The starting index of the substring (inclusive).
     * @param end The ending index of the substring (exclusive).
     * @return A new string that is a substring of this string.
     */
    fun substring(start: Int = 0, end: Int = length): AnsiStyledString {
        require(start in 0..length) {
            "substring start parameter [$start] must be between 0 and length:$length"
        }
        require(end in start..length) {
            "substring end parameter [$end] must be between start $start and length:$length"
        }
        return AnsiStyledString(
            Arrays.copyOfRange(chars, start, end),
            Arrays.copyOfRange(colors, start, end),
        )
    }

    /**
     * Renders this string with ANSI escape codes.
     *
     * @return The string with ANSI escape codes included. Safe to print to a terminal and
     *   concatenate with plain strings.
     */
    override fun toString(): String = render()

    /**
     * Renders this string with ANSI escape codes.
     *
     * @return The string with ANSI escape codes included. Safe to print to a terminal and
     *   concatenate with plain strings.
     */
    fun render(): String {
        // Pre-size StringBuilder with approximate size (ansi colors tend
        // to be about 5 chars long) to avoid re-allocations during growth
        val output = StringBuilder(chars.size + colors.size * 5)

        var currentState: EncodedAnsiStyle = 0

        // Make a local array copy of the immutable Vector, for maximum performance
        // since the Vector is small and we'll be looking it up over & over & over
        val categoryArray = AnsiStyle.categories.toTypedArray()

        var i = 0
        while (i < colors.size) {
            // Emit ANSI escapes to change colors where necessary
            // fast-path optimization to check for integer equality first before
            // going through the whole `enableDiff` rigmarole
            if (colors[i] != currentState) {
                AnsiStyle.emitAnsiCodes0(currentState, colors[i], output, categoryArray)
                currentState = colors[i]
            }
            output.append(chars[i])
            i += 1
        }

        // Cap off the end of the rendered output with the escapes needed to reset state to 0
        AnsiStyle.emitAnsiCodes0(currentState, 0, output, categoryArray)

        return output.toString()
    }

    /**
     * Overlays the specified attributes onto a range of the string.
     *
     * @param attrs The attributes to overlay.
     * @param start The starting index of the range (inclusive).
     * @param end The ending index of the range (exclusive).
     * @return A new string with the attributes overlaid on the specified range.
     */
    fun decorate(attrs: AnsiStyle, start: Int = 0, end: Int = length): AnsiStyledString =
        decorate(listOf((start..<end) to attrs))

    fun decorate(attrs: AnsiStyle, range: IntRange): AnsiStyledString =
        decorate(listOf(range to attrs))

    /**
     * Batch version of [overlays], letting you apply a bunch of [AnsiStyle] onto various parts of
     * the same string in one operation, avoiding the unnecessary copying that would happen if you
     * applied them with [overlays] one by one.
     *
     * The input sequence of overlay-tuples is applied from left to right
     */
    fun decorate(overlays: List<Pair<IntRange, AnsiStyle>>): AnsiStyledString {
        val colorsOut = colors.clone()
        for (value in overlays) {
            val (range, attrs) = value
            val start = range.first
            val end = range.last + 1
            require(end >= start) { "end:$end must be greater than start:$start in overlay call" }
            require(start >= 0) { "start:$start must be greater than or equal to 0" }
            require(end <= colors.size) {
                "end:$end must be less than or equal to length:${colors.size}"
            }

            var i = start
            while (i < end) {
                colorsOut[i] = attrs.transform(colorsOut[i])
                i += 1
            }
        }
        return AnsiStyledString(chars, colorsOut)
    }

    /**
     * Used to control what kind of behavior you get if a `CharSequence` you are trying to parse
     * into a [[AnsiStyledString]] contains an Ansi escape not recognized as a valid color.
     */
    sealed interface ErrorMode {
        /**
         * Given an unknown Ansi escape was found at `sourceIndex` inside your `raw: CharSequence`,
         * what index should you resume parsing at?
         */
        fun handle(sourceIndex: Int, raw: CharSequence): Int =
            when (this) {
                is Throw -> {
                    val match = ansiRegex.find(raw, sourceIndex)
                    val detail =
                        if (match == null) {
                            ""
                        } else {
                            val end = match.range.last + 1
                            " " + raw.subSequence(sourceIndex + 1, end)
                        }

                    throw IllegalArgumentException(
                        "Unknown ansi-escape$detail at index $sourceIndex " +
                            "inside string cannot be parsed into an Str"
                    )
                }
                is Sanitize -> sourceIndex + 1
                is Strip ->
                    ansiRegex.find(raw, sourceIndex)?.range?.last?.let { it + 1 } ?: raw.length
            }

        /** Throw an exception and abort the parse */
        data object Throw : ErrorMode

        /**
         * Skip the `\u001b` that kicks off the unknown Ansi escape but leave subsequent characters
         * in place, so the end-user can see that an Ansi escape was entered e.g. via the [A[B[A[C
         * that appears in the result
         */
        data object Sanitize : ErrorMode

        /**
         * Find the end of the unknown Ansi escape and skip over its characters entirely, so no
         * trace of them appear in the parsed Str.
         */
        data object Strip : ErrorMode
    }

    companion object {
        val empty = AnsiStyledString("")

        /**
         * Make the construction of [[AnsiStyledString]]s from `String`s and other `CharSequence`s
         * automatic
         */
        operator fun invoke(raw: CharSequence): AnsiStyledString = parse(raw)

        operator fun invoke(vararg raw: CharSequence): AnsiStyledString =
            parse(raw.joinToString(separator = "") { it })

        operator fun invoke(vararg args: AnsiStyledString): AnsiStyledString = join(args.toList())

        /**
         * Regex that can be used to identify Ansi escape patterns in a string. Found from:
         * http://stackoverflow.com/a/33925425/871202 Which references:
         * http://www.ecma-international.org/publications/files/ECMA-ST/Ecma-048.pdf Section 5.4:
         * Control Sequences
         */
        private val ansiRegex = Regex("(\u009b|\u001b\\[)[0-?]*[ -\\/]*[@-~]")

        // 16 basic + 256 extended colors, so 273 is the next offset for true color
        private const val TRUECOLOR_BASE_OFFSET = 273L

        /** Shorthand constructor with ErrorMode.Sanitize */
        fun parseSanitized(raw: CharSequence) = parse(raw, ErrorMode.Sanitize)

        /**
         * Parses a string with ANSI escape codes and returns a new string with the invalid codes
         * stripped.
         *
         * @param raw The string to parse.
         * @return A new string with ANSI escape codes stripped.
         */
        fun parseStripped(raw: CharSequence) = parse(raw, ErrorMode.Strip)

        /**
         * Parses a string with ANSI escape codes and returns a new string, throwing an exception if
         * an invalid code is encountered.
         *
         * @param raw The string to parse.
         * @return A new string with ANSI escape codes parsed.
         * @throws IllegalArgumentException If an invalid ANSI escape code is encountered.
         */
        fun parseOrThrow(raw: CharSequence) = parse(raw, ErrorMode.Throw)

        /**
         * Creates an [AnsiStyledString] from a [kotlin.String] or other [CharSequence].
         *
         * Note that this method is implicit, meaning you can pass in a [kotlin.String] anywhere an
         * [AnsiStyledString] is required, and it will be automatically parsed and converted for
         * you.
         *
         * @param errorMode Used to control what kind of behavior you get if the input
         *   `CharSequence` contains an Ansi escape not recognized as a valid color.
         */
        fun parse(raw: CharSequence, errorMode: ErrorMode = ErrorMode.Throw): AnsiStyledString {
            // Pre-allocate some arrays for us to fill up. They will probably be
            // too big if the input has any ansi codes at all but that's ok, we'll
            // trim them later.
            val chars = CharArray(raw.length)
            val colors = LongArray(raw.length)

            var currentColor = 0L
            var sourceIndex = 0
            var destIndex = 0
            val length = raw.length

            while (sourceIndex < length) {
                val char = raw[sourceIndex]

                if (char != '\u001b' && char != '\u009b') {
                    colors[destIndex] = currentColor
                    chars[destIndex] = char
                    sourceIndex += 1
                    destIndex += 1
                    continue
                }

                val escapeStartSourceIndex = sourceIndex
                val tuple = ParseMap.get(raw, escapeStartSourceIndex)

                if (tuple == null) {
                    sourceIndex = errorMode.handle(sourceIndex, raw)
                    continue
                }

                val newIndex = tuple.first
                val styleOrCategory = tuple.second

                when (styleOrCategory) {
                    is Either.Left -> {
                        currentColor = styleOrCategory.value.transform(currentColor)
                        sourceIndex += newIndex
                    }

                    is Either.Right -> {
                        val category = styleOrCategory.value

                        // Gross manual char-by-char parsing of the remainder
                        // of the True-color escape, to maximize performance
                        sourceIndex += newIndex

                        fun isDigit(index: Int) =
                            index < raw.length && raw[index] >= '0' && raw[index] <= '9'

                        fun checkChar(index: Int, char: Char) =
                            index < raw.length && raw[index] == char

                        fun fail() {
                            sourceIndex = errorMode.handle(escapeStartSourceIndex, raw)
                        }

                        fun getNumber(): Int {
                            var value = 0
                            var count = 0
                            while (isDigit(sourceIndex) && count < 3) {
                                value = value * 10 + (raw[sourceIndex] - '0').toInt()
                                sourceIndex += 1
                                count += 1
                            }
                            return value
                        }

                        if (!isDigit(sourceIndex)) {
                            fail()
                            continue
                        }
                        val r = getNumber()
                        if (r !in 0..255) {
                            fail()
                            continue
                        }

                        if (!checkChar(sourceIndex, ';')) {
                            fail()
                            continue
                        }
                        sourceIndex += 1

                        if (!isDigit(sourceIndex)) {
                            fail()
                            continue
                        }
                        val g = getNumber()
                        if (g !in 0..255) {
                            fail()
                            continue
                        }
                        if (!checkChar(sourceIndex, ';')) {
                            fail()
                            continue
                        }
                        sourceIndex += 1

                        if (!isDigit(sourceIndex)) {
                            fail()
                            continue
                        }
                        val b = getNumber()
                        if (b !in 0..255) {
                            fail()
                            continue
                        }

                        if (!checkChar(sourceIndex, 'm')) {
                            fail()
                            continue
                        }
                        sourceIndex += 1

                        // Manually perform the `transform` for perf to avoid
                        // calling `True` which instantiates/allocaties an `Attr`
                        currentColor =
                            (currentColor and category.mask.inv()) or
                                ((TRUECOLOR_BASE_OFFSET + category.trueIndex(r, g, b)) shl
                                    category.offset)
                    }
                }
            }

            return AnsiStyledString(
                Arrays.copyOfRange(chars, 0, destIndex),
                Arrays.copyOfRange(colors, 0, destIndex),
            )
        }

        /**
         * Creates a new string from arrays of characters and colors.
         *
         * @param chars The array of characters.
         * @param colors The array of colors corresponding to each character.
         * @return A new string created from the arrays.
         */
        fun fromArrays(chars: CharArray, colors: LongArray): AnsiStyledString =
            AnsiStyledString(chars.clone(), colors.clone())

        /**
         * Joins multiple strings into a single string with an optional separator.
         *
         * @param args The strings to join.
         * @param sep The separator string to use between the joined strings.
         * @return A new string that is the concatenation of the input strings with the separator.
         */
        fun join(
            args: Iterable<AnsiStyledString>,
            sep: AnsiStyledString = empty,
        ): AnsiStyledString {
            if (args.none()) return empty

            val totalLength = args.sumOf { it.length + sep.length } - sep.length

            val chars = CharArray(totalLength)
            val colors = LongArray(totalLength)
            var j = 0

            fun append(str: AnsiStyledString) {
                for (i in 0..<str.length) {
                    chars[j] = str.chars[i]
                    colors[j] = str.colors[i]
                    j += 1
                }
            }

            for (arg in args) {
                if (j != 0) append(sep)
                append(arg)
            }
            return fromArrays(chars, colors)
        }

        internal sealed interface Either<out A, out B> {
            data class Left<A>(val value: A) : Either<A, Nothing>

            data class Right<B>(val value: B) : Either<Nothing, B>
        }

        internal val ParseMap: Trie<Either<AnsiStyle, AnsiStyle.ColorCategory>> = run {
            val pairs =
                AnsiStyle.categories.flatMap { cat ->
                    cat.all.mapNotNull { color ->
                        val str = color.escapeOpt ?: return@mapNotNull null
                        str to Either.Left(color)
                    }
                }

            val reset = listOf(AnsiCodes.RESET to Either.Left(AnsiStyle.Reset))

            val trueColors =
                listOf(
                    "\u001b[38;2;" to Either.Right(AnsiStyle.ForegroundColor),
                    "\u001b[48;2;" to Either.Right(AnsiStyle.BackgroundColor),
                )

            return@run Trie(pairs + reset + trueColors)
        }
    }
}
