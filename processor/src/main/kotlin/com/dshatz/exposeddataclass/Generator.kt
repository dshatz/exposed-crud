package com.dshatz.exposeddataclass

import com.dshatz.exposeddataclass.typed.CrudRepository
import com.dshatz.exposeddataclass.typed.IEntityTable
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import kotlin.uuid.ExperimentalUuidApi

class Generator(private val models: Map<ClassName, EntityModel>) {

    private val newModelMapping: MutableMap<EntityModel, ClassName> = mutableMapOf()
    private val typedQueriesGenerator: TypedQueriesGenerator? = TypedQueriesGenerator(newModelMapping)

    fun generate(): List<FileSpec> {
        // https://www.jetbrains.com/help/exposed/getting-started-with-exposed.html#define-table-object
        return models.values.map { tableModel ->
            val fileSpec = FileSpec.builder(tableModel.tableClass)

            val newModel = if (tableModel.columns.any { it.autoIncrementing } && tableModel.columns.any { !it.autoIncrementing })
                typedQueriesGenerator?.generateNewModel(tableModel)
            else null

            val newModelClass = newModelMapping[tableModel] ?: tableModel.originalClassName

            // object Tasks : <Int>IdTable("tasks") {
            val tableDef = TypeSpec.objectBuilder(tableModel.tableClass)
                .addSuperinterface(IEntityTable::class.asTypeName().parameterizedBy(tableModel.originalClassName, newModelClass, tableModel.idType()))

            val primaryKeyInit = when (tableModel.primaryKey) {
                is PrimaryKey.Composite -> CodeBlock.of("PrimaryKey(%L)", tableModel.primaryKey.props.joinToString(", ") { it.nameInDsl })
                is PrimaryKey.Simple -> CodeBlock.of("PrimaryKey(%L)", tableModel.primaryKey.prop.nameInDsl)
            }

            val dontGeneratePK = tableModel.shouldExcludePKFields()

            val primaryKey = PropertySpec.builder("primaryKey", Table.PrimaryKey::class, KModifier.OVERRIDE)
                .initializer(primaryKeyInit).takeUnless { dontGeneratePK }

            tableModel.columns.filterNot { dontGeneratePK && it in tableModel.primaryKey }.forEach {
                tableDef.addProperty(it.generateProp(tableModel))
            }

            primaryKey?.build()?.let(tableDef::addProperty)
            tableDef.addFunction(tableModel.generateToEntityConverter())
            tableDef.addFunction(generateUpdateApplicator(tableModel, true))
            tableDef.addFunction(generateUpdateApplicator(tableModel, false))
            tableDef.addFunction(generatePKMaker(tableModel))
            tableDef.addTableSuperclass(tableModel)

            if (tableModel.primaryKey is PrimaryKey.Composite) tableDef.addInitializerBlock(
                CodeBlock.builder()
                    .apply {
                        tableModel.primaryKey.forEach {
                            addStatement("addIdColumn(%N)", it.nameInDsl)
                        }
                    }.build()
            )


            fileSpec
                .addType(tableDef.build())
                .apply {
                    newModel?.let { addType(it) }
                    typedQueriesGenerator?.generateTypesQueries(tableModel)?.let { addProperty(it) }
                }
                .addFunction(generateFindById(tableModel))
                .build()
        }
    }

    private fun generateEntity(model: EntityModel): TypeSpec {
        val originalClassName = model.originalClassName
        val entityClassName = ClassName(model.tableClass.packageName, originalClassName.simpleName + "Entity")
        val spec = TypeSpec.classBuilder(entityClassName)
        model.entitySuperclass().let { (superclass, init) ->
            spec.superclass(superclass)
            spec.addSuperclassConstructorParameter(init)
        }
        model.entityCompanionObjectSuperclass(entityClassName, model.tableClass).let { (superclass, init) ->
            spec.addType(
                TypeSpec.companionObjectBuilder()
                    .superclass(superclass)
                    .addSuperclassConstructorParameter(init)
                    .build()
            )
        }
        model.entityIdType().let { parameterType ->
            spec.primaryConstructor(
                FunSpec
                    .constructorBuilder()
                    .addParameter("id", parameterType)
                    .build()
            )
        }

        (model.columns - model.primaryKey).forEach {
            val prop = if (it.foreignKey != null) {
                val relatedModel = models[it.foreignKey.related]!!
                PropertySpec.builder(it.nameInEntity, relatedModel.entityClass)
                    .mutable(true)
                    .delegate(CodeBlock.of("%T.referencedOn(%T.id)", relatedModel.entityClass, relatedModel.tableClass))
                    .build()
            } else {
                PropertySpec.builder(it.nameInEntity, it.type)
                    .mutable(true)
                    .delegate("%T.%N", model.tableClass, it.nameInDsl)
                    .build()
            }
            spec.addProperty(prop)
        }
        return spec.build()
    }

    private fun generateUpdateApplicator(model: EntityModel, excludeAutoIncrement: Boolean): FunSpec {
        val dataType = if (excludeAutoIncrement) {
            newModelMapping[model] ?: model.originalClassName
        } else model.originalClassName

        val spec = FunSpec.builder(if (excludeAutoIncrement) "writeExceptAutoIncrementing" else "write")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(ParameterSpec.builder("update", UpdateBuilder::class.parameterizedBy(Number::class)).build())
            .addParameter(ParameterSpec("data", dataType))
        model.columns.filterNot { excludeAutoIncrement && it.autoIncrementing }.forEach {
            spec.addStatement("update[%N] = data.%L", it.nameInDsl, it.nameInEntity)
        }
        return spec.build()
    }

    private fun generatePKMaker(model: EntityModel): FunSpec {
        val spec = FunSpec.builder("makePK")
            .addModifiers(KModifier.OVERRIDE)
            .returns(model.entityIdType())
            .addParameter(ParameterSpec("data", model.originalClassName))

        val code = when (model.primaryKey) {
            is PrimaryKey.Composite -> {
                CodeBlock.builder()
                    .beginControlFlow("%T", CompositeID::class)
                    .apply {
                        model.primaryKey.props.forEach { prop ->
                            addStatement("it.set(%T.%N, data.%N)", model.tableClass, prop.nameInDsl, prop.nameInEntity)
                        }
                    }
                    .endControlFlow()
                    .build()
            }
            is PrimaryKey.Simple -> {
                CodeBlock.of("data.%N", model.primaryKey.prop.nameInEntity)
            }
        }
        return spec.addCode(CodeBlock.of("return %T(%L, %T)", model.entityIdType(), code, model.tableClass)).build()
    }

    /**
     * Generates a toEntity(ResultRow): Model.
     */
    private fun EntityModel.generateToEntityConverter(): FunSpec {
        val convertingCode = CodeBlock.builder()

        columns.forEach {
            if (it in primaryKey || it.foreignKey != null) {
                convertingCode.addStatement("%N = row[%N].value,", it.nameInEntity, it.nameInDsl)
            } else {
                convertingCode.addStatement("%N = row[%N],", it.nameInEntity, it.nameInDsl)
            }
        }
        val member = MemberName("com.dshatz.exposeddataclass.typed", "parseReferencedEntity")
        references.forEach { (column, refInfo) ->
            convertingCode.addStatement("%N = %M(row, %T)", column.nameInEntity, member, models[refInfo.related]!!.tableClass)
        }
        val fromRow = FunSpec.builder("toEntity")
            .addModifiers(KModifier.OVERRIDE)
            .returns(originalClassName)
            .addParameter("row", ResultRow::class)
            .addStatement("return %T(\n%L)", originalClassName, convertingCode.build())
            .build()
        return fromRow
    }

    private fun ColumnModel.generateProp(tableModel: EntityModel): PropertySpec {
        val isSimpleId = tableModel.primaryKey is PrimaryKey.Simple && this in tableModel.primaryKey

        val colType = if (foreignKey != null) {
            models[foreignKey.related]!!.entityIdType()
        } else if (isSimpleId) {
            tableModel.entityIdType()
        } else type

        val propType = Column::class.asTypeName().parameterizedBy(colType)


        val spec = PropertySpec.builder(nameInDsl, propType)
        if (isSimpleId) spec.addModifiers(KModifier.OVERRIDE)
        val initializer = CodeBlock.builder()
        if (this.foreignKey != null) {
            val relatedModel = models[foreignKey.related]
            val remotePK = relatedModel?.primaryKey
            when (remotePK) {
                is PrimaryKey.Composite -> TODO()
                is PrimaryKey.Simple -> initializer.add("reference(%S, %T)", columnName, relatedModel.tableClass)
                null -> throw ProcessorException("Could not find primary key on referenced entity ${foreignKey.related}", this.declaration)
            }
        } else {
            initializer.add("%L(%S)", exposedTypeFun(), columnName)
            if (autoIncrementing) initializer.add(".autoIncrement()")
            default?.let { initializer.add(".default(%L)", it) }
            if (type.isNullable) initializer.add(".nullable()")
            if (this in tableModel.primaryKey) initializer.add(".entityId()")
        }

        return spec.initializer(initializer.build()).build()
    }

    private fun ColumnModel.exposedTypeFun(): String {
        return when (this.type.copy(nullable = false)) {
            Int::class.asTypeName() -> "integer"
            Long::class.asTypeName() -> "long"
            String::class.asTypeName() -> "text"
            Boolean::class.asTypeName() -> "bool"
            Float::class.asTypeName() -> "float"
            Short::class.asTypeName() -> "short"
            Char::class.asTypeName() -> "char"
            else -> throw ProcessorException("Unsupported type ${this.type}", this.declaration)
        }
    }

    /*private fun generateModelPrimaryKeyWhere(model: EntityModel): FunSpec {
        val spec = FunSpec.builder("whereUsingPrimaryKey")
            .addModifiers(KModifier.OVERRIDE)
            .receiver(ISqlExpressionBuilder::class)
            .addParameter("data", model.originalClassName)
            .returns(Op::class.parameterizedBy(Boolean::class))
        val code = CodeBlock.builder()
        val primaryKeyColumns = model.columns.filter { it in model.primaryKey }
        primaryKeyColumns.forEachIndexed { index, columnModel ->
            val opCode = CodeBlock.of("(%N eq data.%N)", columnModel.nameInDsl, columnModel.nameInEntity)
            if (index != 0) {
                code.addStatement(".%M(%L)", and, opCode)
            } else {
                code.addStatement("%L", opCode)
            }
        }
        return spec.addCode("return %L", code.build()).build()
    }*/

    private fun generateFindById(model: EntityModel): FunSpec {
        val idCode = when (model.primaryKey) {
            is PrimaryKey.Composite -> {
                CodeBlock.builder()
                    .beginControlFlow("%T", CompositeID::class)
                    .apply {
                        model.primaryKey.forEach {
                            addStatement("it[%T.%N] = %N", model.tableClass, it.nameInDsl, it.nameInEntity)
                        }
                    }
                    .endControlFlow()
                    .build()
            }
            is PrimaryKey.Simple -> {
                CodeBlock.builder()
                    .addStatement("%N", model.primaryKey.prop.nameInEntity)
                    .build()
            }
        }
        return FunSpec.builder("findById")
            .receiver(CrudRepository::class.asTypeName().parameterizedBy(
                model.tableClass,
                model.idType(),
                model.originalClassName,
                newModelMapping[model] ?: model.originalClassName
            ))
            .returns(model.originalClassName.copy(nullable = true))
            .addParameters(model.primaryKey.map {
                ParameterSpec(it.nameInEntity, it.type)
            })
            .addCode("return findOne({%T.id.eq(EntityID(%L, %T))})", model.tableClass, idCode, model.tableClass)
            .build()
    }

    private fun TypeSpec.Builder.addTableSuperclass(model: EntityModel) = model.apply {
        val (superclass, constructorParams) = tableSuperclass()
        superclass(superclass)
        addSuperclassConstructorParameter(constructorParams)
    }

    companion object {
        private val and = MemberName("org.jetbrains.exposed.sql", "and")
    }
}