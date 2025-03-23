package com.dshatz.exposeddataclass

import kotlin.reflect.KClass

@Target(AnnotationTarget.PROPERTY)
annotation class References(val related: KClass<*>, vararg val fkColumns: String) {
}