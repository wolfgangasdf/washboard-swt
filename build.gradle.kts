import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinversion = "1.3.72"
group = "com.wolle.washboard-swt"
version = "1.0-SNAPSHOT"
val cPlatforms = listOf("mac") // compile for these platforms. "mac", "linux", "win"

println("Current Java version: ${JavaVersion.current()}")
if (JavaVersion.current().majorVersion.toInt() < 11) throw GradleException("Use Java >= 11")

plugins {
    kotlin("jvm") version "1.3.72"
    application
    id("com.github.ben-manes.versions") version "0.28.0"
//    id("org.beryx.runtime") version "1.8.4"
    id("com.palantir.graal") version "0.7.0"
}

repositories {
    jcenter()
}

application {
    // Define the main class for the application.
    mainClassName = "WashboardSwtMainKt"
    applicationDefaultJvmArgs = listOf("-XstartOnFirstThread")
}

dependencies {
//    implementation("de.brudaswen.kotlinx.coroutines:kotlinx-coroutines-swt:1.0.0")
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinversion")
    implementation("io.github.microutils:kotlin-logging:1.7.9")
    implementation("org.slf4j:slf4j-simple:1.8.0-beta4") // no colors, everything stderr

    implementation("org.eclipse.platform:org.eclipse.swt.cocoa.macosx.x86_64:3.114.0") {
        isTransitive = false
    }

}

graal {
    graalVersion("20.1.0")
//    option("--no-fallback")
    option("-J-XstartOnFirstThread")
    mainClass("WashboardSwtMainKt")
    outputName("washboard-swt")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}


task("dist") {
//    dependsOn("crosspackage")
    doLast {
//        println("Deleting build/[image,jre,install]")
//        project.delete(project.runtime.imageDir.get(), project.runtime.jreDir.get(), "${project.buildDir.path}/install")
        println("Created zips in build/crosspackage")
    }
}
