package com.dshatz.exposed_crud.typed

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*

data class TypedSelect<T, E, ID: Any>(val table: T, private val query: Query): Iterable<E> where T: IdTable<ID>, T: IEntityTable<E, *, ID> {


    fun where(predicate: SqlExpressionBuilder.() -> Op<Boolean>) = copy(query = query.where(predicate))
    fun where(predicate: Op<Boolean>) = copy(query = query.where(predicate))
    fun orWhere(orPart: SqlExpressionBuilder.() -> Op<Boolean>) = copy(query = query.orWhere(orPart))
    fun andWhere(andPart: SqlExpressionBuilder.() -> Op<Boolean>) = copy(query = query.andWhere(andPart))

    fun limit(limit: Int) = copy(query = query.limit(limit))

    fun withDistinctOn(vararg columns: Column<*>) = copy(query = query.withDistinctOn(columns = columns))
    fun withDistinctOn(vararg columns: Pair<Column<*>, SortOrder>) = copy(query = query.withDistinctOn(columns = columns))
    fun withDistinctOn(value: Boolean = true) = copy(query = query.withDistinct(value))


    override fun iterator(): Iterator<E> = query.mapLazy(table::toEntity).iterator()

}