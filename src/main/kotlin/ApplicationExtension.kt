@file:Suppress("unused", "SpellCheckingInspection")

package com.isyscore.kotlin.ktor

import com.isyscore.kotlin.common.decodeURLPart
import com.isyscore.kotlin.common.normalizeAndRelativize
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.httpsredirect.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.ktorm.database.Database
import org.slf4j.event.*
import org.slf4j.event.Level
import java.io.File
import java.nio.file.Paths
import java.sql.SQLException
import java.time.Duration
import kotlin.collections.set

fun Application.config(key: String): String =
    environment.config.property(key).getString()

fun Application.config(key: String, default: String): String =
    environment.config.propertyOrNull(key)?.getString() ?: default

fun Application.ifconfig(condition: Boolean, key1: String, key2: String): String =
    if (condition) config(key1) else config(key2)

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

inline fun <reified T : Any> Application.pluginSession(
    sessionIdentifier: String? = "Session",
    httpOnly: Boolean = true
) {
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
    gzip {
        priority = 1.0
    }
    deflate {
        priority = 10.0
        minimumSize(1024)
    }
    identity { }
}

fun Application.pluginDefaultHeaders(headers: Map<String, String>? = null) = install(DefaultHeaders) {
    headers?.forEach { (t, u) -> header(t, u) }
}

fun Application.pluginPartialContent() = install(PartialContent) {
    maxRangeCount = 10
}

fun Application.pluginContentNegotiation(sn: Boolean = false) = install(ContentNegotiation) {
    gson {
        if (sn) serializeNulls()
        setPrettyPrinting()
    }
}

fun Application.pluginResources() = install(Resources)

fun Application.pluginWebSocket() = install(WebSockets) {
    pingPeriod = Duration.ofSeconds(15)
    timeout = Duration.ofSeconds(15)
    maxFrameSize = Long.MAX_VALUE
    masking = false
}

fun Application.pluginCORS() = install(CORS) {
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Put)
    allowMethod(HttpMethod.Patch)
    allowMethod(HttpMethod.Delete)
    allowMethod(HttpMethod.Head)
    allowMethod(HttpMethod.Options)
    allowHeader(HttpHeaders.Authorization)
    anyHost()
    allowCredentials = true
    allowNonSimpleContentTypes = true
    maxAgeInSeconds = 1000L * 60 * 60 * 24
}

fun Application.pluginCallLogging(lv: Level = Level.INFO) = install(CallLogging) {
    level = lv
    filter { call ->
        call.request.path().startsWith("/")
    }
}

@Deprecated("installPlugin is Deprecated in common-ktor 2.0.0")
inline fun <reified T : Any> Application.installPlugin(
    useCompress: Boolean = false,
    sessionIdentifier: String? = "Session",
    headers: Map<String, String>? = null,
    httpOnly: Boolean = true,
    redirectHttps: Boolean = false,
    allowCors: Boolean = false,
    serializeNulls: Boolean = false,
    init: () -> Unit
) {
    if (redirectHttps) pluginRedirect()
    pluginSession<T>(sessionIdentifier, httpOnly)
    if (useCompress) pluginCompress()
    pluginDefaultHeaders(headers)
    pluginPartialContent()
    pluginContentNegotiation(serializeNulls)
    if (allowCors) pluginCORS()
    initDatabase()
    init()
}

private lateinit var innerDatabase: Database

@Deprecated("database is Deprecated in common-ktor 2.0.0")
val Application.database: Database
    get() {
        if (!::innerDatabase.isInitialized) {
            throw SQLException("Database not initialized.")
        }
        return innerDatabase
    }

@Deprecated("initDatabase is Deprecated in common-ktor 2.0.0")
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