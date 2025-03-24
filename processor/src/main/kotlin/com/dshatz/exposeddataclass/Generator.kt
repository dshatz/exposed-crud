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
import javax.swing.table.TableModel
import kotlin.uuid.ExperimentalUuidApi

class Generator(private val models: Map<ClassName, EntityModel>) {

    private val newModelMapping: MutableMap<EntityModel, ClassName> = mutableMapOf()
    private val typedQueriesGenerator: TypedQueriesGenerator? = TypedQueriesGenerator(newModelMapping)

    private val finalColumnTypes: MutableMap<Pair<EntityModel, ColumnModel>, TypeName> = mutableMapOf()

    fun generate(): Map<EntityModel, FileSpec> {
        // https://www.jetbrains.com/help/exposed/getting-started-with-exposed.html#define-table-object
        models.values.forEach { calculateColumnTypes(it) }
        return models.values.associateWith { tableModel ->
            val fileSpec = FileSpec.builder(tableModel.tableClass)

            val newModel = if (tableModel.columns.any { it.autoIncrementing } && tableModel.columns.any { !it.autoIncrementing })
                typedQueriesGenerator?.generateNewModel(tableModel)
            else null

            val newModelClass = newModelMapping[tableModel] ?: tableModel.originalClassName


            validateForeignKeyAnnotations(tableModel)
            validateReferenceAnnotations(tableModel)
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

            /*if (tableModel.primaryKey is PrimaryKey.Composite) tableDef.addInitializerBlock(
                CodeBlock.builder()
                    .apply {
                        tableModel.primaryKey.forEach {
                            addStatement("addIdColumn(%N)", it.nameInDsl)
                        }
                    }.build()
            )*/


            fileSpec
                .addType(tableDef.build())
                .apply {
                    newModel?.let { addType(it) }
                    typedQueriesGenerator?.generateTypesQueries(tableModel)?.let { addProperty(it) }
                }
                .addFunction(generateFindById(tableModel))
                .apply {
                    if (tableModel.references.isNotEmpty() || tableModel.columns.any { it.foreignKey != null }) {
                        addFunction(generateInsertWithRelated(tableModel))
                    }
                }
                .build()
        }
    }

    private fun calculateColumnTypes(tableModel: EntityModel) {
        tableModel.columns.forEach {
            it.apply {
                val colType = if (foreignKey != null) {
                    // Foreign key
                    val relatedModel = models[foreignKey.related]
                    val remotePK = relatedModel?.primaryKey
                    when (remotePK) {
                        is PrimaryKey.Simple -> {
                            models[foreignKey.related]!!.entityIdType()
                        }
                        is PrimaryKey.Composite -> {
                            val remoteColumn = foreignKey.onlyColumn ?: throw ProcessorException("@ForeignKey to a table with composite FK must specify remote column name", declaration)
                            relatedModel.columns.find { it.nameInDsl == remoteColumn }!!.type
                        }
                        else -> {
                            type
                        }
                    }

                } else {
                    // Not a FK
                    if (this in tableModel.primaryKey) {
                        // column part of PK
                        if (tableModel.primaryKey is PrimaryKey.Simple) {
                            // Column is the sole PK.
                            tableModel.entityIdType()
                        } else {
                            // Column is part of a composite PK.
                            EntityID::class.asTypeName().parameterizedBy(type)
                        }
                    } else type
                }
                finalColumnTypes[tableModel to this] = colType
            }
        }
    }

    private fun validateForeignKeyAnnotations(model: EntityModel) {
        model.columns.forEach { col ->
            if (col.foreignKey != null) {
                val remoteModel = models[col.foreignKey.related]
                    ?: throw ProcessorException("Unknown foreign key target ${col.foreignKey.related} - is it annotated with @Entity?", col.declaration)
                if (col.foreignKey.onlyColumn != null) {
                    // Custom column specified
                    val remoteColumn = remoteModel.columns.find { it.nameInDsl == col.foreignKey.onlyColumn }
                    if (remoteColumn == null) {
                        throw ProcessorException("Column ${col.foreignKey.onlyColumn} not found in ${remoteModel.originalClassName.simpleName}", col.declaration)
                    } else {
                        if (remoteColumn.type != col.type) throw ProcessorException("Column ${remoteModel.originalClassName.simpleName}.${remoteColumn.nameInDsl} is of type ${remoteColumn.type}, but @ForeignKey annotated prop is ${col.type}", col.declaration)
                    }
                }
            }
        }
    }

    private fun validateReferenceAnnotations(model: EntityModel) {
        model.references.entries.forEach { (column, refInfo) ->
            val remoteModel = models[refInfo.related] ?: throw ProcessorException("Unknown reference ${refInfo.related} - is it annotated with @Entity?", column.declaration)
            val remotePK = remoteModel.primaryKey
            if (refInfo.localIdProps.isNotEmpty()) {
                if (refInfo.localIdProps.size != remotePK.count()) {
                    throw ProcessorException("Expected ${remotePK.count()} column names in 'fkColumns' of @References. You can also not specify any in order to auto-detect them.", column.declaration)
                }
                refInfo.localIdProps.forEach { localIdProp ->
                    if (model.columns.find {
                        it.foreignKey?.related == refInfo.related && it.nameInDsl == localIdProp
                    } == null) {
                        throw ProcessorException("$localIdProp not found in ${model.tableName}. It should be annotated with @ForeignKey(${refInfo.related}::class)", model.declaration)
                    }
                }
            } else {
                // Auto detect foreign keys.
                remotePK.forEach { remoteIDColumn ->
                    val localName = remoteModel.originalClassName.simpleName.decapitate() + remoteIDColumn.nameInDsl.capitalize()
                    if (model.columns.find { it.foreignKey?.related == refInfo.related && it.nameInDsl == localName } == null) {
                        throw ProcessorException("$localName not found in ${model.tableName}. It should be annotated with @ForeignKey(${refInfo.related}::class). If $localName is not correct, specify the correct name in @References", model.declaration)
                    }
                }
            }
        }
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
            convertingCode.addStatement("%N = %M(row, %T),", column.nameInEntity, member, models[refInfo.related]!!.tableClass)
        }
        val toEntity = FunSpec.builder("toEntity")
            .addModifiers(KModifier.OVERRIDE)
            .returns(originalClassName)
            .addParameter("row", ResultRow::class)
            .addStatement("return %T(\n%L)", originalClassName, convertingCode.build())
            .build()
        return toEntity
    }

    private fun ColumnModel.generateProp(tableModel: EntityModel): PropertySpec {
        val isSimpleId = tableModel.primaryKey is PrimaryKey.Simple && this in tableModel.primaryKey

        val initializer = CodeBlock.builder()
        val colType = if (foreignKey != null) {
            // Foreign key
            val relatedModel = models[foreignKey.related]
            val remotePK = relatedModel?.primaryKey
            when (remotePK) {
                is PrimaryKey.Simple -> {
                    initializer.add("reference(%S, %T)", columnName, relatedModel.tableClass)
                    models[foreignKey.related]!!.entityIdType()
                }
                is PrimaryKey.Composite -> {
                    val remoteColumn = foreignKey.onlyColumn ?: throw ProcessorException("@ForeignKey to a table with composite FK must specify remote column name", declaration)
                    initializer.add("reference(%S, %T.%N)", columnName, relatedModel.tableClass, remoteColumn)
                    val remoteCol = relatedModel.columns.find { it.nameInDsl == remoteColumn }!!
                    finalColumnTypes[relatedModel to remoteCol]
                        ?: throw ProcessorException("Could not get column type for ${relatedModel.originalClassName.simpleName}.${remoteCol.nameInDsl}\n$finalColumnTypes", declaration)
                }
                else -> {
                    type
                }
            }.also {
                if (this in tableModel.primaryKey) initializer.add(".also(::addIdColumn)")
            }

        } else {
            // Not a FK
            initializer.add("%L(%S)", exposedTypeFun(), columnName)
            if (autoIncrementing) initializer.add(".autoIncrement()")
            default?.let { initializer.add(".default(%L)", it) }
            if (type.isNullable) initializer.add(".nullable()")
            if (this in tableModel.primaryKey) {
                // column part of PK
                initializer.add(".entityId()")
                if (tableModel.primaryKey is PrimaryKey.Simple) {
                    // Column is the sole PK.
                    tableModel.entityIdType()
                } else {
                    // Column is part of a composite PK.
                    EntityID::class.asTypeName().parameterizedBy(type)
                }
            } else type
        }

        val propType = Column::class.asTypeName().parameterizedBy(colType)

        val spec = PropertySpec.builder(nameInDsl, propType)
        if (isSimpleId) spec.addModifiers(KModifier.OVERRIDE)

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

    private fun EntityModel.crudRepositoryType(): ParameterizedTypeName {
        return CrudRepository::class.asTypeName().parameterizedBy(
            tableClass,
            idType(),
            originalClassName,
            newModelMapping[this] ?: originalClassName
        )
    }

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
            .receiver(model.crudRepositoryType())
            .returns(model.originalClassName.copy(nullable = true))
            .addParameters(model.primaryKey.map {
                ParameterSpec(it.nameInEntity, it.type)
            })
            .addCode("return findOne({%T.id.eq(EntityID(%L, %T))})", model.tableClass, idCode, model.tableClass)
            .build()
    }

    private fun generateInsertWithRelated(model: EntityModel): FunSpec {
        val related = model.references.values

        val foreignKeys = model.columns.filter { it.foreignKey != null }

        val modelParamName = model.originalClassName.simpleName.decapitate()

        data class RelatedInfo(
            val relatedModel: EntityModel,
            val param: ParameterSpec,
            val relatedField: ColumnModel,
            val localColumn: ColumnModel
        )

        val params = foreignKeys.map { it ->
            val fk = it.foreignKey!!
            val relatedModel = models[fk.related]!!
            val paramName = relatedModel.originalClassName.simpleName.decapitate()
            val type = newModelMapping[relatedModel] ?: relatedModel.originalClassName
            val onRemoteCol = relatedModel.columns.find { it.nameInDsl == fk.onlyColumn } ?: relatedModel.primaryKey.single()
            RelatedInfo(
                relatedModel,
                ParameterSpec.builder(paramName, type.copy(nullable = true))
                    .defaultValue("null").build(),
                onRemoteCol,
                it
            )
        }

        val insertCode = CodeBlock.builder().beginControlFlow("return %M", MemberName("org.jetbrains.exposed.sql.transactions", "transaction"))
        params.forEach { (relatedModel, param, remoteColumn, localColumn) ->
            val valRelatedId = param.name + remoteColumn.nameInDsl.capitalize()
            insertCode.addStatement("val %N = %N?.let { %T.repo.createReturning(%L).%N }", valRelatedId, param.name, relatedModel.tableClass, param.name, remoteColumn.nameInEntity)
            insertCode.addStatement("//$remoteColumn")
        }
        val copyCode = CodeBlock.builder().add("%N.copy(\n", modelParamName)
        params.forEach { (relatedModel, param, remoteColumn, localColumn) ->
            val withId = param.name + remoteColumn.nameInDsl.capitalize()
            copyCode.add("%N = %N ?: %N.%N,\n", localColumn.nameInDsl, withId, modelParamName, localColumn.nameInDsl)
        }
        copyCode.add(")")

        insertCode.addStatement("%T.repo.createReturning(%L)", model.tableClass, copyCode.build())
        insertCode.endControlFlow()

        return FunSpec.builder("createWithRelated")
            .receiver(model.crudRepositoryType())
            .addParameter(ParameterSpec(modelParamName, newModelMapping[model] ?: model.originalClassName))
            .returns(model.originalClassName)
            .addParameters(params.map { it.param })
            .addCode(insertCode.build())
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