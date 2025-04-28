@file:Suppress("unused")

package com.isyscore.kotlin.ktor

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import io.ktor.util.pipeline.PipelineContext

inline fun <reified T: Any> PipelineContext<*, ApplicationCall>.session(init: () -> T): T {
    var ses: T?
    try {
        ses = call.sessions.get()
        if (ses == null) {
            ses = init()
            call.sessions.set(ses)
        }
    } catch (th: Throwable) {
        ses = init()
        call.sessions.set(ses)
    }
    return ses
}

fun PipelineContext<*, ApplicationCall>.config(key: String) = call.application.config(key)
fun PipelineContext<*, ApplicationCall>.config(key: String, def: String) = call.application.config(key, def)