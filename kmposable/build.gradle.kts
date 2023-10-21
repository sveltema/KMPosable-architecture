import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import java.net.URI

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    id("maven-publish")
}

kotlin {
    version = libs.versions.libraryVersion

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    targetHierarchy.default()

    androidTarget {
        publishAllLibraryVariants()
    }

    ios()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":kmposable-core"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.bundles.commonTest)
            }
        }

        val androidMain by getting {
            dependsOn(commonMain)
        }
        val androidUnitTest by getting {
            dependsOn(commonTest)
        }

        val iosSimulatorArm64Main by getting
        val iosMain by getting {
            dependsOn(commonMain)
            iosSimulatorArm64Main.dependsOn(this)
        }
        val iosSimulatorArm64Test by getting
        val iosTest by getting {
            dependsOn(commonTest)
            iosSimulatorArm64Test.dependsOn(this)
        }
    }

    targets.all {
        compilations.all {
            kotlinOptions {
                allWarningsAsErrors = true
            }
        }
    }
}

android {
    namespace = "com.labosu.kmposable"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

group = "com.labosu.kmposable"
version = libs.versions.libraryVersion.get()

publishing {
    publications {
        withType<MavenPublication> {

            pom {
                name.set("KMPosable-architecture")
                description.set("TCA like library for Kotlin Multiplatform Mobile applications")
                url.set("https://maven.pkg.github.com/sveltema/KMPosable-architecture")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    url.set("https://github.com/sveltema/KMPosable-architecture")
                }
                developers {
                    developer {
                        name.set("Steven Veltema")
                        url.set("https://github.com/sveltema")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            name = "github"
            url = uri("https://maven.pkg.github.com/sveltema/KMPosable-architecture")
            credentials {
                username = project.extra.get("GITHUB_ACTOR") as String?
                password = project.extra.get("GITHUB_TOKEN") as String?
            }
        }
    }
}