package com.isyscore.kotlin.ktor.plugin

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import kotlin.reflect.KClass

enum class RoleAuthorizationType { ALL, ANY, NONE }

class RoleBasedAuthorization(internal var config: RoleBasedAuthorizationConfig) {
    fun configure(block: RoleBasedAuthorizationConfig.() -> Unit) {
        val newConfiguration = config.copy()
        block(newConfiguration)
        config = newConfiguration
    }

    companion object : BaseApplicationPlugin<Application, RoleBasedAuthorizationConfig, RoleBasedAuthorization> {
        override val key: AttributeKey<RoleBasedAuthorization> = AttributeKey("RoleBasedAuthorizationHolder")

        override fun install(pipeline: Application, configure: RoleBasedAuthorizationConfig.() -> Unit): RoleBasedAuthorization {
            val config = RoleBasedAuthorizationConfig().apply(configure)
            return RoleBasedAuthorization(config)
        }
    }
}

class RoleBasedAuthorizationConfig(
        var type: RoleAuthorizationType = RoleAuthorizationType.ANY,
        var roles: Set<String> = emptySet(),
        var provider: BaseRoleBasedAuthorizationProvider? = null) {
    internal fun copy(): RoleBasedAuthorizationConfig = RoleBasedAuthorizationConfig(type, roles, provider)
}

val RoleAuthenticationInterceptors: RouteScopedPlugin<RoleBasedAuthorizationConfig> =
        createRouteScopedPlugin("RoleAuthenticationInterceptors", ::RoleBasedAuthorizationConfig) {
            // 这种方式获取真实使用时的配置内容
            val reqRoles = pluginConfig.roles
            val reqType = pluginConfig.type
            // 这种方式获取 install 时注册的配置内容
            val config = application.plugin(RoleBasedAuthorization).config
            on(AuthenticationChecked) { call ->
                if (call.isHandled) {
                    return@on
                }
                config.provider?.onRoleAuthorization(call, reqType, reqRoles)
            }
        }

private class RoleAuthorizationRouteSelector(private val description: Set<String>) : RouteSelector() {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
            RouteSelectorEvaluation.Transparent

    override fun toString(): String = "(authorize ${description.joinToString(",")})"
}

private fun Route.withRoles(roles: Set<String>, roleAuthType: RoleAuthorizationType, build: Route.() -> Unit): Route {
    require(roles.isNotEmpty()) { "At least one role name need to be provided" }
    val roleAuthRoute = createChild(RoleAuthorizationRouteSelector(roles))
    roleAuthRoute.install(RoleAuthenticationInterceptors) {
        this.roles = roles
        this.type = roleAuthType
    }
    roleAuthRoute.build()
    return roleAuthRoute
}


abstract class BaseRoleBasedAuthorizationProvider {
    abstract suspend fun onRoleAuthorization(call: ApplicationCall, reqType: RoleAuthorizationType, reqRoles: Set<String>)
    open class Config protected constructor()
}

typealias RoleBasedAuthorizationGetRoleFunc<T> = suspend ApplicationCall.(T) -> Set<String>
typealias RoleBasedAuthorizationRoleAuthFailedFunc = suspend ApplicationCall.(String) -> Unit

class RoleBasedAuthorizationProvider<T : Any>(config: Config<T>) : BaseRoleBasedAuthorizationProvider() {

    private val type: KClass<T> = config.type
    private val getRoleFunc: RoleBasedAuthorizationGetRoleFunc<T> = config.getRoleFunc
    private val roleAuthFailedFunc: RoleBasedAuthorizationRoleAuthFailedFunc = config.roleAuthFailedFunc

    override suspend fun onRoleAuthorization(call: ApplicationCall, reqType: RoleAuthorizationType, reqRoles: Set<String>) {
        val user = call.sessions.get(type)
        if (user == null) {
            roleAuthFailedFunc(call, "Unauthenticated User")
            return
        }
        val roles = getRoleFunc(call, user)
        val denyReasons = mutableListOf<String>()
        when (reqType) {
            RoleAuthorizationType.ALL -> {
                val missing = reqRoles - roles
                if (missing.isNotEmpty()) {
                    denyReasons += "Principal $user lacks required role(s) ${missing.joinToString(" and ")}"
                }
            }

            RoleAuthorizationType.ANY -> {
                if (roles.none { it in reqRoles }) {
                    denyReasons += "Principal $user has none of the sufficient role(s) ${reqRoles.joinToString(" or ")}"
                }
            }

            RoleAuthorizationType.NONE -> {
                if (roles.any { it in reqRoles }) {
                    denyReasons += "Principal $user has forbidden role(s) ${reqRoles.intersect(roles).joinToString(" and ")}"
                }
            }
        }
        if (denyReasons.isNotEmpty()) {
            val message = denyReasons.joinToString(". ")
            roleAuthFailedFunc(call, message)
        }
    }

    class Config<T : Any>(internal val type: KClass<T>) : BaseRoleBasedAuthorizationProvider.Config() {
        internal var getRoleFunc: RoleBasedAuthorizationGetRoleFunc<T> = { emptySet() }
        internal var roleAuthFailedFunc: RoleBasedAuthorizationRoleAuthFailedFunc = {}
        fun getRole(block: RoleBasedAuthorizationGetRoleFunc<T>) {
            getRoleFunc = block
        }

        fun roleAuthFailed(block: RoleBasedAuthorizationRoleAuthFailedFunc) {
            roleAuthFailedFunc = block
        }

        fun buildProvider(): RoleBasedAuthorizationProvider<T> = RoleBasedAuthorizationProvider(this)
    }

}

inline fun <reified T : Any> RoleBasedAuthorizationConfig.roleSession(configure: RoleBasedAuthorizationProvider.Config<T>.() -> Unit) {
    val provider = RoleBasedAuthorizationProvider.Config(T::class).apply(configure).buildProvider()
    this.provider = provider
}

fun Route.withRoles(vararg roles: String, build: Route.() -> Unit) =
        withRoles(roles.toSet(), RoleAuthorizationType.ALL, build)

fun Route.withAnyRole(vararg roles: String, build: Route.() -> Unit) =
        withRoles(roles.toSet(), RoleAuthorizationType.ANY, build)

fun Route.withoutRoles(vararg roles: String, build: Route.() -> Unit) =
        withRoles(roles.toSet(), RoleAuthorizationType.NONE, build)
