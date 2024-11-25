plugins {
    //trick: for the same plugin versions in all sub-modules
    alias(libs.plugins.androidApplication).apply(false)
    alias(libs.plugins.androidLibrary).apply(false)
    alias(libs.plugins.kotlinAndroid).apply(false)
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.kotlin.skie).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
}


allprojects {
    repositories {
        mavenCentral()
        google()
        mavenLocal()
   }
}