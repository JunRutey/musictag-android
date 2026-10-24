import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
}

/**
 * Lyrics text handling: LRC parsing, timestamp rewriting, offsets, bilingual merging.
 *
 * Pure JVM and dependency-free so the editor, the batch service and the tests all share
 * one implementation instead of three slightly different regexes.
 */
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // OpenCC dictionaries for traditional/simplified Chinese, rather than a hand-rolled table.
    api(libs.opencc4j)
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = true
    }
}
