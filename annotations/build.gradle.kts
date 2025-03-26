plugins {
    alias(libs.plugins.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.exposed.core)
}