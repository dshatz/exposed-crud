import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "com.dshatz.exposed-crud"
version = project.findProperty("version") as? String ?: "0.1.0-SNAPSHOT1"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.interop)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(project(":annotations"))
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(project.group.toString(), "processor", project.version.toString())
    pom {
        name = "Exposed-CRUD"
        description = "Exposed CRUD repository generator."
        inceptionYear = "2025"
        url = "https://github.com/dshatz/exposed-crud/"
        licenses {
            license {
                name = "GNU GENERAL PUBLIC LICENSE Version 3, 29 June 2007"
                url = "https://github.com/dshatz/openapi2ktor/blob/main/LICENSE"
            }
        }
        developers {
            developer {
                id = "dshatz"
                name = "Daniels Šatcs"
                email = "dev@dshatz.com"
            }
        }
        scm {
            url = "https://github.com/dshatz/exposed-crud"
            connection = "git@github.com:dshatz/exposed-crud.git"
        }
    }
}
