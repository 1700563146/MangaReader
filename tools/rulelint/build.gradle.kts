plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("io.github.saonte.mangareader.rulelint.MainKt")
}

dependencies {
    implementation(project(":core:source"))
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.truth)
}

tasks.test {
    useJUnitPlatform()
}
