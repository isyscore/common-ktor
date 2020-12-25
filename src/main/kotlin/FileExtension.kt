package com.isyscore.kotlin.ktor

import com.isyscore.kotlin.common.decodeURLPart
import java.io.File

fun findContainingJarFile(url: String): File {
    if (url.startsWith("jar:file:")) {
        val jarPathSeparator = url.indexOf("!", startIndex = 9)
        require(jarPathSeparator != -1) { "Jar path requires !/ separator but it is: $url" }
        return File(url.substring(9, jarPathSeparator).decodeURLPart())
    }
    throw IllegalArgumentException("Only local jars are supported (jar:file:)")
}