@file:Suppress("unused", "SpellCheckingInspection")

package com.isyscore.kotlin.ktor

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer
import com.isyscore.kotlin.common.decodeURLPart
import com.isyscore.kotlin.common.normalizeAndRelativize
import com.isyscore.kotlin.ktor.plugin.RoleBasedAuthorization
import com.isyscore.kotlin.ktor.plugin.RoleBasedAuthorizationProvider
import com.isyscore.kotlin.ktor.plugin.roleSession
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.httpsredirect.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import org.ktorm.jackson.KtormModule
import org.slf4j.event.Level
import java.io.File
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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

fun ObjectMapper.config(localDatePattern: String, localTimePattern: String, localDateTimePattern: String): ObjectMapper {
    registerModule(KtormModule())
    registerModule(JavaTimeModule().apply {
        addDeserializer(LocalDate::class.java, LocalDateDeserializer(DateTimeFormatter.ofPattern(localDatePattern)))
        addSerializer(LocalDate::class.java, LocalDateSerializer(DateTimeFormatter.ofPattern(localDatePattern)))
        addDeserializer(LocalTime::class.java, LocalTimeDeserializer(DateTimeFormatter.ofPattern(localTimePattern)))
        addSerializer(LocalTime::class.java, LocalTimeSerializer(DateTimeFormatter.ofPattern(localTimePattern)))
        addDeserializer(LocalDateTime::class.java, LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(localDateTimePattern)))
        addSerializer(LocalDateTime::class.java, LocalDateTimeSerializer(DateTimeFormatter.ofPattern(localDateTimePattern)))
    })
    configure(SerializationFeature.INDENT_OUTPUT, true)
    setDefaultLeniency(true)
    setDefaultPrettyPrinter(DefaultPrettyPrinter().apply {
        indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
        indentObjectsWith(DefaultIndenter("  ", "\n"))
    })
    return this
}

inline fun <reified T : Any> generateSerializer(
        localDatePattern: String, localTimePattern: String, localDateTimePattern: String
): SessionSerializer<T> = object : SessionSerializer<T> {
    private val om = ObjectMapper().config(localDatePattern, localTimePattern, localDateTimePattern)
    override fun deserialize(text: String): T = om.readValue(text, T::class.java)
    override fun serialize(session: T): String = om.writeValueAsString(session)
}


inline fun <reified T : Principal> Application.pluginSession(
        sessionIdentifier: String? = "Session",
        cookiePath: String = "/",
        httpOnly: Boolean = true,
        maxAge: Long = 60 * 60 * 24,
        // 是否要选择 Cookie 加密
        isSecret: Boolean = false,
        secretEncryptKey: String = "",
        secretSignKey: String = "",
        // Session 对象的序列化器，时间格式
        localDatePattern: String = "yyyy-MM-dd",
        localTimePattern: String = "hh:mm:ss",
        localDateTimePattern: String = "yyyy-MM-dd hh:mm:ss"
) {
    if (sessionIdentifier != null) {
        install(Sessions) {
            cookie<T>(sessionIdentifier) {
                cookie.extensions["SameSite"] = "lax"
                cookie.httpOnly = httpOnly
                cookie.path = cookiePath
                cookie.maxAgeInSeconds = maxAge
                serializer = generateSerializer(localDatePattern, localTimePattern, localDateTimePattern)
                if (isSecret) {
                    transform(SessionTransportTransformerEncrypt(hex(secretEncryptKey), hex(secretSignKey)))
                }
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

fun Application.pluginContentNegotiation(
        localDatePattern: String = "yyyy-MM-dd",
        localTimePattern: String = "hh:mm:ss",
        localDateTimePattern: String = "yyyy-MM-dd hh:mm:ss"
) = install(ContentNegotiation) {
    jackson {
        config(localDatePattern, localTimePattern, localDateTimePattern)
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

inline fun <reified T : Principal> Application.pluginAuthSession(
        sessionName: String, crossinline configure: SessionAuthenticationProvider.Config<T>.() -> Unit
) = install(Authentication) {
    session<T>(sessionName, configure)
}

inline fun <reified T : Principal> Application.pluginRoleAuthorization(
        crossinline configure: RoleBasedAuthorizationProvider.Config<T>.() -> Unit
) = install(RoleBasedAuthorization) {
    roleSession<T>(configure)
}

@Deprecated("installPlugin is Deprecated in common-ktor 2.0.0")
inline fun <reified T : Principal> Application.installPlugin(
        useCompress: Boolean = false,
        sessionIdentifier: String? = "Session",
        headers: Map<String, String>? = null,
        httpOnly: Boolean = true,
        redirectHttps: Boolean = false,
        allowCors: Boolean = false,
        init: () -> Unit
) {
    if (redirectHttps) pluginRedirect()
    pluginSession<T>(sessionIdentifier, "/", httpOnly)
    if (useCompress) pluginCompress()
    pluginDefaultHeaders(headers)
    pluginPartialContent()
    pluginContentNegotiation()
    if (allowCors) pluginCORS()
    init()
}