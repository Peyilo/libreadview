// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.library) apply false
}

tasks.register("verifyCurlArchitecture") {
    group = "verification"
    description = "Keep portable iOSCurl isolated and permit only iOSCurl and GoogleCurl renderers."
    val algorithms = fileTree("ioscurl/src/main") { include("**/*.kt") }
    val library = fileTree("app/libreadview/src/main") { include("**/*.kt") }
    val demo = fileTree("app/src/main") { include("**/*.kt", "**/*.xml") }
    inputs.files(algorithms, library, demo)
    doLast {
        check(algorithms.files.isNotEmpty()) { "Missing portable iOSCurl sources" }
        val forbidden = Regex("import\\s+(android|androidx|java|javax|org\\.peyilo\\.libreadview)(?:\\.|\\b)|System\\.loadLibrary|external\\s+fun")
        algorithms.forEach { file ->
            check(!forbidden.containsMatchIn(file.readText())) { "Platform dependency in ${file.relativeTo(rootDir)}" }
        }
        val renderers = library.flatMap { file ->
            Regex("(?m)^class\\s+(\\w*CurlEffect)\\b").findAll(file.readText()).map { it.groupValues[1] }.toList()
        }.toSet()
        check(renderers == setOf("IosCurlEffect", "GoogleCurlEffect")) { "Unexpected curl implementations: $renderers" }
        val removed = Regex("IBookCurl|GLSLCurl|GlslPageRenderer|PageGLSurfaceView|SimulationView|PageCurlGLView")
        (library + demo).forEach { file ->
            check(!removed.containsMatchIn(file.readText())) { "Removed curl reference in ${file.relativeTo(rootDir)}" }
        }
        logger.lifecycle("Curl architecture verified: ${algorithms.files.size} portable sources, iOSCurl and GoogleCurl adapters")
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(rootProject.tasks.named("verifyCurlArchitecture"))
    }
}

