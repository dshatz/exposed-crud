package com.dshatz.exposeddataclass

import com.dshatz.exposeddataclass.typed.CrudRepository
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

class TypedQueriesGenerator(
    private val newModelMapping: MutableMap<EntityModel, ClassName>,
) {
    /**
     * Generates a data class with all props except the auto-incemeneting ones.
     */
    fun generateNewModel(model: EntityModel): TypeSpec {

        val cls = ClassName(
            model.originalClassName.packageName,
            model.originalClassName.simpleName + "_Data"
        )

        val type = TypeSpec.classBuilder(cls)
            .addModifiers(KModifier.DATA)

        val constructor = FunSpec.constructorBuilder()
        model.columns
            .filterNot { it.autoIncrementing }
            .forEach {
                type.addProperty(PropertySpec.builder(it.nameInEntity, it.type).initializer(it.nameInEntity).build())
                constructor.addParameter(ParameterSpec(it.nameInEntity, it.type))
            }
        type.primaryConstructor(constructor.build())
        newModelMapping[model] = cls
        return type.build()
    }


    fun generateTypesQueries(entityModel: EntityModel): PropertySpec {
        return PropertySpec.builder(
            "repo",
            CrudRepository::class.asTypeName().parameterizedBy(
                entityModel.tableClass,
                entityModel.idType(),
                entityModel.originalClassName,
                newModelMapping[entityModel] ?: entityModel.originalClassName
            )
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



}