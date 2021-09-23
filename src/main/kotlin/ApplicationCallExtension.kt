@file:Suppress("BlockingMethodInNonBlockingContext", "unused", "SpellCheckingInspection")

package com.isyscore.kotlin.ktor

import com.isyscore.kotlin.common.appendPathPart
import com.isyscore.kotlin.common.decodeURLPart
import com.isyscore.kotlin.common.normalizeAndRelativize
import com.isyscore.kotlin.common.toMap
import io.ktor.application.ApplicationCall
import io.ktor.http.HttpHeaders
import io.ktor.request.receiveText
import io.ktor.response.header
import io.ktor.response.respondFile
import java.io.File
import java.nio.file.Paths
import java.util.jar.JarFile
import io.ktor.http.ContentDisposition
import io.ktor.util.*


fun ApplicationCall.config(cfg: String): String = application.config(cfg)

fun ApplicationCall.ifcfg(condition: Boolean, key1: String, key2: String) = if (condition) config(key1) else config(key2)

suspend fun ApplicationCall.requestParameters() = try {
    receiveText().toMap()
} catch (th: Throwable) {
    parameters.toMap().map { Pair(it.key, it.value.joinToString("\n")) }.toMap()
}

fun ApplicationCall.resolveFileContent(path: String, resourcePackage: String? = null, classLoader: ClassLoader = application.environment.classLoader): String? {
    val packagePath = (resourcePackage?.replace('.', '/') ?: "").appendPathPart(path)
    val normalizedPath = Paths.get(packagePath).normalizeAndRelativize()
    val normalizedResource = normalizedPath.toString().replace(File.separatorChar, '/')
    for (url in classLoader.getResources(normalizedResource).asSequence()) {
        when (url.protocol) {
            "file" -> {
                val file = File(url.path.decodeURLPart())
                return if (file.isFile) file.readText() else null
            }
            "jar" -> {
                return if (packagePath.endsWith("/")) {
                    null
                } else {
                    val jar = JarFile(findContainingJarFile(url.toString()))
                    val jarEntry = jar.getJarEntry(normalizedResource)!!
                    String(jar.getInputStream(jarEntry).readBytes())
                }
            }
        }
    }
    return null
}

fun ApplicationCall.resolveFileBytes(path: String, resourcePackage: String? = null, classLoader: ClassLoader = application.environment.classLoader): ByteArray? {
    val packagePath = (resourcePackage?.replace('.', '/') ?: "").appendPathPart(path)
    val normalizedPath = Paths.get(packagePath).normalizeAndRelativize()
    val normalizedResource = normalizedPath.toString().replace(File.separatorChar, '/')
    for (url in classLoader.getResources(normalizedResource).asSequence()) {
        when (url.protocol) {
            "file" -> {
                val file = File(url.path.decodeURLPart())
                return if (file.isFile) file.readBytes() else null
            }
            "jar" -> {
                return if (packagePath.endsWith("/")) {
                    null
                } else {
                    val jar = JarFile(findContainingJarFile(url.toString()))
                    val jarEntry = jar.getJarEntry(normalizedResource)!!
                    jar.getInputStream(jarEntry).readBytes()
                }
            }
        }
    }
    return null
}

suspend fun ApplicationCall.resolveFileSave(dest: File, path: String, resourcePackage: String? = null, classLoader: ClassLoader = application.environment.classLoader): Boolean {
    var ret = false
    val packagePath = (resourcePackage?.replace('.', '/') ?: "").appendPathPart(path)
    val normalizedPath = Paths.get(packagePath).normalizeAndRelativize()
    val normalizedResource = normalizedPath.toString().replace(File.separatorChar, '/')
    for (url in classLoader.getResources(normalizedResource).asSequence()) {
        when (url.protocol) {
            "file" -> {
                val file = File(url.path.decodeURLPart())
                if (file.isFile) {
                    file.copyTo(dest)
                    ret = true
                }
            }
            "jar" -> {
                if (!packagePath.endsWith("/")) {
                    val jar = JarFile(findContainingJarFile(url.toString()))
                    val jarEntry = jar.getJarEntry(normalizedResource)!!
                    jar.getInputStream(jarEntry).use { input ->
                        dest.outputStream().use { output ->
                            ret = input.copyToSuspend(output) > 0
                        }
                    }
                }
            }
        }
    }
    return ret
}

fun ApplicationCall.resolveFile(path: String, resourcePackage: String? = null, classLoader: ClassLoader = application.environment.classLoader): File? {
    val packagePath = (resourcePackage?.replace('.', '/') ?: "").appendPathPart(path)
    val normalizedPath = Paths.get(packagePath).normalizeAndRelativize()
    val normalizedResource = normalizedPath.toString().replace(File.separatorChar, '/')
    for (url in classLoader.getResources(normalizedResource).asSequence()) {
        when (url.protocol) {
            "file" -> {
                val file = File(url.path.decodeURLPart())
                return if (file.isFile) file else null
            }
        }
    }
    return null
}

fun ApplicationCall.resourcePath(resourcePackage: String? = null) = application.resourcePath(resourcePackage)

suspend fun ApplicationCall.sendDownload(file: File, name: String? = null) {
    if (name != null) {
        response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, name).toString())
    }
    respondFile(file)
}

suspend fun ApplicationCall.sendDownload(filePath: String, name: String? = null) = sendDownload(File(filePath), name)