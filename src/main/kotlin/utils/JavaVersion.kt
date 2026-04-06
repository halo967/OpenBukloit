package utils

import cli.Logs
import dev.virefire.yok.Yok
import org.rauschig.jarchivelib.ArchiverFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Converts Java class file major and minor versions to a human-readable Java version string.
 * @return The corresponding Java version string (e.g., "1.8", "11", "17").
 */
fun getJavaVersion(major: Int, minor: Int): String {
    return when (major to minor) {
        45 to 0 -> "1.0"
        45 to 3 -> "1.1"
        46 to 0 -> "1.2"
        47 to 0 -> "1.3"
        48 to 0 -> "1.4"
        else -> (major - 44).toString() // For Java 5 and above, version is major - 44
    }
}

/**
 * Determines the current operating system type.
 */
fun getOsType(): String {
    val os = System.getProperty("os.name", "generic").lowercase()
    return when {
        os.contains("mac") || os.contains("darwin") -> "mac"
        os.contains("win") -> "windows"
        os.contains("nux") -> "linux"
        else -> os.split(" ")[0]
    }
}

/**
 * Ensures that a compatible JDK is available for a given Java class file version.
 * Includes automated download from Adoptium and execution permission fixes for Unix systems.
 */
fun requireJDK(major: Int, minor: Int): Path {
    if (major < 52) {
        throw Exception("Class version $major.$minor is not supported. Minimum version is Java 8 (52.0).")
    }

    val versionId = getJavaVersion(major, minor)
    val jdkDir = File("./.openbukloit/jdk/$versionId")
    val currentOs = getOsType()

    // Determine expected javac path
    val existingJavacPath = when (currentOs) {
        "mac" -> Paths.get(jdkDir.canonicalPath, "Contents", "Home", "bin", "javac")
        "windows" -> Paths.get(jdkDir.canonicalPath, "bin", "javac.exe")
        else -> Paths.get(jdkDir.canonicalPath, "bin", "javac")
    }

    // Return existing if found
    if (existingJavacPath.toFile().exists()) {
        val file = existingJavacPath.toFile()
        if (currentOs != "windows") file.setExecutable(true) // Ensure it's runnable
        Logs.info("Found existing JDK $versionId at ${jdkDir.canonicalPath}")
        return existingJavacPath
    }

    Logs.info("JDK $versionId not found locally. Downloading from Adoptium...")

    // Determine system architecture
    var currentArchitecture = System.getProperty("os.arch").lowercase()
    currentArchitecture = when (currentArchitecture) {
        "x86_64", "amd64" -> "x64"
        "i386" -> "x32"
        "aarch64" -> "aarch64"
        else -> currentArchitecture
    }

    // Fallback for macOS ARM (M1/M2/M3) on older Java versions
    if (currentOs == "mac" && currentArchitecture == "aarch64" && major < 55) {
        Logs.info("Falling back to x64 build for older macOS Java version...")
        currentArchitecture = "x64"
    }

    val extension = if (currentOs == "windows") ".zip" else ".tar.gz"
    val binaryName = "jdk-$versionId-$currentOs-$currentArchitecture$extension"
    val url = "https://api.adoptium.net/v3/binary/latest/$versionId/ga/$currentOs/$currentArchitecture/jdk/hotspot/normal/eclipse?project=jdk"

    File("./.openbukloit/temp/jdk").mkdirs()
    jdkDir.mkdirs()

    val downloadedFilePath = Paths.get("./.openbukloit/temp/jdk", binaryName)

    try {
        Logs.info("Downloading JDK from $url")
        Files.copy(Yok.get(url).body.stream, downloadedFilePath)

        Logs.info("Extracting JDK $versionId...")
        val archiver = if (extension == ".tar.gz") ArchiverFactory.createArchiver("tar", "gz") else ArchiverFactory.createArchiver("zip")
        archiver.extract(downloadedFilePath.toFile(), jdkDir)

        // Fix potential double-directory extraction
        val extractedContents = jdkDir.listFiles()
        if (extractedContents != null && extractedContents.size == 1 && extractedContents[0].isDirectory) {
            copyDirectory(extractedContents[0], jdkDir)
            extractedContents[0].deleteRecursively()
        }

        // Final verification and permission fix
        if (existingJavacPath.toFile().exists()) {
            val file = existingJavacPath.toFile()
            if (currentOs != "windows") file.setExecutable(true) // CRITICAL: Fixes Permission Denied on Linux
            
            Logs.info("JDK $versionId installed successfully")
            return existingJavacPath
        } else {
            throw Exception("JDK installed but 'javac' missing at: ${existingJavacPath.toFile().canonicalPath}")
        }

    } catch (e: Exception) {
        if (downloadedFilePath.toFile().exists()) downloadedFilePath.toFile().delete()
        if (jdkDir.exists()) jdkDir.deleteRecursively()
        throw Exception("Failed to prepare JDK $versionId: ${e.message}", e)
    } finally {
        File("./.openbukloit/temp/jdk").deleteRecursively()
    }
}

/**
 * Finds the path to the 'java' executable associated with a JDK path.
 */
fun toJRE(jdkPath: Path): Path {
    val binDirPath = jdkPath.parent.toFile().canonicalPath
    val currentOs = getOsType()
    
    val jreName = if (currentOs == "windows") "java.exe" else "java"
    val jrePath = Paths.get(binDirPath, jreName)
    
    val jreFile = jrePath.toFile()
    if (jreFile.exists()) {
        if (currentOs != "windows") jreFile.setExecutable(true) // Ensure JRE is also runnable
        return jrePath
    }
    
    throw Exception("JRE executable ('java') not found in $binDirPath")
}
