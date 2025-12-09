plugins {
    id("koto.kotlin-jvm")
    application
}

dependencies {
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("koto.MainKt")
    applicationName = "koto"
}
