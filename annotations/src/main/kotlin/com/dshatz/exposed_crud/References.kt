package com.dshatz.exposed_crud

import kotlin.reflect.KClass

/**
 * Properties annotated with this will be marked as a reference to a remote entity.
 *
 * Requires the same model to have [ForeignKey] columns sufficient to identify the related column by its primary key.
 * @param related the referenced entity.
 * @param fkColumns names of the local columns annotated with [ForeignKey].
 */
@Target(AnnotationTarget.PROPERTY)
annotation class References(val related: KClass<*>, vararg val fkColumns: String)