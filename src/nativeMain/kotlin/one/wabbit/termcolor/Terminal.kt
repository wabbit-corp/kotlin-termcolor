package one.wabbit.termcolor

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
actual object Terminal {
    private fun env(name: String): String? = getenv(name)?.toKString()

    private val noColorEnv: Boolean = env("NO_COLOR")?.isNotEmpty() == true
    private val termDumbEnv: Boolean = env("TERM") == "dumb"
    private val isWindowsTerminal: Boolean = env("WT_SESSION")?.isNotEmpty() == true

    actual val supportsColor: Boolean =
        !noColorEnv && (!termDumbEnv || isWindowsTerminal)
}
