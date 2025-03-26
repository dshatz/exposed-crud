package com.dshatz.exposed_crud

import kotlin.reflect.KClass

@Target(AnnotationTarget.PROPERTY)
annotation class References(val related: KClass<*>, vararg val fkColumns: String) {
}