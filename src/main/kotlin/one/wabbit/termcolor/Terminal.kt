package one.wabbit.termcolor

object Terminal {
    // Check if the NO_COLOR env variable is set (nonempty) 
    private val noColorEnv: Boolean = System.getenv("NO_COLOR")?.isNotEmpty() == true
    // Check if TERM is set to "dumb"
    private val termDumbEnv: Boolean = System.getenv("TERM") == "dumb"
    // Normalize OS name using Kotlin's lowercase()
    private val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("windows")
    private val isCygwin: Boolean = System.getenv("OSTYPE")?.lowercase()?.contains("cygwin") == true
    private val isMsys: Boolean = System.getenv("OSTYPE")?.lowercase()?.contains("msys") == true
    // On Windows Terminal, WT_SESSION is set
    private val isWindowsTerminal: Boolean = System.getenv("WT_SESSION")?.isNotEmpty() == true
    
    /**
     * Returns true if the current environment appears to support ANSI colors.
     * It checks for NO_COLOR, dumb terminals, and uses System.console() if available.
     * On Windows, it also returns true if running in Windows Terminal.
     */
    val supportsColor: Boolean = !noColorEnv && !termDumbEnv &&
            (System.console() != null || isWindowsTerminal)
}