import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinversion = "1.3.72"
group = "com.wolle.washboard-swt"
version = "1.0-SNAPSHOT"
val cPlatforms = listOf("mac") // compile for these platforms. "mac", "linux", "win"

println("Current Java version: ${JavaVersion.current()}")
if (JavaVersion.current().majorVersion.toInt() < 14) throw GradleException("Use Java >= 14")

plugins {
    kotlin("jvm") version "1.3.72"
    application
    id("com.github.ben-manes.versions") version "0.28.0"
    id("org.beryx.runtime") version "1.8.4"
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
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinversion")
    implementation("io.github.microutils:kotlin-logging:1.7.9")
    implementation("org.slf4j:slf4j-simple:1.8.0-beta4") // no colors, everything stderr
    implementation("com.github.gimlet2:kottpd:0.1.4")

    implementation("org.eclipse.platform:org.eclipse.swt.cocoa.macosx.x86_64:3.114.0") {
        isTransitive = false
    }

}


runtime {
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    // first row: suggestModules
    modules.set(listOf("java.desktop",
            "jdk.crypto.cryptoki","jdk.crypto.ec")) // for https URL connection test

    if (cPlatforms.contains("mac")) targetPlatform("mac", System.getenv("JDK_MAC_HOME"))
    if (cPlatforms.contains("win")) targetPlatform("win", System.getenv("JDK_WIN_HOME"))
    if (cPlatforms.contains("linux")) targetPlatform("linux", System.getenv("JDK_LINUX_HOME"))
}

open class CrossPackage : DefaultTask() {
    @org.gradle.api.tasks.Input var execfilename = "execfilename"
    @org.gradle.api.tasks.Input var macicnspath = "macicnspath"

    @TaskAction
    fun crossPackage() {
        File("${project.buildDir.path}/crosspackage/").mkdirs()
        project.runtime.targetPlatforms.get().forEach { (t, _) ->
            println("targetplatform: $t")
            val imgdir = "${project.runtime.imageDir.get()}/${project.name}-$t"
            println("imagedir: $imgdir")
            when(t) {
                "mac" -> {
                    val appp = File(project.buildDir.path + "/crosspackage/mac/$execfilename.app").path
                    project.delete(appp)
                    project.copy {
                        into(appp)
                        from(macicnspath) {
                            into("Contents/Resources").rename { "$execfilename.icns" }
                        }
                        from("$imgdir/${project.application.executableDir}/${project.application.applicationName}") {
                            into("Contents/MacOS")
                        }
                        from(imgdir) {
                            into("Contents")
                        }
                    }
                    val pf = File("$appp/Contents/Info.plist")
                    pf.writeText("""
                        <?xml version="1.0" ?>
                        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                        <plist version="1.0">
                         <dict>
                          <key>LSMinimumSystemVersion</key>
                          <string>10.9</string>
                          <key>CFBundleDevelopmentRegion</key>
                          <string>English</string>
                          <key>CFBundleAllowMixedLocalizations</key>
                          <true/>
                          <key>CFBundleExecutable</key>
                          <string>$execfilename</string>
                          <key>CFBundleIconFile</key>
                          <string>$execfilename.icns</string>
                          <key>CFBundleIdentifier</key>
                          <string>${project.group}</string>
                          <key>CFBundleInfoDictionaryVersion</key>
                          <string>6.0</string>
                          <key>CFBundleName</key>
                          <string>${project.name}</string>
                          <key>CFBundlePackageType</key>
                          <string>APPL</string>
                          <key>CFBundleShortVersionString</key>
                          <string>${project.version}</string>
                          <key>CFBundleSignature</key>
                          <string>????</string>
                          <!-- See http://developer.apple.com/library/mac/#releasenotes/General/SubmittingToMacAppStore/_index.html
                               for list of AppStore categories -->
                          <key>LSApplicationCategoryType</key>
                          <string>Unknown</string>
                          <key>CFBundleVersion</key>
                          <string>100</string>
                          <key>NSHumanReadableCopyright</key>
                          <string>Copyright (C) 2019</string>
                          <key>NSHighResolutionCapable</key>
                          <string>true</string>
                          <key>NSAppTransportSecurity</key>
                          <dict>
                            <key>NSAllowsArbitraryLoads</key>
                            <true/>
                          </dict>
                          <key>LSUIElement</key>
                            <true/>
                         </dict>
                        </plist>
                    """.trimIndent())
                    // touch folder to update Finder
                    File(appp).setLastModified(System.currentTimeMillis())
                    // zip it
                    org.gradle.kotlin.dsl.support.zipTo(File("${project.buildDir.path}/crosspackage/$execfilename-mac.zip"), File("${project.buildDir.path}/crosspackage/mac"))
                }
                "win" -> {
                    org.gradle.kotlin.dsl.support.zipTo(File("${project.buildDir.path}/crosspackage/$execfilename-win.zip"), File(imgdir))
                }
                "linux" -> {
                    org.gradle.kotlin.dsl.support.zipTo(File("${project.buildDir.path}/crosspackage/$execfilename-linux.zip"), File(imgdir))
                }
            }
        }
    }
}

tasks.register<CrossPackage>("crosspackage") {
    dependsOn("runtime")
    execfilename = "washboard-swt"
    macicnspath = "./icon.icns"
}

tasks.withType(CreateStartScripts::class).forEach {script ->
    script.doFirst {
        script.classpath =  files("lib/*")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}


task("dist") {
    dependsOn("crosspackage")
    doLast {
//        println("Deleting build/[image,jre,install]")
//        project.delete(project.runtime.imageDir.get(), project.runtime.jreDir.get(), "${project.buildDir.path}/install")
        println("Created zips in build/crosspackage")
    }
}
