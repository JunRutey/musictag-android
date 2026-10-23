import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
}

/**
 * Pure JVM tag engine.
 *
 * Deliberately has no Android dependency: the whole read/write path is exercised by
 * plain JUnit tests against a host build of the JNI library (see docs/TESTING.md).
 */
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // Keep the public surface explicit; the app consumes this module directly.
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    // The native CI job builds TagLib for the host and exports this path. Without it the
    // native-backed tests skip through org.junit.Assume, so a plain ./gradlew test on a
    // machine without cmake stays green.
    val hostLibrary = providers.environmentVariable("MUSICTAG_HOST_LIB").orNull
    if (!hostLibrary.isNullOrBlank()) {
        environment("MUSICTAG_HOST_LIB", hostLibrary)
    }
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = true
    }
}
