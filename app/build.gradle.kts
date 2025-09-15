import kotlin.collections.forEach
import java.net.URL

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.peyilo.readview"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.peyilo.readview"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    sourceSets {
        getByName("main") {
            assets {
                srcDirs("src\\main\\assets", "src\\main\\assets")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)

    implementation(project(":app:libreadview"))

    // 检测txt文本的编码格式
    implementation(libs.juniversalchardet)

    implementation(libs.gson)
    implementation(libs.jsoup)
    implementation(libs.json.path)
    implementation(libs.jsoupxpath)
//    implementation(libs.okhttp)
//    implementation("com.squareup.okhttp3:okhttp-java-net-cookiejar:5.1.0")


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.leakcanary)
}


// 下载txt文件到src/main/assets/txts目录
tasks.register("downloadTxtFiles") {
    val outputDir = File(projectDir, "src/main/assets/txts")

    // Pair(下载地址, 保存时的文件名)
    val urls = listOf(
        "https://github.com/Peyilo/libreadview/releases/download/0.0.2/default.txt" to "妖精之诗 作者：尼希维尔特.txt"
    )

    doLast {
        if (!outputDir.exists()) outputDir.mkdirs()

        urls.forEach { (url, fileName) ->
            val outputFile = File(outputDir, fileName)
            if (!outputFile.exists()) {
                println("Downloading $url -> $outputFile")
                URL(url).openStream().use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                println("Already exists: $outputFile")
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn("downloadTxtFiles")
}
