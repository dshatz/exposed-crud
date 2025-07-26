package com.dshatz.exposed_crud

@Target(AnnotationTarget.PROPERTY)
annotation class Jsonb(val formatName: String)

@Target(AnnotationTarget.PROPERTY)
annotation class Json(val formatName: String)


@Target(AnnotationTarget.FUNCTION)
annotation class JsonFormat(val name: String)