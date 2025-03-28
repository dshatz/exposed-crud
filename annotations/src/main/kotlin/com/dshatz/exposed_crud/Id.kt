package com.dshatz.exposed_crud

/**
 * Marks the property as an optionally auto-incrementing primary key.
 *
 * To make a composite primary key, annotate multiple columns with this.
 */
@Target(AnnotationTarget.PROPERTY)
annotation class Id(val autoGenerate: Boolean = false)
