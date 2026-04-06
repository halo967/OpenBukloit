import injector.injectFunc
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // Validate that all required arguments are provided
    if (args.size < 4) {
        System.err.println("Usage: InjectMain <class> <method> <insert_code> <save_path>")
        exitProcess(1)
    }

    val clazz = args[0]  // Target class name
    val method = args[1] // Target method name
    val insert = args[2] // Code to insert (e.g., "{ Exploit.inject(this); }")
    val saveTo = args[3] // Path to save the modified class file

    try {
        // Attempt to inject the code into the target method
        // This uses the Javassist logic defined in PluginProcessor.kt
        injectFunc(clazz, method, insert, saveTo)
        exitProcess(0) 
    } catch (e: Throwable) {
        // Using Throwable instead of Exception to catch more severe linkage errors
        System.err.println("Injection failed: ${e.message}")
        e.printStackTrace() // Useful for debugging within the subprocess
        exitProcess(1) 
    }
}
