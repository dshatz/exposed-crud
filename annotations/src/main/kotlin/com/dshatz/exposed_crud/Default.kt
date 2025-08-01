package com.dshatz.exposed_crud

/**
 * Specify default as exactly [literal] for example, 0, 1.0, 10L, 1f or true.
 */
@Target(AnnotationTarget.PROPERTY)
annotation class Default(val literal: String)

/**
 * Specify default as String (only for String props). Will be put in quotes.
 */
@Target(AnnotationTarget.PROPERTY)
annotation class DefaultText(val text: String)