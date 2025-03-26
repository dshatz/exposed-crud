plugins {
    alias(libs.plugins.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.interop)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(project(":annotations"))
}