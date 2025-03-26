package com.dshatz.exposeddataclass

import kotlin.reflect.KClass

/**
 * Annotate a data class property of type List<T>?.
 * The property should be initialized to null for convenience.
 * The entity passed as the argument must have a @Reference annotation to the current module.
 */
@Target(AnnotationTarget.PROPERTY)
annotation class BackReference(val model: KClass<*>)
