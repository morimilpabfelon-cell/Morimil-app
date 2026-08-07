import com.android.build.api.dsl.ManagedVirtualDevice
import groovy.json.JsonSlurper
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val morimilCanvasVersion = "0.3.1"
val morimilCanvasRecoveryId = "morimil.canvas.runtime-recovery.v1"
val morimilCanvasBundleSha256 = "6bbc1a5127f6db742db87a3cb6af9631bba387e7c0ff543309d48ffb5eac4835"
val morimilCanvasTreeSha256 = "e3d58636c98987d41f57409cc91e473564207eacd0e81e385108a0f54ddd6985"
val morimilCanvasVendoredArchive = layout.projectDirectory.file(
    "vendor/morimil-canvas/morimil-canvas-0.3.1-runtime-recovery-v1.zip"
)
val morimilCanvasProvenance = layout.projectDirectory.file(
    "vendor/morimil-canvas/morimil-canvas-0.3.1-runtime-recovery-v1.provenance.json"
)
val morimilCanvasArchive = layout.buildDirectory.file("downloads/morimil-canvas-$morimilCanvasVersion.zip")
val morimilCanvasGeneratedAssets = layout.buildDirectory.dir("generated/morimilCanvasAssets")

val releaseStoreFile = providers.gradleProperty("MORIMIL_RELEASE_STORE_FILE")
    .orElse(providers.environmentVariable("MORIMIL_RELEASE_STORE_FILE"))
val releaseStorePassword = providers.gradleProperty("MORIMIL_RELEASE_STORE_PASSWORD")
    .orElse(providers.environmentVariable("MORIMIL_RELEASE_STORE_PASSWORD"))
val releaseKeyAlias = providers.gradleProperty("MORIMIL_RELEASE_KEY_ALIAS")
    .orElse(providers.environmentVariable("MORIMIL_RELEASE_KEY_ALIAS"))
val releaseKeyPassword = providers.gradleProperty("MORIMIL_RELEASE_KEY_PASSWORD")
    .orElse(providers.environmentVariable("MORIMIL_RELEASE_KEY_PASSWORD"))
val releaseSigningInputs = linkedMapOf(
    "MORIMIL_RELEASE_STORE_FILE" to releaseStoreFile,
    "MORIMIL_RELEASE_STORE_PASSWORD" to releaseStorePassword,
    "MORIMIL_RELEASE_KEY_ALIAS" to releaseKeyAlias,
    "MORIMIL_RELEASE_KEY_PASSWORD" to releaseKeyPassword
)
val hasCompleteReleaseSigningMaterial = releaseSigningInputs.values.all { provider ->
    !provider.orNull.isNullOrBlank()
}

val validateReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails closed unless explicit Morimil Android release-signing material is present."

    doLast {
        val missing = releaseSigningInputs
            .filterValues { provider -> provider.orNull.isNullOrBlank() }
            .keys
            .sorted()
        check(missing.isEmpty()) {
            "Missing release signing inputs: ${missing.joinToString(", ")}. " +
                "Release builds must never fall back to debug or unsigned signing."
        }

        val keystorePath = requireNotNull(releaseStoreFile.orNull).trim()
        val keystore = file(keystorePath)
        check(keystore.isFile && keystore.length() > 0L) {
            "MORIMIL_RELEASE_STORE_FILE does not point to a non-empty keystore file."
        }
    }
}

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
    description = "Verifies and expands the vendored Morimil Canvas runtime-recovery bundle."

    inputs.property("morimilCanvasVersion", morimilCanvasVersion)
    inputs.property("morimilCanvasRecoveryId", morimilCanvasRecoveryId)
    inputs.property("morimilCanvasBundleSha256", morimilCanvasBundleSha256)
    inputs.property("morimilCanvasTreeSha256", morimilCanvasTreeSha256)
    inputs.file(morimilCanvasVendoredArchive)
    inputs.file(morimilCanvasProvenance)
    providers.environmentVariable("MORIMIL_CANVAS_ZIP").orNull?.let { localPath ->
        inputs.file(localPath)
    }
    outputs.dir(morimilCanvasGeneratedAssets)

    doLast {
        val provenanceFile = morimilCanvasProvenance.asFile
        check(provenanceFile.isFile && provenanceFile.length() == 964L) {
            "Morimil Canvas recovery provenance is missing or has an invalid size"
        }
        check(
            sha256(provenanceFile) ==
                "cf57eff71ac919cc59a18e1815d49dd97702b3fe8e4864bb101f016f7147a542"
        ) { "Morimil Canvas recovery provenance hash mismatch" }

        val provenance = JsonSlurper().parse(provenanceFile) as? Map<*, *>
            ?: error("Morimil Canvas recovery provenance must be a JSON object")
        val expectedProvenanceKeys = setOf(
            "apkEntry",
            "apkSha256",
            "canonicalTreeSha256",
            "originalBundleRecovered",
            "originalBundleSha256",
            "recoveryId",
            "runtimeFileCount",
            "runtimeTotalBytes",
            "schema",
            "sourceArtifactDigest",
            "sourceArtifactExpiresAt",
            "sourceArtifactId",
            "sourceHead",
            "sourceWorkflowRunId",
            "successorBundleName",
            "successorBundleSha256",
            "successorBundleSizeBytes"
        )
        check(provenance.keys == expectedProvenanceKeys) {
            "Morimil Canvas recovery provenance field inventory mismatch"
        }

        fun provenanceString(key: String): String =
            provenance[key] as? String ?: error("Provenance field $key must be a string")
        fun provenanceLong(key: String): Long =
            (provenance[key] as? Number)?.toLong()
                ?: error("Provenance field $key must be an integer")

        check(provenanceString("schema") == "morimil.canvas.runtime-recovery.provenance.v1")
        check(provenanceString("recoveryId") == morimilCanvasRecoveryId)
        check(provenance["originalBundleRecovered"] == false)
        check(
            provenanceString("originalBundleSha256") ==
                "73b061406d9fff999a859025f497bece4680a896ad19eccb6a391cdb50cd0507"
        )
        check(provenanceLong("sourceWorkflowRunId") == 30_592_451_855L)
        check(provenanceLong("sourceArtifactId") == 8_779_073_588L)
        check(
            provenanceString("sourceArtifactDigest") ==
                "sha256:72c00b39491d4ba8b46478f9749e5e09d936718795bd314ce15e17df8a166c54"
        )
        check(provenanceString("sourceArtifactExpiresAt") == "2026-10-29T00:04:24Z")
        check(
            provenanceString("sourceHead") ==
                "7bdbda2aa4b7568695ba8e98be54d506d42c99d5"
        )
        check(provenanceString("apkEntry") == "app/build/outputs/apk/debug/app-debug.apk")
        check(
            provenanceString("apkSha256") ==
                "314b99a5a67d60f8d2d379d8efc1d7ef52caeacdc24d7dd1b32eb7b448cab623"
        )
        check(provenanceLong("runtimeFileCount") == 48L)
        check(provenanceLong("runtimeTotalBytes") == 3_922_742L)
        check(provenanceString("canonicalTreeSha256") == morimilCanvasTreeSha256)
        check(
            provenanceString("successorBundleName") ==
                "morimil-canvas-0.3.1-runtime-recovery-v1.zip"
        )
        check(provenanceLong("successorBundleSizeBytes") == 3_931_846L)
        check(provenanceString("successorBundleSha256") == morimilCanvasBundleSha256)

        val archive = morimilCanvasArchive.get().asFile
        archive.parentFile.mkdirs()
        val localOverride = System.getenv("MORIMIL_CANVAS_ZIP")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val sourceArchive = if (localOverride != null) {
            file(localOverride).also { localFile ->
                check(localFile.isFile) {
                    "MORIMIL_CANVAS_ZIP does not point to a file: $localOverride"
                }
            }
        } else {
            morimilCanvasVendoredArchive.asFile.also { vendoredFile ->
                check(vendoredFile.isFile) { "Vendored Morimil Canvas recovery bundle is missing" }
            }
        }
        sourceArchive.copyTo(archive, overwrite = true)

        check(archive.length() == 3_931_846L) { "Morimil Canvas bundle size mismatch" }
        val actualArchiveHash = sha256(archive)
        check(actualArchiveHash == morimilCanvasBundleSha256) {
            "Morimil Canvas bundle hash mismatch. Expected $morimilCanvasBundleSha256, got $actualArchiveHash"
        }

        val generatedRoot = morimilCanvasGeneratedAssets.get().asFile
        generatedRoot.deleteRecursively()
        val canvasRoot = File(generatedRoot, "morimil-canvas").apply { mkdirs() }
        val canonicalRoot = canvasRoot.canonicalFile
        val extractedByPath = linkedMapOf<String, File>()
        var extractedFiles = 0
        var extractedBytes = 0L

        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                check(!entry.isDirectory) { "Morimil Canvas recovery ZIP contains a directory entry" }
                check('\\' !in entry.name) { "Unsafe Morimil Canvas ZIP entry: ${entry.name}" }
                val normalizedName = entry.name
                val segments = normalizedName.split('/')
                check(
                    normalizedName.isNotBlank() &&
                        !normalizedName.startsWith('/') &&
                        segments.none { segment -> segment.isBlank() || segment == "." || segment == ".." }
                ) { "Unsafe Morimil Canvas ZIP entry: ${entry.name}" }
                check(normalizedName !in extractedByPath) {
                    "Duplicate Morimil Canvas ZIP entry: ${entry.name}"
                }

                val target = File(canvasRoot, normalizedName).canonicalFile
                check(
                    target != canonicalRoot &&
                        target.path.startsWith(canonicalRoot.path + File.separator)
                ) { "Morimil Canvas ZIP entry escapes the asset root: ${entry.name}" }

                target.parentFile.mkdirs()
                target.outputStream().buffered().use { output -> zip.copyTo(output) }
                extractedFiles += 1
                extractedBytes += target.length()
                check(extractedFiles <= 200) { "Morimil Canvas bundle contains too many files" }
                check(extractedBytes <= 6L * 1024L * 1024L) {
                    "Morimil Canvas bundle exceeds 6 MB"
                }
                extractedByPath[normalizedName] = target
                zip.closeEntry()
            }
        }

        check(extractedFiles == 48) { "Morimil Canvas runtime file-count mismatch" }
        check(extractedBytes == 3_922_742L) { "Morimil Canvas runtime byte-count mismatch" }
        val indexFile = File(canvasRoot, "index.html")
        val manifestFile = File(canvasRoot, "morimil-canvas.manifest.json")
        check(indexFile.isFile) { "Morimil Canvas index.html is missing" }
        check(manifestFile.isFile) { "Morimil Canvas integrity manifest is missing" }

        val manifest = JsonSlurper().parse(manifestFile) as? Map<*, *>
            ?: error("Morimil Canvas integrity manifest must be a JSON object")
        check(manifest["schema"] == "morimil.canvas.bundle.v1")
        check(manifest["version"] == morimilCanvasVersion)
        check(manifest["entrypoint"] == "index.html")
        check(manifest["bridgeSchema"] == "morimil.canvas.bridge.v1")
        check((manifest["totalBytes"] as? Number)?.toLong() == 3_913_521L)
        val manifestFiles = manifest["files"] as? List<*>
            ?: error("Morimil Canvas manifest files must be an array")
        check(manifestFiles.size == 47)
        val declaredPaths = linkedSetOf<String>()
        var declaredBytes = 0L
        manifestFiles.forEach { rawEntry ->
            val item = rawEntry as? Map<*, *>
                ?: error("Morimil Canvas manifest entry must be an object")
            val path = item["path"] as? String
                ?: error("Morimil Canvas manifest path must be a string")
            check(path != "morimil-canvas.manifest.json" && declaredPaths.add(path))
            val target = extractedByPath[path]
                ?: error("Morimil Canvas manifest path is missing from the bundle: $path")
            val expectedSize = (item["size"] as? Number)?.toLong()
                ?: error("Morimil Canvas manifest size must be an integer: $path")
            val expectedHash = item["sha256"] as? String
                ?: error("Morimil Canvas manifest hash must be a string: $path")
            check(target.length() == expectedSize) { "Manifest size mismatch: $path" }
            check(sha256(target) == expectedHash) { "Manifest hash mismatch: $path" }
            declaredBytes += target.length()
        }
        check(declaredPaths == extractedByPath.keys - "morimil-canvas.manifest.json")
        check(declaredBytes == 3_913_521L)

        val treeDigest = MessageDigest.getInstance("SHA-256")
        extractedByPath.toSortedMap().forEach { (path, target) ->
            val record = "$path\u0000${target.length()}\u0000${sha256(target)}\n"
            treeDigest.update(record.toByteArray(Charsets.UTF_8))
        }
        val actualTreeHash = treeDigest.digest().joinToString("") { byte -> "%02x".format(byte) }
        check(actualTreeHash == morimilCanvasTreeSha256) {
            "Morimil Canvas canonical runtime-tree hash mismatch"
        }

        logger.lifecycle(
            "Prepared Morimil Canvas $morimilCanvasVersion from $morimilCanvasRecoveryId: " +
                "$extractedFiles files, $extractedBytes bytes"
        )
    }
}

android {
    namespace = "com.morimil.app"
    compileSdk = 35

    signingConfigs {
        create("release") {
            if (hasCompleteReleaseSigningMaterial) {
                storeFile = file(requireNotNull(releaseStoreFile.orNull).trim())
                storePassword = requireNotNull(releaseStorePassword.orNull)
                keyAlias = requireNotNull(releaseKeyAlias.orNull).trim()
                keyPassword = requireNotNull(releaseKeyPassword.orNull)
            }
        }
    }

    defaultConfig {
        applicationId = "com.morimil.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.3.1-prealpha.plan-v3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
        // MORIMIL_RELEASE_UNSIGNED_BUILD_TYPE_BEGIN
        create("releaseUnsigned") {
            initWith(getByName("release"))
            signingConfig = null
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
        // MORIMIL_RELEASE_UNSIGNED_BUILD_TYPE_END
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

tasks.matching { task -> task.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseSigning)
}

// MORIMIL_RELEASE_UNSIGNED_TASKS_BEGIN
val verifyReleaseUnsignedBoundary = tasks.register("verifyReleaseUnsignedBoundary") {
    group = "verification"
    description = "Verifies the final releaseUnsigned model and unsigned task graph."

    doLast {
        val releaseUnsigned = android.buildTypes.getByName("releaseUnsigned")
        check(!releaseUnsigned.isDebuggable) {
            "releaseUnsigned must remain non-debuggable"
        }
        check(releaseUnsigned.signingConfig == null) {
            "releaseUnsigned must not have a signingConfig"
        }
        check("release" in releaseUnsigned.matchingFallbacks) {
            "releaseUnsigned must retain the release fallback"
        }

        val debugTasks = gradle.taskGraph.allTasks
            .filter { task -> task.project == project && task.name.contains("debug", ignoreCase = true) }
            .map { task -> task.path }
            .sorted()
        check(debugTasks.isEmpty()) {
            "Unsigned release flow must not include debug tasks: $debugTasks"
        }
    }
}

// Explicit CI-only input for the isolated signing job. The normal release task remains gated above.
val isolatedUnsignedApk = layout.buildDirectory.file(
    "outputs/isolatedUnsigned/app-release-unsigned.apk"
)
tasks.register("assembleUnsignedReleaseForSigning") {
    group = "build"
    description = "Builds and stages one non-debuggable unsigned release input for isolated signing."
    dependsOn(verifyReleaseUnsignedBoundary, "assembleReleaseUnsigned")
    outputs.file(isolatedUnsignedApk)
    // Staging must always refresh after the unsigned variant is assembled.
    outputs.upToDateWhen { false }

    doLast {
        val sourceDirectory = layout.buildDirectory.dir("outputs/apk/releaseUnsigned").get().asFile
        val candidates = sourceDirectory.listFiles().orEmpty()
            .filter { candidate -> candidate.isFile && candidate.extension == "apk" }
            .sortedBy(File::getName)
        check(candidates.size == 1) {
            "Expected exactly one unsigned release APK, found ${candidates.map(File::getName)}"
        }
        val source = candidates.single()
        check(!Files.isSymbolicLink(source.toPath())) {
            "Unsigned release input must not be a symbolic link"
        }

        val target = isolatedUnsignedApk.get().asFile
        target.parentFile.deleteRecursively()
        check(target.parentFile.mkdirs()) { "Unable to create isolated unsigned output directory" }
        source.copyTo(target, overwrite = false)
        check(target.isFile && target.length() > 0L && !Files.isSymbolicLink(target.toPath()))
    }
}
// MORIMIL_RELEASE_UNSIGNED_TASKS_END

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    val roomVersion = "2.8.4"

    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.webkit:webkit:1.16.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.fasterxml.jackson.core:jackson-core:2.22.1")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.15.0")
    implementation("com.google.crypto.tink:tink-android:1.23.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("androidx.sqlite:sqlite:2.7.0")
    implementation("net.zetetic:sqlcipher-android:4.17.0@aar")

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260719")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation("androidx.work:work-testing:2.9.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
