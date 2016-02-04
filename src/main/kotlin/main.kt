package org.jetbrains.kotlin.stdlibDocsRedirectGen

import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

fun loadFiles(dir: String, target: MutableCollection<File>) {
    val dirFile = File(dir)
    if (!dirFile.isDirectory) {
        println("$dir is not a directory")
        exitProcess(1)
    }
    File(dir).walk().filter { !it.isDirectory }.map { it.relativeTo(dirFile) }.toCollection(target)
}

fun findMapping(oldFile: File, filesWithMatchingName: Collection<File>): File? {
    if (filesWithMatchingName.size == 1) {
        return filesWithMatchingName.first()
    }
    val components = oldFile.path.split('/')

    fun matchPattern(pattern: String): Boolean {
        val patternComponents = pattern.split('/')
        if (patternComponents.size != components.size) {
            return false
        }
        return (components zip patternComponents).all { it.first == it.second || it.second == "*" }
    }

    fun processReplacementPattern(pattern: String): String {
        var result = pattern
        for ((i, c) in components.withIndex()) {
            result = result.replace("\$$i", c)
        }
        return result
    }

    fun match(pattern: String, replacementPattern: String): File? =
        if (matchPattern(pattern)) filesWithMatchingName.find { it.path == processReplacementPattern(replacementPattern) } else null

    return match("*/*/*", "$0/$2")
        ?: match("kotlin/*", "kotlin.collections/$1")
        ?: match("kotlin/*", "kotlin.text/$1")
        ?: match("kotlin/*", "kotlin.ranges/$1")
        ?: match("kotlin/*/*", "kotlin.collections/$1/$2")
        ?: match("kotlin/*/*", "kotlin.text/$1/$2")
        ?: match("kotlin/*/*", "kotlin.sequences/$1/$2")
        ?: match("kotlin/*/*", "kotlin.ranges/$1/$2")
        ?: match("*/-floating-point-constants/*", "kotlin/-double/$2")
        ?: match("*/-integer-constants/*", "kotlin/-int/$2")
        ?: match("kotlin/-extension/*", "kotlin/-extension-function-type/$2")
        ?: match("kotlin.math/*/*", "kotlin/$1/$2")
        ?: match("kotlin.properties/*/*", "kotlin/$2")
        ?: run {
            println("Unmapped: $oldFile -> $filesWithMatchingName")
            null
        }
}

fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Usage: StdlibDocsRedirectGenKt <current> <old...>")
        return
    }

    val latestFiles = LinkedHashSet<File>().apply { loadFiles(args[0], this) }
    println("Loaded ${latestFiles.size} latest files")
    val simpleNameMap = latestFiles.groupBy { it.name }
    val oldFiles = LinkedHashSet<File>().apply { args.drop(1).map { arg -> loadFiles(arg, this )} }
    println("Loaded ${oldFiles.size} old files")
    oldFiles.removeAll(latestFiles)

    val redirectMap = oldFiles.map {
        it.path.replace(".md", ".html") to findMapping(it, simpleNameMap[it.name] ?: emptyList())?.path?.replace(".md", ".html").orEmpty()
    }.filter { it.second.isNotEmpty() }

    if (redirectMap.isEmpty()) {
        println("Could not find any redirects")
        exitProcess(1)
    }

    println("Writing ${redirectMap.size} redirects")

    writeRoutingRules(redirectMap)
    writeRedirectPages(redirectMap)
}

private fun writeRedirectPages(redirects: List<Pair<String, String>>) {
    ZipOutputStream(FileOutputStream("redirects.zip")).use { redirectOutputStream ->
        for ((oldFile, mapping) in redirects) {
            redirectOutputStream.putNextEntry(ZipEntry("api/latest/jvm/stdlib/$oldFile"))
            redirectOutputStream.writer().write("<html><head><meta http-equiv=\"refresh\" content=\"0; url=https://kotlinlang.org/api/latest/jvm/stdlib/$mapping\" /></head></html>")
        }
    }
}

private fun writeRoutingRules(redirects: List<Pair<String, String>>) {
    FileWriter(File("routingrules.xml")).use { output ->
        output.appendln("<RoutingRules>")

        for ((oldFile, mapping) in redirects) {
            output.appendln("  <RoutingRule>")
            output.appendln("    <Condition><KeyPrefixEquals>api/latest/jvm/stdlib/$oldFile</KeyPrefixEquals></Condition>")
            output.appendln("    <Redirect><HostName>kotlinlang.org</HostName><ReplaceKeyWith>api/latest/jvm/stdlib/$mapping</ReplaceKeyWith></Redirect>")
            output.appendln("  </RoutingRule>")
        }

        output.appendln("</RoutingRules>")
    }
}

