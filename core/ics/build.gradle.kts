import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:domain"))
    // Jackson is only used by biweekly's jCal (JSON) support, which we never touch.
    implementation(libs.biweekly) {
        exclude(group = "com.fasterxml.jackson.core")
    }
    testImplementation(libs.junit)
}
