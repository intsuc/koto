plugins {
    id("koto.kotlin-jvm")
    application
}

dependencies {
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
