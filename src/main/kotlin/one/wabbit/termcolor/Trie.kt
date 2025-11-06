package one.wabbit.termcolor

/** A string trie for quickly looking up values of type [[T]] using string-keys. */
internal class Trie<T : Any>(strings: List<Pair<String, T>>) {
    private val min: Char
    private val max: Char
    private val arr: Array<Trie<T>?>
    private val value: T?

    init {
        require(strings.isNotEmpty()) { "Empty trie" }

        var value: T? = null
        var min: Int = Char.MAX_VALUE.code
        var max: Int = Char.MIN_VALUE.code
        var continued: Int = 0

        for ((k, v) in strings) {
            if (k.isEmpty()) {
                if (value != null) {
                    throw IllegalArgumentException("Duplicate key")
                }
                value = v
            } else {
                val c = k[0].code
                if (c < min) min = c
                if (c > max) max = c
                continued += 1
            }
        }

        this.min = min.toChar()
        this.max = max.toChar()
        this.value = value

        if (continued > 0) {
            val continuations = Array(max - min + 1) { mutableListOf<Pair<String, T>>() }
            for ((k, v) in strings) {
                if (k.isEmpty()) continue
                continuations[k[0].code - min].add(k.drop(1) to v)
            }
            this.arr =
                continuations
                    .map {
                        if (it.isNotEmpty()) {
                            Trie(it)
                        } else {
                            null
                        }
                    }
                    .toTypedArray()
        } else {
            this.arr = emptyArray()
        }
    }

    /** Returns the length of the matching string, or -1 if not found */
    fun get(input: CharSequence, index: Int = 0): Pair<Int, T>? {
        var currentNode = this
        var offset = index

        while (true) {
            val currentValue = currentNode.value
            if (currentValue != null) {
                return (offset - index) to currentValue
            }
            if (offset >= input.length) {
                return null
            }
            val next = currentNode[input[offset]] ?: return null
            currentNode = next
            offset++
        }
    }

    operator fun get(input: CharSequence): T? {
        val (length, value) = get(input, 0) ?: return null
        return if (length == input.length) value else null
    }

    operator fun get(c: Char): Trie<T>? {
        if (c > max || c < min) {
            return null
        } else {
            return this.arr[c - min]
        }
    }
}
