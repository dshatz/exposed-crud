package com.dshatz.exposed_crud

/**
 * Marks the property as primary key and optionally autogenerate.
 */
@Target(AnnotationTarget.PROPERTY)
annotation class Id(val autoGenerate: Boolean = false)
