package com.dshatz.exposed_crud.typed

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder

interface IEntityTable<T, ID: Any> {
    fun toEntity(row: ResultRow, related: List<ColumnSet> = emptyList()): T {
        error("Not implemented")
    }

    fun Query.toEntityList(): List<T> = map(::toEntity)

    abstract fun write(update: UpdateBuilder<Number>, data: T)
    abstract fun writeExceptAutoIncrementing(update: UpdateBuilder<Number>, data: T)
    abstract fun makePK(data: T): EntityID<ID>
}