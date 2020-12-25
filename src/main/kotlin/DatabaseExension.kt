package com.isyscore.kotlin.ktor

import io.ktor.application.Application
import io.ktor.util.*
import java.sql.Connection
import java.sql.DriverManager

private var connection: Connection? = null

@Suppress("MemberVisibilityCanBePrivate")
@KtorExperimentalAPI
class DB(val app: Application) {

    private fun newConnection(): Connection {
        val driver = app.config("ktor.database.driver")
        val url = app.config("ktor.database.url")
        val user = app.config("ktor.database.user")
        val password = app.config("ktor.database.password")
        Class.forName(driver)
        return DriverManager.getConnection(url, user, password)
    }

    fun conn(): Connection {
        if (connection == null) {
            connection = newConnection()
        } else if (connection!!.isClosed) {
            connection = newConnection()
        }
        return connection!!
    }
}
