import cli.boolArg
import cli.findArg
import injector.process
import cli.Logs
import utils.PreparedExploit
import utils.loadExploit
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Paths
import java.nio.file.Files
import kotlin.system.exitProcess

fun run(args: Array<String>) {
    // 1. Load the Exploit Template
    val exploit = loadExploit(findArg("exploit", "e", args))
    val preparedExploit = PreparedExploit(exploit)
    
    // 2. Determine Mode and Paths
    val mode = findArg("mode", "m", args) ?: "multiple"
    if (mode !in listOf("multiple", "single")) throw Exception("Invalid mode: $mode. Use 'single' or 'multiple'.")
    
    val input = findArg("input", "i", args) ?: if (mode == "multiple") "in" else "in.jar"
    val output = findArg("output", "o", args) ?: if (mode == "multiple") "out" else "out.jar"
    
    val replace = boolArg("replace", "r", args)
    val traceErrors = boolArg("trace-errors", "tr", args)
    val noCamouflage = boolArg("no-camouflage", null, args)
    val className = findArg("class-name", null, args)
    val methodName = findArg("method-name", null, args)

    // 3. Collect Input Files
    val inputPath = Paths.get(input)
    if (!Files.exists(inputPath)) throw Exception("Input path does not exist: $input")

    val inputFiles = if (mode == "multiple") {
        if (!Files.isDirectory(inputPath)) throw Exception("Mode is 'multiple' but input '$input' is not a directory.")
        inputPath.toFile().listFiles()?.filter { it.extension == "jar" }?.map { it.toPath() } ?: emptyList()
    } else {
        if (Files.isDirectory(inputPath)) throw Exception("Mode is 'single' but input '$input' is a directory.")
        listOf(inputPath)
    }

    if (inputFiles.isEmpty()) {
        Logs.warn("No JAR files found to process.")
        return
    }

    // 4. Prepare Output Paths
    val outputFiles = if (mode == "multiple") {
        val outputDir = Paths.get(output)
        if (!Files.exists(outputDir)) Files.createDirectories(outputDir)
        inputFiles.map { outputDir.resolve(it.fileName) }
    } else {
        listOf(Paths.get(output))
    }

    // 5. Gather Exploit Parameters (if any exist in the bytecode)
    val exploitParams = mutableMapOf<String, String>()
    for (exploitParam in preparedExploit.params) {
        val value = findArg(exploitParam, args = args, last = true)
            ?: throw Exception("This exploit requires the parameter: --$exploitParam <value>")
        exploitParams[exploitParam] = value
    }

    // 6. Execution Loop
    for (i in inputFiles.indices) {
        try {
            // Generate a fresh class instance (with param replacement if applicable)
            val exploitClass = preparedExploit.make(exploitParams)
            
            process(
                inputFiles[i],
                outputFiles[i],
                exploitClass,
                replace,
                noCamouflage,
                className,
                methodName,
            )
        } catch (e: Exception) {
            handleError(e, traceErrors)
        }
    }
}

fun handleError(e: Exception, traceErrors: Boolean) {
    if (Logs.task) Logs.finish()
    Logs.error("Error: ${e.message}")
    
    if (traceErrors) {
        val stackTrace = ByteArrayOutputStream().use { buff ->
            PrintStream(buff).use { ps -> e.printStackTrace(ps) }
            buff.toString()
        }
        stackTrace.lines().filter { it.isNotBlank() }.forEach { Logs.error("  $it") }
    }
}

fun main(args: Array<String>) {
    val tempPath = Paths.get("./.openbukloit/temp")
    
    // Clean start: remove any junk from previous failed runs
    if (Files.exists(tempPath)) tempPath.toFile().deleteRecursively()
    Files.createDirectories(tempPath)
    
    try {
        run(args)
    } catch (e: Exception) {
        handleError(e, true)
    } finally {
        // Always clean up temp files on exit
        if (Files.exists(tempPath)) tempPath.toFile().deleteRecursively()
    }
}
