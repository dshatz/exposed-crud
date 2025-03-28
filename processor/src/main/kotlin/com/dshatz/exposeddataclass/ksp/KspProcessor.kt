package com.dshatz.exposeddataclass.ksp

import com.dshatz.exposed_crud.*
import com.dshatz.exposeddataclass.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class KspProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
    private val basePackage: String = "com.exposeddataclass"
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotated = resolver.getSymbolsWithAnnotation(Entity::class.qualifiedName!!)
        try {
            val entityClasses = annotated.asDataClassDeclarations()
            val models = entityClasses.associate {
                it.toClassName() to processEntity(it)
            }

            validate(models.values)

            models.forEach {
                logger.warn(it.toString())
            }

            val generator = Generator(models, logger)
            val files = generator.generate()
            files.forEach { (model, file) ->
                file.writeTo(codeGenerator, true, listOf(model.declaration.containingFile!!))
            }
            return emptyList()
        } catch (e: ProcessorException) {
            logger.error(e.message!!, e.symbol)
            return annotated.toList()
        }
    }

    private fun Sequence<KSAnnotated>.asDataClassDeclarations(): Sequence<KSClassDeclaration> {
        return mapNotNull {
            if (it is KSClassDeclaration && Modifier.DATA in it.modifiers) it
            else throw ProcessorException("Not a data class", it)
        }
    }

    @Throws(ProcessorException::class)
    private fun processEntity(entityClass: KSClassDeclaration): EntityModel {
        val tableAnnotation = entityClass.getAnnotation(Table::class)
        val tableName = tableAnnotation?.getArgumentAs<String>() ?: entityClass.toClassName().simpleName
        val props = entityClass.getAllProperties()
        val idProps = entityClass.findPropsWithAnnotation(Id::class)

        val referenceProps = props.filter { it.getAnnotation(References::class) != null }
        val backReferenceProps = props.filter { it.getAnnotation(BackReference::class) != null }

        val annotations = entityClass.annotations
            .filterNot { it.annotationType.toTypeName() == Entity::class.asTypeName() }
            .map { it.parse() }

        val uniqueAnnotations = mutableMapOf<String, MutableList<ColumnModel>>()

        fun computeProp(declaration: KSPropertyDeclaration): ColumnModel {
            val name = declaration.getPropName()
            val type = declaration.type.toTypeName()
            val columnAnnotation = declaration.getAnnotation(Column::class)

            val default = if (type.copy(nullable = false) == STRING) {
                declaration.getAnnotation(DefaultText::class)?.getArgumentAs<String>()?.let { CodeBlock.of("%S", it) }
            } else {
                declaration.getAnnotation(Default::class)?.getArgumentAs<String>()?.let { CodeBlock.of("%L", it) }
            }
            val columnName = columnAnnotation?.getArgumentAs<String>() ?: name.decapitate()

            val foreignKey = declaration.getAnnotation(ForeignKey::class)?.let {
                val remoteType = it.getArgumentAs<KSType>()?.toTypeName()!!
                val remoteColumn = it.getArgumentAs<String>(1)?.takeUnless { it.isEmpty() }
                FKInfo(remoteType, remoteColumn)
            }

            val autoIncrement = declaration.getAnnotation(Id::class)?.getArgumentAs<Boolean>() == true


            return ColumnModel(
                declaration = declaration,
                nameInEntity = name,
                columnName = columnName,
                nameInDsl = name.takeUnless { idProps.size == 1 && idProps.first().first.getPropName() == name } ?: "id",
                type = declaration.type.toTypeName(),
                autoIncrementing = autoIncrement,
                default = default,
                foreignKey = foreignKey
            ).also {
                val uniqueIndexName = declaration.getAnnotation(Unique::class)?.getArgumentAs<String>()
                if (uniqueIndexName != null) {
                    uniqueAnnotations.getOrPut(uniqueIndexName) { mutableListOf() }.add(it)
                }
            }
        }

        val columns = (props - referenceProps - backReferenceProps).associateWith { declaration ->
            computeProp(declaration)
        }

        val refColumns = referenceProps.associate { declaration ->
            val prop = computeProp(declaration)
            if (!prop.type.isNullable) throw ProcessorException("@References annotated props should be nullable and have default null.", declaration)
            val ref = ReferenceInfo.WithFK(
                related = declaration.getAnnotation(References::class)?.getArgumentAs<KSType>(0)?.toTypeName()!!,
                localIdProps = declaration.getAnnotation(References::class)?.getArgumentAs<List<String>>(1)!!.toTypedArray()
            )
            prop to ref
        }

        val backRefColumns = backReferenceProps.associate { declaration ->
            val prop = computeProp(declaration)
            if (!prop.type.isNullable) throw ProcessorException("@BackReference annotated props should be nullable and have default null.", declaration)
            val baseType = prop.type.run {
                if (this is ParameterizedTypeName) this.rawType
                else this
            }.copy(nullable = false)
            val ref = ReferenceInfo.Reverse(
                related = declaration.getAnnotation(BackReference::class)?.getArgumentAs<KSType>(0)?.toTypeName()!!,
                isMany = baseType == LIST
            )
            prop to ref
        }

        val primaryKey = if (idProps.size == 1) {
            PrimaryKey.Simple(columns[idProps.first().first]!!)
        } else if (idProps.size > 1) {
            PrimaryKey.Composite(idProps.map { columns[it.first]!! })
        } else {
            throw ProcessorException("No @Id annotation found", entityClass)
        }

        return EntityModel(
            declaration = entityClass,
            originalClassName = entityClass.toClassName(),
            tableName = tableName,
            columns = columns.values.toList(),
            annotations = annotations.toList(),
            primaryKey = primaryKey,
            uniques = uniqueAnnotations,
            references = refColumns,
            backReferences = backRefColumns
        )
    }

    private fun validate(models: Iterable<EntityModel>) {
        models.forEach { table ->
            if (table.primaryKey is PrimaryKey.Composite && table.columns.any { it.autoIncrementing && it in table.primaryKey}) {
                logger.error("auto-increment on a composite key now allowed", table.declaration)
            }
        }
    }
}