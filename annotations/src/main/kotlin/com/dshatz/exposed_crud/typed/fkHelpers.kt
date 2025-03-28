package com.dshatz.exposed_crud.typed

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ResultRow
import java.util.*


fun <T, TABLE> IdTable<*>.parseReferencedEntity(resultRow: ResultRow, referencedTable: TABLE): T?
where TABLE: IEntityTable<T, *>, TABLE: IdTable<*> {
    return runCatching { referencedTable.toEntity(resultRow) }.getOrNull()
}
