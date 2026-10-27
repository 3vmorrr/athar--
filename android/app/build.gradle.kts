import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    jacoco
}

val apiBase = providers.gradleProperty("ATHAR_API_BASE_URL").orElse("http://10.0.2.2:8002/api/v1/").get()
require(apiBase.endsWith("/") && URI(apiBase).scheme in listOf("http", "https") && !URI(apiBase).host.isNullOrBlank()) { "ATHAR_API_BASE_URL must be an absolute API URL ending in /." }
if (
    gradle.startParameter.taskNames.any {
        it.contains("release", ignoreCase = true) ||
            it.contains("benchmark", ignoreCase = true)
    }
) {
    require(apiBase.startsWith("https://")) {
        "Release-like builds require an HTTPS ATHAR_API_BASE_URL."
    }
}

val generatedReviewModelAssets = layout.buildDirectory.dir("generated/review-model-assets")
val reviewModelSourceDirectory = rootProject.file("../public/assets/models")

android {
    namespace = "org.athar.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.athar.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "org.athar.app.AtharTestRunner"
        buildConfigField("String", "API_BASE_URL", "\"${apiBase.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            versionNameSuffix = "-benchmark"
            matchingFallbacks += listOf("release")
        }
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    sourceSets.getByName("main").assets.srcDir(generatedReviewModelAssets)
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

val verifyReferenceAssets by tasks.registering {
    val pairs = listOf("quran-index.json" to "index.json", "quran/tanzil-uthmani.xml" to "tanzil-uthmani.xml", "quran/SOURCE.json" to "SOURCE.json")
    inputs.files(pairs.map { rootProject.file("../shared/${it.first}") })
    inputs.files(pairs.map { file("src/main/assets/quran/${it.second}") })
    doLast {
        pairs.forEach { (source, bundled) ->
            val original = rootProject.file("../shared/$source")
            val target = file("src/main/assets/quran/$bundled")
            check(original.readBytes().contentEquals(target.readBytes())) { "Bundled Quran asset differs from shared reference: $bundled" }
        }
        check(rootProject.file("../docs/design/assets/athar-official.png").readBytes().contentEquals(file("src/main/res/drawable-nodpi/athar_logo.png").readBytes())) { "Official logo integrity check failed" }
    }
}

val generateReviewModelAssets by tasks.registering {
    val artifactSpecs = listOf(
        "model" to "zipformer_a0w_ep1_a05.int8.onnx",
        "corpus" to "zipformer_quran.json",
        "io" to "zipformer_a0w_ep1_a05.io.json",
    )
    val sourceFiles = artifactSpecs.map { reviewModelSourceDirectory.resolve(it.second) }
    val licenseFile = reviewModelSourceDirectory.resolve("NPL-1.2.txt")
    val noticeFile = reviewModelSourceDirectory.resolve("NOTICE.md")
    inputs.files(sourceFiles + listOf(licenseFile, noticeFile))
    outputs.dir(generatedReviewModelAssets)

    doLast {
        sourceFiles.forEach { check(it.isFile) { "Missing review model artifact: " + it.name } }
        check(licenseFile.isFile && noticeFile.isFile) {
            "Review model license/notice is missing."
        }

        fun sha256(file: java.io.File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        val outputRoot = generatedReviewModelAssets.get().asFile
        val reviewDirectory = outputRoot.resolve("review")
        outputRoot.deleteRecursively()
        check(reviewDirectory.mkdirs())

        val entries = artifactSpecs.joinToString(",\n") { spec ->
            val source = reviewModelSourceDirectory.resolve(spec.second)
            "    {" +
                "\"id\":\"" + spec.first + "\"," +
                "\"file_name\":\"" + spec.second + "\"," +
                "\"relative_path\":\"assets/models/" + spec.second + "\"," +
                "\"size_bytes\":" + source.length() + "," +
                "\"sha256\":\"" + sha256(source) + "\"" +
                "}"
        }
        val manifest = "{\n" +
            "  \"schema_version\":1,\n" +
            "  \"version\":\"zipformer-a0w-ep1-a05\",\n" +
            "  \"license\":{\"id\":\"NPL-1.2\",\"paid_feature_prohibited\":true},\n" +
            "  \"runtime_verified\":false,\n" +
            "  \"artifacts\":[\n" + entries + "\n  ]\n" +
            "}\n"

        reviewDirectory.resolve("model_manifest.json")
            .writeText(manifest, Charsets.UTF_8)
        licenseFile.copyTo(
            reviewDirectory.resolve("NPL-1.2.txt"),
            overwrite = true,
        )
        noticeFile.copyTo(
            reviewDirectory.resolve("NOTICE.md"),
            overwrite = true,
        )
    }
}

tasks.named("preBuild") {
    dependsOn(verifyReferenceAssets, generateReviewModelAssets)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    implementation("androidx.work:work-runtime-ktx:2.10.3")
    implementation("androidx.webkit:webkit:1.16.0")
    // Official Java implementation avoids newer Kotlin/JDK binary requirements.
    implementation("com.batoulapps.adhan:adhan:1.2.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
    androidTestImplementation("androidx.work:work-testing:2.10.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
