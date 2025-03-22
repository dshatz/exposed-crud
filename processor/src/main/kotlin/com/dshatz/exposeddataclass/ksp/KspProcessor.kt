package com.dshatz.exposeddataclass.ksp

import com.dshatz.exposeddataclass.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.STRING
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

            models.forEach {
                logger.warn(it.toString())
            }

            val generator = Generator(models)
            val files = generator.generate()
            files.forEach {
                it.writeTo(codeGenerator, Dependencies(true))
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

        val columns = props.associateWith { declaration ->
            val name = declaration.getPropName()
            val type = declaration.type.toTypeName()
            val columnAnnotation = declaration.getAnnotation(Column::class)

            val default = if (type.copy(nullable = false) == STRING) {
                declaration.getAnnotation(DefaultText::class)?.getArgumentAs<String>()?.let { CodeBlock.of("%S", it) }
            } else {
                declaration.getAnnotation(Default::class)?.getArgumentAs<String>()?.let { CodeBlock.of("%L", it) }
            }
            val columnName = columnAnnotation?.getArgumentAs<String>() ?: name.decapitate()

            val references = declaration.getAnnotation(References::class)?.getArgumentAs<KSType>()?.let {
                ReferenceInfo(it.toTypeName())
            }

            val autoIncrement = declaration.getAnnotation(Id::class)?.getArgumentAs<Boolean>() == true

            ColumnModel(
                declaration = declaration,
                nameInEntity = name,
                columnName = columnName,
                nameInDsl = name.takeUnless { idProps.size == 1 && idProps.first().first.getPropName() == name } ?: "id",
                type = declaration.type.toTypeName(),
                autoIncrementing = autoIncrement,
                default = default,
                foreignKey = references
            )
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
            primaryKey = primaryKey
        )
    }
}