@file:Suppress("unused", "SpellCheckingInspection")

package com.isyscore.kotlin.ktor

import com.isyscore.kotlin.common.decodeURLPart
import com.isyscore.kotlin.common.normalizeAndRelativize
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.URLProtocol
import io.ktor.sessions.Sessions
import io.ktor.sessions.cookie
import io.ktor.util.KtorExperimentalAPI
import java.io.File
import java.nio.file.Paths
import java.sql.Connection

@KtorExperimentalAPI
fun Application.config(key: String) = environment.config.property(key).getString()

@KtorExperimentalAPI
fun Application.ifcfg(condition: Boolean, key1: String, key2: String) = if (condition) config(key1) else config(key2)

fun Application.resourcePath(resourcePackage: String? = null): File? {
    val packagePath = (resourcePackage?.replace('.', '/') ?: "")
    val normalizedPath = Paths.get(packagePath).normalizeAndRelativize()
    val normalizedResource = normalizedPath.toString().replace(File.separatorChar, '/')
    for (url in environment.classLoader.getResources(normalizedResource).asSequence()) {
        when (url.protocol) {
            "file" -> {
                val file = File(url.path.decodeURLPart())
                return if (file.isDirectory) file else null
            }
        }
    }
    return null
}

@KtorExperimentalAPI
inline val Application.db: DB
    get() = DB(this)

@KtorExperimentalAPI
inline val Application.conn: Connection
    get() = db.conn()

fun Application.pluginRedirect() = install(HttpsRedirect) {
    sslPort = URLProtocol.HTTPS.defaultPort
    permanentRedirect = true
}

inline fun <reified T : Any> Application.pluginSession(sessionIdentifier: String? = "Session", httpOnly: Boolean = true) {
    if (sessionIdentifier != null) {
        install(Sessions) {
            cookie<T>(sessionIdentifier) {
                cookie.extensions["SameSite"] = "lax"
                cookie.httpOnly = httpOnly
            }
        }
    }
}

fun Application.pluginCompress() = install(Compression) {
    gzip { priority = 1.0 }
    deflate {
        priority = 10.0
        minimumSize(1024)
    }
}

fun Application.pluginDefaultHeaders(headers: Map<String, String>? = null) = install(DefaultHeaders) { headers?.forEach { t, u -> header(t, u) } }
fun Application.pluginPartialContent() = install(PartialContent) { maxRangeCount = 10 }
fun Application.pluginContentNegotiation() = install(ContentNegotiation) { gson { setPrettyPrinting() } }

inline fun <reified T : Any> Application.installPlugin(
        useCompress: Boolean = false,
        sessionIdentifier: String? = "Session",
        headers: Map<String, String>? = null,
        httpOnly: Boolean = true,
        redirectHttps: Boolean = false,
        init: () -> Unit) {
    if (redirectHttps) pluginRedirect()
    pluginSession<T>(sessionIdentifier, httpOnly)
    if (useCompress) pluginCompress()
    pluginDefaultHeaders(headers)
    pluginPartialContent()
    pluginContentNegotiation()
    init()
}