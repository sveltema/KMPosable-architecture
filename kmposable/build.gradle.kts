import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    id("maven-publish")
    id("org.jetbrains.dokka")
}

kotlin {
    version = libs.versions.libraryVersion

    applyDefaultHierarchyTemplate()

    androidTarget {
        publishLibraryVariants()
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_1_8)
                }
            }
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        allWarningsAsErrors.set(true)
    }

    sourceSets {
        commonMain.dependencies {
            //put your multiplatform dependencies here
            api(project(":kmposable-core"))
        }
        commonTest.dependencies {
            implementation(libs.bundles.commonTest)
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