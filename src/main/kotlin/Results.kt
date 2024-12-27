@file:Suppress("unused")

package com.isyscore.kotlin.ktor

data class Result<T>(var code: Int, var message: String?, var data: T?) {
    companion object {
        @JvmStatic
        fun successNoData(code: Int = 0, message: String? = ""): Result<*> = Result(code, message, null)
        @JvmStatic
        fun <T> success(code: Int = 0, message: String? = "", data: T? = null): Result<T> = Result(code, message, data)
        @JvmStatic
        fun errorNoData(code: Int = 500, message: String? = ""): Result<*> = Result(code, message, null)
        @JvmStatic
        fun <T> error(code: Int = 500, message: String? = "", data: T?): Result<T> = Result(code, message, data)
    }
}

data class PagedData<T>(var current: Int = 1, var size: Int = 20, var total: Int = 0, var pages: Int = 1, var records: List<T>? = null) {
    companion object {
        @JvmStatic
        fun noData(): PagedData<*> = PagedData<Unit>(records = null)
    }
}

data class PagedResult<T>(var code: Int, var message: String?, var data: PagedData<T>) {
    companion object {
        @JvmStatic
        fun successNoData(code: Int = 0, message: String? = ""): PagedResult<*> = PagedResult(code, message, PagedData.noData())
        @JvmStatic
        fun <T> success(code: Int = 0, message: String? = "", data: PagedData<T>): PagedResult<T> = PagedResult(code, message, data)
        @JvmStatic
        fun errorNoData(code: Int = 500, message: String? = ""): PagedResult<*> = PagedResult(code, message, PagedData.noData())
        @JvmStatic
        fun <T> error(code: Int = 500, message: String? = "", data: PagedData<T>): PagedResult<T> = PagedResult(code, message, data)
    }
}