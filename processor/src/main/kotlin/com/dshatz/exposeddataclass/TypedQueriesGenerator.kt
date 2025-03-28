package com.dshatz.exposeddataclass

import com.dshatz.exposed_crud.typed.CrudRepository
import com.dshatz.exposeddataclass.EntityModel.Companion.crudRepositoryType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlin.reflect.KClass

class TypedQueriesGenerator(
    private val newModelMapping: MutableMap<EntityModel, ClassName>,
) {
    /**
     * Generates a data class with all props except the auto-incrementing ones.
     */
    /*fun generateNewModel(model: EntityModel): TypeSpec {
        val cls = ClassName(
            model.originalClassName.packageName,
            model.originalClassName.simpleName + "_Data"
        )

        val type = TypeSpec.classBuilder(cls)
            .addModifiers(KModifier.DATA)

        model.annotations.forEach {
            type.addAnnotation(it.generate())
        }

        val constructor = FunSpec.constructorBuilder()
        model.columns
            .filterNot { it.autoIncrementing }
            .forEach {
                val prop = PropertySpec.builder(it.nameInEntity, it.type).initializer(it.nameInEntity)
                it.annotations.forEach {
                    prop.addAnnotation(it.generate())
                }
                type.addProperty(prop.build())
                constructor.addParameter(ParameterSpec.builder(it.nameInEntity, it.type).apply { it.default?.let(::defaultValue) }.build())
            }
        type.primaryConstructor(constructor.build())
        newModelMapping[model] = cls
        return type.build()
    }
*/

    private fun repoAccessorOnTable(entityModel: EntityModel): PropertySpec {
        return PropertySpec.builder(
            "repo",
            entityModel.crudRepositoryType()
        )
            .delegate(CodeBlock.builder()
                .beginControlFlow("lazy")
                .addStatement("%T(%T)", CrudRepository::class, entityModel.tableClass)
                .endControlFlow()
                .build()
            )
            .receiver(entityModel.tableClass)
            .build()
    }

    private fun tableAccessorOnEntityClass(entityModel: EntityModel): PropertySpec {
        return PropertySpec.builder("table", entityModel.tableClass)
            .getter(FunSpec.getterBuilder().addStatement("return %T", entityModel.tableClass).build())
            .receiver(KClass::class.asTypeName().parameterizedBy(entityModel.originalClassName))
            .build()
    }

    private fun repoAccessorOnEntityClass(entityModel: EntityModel): PropertySpec {
        return PropertySpec.builder("repo", entityModel.crudRepositoryType())
            .getter(FunSpec.getterBuilder().addStatement("return table.repo").build())
            .receiver(KClass::class.asTypeName().parameterizedBy(entityModel.originalClassName))
            .build()
    }

    fun generateRepoAccessors(entityModel: EntityModel): List<PropertySpec> {
        return listOf(
            repoAccessorOnTable(entityModel),
            tableAccessorOnEntityClass(entityModel),
            repoAccessorOnEntityClass(entityModel)
        )
    }



}