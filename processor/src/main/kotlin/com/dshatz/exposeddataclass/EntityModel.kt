package com.dshatz.exposeddataclass

import com.dshatz.exposed_crud.typed.CrudRepository
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.exposed.dao.id.*
import java.util.*

data class EntityModel(
    val declaration: KSClassDeclaration,
    val originalClassName: ClassName,
    val tableName: String,
    val columns: List<ColumnModel>,
    val annotations: List<AnnotationInfo>,
    val primaryKey: PrimaryKey,
    val uniques: Map<String, List<ColumnModel>>,
    val references: Map<ColumnModel, ReferenceInfo.WithFK>,
    val backReferences: Map<ColumnModel, ReferenceInfo.Reverse>
) {

    override fun toString(): String {
        return "[${declaration.simpleName.asString()}] (${columns.joinToString { 
            it.nameInDsl + (it.default?.let { " = " + it.toString() } ?: "")
        }}) PK: $primaryKey; Unique: $uniques"
    }

    val tableClass by lazy {
        ClassName(originalClassName.packageName, originalClassName.simpleName + "Table")
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

        fun EntityModel.crudRepositoryType(): ParameterizedTypeName {
            return CrudRepository::class.asTypeName().parameterizedBy(
                tableClass,
                idType(),
                originalClassName,
            )
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
    val foreignKey: FKInfo?,
    val attrs: List<FieldAttrs>
) {
    override fun toString(): String {
        return "$nameInDsl: $type"
    }
}

sealed class FieldAttrs {

    data class Collate(val collate: String? = null): FieldAttrs()
    sealed class ColType: FieldAttrs() {
        abstract val exposedFunction: kotlin.String
        sealed class String(): ColType() {
            data class Varchar(val length: Int): String() {
                override val exposedFunction: kotlin.String = "varchar"
            }

            sealed class Text: String() {
                abstract val eager: Boolean
                data class GenericText(override val eager: Boolean): Text() {
                    override val exposedFunction: kotlin.String = "text"
                }
                data class MediumText(override val eager: Boolean): Text() {
                    override val exposedFunction: kotlin.String = "mediumText"
                }
                data class LargeText(override val eager: Boolean): Text() {
                    override val exposedFunction: kotlin.String = "largeText"
                }
            }
        }

        sealed class Json: ColType() {

            abstract val formatName: kotlin.String
            data class Json(override val formatName: kotlin.String): ColType.Json() {
                override val exposedFunction: kotlin.String = "json"
            }
            data class Jsonb(override val formatName: kotlin.String): ColType.Json() {
                override val exposedFunction: kotlin.String = "jsonb"
            }
        }
    }
}

sealed class PrimaryKey: Iterable<ColumnModel> {
    data class Simple(val prop: ColumnModel): PrimaryKey() {
        override fun iterator(): Iterator<ColumnModel> = listOf(prop).iterator()
    }
    data class Composite(val props: List<ColumnModel>): PrimaryKey() {
        override fun iterator(): Iterator<ColumnModel> = props.iterator()

    }
}


data class FKInfo(val related: TypeName, val onlyColumn: String? = null)

sealed class ReferenceInfo(open val related: TypeName) {
    data class WithFK(override val related: TypeName, val localIdProps: Array<String>): ReferenceInfo(related)
    data class Reverse(override val related: TypeName, val isMany: Boolean): ReferenceInfo(related)
}

data class AnnotationInfo(val cls: ClassName, val params: List<Any?>)
