package com.isyscore.kotlin.ktor

data class Result<T>(val code: Int, val message: String, val data: T?) {
    companion object {
        fun success(): Result<*> = Result(200, "success", null)
        fun fail(): Result<*> = Result(500, "fail", null)
    }
}