package utils

import java.io.File
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.random.Random

data class Camouflage(
    val className: String,
    val methodName: String,
)

fun splitCamelCase(s: String): List<String> {
    // Improved regex to handle acronyms and numbers more naturally
    return s.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])|(?<=[a-zA-Z])(?=[0-9])".toRegex())
}

fun uniqueName(dict: List<String>, files: List<String>, length: IntRange): String {
    var name: String
    var attempts = 0
    do {
        name = (1..Random.nextInt(length.first, length.last + 1))
            .map { dict[Random.nextInt(dict.size)] }
            .joinToString("")
            .replaceFirstChar { it.uppercase() } // Classes should be Uppercase

        if (attempts > 20) name += Random.nextInt(10, 99).toString()
        attempts++
    } while (files.contains(name))
    return name
}

fun calculateCamouflage(file: File): Camouflage {
    val fileTree = mutableMapOf<String, MutableList<String>>()

    ZipFile(file.canonicalPath).use { zipFile ->
        val zipEntries = zipFile.entries()
        while (zipEntries.hasMoreElements()) {
            val zipEntry = zipEntries.nextElement() as ZipEntry
            if (zipEntry.isDirectory) continue

            val fileName = zipEntry.name
            if (!fileName.endsWith(".class")) continue

            val dir = fileName.substringBeforeLast("/", "")
            val name = fileName.substringAfterLast("/")
            fileTree.getOrPut(dir) { mutableListOf() }.add(name)
        }
    }

    // Safety check: if no classes found at all
    if (fileTree.isEmpty()) {
        return Camouflage("Internal" + makeRandomLowerCaseString(5).replaceFirstChar { it.uppercase() }, "init")
    }

    val asList = fileTree.toList().sortedByDescending { it.second.size }
    
    // Pick from top directories, but ensure we don't index out of bounds
    val poolSize = asList.size.coerceAtMost(4)
    val intoDirIndex = if (poolSize <= 1) 0 else Random.nextInt(poolSize)
    val (dir, files) = asList[intoDirIndex]

    val dict = mutableListOf<String>()
    for (f in files) {
        val fileName = f.substringBeforeLast(".")
        splitCamelCase(fileName)
            .map { it.replace(Regex("[^a-zA-Z]"), "") } // Remove $, numbers, and special chars
            .filter { it.length > 2 }
            .forEach { if (!dict.contains(it)) dict.add(it) }
    }

    if (dict.size < 2) {
        // Fallback dictionary if the plugin is too small to build a good one
        dict.addAll(listOf("Manager", "Utils", "Provider", "Service", "Task", "Handler", "Registry", "Loader"))
    }

    val existingNames = files.map { it.substringBeforeLast(".") }
    
    // Generate Class Name
    val generatedClassName = uniqueName(dict, existingNames, 2..3)
    val fullClassName = if (dir.isEmpty()) generatedClassName else "$dir/$generatedClassName"

    // Generate Method Name
    var generatedMethodName = dict[Random.nextInt(dict.size)]
    if (dict.size > 1 && Random.nextBoolean()) {
        generatedMethodName += dict[Random.nextInt(dict.size)]
    }
    
    // Clean up Method Name (lowercase first char, then keep camelCase)
    generatedMethodName = generatedMethodName.replaceFirstChar { it.lowercase() }
    if (generatedMethodName.length < 3) generatedMethodName += "Context"

    return Camouflage(fullClassName, generatedMethodName)
}
