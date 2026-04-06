package cli

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*

object Logs {
    // Indicates if a task is currently being logged.
    var task = false
        private set

    // Indicates if the current task is being finished.
    private var taskFinish = false

    /**
     * Starts logging a new task. If a previous task wasn't finished, 
     * it force-closes the previous one to keep the UI clean.
     */
    fun task(msg: String) {
        if (task) forceFinish() 
        println(" ${brightMagenta("╓")} ${brightGreen(bold(msg))}")
        task = true
    }

    fun finish(): Logs {
        taskFinish = true
        return this
    }

    /**
     * Internal function to handle the ASCII borders.
     */
    private fun log(message: String) {
        if (task) {
            if (taskFinish) {
                println(" ${brightMagenta("╙")} $message")
                resetState()
            } else {
                println(" ${brightMagenta("║")} $message")
            }
        } else {
            println(message)
        }
    }

    private fun resetState() {
        taskFinish = false
        task = false
    }

    /**
     * Closes a task line if an error occurred before finish() was called.
     */
    fun forceFinish() {
        if (task) {
            println(" ${brightMagenta("╙")} ${brightRed("Task interrupted/failed")}")
            resetState()
        }
    }

    fun info(message: String) = log(brightWhite(message))
    fun warn(message: String) = log(brightYellow(message))
    fun error(message: String) = log(brightRed(message))
}
