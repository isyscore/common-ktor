package com.isyscore.kotlin.ktor.swagger

import io.ktor.util.pipeline.*

@ContextDsl
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class SWRoute(val value: String = "", val tag: String = "")

@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
annotation class SWOperation(val value: String = "", val httpMethod: String = "GET", val notes: String = "")

@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
annotation class SWParam(val name: String = "", val value: String = "", val required: Boolean = false, val type: String = "", val allowEmptyValue: Boolean = false, val example: String = "")

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class SWProperty(val name: String = "", val value: String = "", val required: Boolean = false, val type: String = "", val notes: String = "", val allowEmptyValue: Boolean = false, val example: String = "")

@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
annotation class SWReturn(val value: String = "", val type: String = "")

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class SWType(val value: String = "", val title: String = "")

@Target(AnnotationTarget.CLASS,
        AnnotationTarget.ANNOTATION_CLASS,
        AnnotationTarget.TYPE_PARAMETER,
        AnnotationTarget.PROPERTY,
        AnnotationTarget.FIELD,
        AnnotationTarget.LOCAL_VARIABLE,
        AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.CONSTRUCTOR,
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY_GETTER,
        AnnotationTarget.PROPERTY_SETTER,
        AnnotationTarget.TYPE,
        AnnotationTarget.EXPRESSION,
        AnnotationTarget.FILE,
        AnnotationTarget.TYPEALIAS)
@Retention(AnnotationRetention.SOURCE)
annotation class SWBase(
        val swagger: String = "2.0",
        val description: String = "",
        val version: String = "",
        val title: String = "",
        val termsOfService: String = "",
        val contactName: String = "",
        val contactUrl: String = "",
        val contactEmail: String = "",
        val host: String = "",
        val basePath: String = "/"
)