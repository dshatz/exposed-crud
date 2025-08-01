package com.dshatz.exposeddataclass

import com.dshatz.exposed_crud.typed.IEntityTable
import com.dshatz.exposeddataclass.EntityModel.Companion.crudRepositoryType
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.UpdateBuilder

class Generator(
    private val models: Map<ClassName, EntityModel>,
    private val jsonFormats: JsonFormatModel,
    private val logger: KSPLogger
) {

    private val newModelMapping: MutableMap<EntityModel, ClassName> = mutableMapOf()
    private val typedQueriesGenerator: TypedQueriesGenerator? = TypedQueriesGenerator(newModelMapping)

    private val finalColumnTypes: MutableMap<Pair<EntityModel, ColumnModel>, TypeName> = mutableMapOf()

    private var jsonFormatAccessors: MutableMap<String, MemberName> = mutableMapOf()
    fun generate(): Map<EntityModel, FileSpec> {
        // https://www.jetbrains.com/help/exposed/getting-started-with-exposed.html#define-table-object
        models.values.forEach { calculateColumnTypes(it) }
        return models.values.associateWith { tableModel ->
            val fileSpec = FileSpec.builder(tableModel.tableClass)


            validateForeignKeyAnnotations(tableModel)
            validateReferenceAnnotations(tableModel)
            // object Tasks : <Int>IdTable("tasks") {
            val tableDef = TypeSpec.objectBuilder(tableModel.tableClass)
                .addSuperinterface(IEntityTable::class.asTypeName().parameterizedBy(tableModel.originalClassName, tableModel.idType()))

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
            if (tableModel.uniques.isNotEmpty()) {
                tableDef.addInitializerBlock(CodeBlock.builder().apply {
                    tableModel.uniques.forEach { indexName, columns ->
                        val template = columns.joinToString(", ") { "%N" }
                        addStatement("uniqueIndex(%S, $template)", indexName, *columns.map { it.nameInEntity }.toTypedArray())
                    }
                }.build())
            }

            fileSpec
                .addType(tableDef.build())
                .apply {
                    typedQueriesGenerator?.generateRepoAccessors(tableModel)?.forEach { addProperty(it) }
                }
                .addFunction(generateFindById(tableModel))
                .addFunction(generateDeleteById(tableModel))
                .apply {
                    if (tableModel.columns.any { it.foreignKey != null }) {
                        addFunction(generateInsertWithRelated(tableModel))
                    }
                }
                .build()
        }
    }

    fun generateJsonFormatAccessors(): Map<String, Pair<KSFunctionDeclaration, FileSpec>> {
        return jsonFormats.formats.mapValues { (name, declaration) ->
            val fileSpec = FileSpec.builder(declaration.packageName.asString(), declaration.simpleName.asString() + "JsonFormats")
            val accessorSpec = PropertySpec.builder(name + "jsonFormat", ClassName("kotlinx.serialization.json", "Json"))
                .delegate(CodeBlock.builder()
                    .beginControlFlow("lazy")
                    .addStatement("${declaration.qualifiedName?.asString()}()")
                    .endControlFlow()
                    .build()
                ).build()
            jsonFormatAccessors[name] = MemberName(declaration.packageName.asString(), name + "jsonFormat")
            declaration to fileSpec.addProperty(accessorSpec).build()
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
                // Auto-detect foreign keys.
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
     * Given a model and reference belonging to a column on that model, return a list of columns with FKs relating to that reference.
     */
    private fun getForeignKeysForReference(tableModel: EntityModel, referenceInfo: ReferenceInfo.WithFK): Collection<ColumnModel> {
        val specifiedColumns = referenceInfo.localIdProps.takeUnless { it.isEmpty() }?.toList()
        return if (specifiedColumns == null) {
            // Find matching foreign keys
            val fkColumns = tableModel.columns.filter {
                it.foreignKey?.related == referenceInfo.related
            }.associateBy { it.nameInDsl }

            val relatedModel = models[referenceInfo.related]!!
            relatedModel.primaryKey.forEach {
                if (it.nameInDsl !in fkColumns) throw ProcessorException("@Reference(${relatedModel.originalClassName.simpleName}) defined but no sufficient ForeignKeys found. Expected @ForeignKey annotated columns ${relatedModel.primaryKey.map { it.nameInDsl }}", tableModel.declaration)
            }
            fkColumns.values
        } else specifiedColumns.map { name -> tableModel.columns.find { it.nameInDsl == name }!! }
    }

    /**
     * Given a FKInfo, return the referenced column on the related table.
     */
    private fun getForeignKeyColumn(foreignKey: FKInfo): ColumnModel {
        val relatedModel = models[foreignKey.related]!!
        if (foreignKey.onlyColumn != null) return relatedModel.columns.find { it.nameInDsl == foreignKey.onlyColumn }!!
        else {
            return (relatedModel.primaryKey as PrimaryKey.Simple).prop
        }
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
        val member = MemberName("com.dshatz.exposed_crud.typed", "parseReferencedEntity")
        references.forEach { (column, refInfo) ->
            convertingCode.addStatement("%N = %M(row, %T),", column.nameInEntity, member, models[refInfo.related]!!.tableClass)
        }
        backReferences.forEach { (column, refinfo) ->
            val relatedModel = models[refinfo.related]!!
            val (_, remoteReference) = relatedModel.references.entries.find { it.value.related == this.originalClassName }
                ?: throw ProcessorException("Could not find @Reference on ${relatedModel.originalClassName} that matches this @BackReference", column.declaration)

            val remoteColumns = getForeignKeysForReference(relatedModel, remoteReference)

            val whereClause = CodeBlock.builder()
            remoteColumns.forEachIndexed { idx, col ->
                val localCol = getForeignKeyColumn(col.foreignKey!!).nameInDsl
                val remoteCol = col.nameInDsl
                whereClause.add(
                    "%T.%N.%M(row[%N])",
                    relatedModel.tableClass,
                    remoteCol,
                    MemberName("org.jetbrains.exposed.sql.SqlExpressionBuilder","eq"),
                    localCol,
                )
            }
            val code = CodeBlock.builder().addNamed("%localProp:N = if (%relTable:T in related) %relTable:T.repo.select()", mapOf(
                "localProp" to column.nameInEntity,
                "relTable" to relatedModel.tableClass
            )).beginControlFlow(".where")
                .add("%L\n", whereClause.build())
                .endControlFlow()
                .add(".toList() else null,")
                .build()
            convertingCode.addStatement("%L", code)
        }
        val toEntity = FunSpec.builder("toEntity")
            .addModifiers(KModifier.OVERRIDE)
            .returns(originalClassName)
            .addParameter("row", ResultRow::class)
            .addParameter(ParameterSpec.builder("related", LIST.parameterizedBy(ColumnSet::class.asTypeName())).build())
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
            val typeFun = exposedTypeFun(columnName)
            initializer.add(typeFun)
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

    private fun ColumnModel.exposedTypeFun(colName: String): CodeBlock {
        val kotlinDateTimePackage = "org.jetbrains.exposed.sql.kotlin.datetime"
        fun makeBuiltinCode(name: String): CodeBlock {
            return CodeBlock.of("%N(%S)", MemberName(Table::class.asClassName(), name), colName)
        }
        fun makeKotlinDatetimeCode(name: String): CodeBlock {
            return CodeBlock.of(
                "%M(%S)",
                MemberName("org.jetbrains.exposed.sql.kotlin.datetime", name, isExtension = true),
                colName
            )
        }
        fun makeTextCode(type: FieldAttrs.ColType.String.Text, collate: String?): CodeBlock {
            return CodeBlock.of(
                "%N(%S, %L, %L)",
                MemberName(Table::class.asClassName(), type.exposedFunction), // text, mediumText, largeText
                colName,
                collate?.let { "\"$it\"" }.toString(), // Quoted collate value or unquoted null,
                type.eager
            )
        }
        fun makeVarcharCode(length: Int, collate: String?): CodeBlock {
            return CodeBlock.of(
                "varchar(%S, %L, %L)",
                colName,
                length,
                collate?.let { "\"$it\"" }.toString(), // Quoted collate value or unquoted null,
            )
        }

        fun makeJsonCode(typeAttr: FieldAttrs.ColType.Json, propType: TypeName): CodeBlock {
            return CodeBlock.of(
                "%M<%T>(%S, %M)",
                MemberName("org.jetbrains.exposed.sql.json", typeAttr.exposedFunction, isExtension = true),
                propType.copy(nullable = false),
                colName,
                jsonFormatAccessors[typeAttr.formatName] ?: throw ProcessorException("Could not find json format with name ${typeAttr.formatName}. Please define it with @JsonFormat.", declaration)
            )
        }
        val nonNullType = type.copy(nullable = false)

        val colTypeAttrs = attrs.filterIsInstance<FieldAttrs.ColType>()

        return when (nonNullType) {
            BYTE -> makeBuiltinCode("byte")
            SHORT -> makeBuiltinCode("short")
            INT -> makeBuiltinCode("integer")
            LONG -> makeBuiltinCode("long")

            U_BYTE -> makeBuiltinCode("ubyte")
            U_SHORT -> makeBuiltinCode("ushort")
            U_INT -> makeBuiltinCode("uint")
            U_LONG -> makeBuiltinCode("ulong")

            CHAR -> makeBuiltinCode("char")
            STRING -> {
                val collate = attrs.filterIsInstance<FieldAttrs.Collate>().firstOrNull()?.collate
                val type = colTypeAttrs.filterIsInstance<FieldAttrs.ColType.String>().firstOrNull()
                    ?: FieldAttrs.ColType.String.Text.GenericText(false)
                when (type) {
                    is FieldAttrs.ColType.String.Text -> makeTextCode(type, collate)
                    is FieldAttrs.ColType.String.Varchar -> makeVarcharCode(type.length, collate)
                }
            }
            BOOLEAN -> makeBuiltinCode("bool")
            FLOAT -> makeBuiltinCode("float")
            DOUBLE -> makeBuiltinCode("double")
            BYTE_ARRAY -> makeBuiltinCode("binary")
            LIST -> makeBuiltinCode("array")
            else -> {
                when ((nonNullType as? ClassName)?.canonicalName) {
                    "kotlinx.datetime.LocalDate" -> makeKotlinDatetimeCode("date")
                    "kotlinx.datetime.LocalDateTime" -> makeKotlinDatetimeCode("datetime")
                    "kotlinx.datetime.Instant" -> makeKotlinDatetimeCode("timestamp")
                    "java.math.BigDecimal" -> makeBuiltinCode("decimal")
                    "java.util.UUID" -> makeBuiltinCode("uuid")
                    else -> {
                        // Some other type. Check for json attrs.
                        val jsonAttr = colTypeAttrs.filterIsInstance<FieldAttrs.ColType.Json>().firstOrNull()
                        if (jsonAttr != null) {
                            makeJsonCode(jsonAttr, type)
                        } else {
                            throw ProcessorException("Unsupported primitive ${this.type}. Please file a bug.", this.declaration)
                        }
                    }
                }
            }
        }
    }

    private fun generateFindById(model: EntityModel): FunSpec {
        val idCode = makeIdCode(model)
        return FunSpec.builder("findById")
            .receiver(model.crudRepositoryType())
            .returns(model.originalClassName.copy(nullable = true))
            .addParameters(model.primaryKey.map {
                ParameterSpec(it.nameInEntity, it.type)
            })
            .addCode("return findOne({%T.id.eq(EntityID(%L, %T))})", model.tableClass, idCode, model.tableClass)
            .build()
    }

    private fun makeIdCode(model: EntityModel): CodeBlock {
        return when (model.primaryKey) {
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
    }

    private fun generateDeleteById(model: EntityModel): FunSpec {
        val idCode = makeIdCode(model)
        val deleteWhere = MemberName("org.jetbrains.exposed.sql", "deleteWhere")
        val eq = MemberName("org.jetbrains.exposed.sql.SqlExpressionBuilder", "eq")
        return FunSpec.builder("deleteById")
            .receiver(model.crudRepositoryType())
            .returns(INT)
            .addParameters(model.primaryKey.map {
                ParameterSpec(it.nameInEntity, it.type)
            })
            .addCode("return table.%M(op = {\n%T.id.%M(EntityID(%L, %T))\n})", deleteWhere, model.tableClass, eq, idCode, model.tableClass)
            .build()
    }

    private fun generateInsertWithRelated(model: EntityModel): FunSpec {
        val modelParamName = model.originalClassName.simpleName.decapitate()

        data class RelatedInfo(
            val relatedModel: EntityModel,
            val param: ParameterSpec?,
            val refColumn: ColumnModel,
            val relatedField: Collection<ColumnModel>,
            val localColumns: Collection<ColumnModel>
        )

        val params = model.references.map { (refColumn, ref) ->
            val relatedModel = models[ref.related]!!
            val paramName = relatedModel.originalClassName.simpleName.decapitate()
            val type = newModelMapping[relatedModel] ?: relatedModel.originalClassName
            val localFKColumns = getForeignKeysForReference(model, ref)
            val remoteColumns = localFKColumns.map {
                getForeignKeyColumn(it.foreignKey!!)
            }
            RelatedInfo(
                relatedModel,
                null,
                refColumn,
                remoteColumns,
                localFKColumns
            )
        }

        val insertCode = CodeBlock.builder().beginControlFlow("return %M", MemberName("org.jetbrains.exposed.sql.transactions", "transaction"))
        params.forEach { (relatedModel, param, refColumn, remoteColumns, localColumns) ->
            val repoHasRelated = CodeBlock.builder()
                .addStatement("val has%N: Boolean = %T in related && %N.%N != null",
                    relatedModel.originalClassName.simpleName,
                    relatedModel.tableClass,
                    modelParamName,
                    refColumn.nameInEntity
                ).build()

            val getIdOrInsert = CodeBlock.builder()
            getIdOrInsert.add("%L", repoHasRelated)
            localColumns.zip(remoteColumns).forEach { (localCol, remoteCol) ->
                getIdOrInsert.beginControlFlow("val %N = if (has%N)", localCol.nameInEntity, relatedModel.originalClassName.simpleName)
                    .addStatement("%T.repo.createReturning(%N.%N!!).%N", relatedModel.tableClass, modelParamName, refColumn.nameInEntity, remoteCol.nameInEntity)
                    .nextControlFlow("else")
                    .addStatement("%N.%N", modelParamName, localCol.nameInEntity)
                    .endControlFlow()
            }
            insertCode.add("%L", getIdOrInsert.build())
        }
        val copyCode = CodeBlock.builder().add("%N.copy(\n", modelParamName)
        params.forEach { (relatedModel, param, refColumn, remoteColumn, localColumns) ->
            localColumns.forEach { localCol ->
                copyCode.add("%N = %N,\n", localCol.nameInDsl, localCol.nameInDsl)
            }
        }
        copyCode.add(")")

        insertCode.addStatement("createReturning(%L)", copyCode.build())
        insertCode.endControlFlow()

        val docs = CodeBlock.builder().addNamed("""
            `INSERT` [%model:T] with referenced entities.
            
            For example, to insert related model [%relatedModel:T] (`%relatedModelProp:L`), two conditions must be met.
            1. This method is called on a [CrudRepository] with [%targetTable:T] added using [CrudRepository.withRelated].
            2. Passed `%relatedModelProp:L` must not be null.
            
            If both the conditions are met, [%relatedModel:T] will be inserted and corresponding values will be assigned to columns of the relationship:
            %localIdColumns:L
            
            Otherwise, `%dataParam:L` will be inserted as-is. 
              
            The same goes for the other relationships on [%model:T]: %otherRelationships:L
        """.trimIndent(), mapOf(
            "model" to model.originalClassName,
            "dataParam" to modelParamName,
            "relatedModelProp" to CodeBlock.of("%N.%L", modelParamName, params.first().refColumn.nameInEntity),
            "relatedModel" to params.first().relatedModel.originalClassName,
            "targetTable" to params.first().relatedModel.tableClass,
            "localIdColumns" to CodeBlock.builder().apply {
                val columns = params.first().localColumns
                columns.forEachIndexed { idx, localCol ->
                    add("[%T.%N]" + if (idx != columns.indices.last) ", " else "", model.originalClassName, localCol.nameInEntity)
                }
            }.build(),
            "otherRelationships" to CodeBlock.builder().apply {
                val otherParams = params.drop(1)
                otherParams.forEachIndexed { idx, param ->
                    add("[%T]" + if (idx != otherParams.indices.last) ", " else "", param.relatedModel.originalClassName)
                }
            }.build()
        ))

        return FunSpec.builder("createWithRelated")
            .receiver(model.crudRepositoryType())
            .addKdoc(docs.build())
            .addParameter(ParameterSpec(modelParamName, newModelMapping[model] ?: model.originalClassName))
            .returns(model.originalClassName)
            .addParameters(params.mapNotNull { it.param })
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