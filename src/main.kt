package org.jetbrains.kotlin.stdlibDocsRedirectGen

import java.io.File
import java.io.FileWriter
import java.util.*

fun loadFiles(dir: String, target: MutableCollection<File>) {
    val dirFile = File(dir)
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
    val simpleNameMap = latestFiles.groupBy { it.name }
    val oldFiles = LinkedHashSet<File>().apply { args.drop(1).map { arg -> loadFiles(arg, this )}}
    oldFiles.removeAll(latestFiles)

    FileWriter(File("routingrules.xml")).use { output ->
        output.appendln("<RoutingRules>")

        for (oldFile in oldFiles) {
            val mapping = findMapping(oldFile, simpleNameMap[oldFile.name] ?: emptyList())
            if (mapping != null) {
                output.appendln("  <RoutingRule>")
                output.appendln("    <Condition><KeyPrefixEquals>api/latest/jvm/stdlib/${oldFile.path}</KeyPrefixEquals></Condition>")
                output.appendln("    <Redirect><ReplaceKeyWith>api/latest/jvm/stdlib/${mapping.path}</ReplaceKeyWith></Redirect>")
                output.appendln("  </RoutingRule>")
            }
        }

        output.appendln("</RoutingRules>")
    }
}
