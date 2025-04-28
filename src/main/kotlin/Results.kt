@file:Suppress("unused")

package com.isyscore.kotlin.ktor

data class KResult<T>(var code: Int, var message: String?, var data: T?) {
    companion object {
        @JvmStatic
        fun successNoData(code: Int = 0, message: String? = ""): KResult<*> = KResult(code, message, null)
        @JvmStatic
        fun <T> success(code: Int = 0, message: String? = "", data: T? = null): KResult<T> = KResult(code, message, data)
        @JvmStatic
        fun errorNoData(code: Int = 500, message: String? = ""): KResult<*> = KResult(code, message, null)
        @JvmStatic
        fun <T> error(code: Int = 500, message: String? = "", data: T?): KResult<T> = KResult(code, message, data)
    }
}

data class KPagedData<T>(var current: Int = 1, var size: Int = 20, var total: Int = 0, var pages: Int = 1, var records: List<T>? = null) {
    companion object {
        @JvmStatic
        fun noData(): KPagedData<*> = KPagedData<Unit>(records = null)
    }
}

data class KPagedResult<T>(var code: Int, var message: String?, var data: KPagedData<T>) {
    companion object {
        @JvmStatic
        fun successNoData(code: Int = 0, message: String? = ""): KPagedResult<*> = KPagedResult(code, message, KPagedData.noData())
        @JvmStatic
        fun <T> success(code: Int = 0, message: String? = "", data: KPagedData<T>): KPagedResult<T> = KPagedResult(code, message, data)
        @JvmStatic
        fun errorNoData(code: Int = 500, message: String? = ""): KPagedResult<*> = KPagedResult(code, message, KPagedData.noData())
        @JvmStatic
        fun <T> error(code: Int = 500, message: String? = "", data: KPagedData<T>): KPagedResult<T> = KPagedResult(code, message, data)
    }
}