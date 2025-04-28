@file:Suppress("unused", "SpellCheckingInspection")

package com.isyscore.kotlin.ktor

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.isyscore.kotlin.common.decodeURLPart
import com.isyscore.kotlin.common.normalizeAndRelativize
import com.isyscore.kotlin.ktor.plugin.RoleBasedAuthorization
import com.isyscore.kotlin.ktor.plugin.RoleBasedAuthorizationProvider
import com.isyscore.kotlin.ktor.plugin.roleSession
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.calllogging.*
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
import org.ktorm.jackson.KtormModule
import org.slf4j.event.Level
import java.io.File
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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

@Suppress("DEPRECATION")
fun ObjectMapper.config(localDatePattern: DateTimeFormatter, localTimePattern: DateTimeFormatter, localDateTimePattern: DateTimeFormatter): ObjectMapper {
    registerModule(KtormModule())
    registerModule(KotlinModule.Builder().build())
    registerModule(JavaTimeModule().apply {
        addDeserializer(LocalDate::class.java, LocalDateDeserializer(localDatePattern))
        addSerializer(LocalDate::class.java, LocalDateSerializer(localDatePattern))
        addDeserializer(LocalTime::class.java, LocalTimeDeserializer(localTimePattern))
        addSerializer(LocalTime::class.java, LocalTimeSerializer(localTimePattern))
        addDeserializer(LocalDateTime::class.java, LocalDateTimeDeserializer(localDateTimePattern))
        addSerializer(LocalDateTime::class.java, LocalDateTimeSerializer(localDateTimePattern))
    })
    configure(SerializationFeature.INDENT_OUTPUT, true)
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
    configure(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS, false)
    configure(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY, false)
    configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    configure(SerializationFeature.FAIL_ON_SELF_REFERENCES, false)
    configure(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS, false)

    configure(JsonReadFeature.ALLOW_YAML_COMMENTS.mappedFeature(), true)
    configure(JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature(), true)
    configure(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES.mappedFeature(), true)
    configure(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature(), true)
    configure(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true)
    configure(JsonReadFeature.ALLOW_MISSING_VALUES.mappedFeature(), true)

    setDefaultLeniency(true)
    setDefaultPrettyPrinter(DefaultPrettyPrinter().apply {
        indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
        indentObjectsWith(DefaultIndenter("  ", "\n"))
    })

    setSerializationInclusion(JsonInclude.Include.ALWAYS)
    enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
    deactivateDefaultTyping()
    return this
}

inline fun <reified T : Any> generateSerializer(
    localDatePattern: DateTimeFormatter, localTimePattern: DateTimeFormatter, localDateTimePattern: DateTimeFormatter
): SessionSerializer<T> = object : SessionSerializer<T> {
    private val om = ObjectMapper().config(localDatePattern, localTimePattern, localDateTimePattern)
    override fun deserialize(text: String): T = om.readValue(text, T::class.java)
    override fun serialize(session: T): String = om.writeValueAsString(session)
}


inline fun <reified T : Any> Application.pluginSession(
    sessionIdentifier: String? = "Session",
    cookiePath: String = "/",
    httpOnly: Boolean = true,
    maxAge: Long = 60 * 60 * 24,
    // 是否要选择 Cookie 加密
    isSecret: Boolean = false,
    secretEncryptKey: String = "",
    secretSignKey: String = "",
    // Session 对象的序列化器，时间格式
    localDatePattern: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    localTimePattern: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss"),
    localDateTimePattern: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
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
    localDatePattern: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    localTimePattern: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss"),
    localDateTimePattern: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
) = install(ContentNegotiation) {
    jackson {
        config(localDatePattern, localTimePattern, localDateTimePattern)
    }
}

fun Application.pluginResources() = install(Resources)

fun Application.pluginWebSocket() = install(WebSockets) {
    pingPeriod = 15L.toDuration(DurationUnit.SECONDS)
    timeout = 15L.toDuration(DurationUnit.SECONDS)
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
    allowHeaders { true }
    allowOrigins { true }
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

inline fun <reified T : Any> Application.pluginAuthSession(
    sessionName: String? = null, noinline configure: SessionAuthenticationProvider.Config<T>.() -> Unit
) = install(Authentication) {
    session<T>(sessionName, configure)
}

inline fun <reified T : Any> Application.pluginRoleAuthorization(
    crossinline configure: RoleBasedAuthorizationProvider.Config<T>.() -> Unit
) = install(RoleBasedAuthorization) {
    roleSession<T>(configure)
}

@Deprecated("installPlugin is Deprecated in common-ktor 2.0.0")
inline fun <reified T : Any> Application.installPlugin(
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