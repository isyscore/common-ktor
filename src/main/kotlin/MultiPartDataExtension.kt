@file:Suppress("unused")

package com.isyscore.kotlin.ktor

import io.ktor.http.content.*
import java.io.File

suspend fun MultiPartData.firstOrNull(f: (part: PartData) -> Boolean): PartData? {
    val list = filter(f)
    return if (list.isEmpty()) null else list[0]
}

suspend fun MultiPartData.filter(f: (part: PartData) -> Boolean): List<PartData> {
    val list = mutableListOf<PartData>()
    forEachPart { if (f(it)) list.add(it) }
    return list
}

suspend fun MultiPartData.save(field: String, dest: File): Boolean {
    val part = firstOrNull { it is PartData.FileItem && it.name == field } as? PartData.FileItem
    return part?.save(dest) ?: false
}

suspend fun PartData.FileItem.save(dest: File): Boolean =
        this.streamProvider().use { input ->
            dest.outputStream().buffered().use { output ->
                input.copyToSuspend(output) > 0
            }
        }

suspend fun MultiPartData.value(name: String): String? = (firstOrNull { it is PartData.FormItem && it.name == name } as? PartData.FormItem)?.value
suspend fun MultiPartData.file(name: String): PartData.FileItem? = firstOrNull { it is PartData.FileItem && it.name == name } as? PartData.FileItem


