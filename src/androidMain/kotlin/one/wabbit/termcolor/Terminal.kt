package one.wabbit.termcolor

actual object Terminal {
    private val noColorEnv: Boolean = System.getenv("NO_COLOR")?.isNotEmpty() == true
    private val termDumbEnv: Boolean = System.getenv("TERM") == "dumb"
    private val isWindowsTerminal: Boolean = System.getenv("WT_SESSION")?.isNotEmpty() == true

    actual val supportsColor: Boolean =
        !noColorEnv && !termDumbEnv && (System.console() != null || isWindowsTerminal)
}
