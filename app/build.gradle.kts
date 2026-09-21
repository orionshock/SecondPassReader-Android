import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

val releaseSigningPropertyNames =
    listOf(
        "SECOND_PASS_RELEASE_STORE_FILE",
        "SECOND_PASS_RELEASE_STORE_PASSWORD",
        "SECOND_PASS_RELEASE_KEY_ALIAS",
        "SECOND_PASS_RELEASE_KEY_PASSWORD"
    )
val releaseSigningValues =
    releaseSigningPropertyNames.associateWith { name ->
        providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull
            ?.takeIf(String::isNotBlank)
    }
val releaseStoreFile =
    releaseSigningValues["SECOND_PASS_RELEASE_STORE_FILE"]?.let(rootProject::file)

val verifyReleaseSigning =
    tasks.register("verifyReleaseSigning") {
        group = "verification"
        description = "Checks that local release signing credentials are configured."
        val missing = releaseSigningValues.filterValues { it == null }.keys.toList()
        val storePath = releaseStoreFile?.path
        doLast {
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "Release signing is missing: ${missing.joinToString()}. " +
                        "Set these Gradle properties or environment variables before assembling release."
                )
            }
            if (storePath == null || !File(storePath).isFile) {
                throw GradleException(
                    "Release signing keystore does not exist: $storePath"
                )
            }
        }
    }

android {
    namespace = "com.secondpasslibrary.reader"
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
    compileSdk = 37

    defaultConfig {
        applicationId = "com.secondpasslibrary.reader"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-alpha.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile
            storePassword = releaseSigningValues["SECOND_PASS_RELEASE_STORE_PASSWORD"]
            keyAlias = releaseSigningValues["SECOND_PASS_RELEASE_KEY_ALIAS"]
            keyPassword = releaseSigningValues["SECOND_PASS_RELEASE_KEY_PASSWORD"]
        }
    }

    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        lintConfig = file("lint.xml")
        warningsAsErrors = true
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseSigning)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val ktlintCliVersion =
    versionCatalogs
        .named("libs")
        .findVersion("ktlint")
        .get()
        .requiredVersion

ktlint {
    version.set(ktlintCliVersion)
    android.set(true)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}

ksp {
    arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.path)
}

dependencies {
    implementation(project(":spl-client"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.jsoup)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.room3.runtime)
    implementation(libs.androidx.work.runtime)
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.room3.compiler)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.room3.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
