package com.dshatz.exposed_crud


@Target(AnnotationTarget.PROPERTY)
annotation class Column(val name: String)
