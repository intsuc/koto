plugins {
    id("koto.kotlin-jvm")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.lsp4j)
    testImplementation(kotlin("test"))
}
