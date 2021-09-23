package com.isyscore.kotlin.ktor

/********************************************************************
  Notes: no extensions now, you may follow the code template for databases

// declare an entity class
interface Department: Entity<Department> {
    companion object: Entity.Factory<Department>()
    val id: Int
    var name: String
}

// declare a data table class and bind to entity
object Departments: Table<Department>("t_department") {
    val id = int("id").primaryKey().bindTo { it.id }
    val name = varchar("name").bindTo { it.name }
}

// extend Application.database
val Database.departments get() = this.sequenceOf(Departments)

 */

