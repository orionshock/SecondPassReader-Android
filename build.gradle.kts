plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
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
    dependsOn(":app:check", ":spl-client:check", "detekt", "ktlintCheck")
}
