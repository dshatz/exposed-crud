plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.serial)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)
    implementation(libs.serial)
    implementation(project(":annotations"))
    ksp(project(":processor"))
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.44.1.0")
}