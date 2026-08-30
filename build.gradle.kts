plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
}

val hygieneFiles =
    fileTree(rootDir) {
        include(
            "**/*.bat",
            "**/*.gradle",
            "**/*.json",
            "**/*.kt",
            "**/*.kts",
            "**/*.md",
            "**/*.mjs",
            "**/*.properties",
            "**/*.ps1",
            "**/*.toml",
            "**/*.txt",
            "**/*.ts",
            "**/*.xml",
            "**/*.yaml",
            "**/*.yml",
            ".editorconfig",
            ".gitignore",
            "gradlew"
        )
        exclude(
            ".git/**",
            ".gradle/**",
            ".idea/**",
            ".kotlin/**",
            ".linecount/**",
            ".vscode/**",
            "**/build/**",
            "**/node_modules/**",
            "gitlog.txt",
            "local.properties"
        )
    }

tasks.register<StaticHygieneTask>("staticHygiene") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Checks repository text files for encoding and whitespace damage."
    textFiles.from(hygieneFiles)
    repositoryDirectory.set(layout.projectDirectory)
}

val readerBoundaryCheck =
    tasks.register<ReaderBoundaryCheckTask>("readerBoundaryCheck") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Checks Reader engine isolation and forbidden renderer movement seams."
        productionSources.from(
            fileTree("app/src/main/kotlin") { include("**/*.kt") },
            fileTree("app/src/debug/kotlin") { include("**/*.kt") },
            fileTree("app/src/release/kotlin") { include("**/*.kt") }
        )
        authoredJavascript.from(
            file("app/src/main/assets/reader/cfi/secondpass-epub-cfi-runtime.js")
        )
    }

val colibrioBundleCheck =
    tasks.register<ColibrioBundleCheckTask>("colibrioBundleCheck") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Verifies the pinned Colibrio bundle and packaged MIT license offline."
        browserBundle.set(
            layout.projectDirectory.file(
                "app/src/main/assets/reader/cfi/colibrio-epubcfi-1.1.0.min.js"
            )
        )
        canonicalLicense.set(
            layout.projectDirectory.file("third_party/colibrio-web-epubcfi/LICENSE")
        )
        packagedLicense.set(
            layout.projectDirectory.file(
                "app/src/main/assets/reader/cfi/licenses/colibrio-web-epubcfi-MIT.txt"
            )
        )
        expectedSha256.set(
            "661515025940C5D1AD2F2238FE03455D4616A3B00BD58978EC59823D818B24E0"
        )
    }

val readerCfiRuntimeCheck =
    tasks.register<ReaderCfiRuntimeCheckTask>("readerCfiRuntimeCheck") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Verifies the generated Reader CFI runtime source digest without Node."
        sourceInputs.from(
            fileTree("tools/reader-cfi-runtime/src") { include("**/*.ts") },
            file("tools/reader-cfi-runtime/package.json"),
            file("tools/reader-cfi-runtime/package-lock.json"),
            file("tools/reader-cfi-runtime/tsconfig.json"),
            file("tools/reader-cfi-runtime/scripts/build-support.mjs")
        )
        generatedRuntime.set(
            layout.projectDirectory.file(
                "app/src/main/assets/reader/cfi/secondpass-epub-cfi-runtime.js"
            )
        )
        repositoryDirectory.set(layout.projectDirectory)
    }

tasks.named("staticHygiene") {
    dependsOn(readerBoundaryCheck, colibrioBundleCheck, readerCfiRuntimeCheck)
}

tasks.register("detekt") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs detekt for every source module."
    dependsOn(":app:detekt", ":spl-client:detekt")
}

tasks.register("ktlintCheck") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Checks Kotlin formatting in every source module."
    dependsOn(":app:ktlintCheck", ":spl-client:ktlintCheck")
}

tasks.named("check") {
    dependsOn(":app:check", ":spl-client:check", "detekt", "ktlintCheck", "staticHygiene")
}
