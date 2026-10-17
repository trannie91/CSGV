import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import com.android.build.gradle.BaseExtension

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("com.github.recloudstream.gradle:gradle:81b1d424d")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) =
    extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) =
    extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        // Tự động nhận URL repository từ GitHub Actions hoặc fallback mặc định
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/trannie91/CloudBL")
    }

    android {
        compileSdkVersion(34)

        defaultConfig {
            minSdk = 21
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
                freeCompilerArgs.set(
                    freeCompilerArgs.get() + listOf(
                        "-opt-in=kotlin.RequiresOptIn",
                        "-Xskip-metadata-version-check"
                    )
                )
            }
        }
    }

    dependencies {
        val cloudstream by configurations
        val implementation by configurations

        // Stubs chính thức cho Cloudstream
        cloudstream("com.lagradost:cloudstream3:pre-release")

        implementation(kotlin("stdlib"))
        implementation("com.github.Blatzar:NiceHttp:0.4.11")
        implementation("org.jsoup:jsoup:1.18.3")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    }
}

// Cấu hình cụ thể cho module extension :GLVietsub
project(":GLVietsub") {
    version = 1

    android {
        namespace = "com.glvietsub"
    }

    cloudstream {
        authors = listOf("GLVietsub")
        description = "Extension xem phim BL - Chỉ lấy duy nhất Catalog Phim BL, tự động bóc tách link 1080p FHD từ máy chủ Rumble Cloud, OK.ru, VK, loại bỏ chất lượng thấp."
        status = 1
        tvTypes = listOf("TvSeries", "AsianDrama")
        language = "vi"
        iconUrl = "https://www.glvietsubz.net/favicon.ico"
    }
}

task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

