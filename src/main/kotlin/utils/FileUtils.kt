package utils

import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * Copies a directory recursively from a source location to a target location.
 */
fun copyDirectory(sourceLocation: File, targetLocation: File) {
    Files.walk(sourceLocation.toPath())
        .forEach { source ->
            val relativePath = source.toFile().canonicalPath
                .substring(sourceLocation.canonicalPath.length)
            
            val destination: Path = Paths.get(targetLocation.path, relativePath)
            
            if (Files.isDirectory(source)) {
                if (!Files.exists(destination)) {
                    Files.createDirectories(destination)
                }
            } else {
                // Added REPLACE_EXISTING to prevent crashes on repeated runs
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        }
}
