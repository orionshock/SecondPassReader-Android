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
            "**/*.sh",
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

val architectureBoundaryCheck =
    tasks.register<ArchitectureBoundaryCheckTask>("architectureBoundaryCheck") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Checks repository architecture and transport seams."
        productionSources.from(
            fileTree("app/src/main/kotlin") { include("**/*.kt") },
            fileTree("app/src/debug/kotlin") { include("**/*.kt") },
            fileTree("app/src/release/kotlin") { include("**/*.kt") },
            fileTree("spl-client/src/main/kotlin") { include("**/*.kt") }
        )
        authoredJavascript.from(
            file("app/src/main/assets/reader/cfi/secondpass-epub-cfi-runtime.js")
        )
        repositoryDirectory.set(layout.projectDirectory)
    }

tasks.register("readerBoundaryCheck") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Compatibility alias for architectureBoundaryCheck."
    dependsOn(architectureBoundaryCheck)
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

val cfiProtocolManifest =
    layout.projectDirectory.file("tools/reader-cfi-runtime/protocol.json")
val cfiKotlinProtocolDeclaration =
    layout.projectDirectory.file(
        "app/src/main/kotlin/com/secondpasslibrary/reader/reader/readium/cfi/CfiProtocol.kt"
    )
val cfiTypescriptProtocolDeclaration =
    layout.projectDirectory.file("tools/reader-cfi-runtime/src/protocol.generated.ts")
val cfiTypescriptEntrypoint =
    layout.projectDirectory.file("tools/reader-cfi-runtime/src/runtime.ts")
val cfiKotlinRuntimeAdapter =
    layout.projectDirectory.file(
        "app/src/main/kotlin/com/secondpasslibrary/reader/reader/readium/cfi/ReadiumCfiJavascriptRuntime.kt"
    )
val cfiSourceInputs =
    files(
        fileTree("tools/reader-cfi-runtime/src") { include("**/*.ts") },
        file("tools/reader-cfi-runtime/package.json"),
        file("tools/reader-cfi-runtime/package-lock.json"),
        file("tools/reader-cfi-runtime/tsconfig.json"),
        cfiProtocolManifest,
        file("tools/reader-cfi-runtime/scripts/protocol.mjs"),
        file("tools/reader-cfi-runtime/scripts/build-support.mjs")
    )

fun registerCfiRuntimeCheck(
    name: String,
    taskDescription: String,
    runtimePath: String
) = tasks.register<ReaderCfiRuntimeCheckTask>(name) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = taskDescription
    sourceInputs.from(cfiSourceInputs)
    protocolManifest.set(cfiProtocolManifest)
    kotlinProtocolDeclaration.set(cfiKotlinProtocolDeclaration)
    typescriptProtocolDeclaration.set(cfiTypescriptProtocolDeclaration)
    typescriptEntrypoint.set(cfiTypescriptEntrypoint)
    kotlinRuntimeAdapter.set(cfiKotlinRuntimeAdapter)
    generatedRuntime.set(
        layout.projectDirectory.file(runtimePath)
    )
    repositoryDirectory.set(layout.projectDirectory)
}

val readerCfiRuntimeCheck =
    registerCfiRuntimeCheck(
        name = "readerCfiRuntimeCheck",
        taskDescription = "Verifies the readable Reader CFI runtime without Node.",
        runtimePath = "app/src/main/assets/reader/cfi/secondpass-epub-cfi-runtime.js"
    )

val releaseReaderCfiRuntimeCheck =
    registerCfiRuntimeCheck(
        name = "releaseReaderCfiRuntimeCheck",
        taskDescription = "Verifies the minified release Reader CFI runtime without Node.",
        runtimePath = "app/src/release/assets/reader/cfi/secondpass-epub-cfi-runtime.js"
    )

val buildLogicTest =
    tasks.register<Exec>("buildLogicTest") {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Runs deterministic tests for repository build logic."
        workingDir(rootDir)
        val buildSrcPath = rootDir.resolve("buildSrc").absolutePath
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            commandLine(rootDir.resolve("gradlew.bat").absolutePath, "-p", buildSrcPath, "test")
        } else {
            commandLine("bash", rootDir.resolve("gradlew").absolutePath, "-p", buildSrcPath, "test")
        }
    }

project(":app") {
    tasks.matching { it.name == "preBuild" }.configureEach {
        dependsOn(colibrioBundleCheck, readerCfiRuntimeCheck, releaseReaderCfiRuntimeCheck)
    }
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
    dependsOn(
        ":app:check",
        ":spl-client:check",
        "detekt",
        "ktlintCheck",
        "staticHygiene",
        architectureBoundaryCheck,
        colibrioBundleCheck,
        readerCfiRuntimeCheck,
        releaseReaderCfiRuntimeCheck,
        buildLogicTest
    )
}
