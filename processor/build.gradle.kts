plugins {
    alias(libs.plugins.jvm)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.interop)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(project(":annotations"))
}