plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("com.vanniktech.maven.publish") version "0.34.0"
    id("kotlin-parcelize")
}

// MavenCentral Publishing
val artifactId = "libreadview"
val groupId = "io.github.peyilo"
val versionId = "0.0.2"
val descriptions = "An Android library for customizable page turning animations."

val authorName = "Peyilo"
val developerId= authorName

val gitRepoName = artifactId
val gitUri = "github.com/${authorName}"
val emails = "peyilo.me@gmail.com"

val license = "MIT License"
val licenseUrl = "https://opensource.org/licenses/MIT"
val year = "2025"

description = descriptions
group = groupId
version = versionId

mavenPublishing {
    if (project.hasProperty("enablePublishing")) {
        publishToMavenCentral()
        signAllPublications()

        if (!project.hasProperty("mavenCentralUsername")) {
            throw IllegalArgumentException("mavenCentralUsername is not set")
        } else if (!project.hasProperty("mavenCentralPassword")) {
            throw IllegalArgumentException("mavenCentralPassword is not set")
        } else if (!project.hasProperty("signing.keyId")) {
            throw IllegalArgumentException("signing.keyId is not set")
        } else if (!project.hasProperty("signing.password")) {
            throw IllegalArgumentException("signing.password is not set")
        }

        coordinates(groupId, artifactId, versionId)

        pom {
            name.set(artifactId)
            description.set(descriptions)
            inceptionYear.set(year)
            url.set("https://$gitUri/$gitRepoName/")
            licenses {
                license {
                    name.set(license)
                    url.set(licenseUrl)
                    distribution.set(licenseUrl)
                }
            }
            developers {
                developer {
                    id.set(developerId)
                    name.set(authorName)
                    email.set(emails)
                    url.set("https://$gitUri")
                }
            }
            scm {
                url.set(gitRepoName)
                connection.set("scm:git:git://$gitUri/$gitRepoName.git")
                developerConnection.set("scm:git:ssh://git@$gitUri/$gitRepoName.git")
            }
        }
    }
}

android {
    namespace = "org.peyilo.libreadview"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags += ""
            }
        }
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
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}