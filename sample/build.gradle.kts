plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)
    implementation(project(":annotations"))
    ksp(project(":processor"))
//    kspTest(project(":processor"))
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.44.1.0")
}