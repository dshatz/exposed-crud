package com.dshatz.exposed_crud

@Target(AnnotationTarget.PROPERTY)
annotation class Collate(val collate: String)

/**
 * Creates a character column for storing strings with the specified maximum [length].
 */
@Target(AnnotationTarget.PROPERTY)
annotation class Varchar(val length: Int)

/**
 * Creates a character column for storing strings of arbitrary length.
 *
 * Some database drivers do not load text content immediately (for performance and memory reasons), which means that you can obtain column value only within the open transaction. If you desire to make content available outside the transaction use [eagerLoading] param.
 */
@Target(AnnotationTarget.PROPERTY)
annotation class Text(val eagerLoading: Boolean = false)

/**
 * Creates a character column for storing strings of medium length.
 *
 * Some database drivers do not load text content immediately (for performance and memory reasons), which means that you can obtain column value only within the open transaction. If you desire to make content available outside the transaction use [eagerLoading] param.
 */
@Target(AnnotationTarget.PROPERTY)
annotation class MediumText(val eagerLoading: Boolean = false)

/**
 * Creates a character column for storing strings of large length.
 *
 * Some database drivers do not load text content immediately (for performance and memory reasons), which means that you can obtain column value only within the open transaction. If you desire to make content available outside the transaction use [eagerLoading] param.
 */
@Target(AnnotationTarget.PROPERTY)
annotation class LargeText(val eagerLoading: Boolean = false)



