@file:Suppress("unused", "SpellCheckingInspection")

package com.isyscore.kotlin.ktor

import com.isyscore.kotlin.common.decodeURLPart
import com.isyscore.kotlin.common.normalizeAndRelativize
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.sessions.*
import org.ktorm.database.Database
import java.io.File
import java.nio.file.Paths
import java.sql.SQLException
import kotlin.collections.Map
import kotlin.collections.set

fun Application.config(key: String) = environment.config.property(key).getString()

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

fun Application.pluginCORS() = install(CORS) {
    method(HttpMethod.Get)
    method(HttpMethod.Post)
    method(HttpMethod.Put)
    method(HttpMethod.Patch)
    method(HttpMethod.Delete)
    method(HttpMethod.Head)
    method(HttpMethod.Options)
    anyHost()
    allowCredentials = true
    allowNonSimpleContentTypes = true
    maxAgeInSeconds = 1000L * 60 * 60 * 24
}

inline fun <reified T : Any> Application.installPlugin(
        useCompress: Boolean = false,
        sessionIdentifier: String? = "Session",
        headers: Map<String, String>? = null,
        httpOnly: Boolean = true,
        redirectHttps: Boolean = false,
        allowCors: Boolean = false,
        init: () -> Unit) {
    if (redirectHttps) pluginRedirect()
    pluginSession<T>(sessionIdentifier, httpOnly)
    if (useCompress) pluginCompress()
    pluginDefaultHeaders(headers)
    pluginPartialContent()
    pluginContentNegotiation()
    if (allowCors) pluginCORS()
    initDatabase()
    init()
}

private lateinit var innerDatabase: Database

val Application.database: Database
    get() {
        if (!::innerDatabase.isInitialized) {
            throw SQLException("Database not initialized.")
        }
        return innerDatabase
    }

fun Application.initDatabase() {
    val driver = config("ktor.database.driver")
    val url = config("ktor.database.url")
    val user = config("ktor.database.user")
    val password = config("ktor.database.password")
    try {
        Class.forName(driver)
        innerDatabase = Database.connect(url, user = user, password = password)
    } catch (e: Exception) {
        log.error(e.message)
    }
}