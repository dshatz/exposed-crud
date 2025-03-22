package com.dshatz.exposeddataclass

/**
 * Marks the property as primary key and optionally autogenerate.
 */
@Target(AnnotationTarget.PROPERTY)
annotation class Id(val autoGenerate: Boolean = false)
