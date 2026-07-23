import com.android.build.api.dsl.ManagedVirtualDevice
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val morimilCanvasVersion = "0.3.1"
val morimilCanvasSourceCommit = "2a312c11062e7e21f4f2173a8191c2e3954fde56"
val morimilCanvasBundleUrl = "https://raw.githubusercontent.com/morimilpabfelon-cell/Morimil-excalidraw/bundle/morimil-canvas.zip"
val morimilCanvasBundleSha256 = "73b061406d9fff999a859025f497bece4680a896ad19eccb6a391cdb50cd0507"
val morimilCanvasArchive = layout.buildDirectory.file("downloads/morimil-canvas-$morimilCanvasVersion.zip")
val morimilCanvasGeneratedAssets = layout.buildDirectory.dir("generated/morimilCanvasAssets")

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

val prepareMorimilCanvasAssets by tasks.registering {
    group = "build"
    description = "Downloads, verifies and expands the pinned Morimil Canvas bundle."

    inputs.property("morimilCanvasVersion", morimilCanvasVersion)
    inputs.property("morimilCanvasSourceCommit", morimilCanvasSourceCommit)
    inputs.property("morimilCanvasBundleSha256", morimilCanvasBundleSha256)
    providers.environmentVariable("MORIMIL_CANVAS_ZIP").orNull?.let { localPath ->
        inputs.file(localPath)
    }
    outputs.dir(morimilCanvasGeneratedAssets)

    doLast {
        val archive = morimilCanvasArchive.get().asFile
        archive.parentFile.mkdirs()

        val localOverride = System.getenv("MORIMIL_CANVAS_ZIP")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (localOverride != null) {
            val localFile = file(localOverride)
            check(localFile.isFile) { "MORIMIL_CANVAS_ZIP does not point to a file: $localOverride" }
            localFile.copyTo(archive, overwrite = true)
        } else if (!archive.isFile || sha256(archive) != morimilCanvasBundleSha256) {
            val connection = URI(morimilCanvasBundleUrl).toURL().openConnection().apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "Morimil-app/$morimilCanvasVersion")
            }
            connection.getInputStream().buffered().use { input ->
                archive.outputStream().buffered().use { output -> input.copyTo(output) }
            }
        }

        val actualArchiveHash = sha256(archive)
        check(actualArchiveHash == morimilCanvasBundleSha256) {
            "Morimil Canvas bundle hash mismatch. Expected $morimilCanvasBundleSha256, got $actualArchiveHash"
        }

        val generatedRoot = morimilCanvasGeneratedAssets.get().asFile
        generatedRoot.deleteRecursively()
        val canvasRoot = File(generatedRoot, "morimil-canvas").apply { mkdirs() }
        val canonicalRoot = canvasRoot.canonicalFile
        var extractedFiles = 0
        var extractedBytes = 0L

        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val normalizedName = entry.name.replace('\\', '/')
                check(normalizedName.isNotBlank() && !normalizedName.startsWith('/')) {
                    "Unsafe Morimil Canvas ZIP entry: ${entry.name}"
                }
                val target = File(canvasRoot, normalizedName).canonicalFile
                check(
                    target == canonicalRoot ||
                        target.path.startsWith(canonicalRoot.path + File.separator)
                ) { "Morimil Canvas ZIP entry escapes the asset root: ${entry.name}" }

                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile.mkdirs()
                    target.outputStream().buffered().use { output -> zip.copyTo(output) }
                    extractedFiles += 1
                    extractedBytes += target.length()
                    check(extractedFiles <= 200) { "Morimil Canvas bundle contains too many files" }
                    check(extractedBytes <= 6L * 1024L * 1024L) { "Morimil Canvas bundle exceeds 6 MB" }
                }
                zip.closeEntry()
            }
        }

        check(File(canvasRoot, "index.html").isFile) { "Morimil Canvas index.html is missing" }
        check(File(canvasRoot, "morimil-canvas.manifest.json").isFile) {
            "Morimil Canvas integrity manifest is missing"
        }
        logger.lifecycle(
            "Prepared Morimil Canvas $morimilCanvasVersion from $morimilCanvasSourceCommit: " +
                "$extractedFiles files, $extractedBytes bytes"
        )
    }
}

android {
    namespace = "com.morimil.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.morimil.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.8.0-phase5d"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main").assets.srcDir(morimilCanvasGeneratedAssets)
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    testOptions {
        managedDevices {
            devices {
                maybeCreate<ManagedVirtualDevice>("pixel2Api30").apply {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "aosp-atd"
                }
                maybeCreate<ManagedVirtualDevice>("pixel2Api35").apply {
                    device = "Pixel 2"
                    apiLevel = 35
                    systemImageSource = "aosp"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareMorimilCanvasAssets)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    val roomVersion = "2.7.2"

    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.webkit:webkit:1.16.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.fasterxml.jackson.core:jackson-core:2.22.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.14.0")
    implementation("com.google.crypto.tink:tink-android:1.23.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.0")
    implementation("androidx.sqlite:sqlite:2.6.2")
    implementation("net.zetetic:sqlcipher-android:4.15.0@aar")

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation("androidx.work:work-testing:2.9.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
