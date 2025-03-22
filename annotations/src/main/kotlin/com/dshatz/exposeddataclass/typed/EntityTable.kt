package com.dshatz.exposeddataclass.typed

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder

interface IEntityTable<T, N, ID: Any> {
    fun toEntity(row: ResultRow): T {
        error("Not implemented")
    }

    fun Query.toEntityList(): List<T> = map(::toEntity)

    abstract fun write(update: UpdateBuilder<Number>, data: T)
    abstract fun writeExceptAutoIncrementing(update: UpdateBuilder<Number>, data: N)
    abstract fun makePK(data: T): EntityID<ID>
}