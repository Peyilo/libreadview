plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "org.peyilo.ireadview"

version = providers.gradleProperty("ireadVersion").getOrElse("0.1.0-SNAPSHOT")

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
}

dependencies { testImplementation(libs.junit) }

// Build-time formatter only; never part of the published/runtime dependencies.
val kotlinFormatter by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies { add(kotlinFormatter.name, "com.facebook:ktfmt:0.64:with-dependencies") }

val kotlinFormatSources = files(fileTree("src") { include("**/*.kt") }, "build.gradle.kts")

tasks.register<JavaExec>("formatKotlin") {
    group = "formatting"
    description = "Format the portable iOSCurl sources with Kotlin's four-space style."
    classpath = kotlinFormatter
    mainClass.set("com.facebook.ktfmt.cli.Main")
    args("--kotlinlang-style", "--quiet")
    args(kotlinFormatSources.files.sorted().map { it.absolutePath })
}

tasks.register<JavaExec>("checkKotlinFormat") {
    group = "verification"
    description = "Check Kotlin source formatting without modifying files."
    classpath = kotlinFormatter
    mainClass.set("com.facebook.ktfmt.cli.Main")
    args("--kotlinlang-style", "--dry-run", "--set-exit-if-changed", "--quiet")
    args(kotlinFormatSources.files.sorted().map { it.absolutePath })
}

publishing {
    publications {
        register<MavenPublication>("release") {
            from(components["java"])
            artifactId = "ioscurl"
            pom {
                name.set("iReadView iOSCurl")
                description.set(
                    "Portable Kotlin port of the iOS page-curl control, gestures, layout and mesh algorithms."
                )
            }
        }
    }
    repositories {
        maven {
            name = "local"
            url = uri(layout.buildDirectory.dir("repository"))
        }
    }
}
