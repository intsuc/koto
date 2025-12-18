package koto

import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-Xreturn-value-checker=full")
        freeCompilerArgs.add("-Xexplicit-backing-fields")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
        )
    }
}
