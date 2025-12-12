plugins {
    id("koto.kotlin-jvm")
    application
}

dependencies {
    implementation(project(":lsp"))
    implementation(libs.clikt)
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("koto.MainKt")
    applicationName = "koto"
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
    )
}
