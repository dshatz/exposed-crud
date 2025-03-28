package com.dshatz.exposed_crud

/**
 * Mark properties of your @Entity-annotated class with this to add a unique index and constraint.
 *
 * For defining a unique constraint on multiple columns, pass the same [indexName] to each annotation.
 * @param indexName name of the unique index (Optional)
 */
@Target(AnnotationTarget.PROPERTY)
annotation class Unique(val indexName: String = "unique_index")
