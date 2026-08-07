import org.gradle.api.artifacts.dsl.LockMode

plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("com.google.devtools.ksp") version "2.3.11" apply false
}

allprojects {
    dependencyLocking {
        lockAllConfigurations()
        lockMode = LockMode.STRICT
    }

    // AGP's debug instrumentation runtime resolves a Kotlin component that Gradle
    // does not persist with --write-locks. Keep that test-only surface under
    // strict checksum verification while production/release configurations remain locked.
    if (path == ":app") {
        configurations.configureEach {
            if (name == "debugAndroidTestRuntimeClasspath") {
                resolutionStrategy.deactivateDependencyLocking()
            }
        }
    }
}
