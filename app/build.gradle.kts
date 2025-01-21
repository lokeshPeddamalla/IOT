import org.gradle.internal.impldep.org.apache.ivy.util.url.IvyAuthenticator
import org.gradle.internal.impldep.org.apache.ivy.util.url.IvyAuthenticator.install

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("com.google.gms.google-services")
    id("com.chaquo.python")

}

android {
    namespace = "com.example.iot1"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.iot1"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk{
            abiFilters += listOf("arm64-v8a", "x86_64")
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
    buildFeatures{
        viewBinding  = true
        dataBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    flavorDimensions += "pyVersion"
    productFlavors {
        create("py310") { dimension = "pyVersion" }
        create("py311") { dimension = "pyVersion" }
    }
}
chaquopy {
    productFlavors {
        getByName("py310") { version = "3.10" }
        getByName("py311") { version = "3.11" }
    }
    defaultConfig {
        buildPython("/usr/bin/python3")
        pip{
            install("cryptography")
        }
    }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    //noinspection UseTomlInstead
    implementation ("com.microsoft.sqlserver:mssql-jdbc:11.2.0.jre8")
   implementation(files("libs/jtds-1.3.1.jar"))
   // implementation 
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.mysql.connector.java.v8030)
    implementation ("androidx.cardview:cardview:1.0.0")
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation ("com.squareup.okhttp3:okhttp:4.9.0")
    implementation ("androidx.security:security-crypto:1.1.0-alpha03")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.10.0")
    implementation(libs.gson)
}