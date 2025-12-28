plugins {
    id("koto.kotlin-jvm")
}

dependencies {
    implementation(libs.kotlinxCollectionsImmutable)
    testImplementation(kotlin("test"))
}
