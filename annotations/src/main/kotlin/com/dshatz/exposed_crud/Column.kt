package com.dshatz.exposed_crud


/**
 * Override the column name.
 * @param name column name to be used in the database.
 */
@Target(AnnotationTarget.PROPERTY)
annotation class Column(val name: String)
