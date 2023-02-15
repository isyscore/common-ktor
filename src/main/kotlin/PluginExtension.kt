/**
 * Ktor 插件机制
 *
 * 1. 设置插件所在目录，该目录包含对插件的配置文件以及插件本身
 *      1.1 插件与配置文件
 *          对于在目录内的一个插件，必须包含两个文件，即一个配置文件与一个真实插件文件，如：
 *              SamplePlugin.jar
 *              SamplePlugin.cfg
 *      1.2 配置文件的内容
 *          配置文件内容如下所示:
 *              PluginClass=com.sample.plugin.SampleRoutingKt    // 此处必须是完整类名
 *              RoutingMethods=SampleRouting1,SampleRouting2,... // 多个路由注册方法之间用逗号隔开
 *              Routings=/sample1,/sample2,...                   // 多个路由之间用逗号隔开
 * 2. 加载插件
 *      2.1 依据目录加载所有找到的插件
 *      2.2 依据插件名称加载指定插件
 * 3. 卸载插件
 *      3.1 依据目录卸载所有找到的插件
 *      3.2 依据插件名称卸载插件
 * 4. 依据名称判断某个插件是否已加载
 */

@file:Suppress("unused")

package com.isyscore.kotlin.ktor

import io.ktor.server.routing.*
import java.io.File
import java.net.URLClassLoader

private val pluginList = mutableMapOf<String, List<String>>()

/**
 * 加载插件
 * @param plugPath 插件所在路径
 * @param plugName 插件名称，将自动寻找该名称的插件与配置文件
 */
fun Routing.loadPlugin(plugPath: String, plugName: String) {
    if (pluginList.containsKey(plugName)) {
        error("Plugin $plugName is already loaded.")
    }
    val jarPath = File(plugPath, "$plugName.jar")
    val cfgPath = File(plugPath, "$plugName.cfg")
    if (jarPath.exists() && cfgPath.exists()) {
        val m = cfgPath.readLines().filter { it.trim() != "" && it.contains("=") }.associate { it.split("=").run { Pair(this[0], this[1]) } }
        loadPlugin(jarPath, m["PluginClass"] ?: error("PluginClass must have a value"), m["RoutingMethods"] ?: error("RoutingMethods must have a value"))
        pluginList[plugName] = (m["Routings"] ?: error("Routings must have a value")).split(",")
    }
}

/**
 * 卸载插件
 * @param plugName 插件名称
 */
fun Routing.unloadPlugin(plugName: String) {
    if (!pluginList.containsKey(plugName)) {
        error("Plugin $plugName was not loaded.")
    }
    unloadRoute(pluginList[plugName]!!)
    pluginList.remove(plugName)
}

/**:
 * 判断某个插件是否已加载
 * @param plugName 插件名称
 */
fun Routing.isPluginLoaded(plugName: String) = pluginList.containsKey(plugName)

/**
 * 加载指定目录下的所有插件
 * @param plugPath 插件所在目录
 */
fun Routing.loadAllPlugins(plugPath: String) = File(plugPath).listFiles()?.filter { it.extension == "jar" }?.map { it.nameWithoutExtension }?.forEach {
    loadPlugin(plugPath, it)
}

/**
 * 卸载指定目录下的所有插件
 * @param plugPath 插件所在目录
 */
fun Routing.unloadAllPlugins(plugPath: String) = File(plugPath).listFiles()?.filter { it.extension == "jar" }?.map { it.nameWithoutExtension }?.forEach {
    unloadPlugin(it)
}

/**
 * 卸载所有插件
 */
fun Routing.unloadAllPlugins() {
    pluginList.forEach { (_, u) -> unloadRoute(u) }
    pluginList.clear()
}

private fun Routing.loadPlugin(jarFile: File, clsName: String, routingName: String) =
    URLClassLoader(arrayOf(jarFile.toURI().toURL()))
        .loadClass(clsName)
        .getDeclaredMethod(routingName, Routing::class.java)
        .invoke(null, this)

private fun Routing.unloadRoute(routes: List<String>) {
    val list = javaClass.superclass.getDeclaredField("childList").apply { isAccessible = true }.get(this) as MutableList<*>
    list.removeIf { "$it" in routes }
}
