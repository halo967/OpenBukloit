package utils

import java.io.File
import javassist.ClassPool
import java.nio.file.Paths

private val currentJar: File by lazy {
    // Using Paths.get().toFile() is slightly more robust for local file systems
    val location = ClassPool::class.java.protectionDomain.codeSource.location.toURI()
    Paths.get(location).toFile()
}

/**
 * Determines the correct classpath argument for compilation or injection.
 */
fun getClassPathArg(classpath: String): String {
    // If we are in an IDE, use the full classpath. 
    // If we are a JAR, just use the JAR path itself.
    return if (currentJar.isDirectory) {
        classpath 
    } else {
        currentJar.absolutePath
    }
}
