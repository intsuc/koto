plugins {
    id("koto.kotlin-jvm")
}

dependencies {
    implementation(libs.lsp4j)
    testImplementation(kotlin("test"))
}
