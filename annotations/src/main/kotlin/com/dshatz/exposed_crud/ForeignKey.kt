package com.dshatz.exposed_crud

import kotlin.reflect.KClass

/**
 * Defines a foreign key from this column to the [related] entity.
 *
 * By default, the foreign key will point to the primary key of the referenced entity. You can override that by specifying [onlyColumn].
 * @param referenced entity class.
 * @param onlyColumn name of the column on the referenced entity class. (Optional)
 */
@Target(AnnotationTarget.PROPERTY)
annotation class ForeignKey(val related: KClass<*>, val onlyColumn: String = "")
