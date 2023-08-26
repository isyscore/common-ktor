@file:Suppress("unused")

package com.isyscore.kotlin.ktor

data class Result<T>(val code: Int, val message: String, val data: T?) {
    companion object {
        fun successNoData(code: Int = 0, message: String = ""): Result<*> = Result(code, message, null)
        fun <T> success(code: Int = 0, message: String = "", data: T? = null): Result<T> = Result(code, message, data)

        fun errorNoData(code: Int = 500, message: String = ""): Result<*> = Result(code, message, null)
        fun <T> error(code: Int = 500, message: String = "", data: T?): Result<T> = Result(code, message, data)
    }
}

data class PagedData<T>(val current: Int = 1, val size: Int = 20, val total: Int = 0, val pages: Int = 1, val records: List<T>? = null) {
    companion object {
        fun noData(): PagedData<*> = PagedData<Unit>(records = null)
    }
}

data class PagedResult<T>(val code: Int, val message: String, val data: PagedData<T>) {
    companion object {
        fun successNoData(code: Int = 0, message: String = ""): PagedResult<*> = PagedResult(code, message, PagedData.noData())
        fun <T> success(code: Int = 0, message: String = "", data: PagedData<T>): PagedResult<T> = PagedResult(code, message, data)
        fun errorNoData(code: Int = 500, message: String = ""): PagedResult<*> = PagedResult(code, message, PagedData.noData())
        fun <T> error(code: Int = 500, message: String = "", data: PagedData<T>): PagedResult<T> = PagedResult(code, message, data)
    }
}