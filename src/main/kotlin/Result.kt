@file:Suppress("unused")

package com.isyscore.kotlin.ktor

import kotlinx.serialization.Serializable

@Serializable
data class Result<T>(val code: Int, val message: String, val data: T?) {
    companion object {
        fun success(message: String = ""): Result<*> = Result(200, message, null)
        fun fail400(message: String = ""): Result<*> = Result(400, message, null)
        fun fail500(message: String = ""): Result<*> = Result(500, message, null)
    }
}

@Serializable
data class PagedData<T>(val current: Int = 1, val size: Int = 20, val total: Int = 0, val pages: Int = 1, val records: List<T>? = null) {
    companion object {
        fun noData(): PagedData<*> = PagedData<Unit>(records = null)
    }
}