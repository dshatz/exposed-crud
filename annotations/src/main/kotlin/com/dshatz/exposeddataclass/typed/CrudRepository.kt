package com.dshatz.exposeddataclass.typed

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement

data class CrudRepository<T, ID : Any, E, N>(private val table: T, private val related: List<ColumnSet> = emptyList()) where T: IdTable<ID>, T: IEntityTable<E, N, ID>{

    private fun selectWithJoins(): Query {
        return if (related.isEmpty()) {
            table.selectAll()
        } else {
            var source = table.leftJoin(related.first())
            related.drop(1).forEach {
                source = source.leftJoin(it)
            }
            source.selectAll()
        }
    }

    fun selectAllLazy(): SizedIterable<E> {
        return selectWithJoins().mapLazy { table.toEntity(it) }
    }

    fun selectAll(): List<E> {
        return selectWithJoins().map { table.toEntity(it) }
    }

    fun create(data: N): InsertStatement<Number> {
        return table.insert {
            table.writeExceptAutoIncrementing(it, data)
        }
    }

    fun createReturning(data: N): E {
        return table.insertReturning {
            table.writeExceptAutoIncrementing(it, data)
        }.first().let(table::toEntity)
    }

    fun insert(data: E): InsertStatement<Number> {
        return table.insert {
            table.write(it, data)
        }
    }

    fun update(where: SqlExpressionBuilder.() -> Op<Boolean>, data: N) {
        table.update(where) {
            table.writeExceptAutoIncrementing(it, data)
        }
    }

    fun update(data: E) {
        with (table) {
            update({
                table.id eq table.makePK(data)
            }) {
                table.write(it, data)
            }
        }
    }

    fun select(): TypedSelect<T, E, ID> {
        return TypedSelect(table, selectWithJoins())
    }

    fun withRelated(table: ColumnSet) = copy(related = related + table)

    fun findById(id: ID): E? {
        val eid = EntityID(id, table)
        return selectWithJoins().where({
            table.id eq eid
        }).limit(1).firstOrNull()?.let(table::toEntity)
    }

    fun findOne(where: SqlExpressionBuilder.() -> Op<Boolean>): E? {
        return select().where(where).limit(1).firstOrNull()
    }
}