package com.dshatz.exposeddataclass

@Target(AnnotationTarget.CLASS)
/**
 * Use to override table name.
 */
annotation class Table(val name: String)
