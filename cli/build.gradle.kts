plugins {
    id("koto.kotlin-jvm")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":lsp"))
    implementation(libs.clikt)
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("koto.cli.MainKt")
    applicationName = "koto"
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
    )
}
