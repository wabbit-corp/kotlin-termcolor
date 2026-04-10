package one.wabbit.termcolor

/**
 * Best-effort terminal capability detection for the current runtime.
 *
 * Multiplatform targets provide their own implementation, but callers should still treat this as
 * advisory rather than authoritative.
 */
expect object Terminal {
    val supportsColor: Boolean
}
