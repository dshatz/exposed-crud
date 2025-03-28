package com.dshatz.exposed_crud

@Target(AnnotationTarget.CLASS)
/**
 * Override the table name.
 * @param name table name to use in the database.
 */
annotation class Table(val name: String)
