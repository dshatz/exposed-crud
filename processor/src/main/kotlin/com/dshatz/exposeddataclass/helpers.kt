package com.dshatz.exposeddataclass

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.CodeBlock
import kotlin.reflect.KClass

@Throws(ProcessorException::class)
fun KSClassDeclaration.findPropWithAnnotation(cls: KClass<*>): Pair<KSPropertyDeclaration, KSAnnotation> {
    return findPropsWithAnnotation(cls).singleOrNull()
        ?: throw ProcessorException("Expected single annotation ${cls.simpleName}", this)
}

@Throws(ProcessorException::class)
fun KSClassDeclaration.findPropsWithAnnotation(cls: KClass<*>): List<Pair<KSPropertyDeclaration, KSAnnotation>> {
    return getAllProperties().associateWith {
        it.getAnnotation(cls)
    }.entries
        .filter { it.value != null }
        .map { it.key to it.value!! }
}

fun KSAnnotated.getAnnotation(cls: KClass<*>): KSAnnotation? {
    return annotations.find { ka ->
        ka.annotationType.resolve().declaration.qualifiedName?.asString() == cls.qualifiedName
    }
}

class ProcessorException(message: String, val symbol: KSNode): Exception(message)

/*fun KSPropertyDeclaration.getPropType(): PropType {
    val resolved = type.resolve()
    return when (resolved.declaration.qualifiedName?.asString()) {
        String::class.qualifiedName -> PropType.String
        Long::class.qualifiedName -> PropType.Long
        Int::class.qualifiedName -> PropType.Int
        else -> throw ProcessorException("Unknown type ${resolved.declaration.qualifiedName?.asString()}", this)
    }
}*/

fun KSPropertyDeclaration.getPropName(): String {
    return simpleName.asString()
}

fun KSClassDeclaration.getClassName(): String {
    return simpleName.asString()
}

fun KSValueParameter.getName(): String {
    return name?.asString() ?: throw ProcessorException("Unable to find default value", this)
}

fun String.decapitate(): String = replaceFirstChar { it.lowercase() }

inline fun <reified T> KSAnnotation.getArgumentAs(index: Int = 0): T? {
    return arguments.getOrNull(index)?.value?.let {
        it as T
    }
}