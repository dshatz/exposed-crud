package com.dshatz.exposeddataclass

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.id.*
import java.util.*

data class EntityModel(
    val declaration: KSClassDeclaration,
    val originalClassName: ClassName,
    val tableName: String,
    val columns: List<ColumnModel>,
    val primaryKey: PrimaryKey,
    val references: Map<ColumnModel, ReferenceInfo>
) {

    val tableClass by lazy {
        ClassName(originalClassName.packageName, originalClassName.simpleName + "Table")
    }

    val entityClass by lazy {
        ClassName(originalClassName.packageName, originalClassName.simpleName + "Entity")
    }

    fun tableSuperclass(): Pair<TypeName, CodeBlock> {
        return when (primaryKey) {
            is PrimaryKey.Composite -> {
                CompositeIdTable::class.asClassName() to CodeBlock.of("%S", tableName)
            }
            is PrimaryKey.Simple -> {
                if (primaryKey.prop.type in tableTypes) {
                    tableTypes[primaryKey.prop.type]!! to CodeBlock.of("%S, %S", tableName, primaryKey.prop.nameInDsl)
                } else IdTable::class.asTypeName().parameterizedBy(primaryKey.prop.type) to CodeBlock.of("%S", tableName)
            }
        }
    }

    fun entitySuperclass(): Pair<TypeName, CodeBlock> {
        val codeBlock = CodeBlock.of("id")
        return when (primaryKey) {
            is PrimaryKey.Composite -> {
                CompositeEntity::class.asTypeName() to codeBlock
            }
            is PrimaryKey.Simple -> {
                if (primaryKey.prop.type in entityTypes) {
                    entityTypes[primaryKey.prop.type]!! to codeBlock
                } else Entity::class.asTypeName().parameterizedBy(primaryKey.prop.type) to codeBlock
            }
        }
    }

    fun entityCompanionObjectSuperclass(entityClass: ClassName, tableClass: ClassName): Pair<TypeName, CodeBlock> {
        val codeBlock = CodeBlock.of("%T", tableClass)
        return when (primaryKey) {
            is PrimaryKey.Composite -> {
                CompositeEntityClass::class.asTypeName().parameterizedBy(entityClass) to codeBlock
            }
            is PrimaryKey.Simple -> {
                if (primaryKey.prop.type in entityCompanionTypes) {
                    entityCompanionTypes[primaryKey.prop.type]!!.parameterizedBy(entityClass) to codeBlock
                } else EntityClass::class.asTypeName().parameterizedBy(primaryKey.prop.type, entityClass) to codeBlock
            }
        }
    }

    fun entityIdType(): TypeName {
        return EntityID::class.asTypeName().parameterizedBy(idType())
    }

    fun idType(): TypeName {
        return when (primaryKey) {
            is PrimaryKey.Composite -> {
                CompositeID::class.asTypeName()
            }
            is PrimaryKey.Simple -> {
                primaryKey.prop.type
            }
        }
    }

    fun shouldExcludePKFields(): Boolean {
        return primaryKey is PrimaryKey.Simple && primaryKey.prop.type in simpleIdTypes
    }

    companion object {
        private val simpleIdTypes = sequenceOf(Int::class, Long::class, UInt::class, ULong::class, UUID::class)
            .map { it.asTypeName() }.toSet()
        private val tableTypes = simpleIdTypes.associateWith {
            ClassName("org.jetbrains.exposed.dao.id", it.simpleName + "IdTable")
        }

        private val entityTypes = simpleIdTypes.associateWith {
            ClassName("org.jetbrains.exposed.dao", it.simpleName + "Entity")
        }

        private val entityCompanionTypes = simpleIdTypes.associateWith {
            ClassName("org.jetbrains.exposed.dao", it.simpleName + "EntityClass")
        }
    }
}

data class ColumnModel(
    val declaration: KSPropertyDeclaration,
    val nameInEntity: String,
    val columnName: String,
    val nameInDsl: String,
    val type: TypeName,
    val autoIncrementing: Boolean,
    val default: CodeBlock?,
    val foreignKey: FKInfo?
)

sealed class PrimaryKey: Iterable<ColumnModel> {
    data class Simple(val prop: ColumnModel): PrimaryKey() {
        override fun iterator(): Iterator<ColumnModel> = listOf(prop).iterator()
    }
    data class Composite(val props: List<ColumnModel>): PrimaryKey() {
        override fun iterator(): Iterator<ColumnModel> = props.iterator()

    }
}


data class FKInfo(val related: TypeName)

data class ReferenceInfo(val related: TypeName, val localIdProps: Array<String>)
