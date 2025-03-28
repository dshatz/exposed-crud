package com.dshatz.exposed_crud

import kotlin.reflect.KClass

/**
 * Annotate a data class property of type `List<T>`?.
 *
 * The entity passed as the argument must have a [References] annotation pointing to the current entity.
 *
 * The property can be initialized to null for convenience.
 */
@Target(AnnotationTarget.PROPERTY)
annotation class BackReference(val model: KClass<*>)
