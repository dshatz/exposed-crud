package com.dshatz.exposed_crud

/**
 * Mark a data class with this to generate Exposed table DSL.
 */
@Target(AnnotationTarget.CLASS)
annotation class Entity(val name: String = "")
