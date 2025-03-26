package com.dshatz.exposed_crud

@Target(AnnotationTarget.CLASS)
/**
 * Use to override table name.
 */
annotation class Table(val name: String)
