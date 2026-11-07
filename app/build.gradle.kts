import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dokka)
}

dokka {
    moduleName.set("foyer")
    dokkaPublications.html {
        suppressInheritedMembers.set(true)
    }
    dokkaSourceSets.configureEach {
        // API docs are for contributors; internal symbols included on purpose.
        documentedVisibilities.set(
            setOf(
                org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier.Public,
                org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier.Internal,
            ),
        )
        sourceLink {
            localDirectory.set(file("src/main/java"))
            remoteUrl("https://github.com/akshay2211/Foyer/tree/main/app/src/main/java")
            remoteLineSuffix.set("#L")
        }
    }
}

// docs/privacy.md is the single source for the privacy policy: the website
// publishes it (foyerlauncher.com/docs/privacy/) and this task bundles the
// same file into the APK for the in-app page.
abstract class BundlePolicyTask : DefaultTask() {
    @get:InputFile
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        source.get().asFile.copyTo(outputDir.get().file("privacy.md").asFile, overwrite = true)
    }
}

val bundlePrivacyPolicy =
    tasks.register<BundlePolicyTask>("bundlePrivacyPolicy") {
        source.set(rootProject.layout.projectDirectory.file("docs/privacy.md"))
    }

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundlePrivacyPolicy, BundlePolicyTask::outputDir)
    }
}

// ---- Versioning ------------------------------------------------------------
// app/version.properties is the single source of truth: versionName is a
// human decision, edited by hand; versionCode holds the code the *next*
// release will carry and moves forward on its own after every signed release
// build (see the doLast below), so no Play upload can ever reuse a code.
val versionPropsFile = layout.projectDirectory.file("version.properties")
val appVersion =
    Properties().apply {
        versionPropsFile.asFile.inputStream().use { load(it) }
    }

// Artifacts carry their identity in the filename — foyer-1.0-1-release.aab,
// not app-release.aab — so Play uploads and local archives stay tellable apart.
base.archivesName.set(
    "foyer-${appVersion.getProperty("versionName").trim()}-${appVersion.getProperty("versionCode").trim()}",
)

// Upload-key credentials live in ~/.gradle/gradle.properties (never in the
// repo); release builds fall back to unsigned when they're absent so
// contributors and CI can still compile.
val uploadStoreFile = providers.gradleProperty("FOYER_UPLOAD_STORE_FILE").orNull

android {
    namespace = "com.foyerlauncher.foyer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.foyerlauncher.foyer"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersion.getProperty("versionCode").trim().toInt()
        versionName = appVersion.getProperty("versionName").trim()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (uploadStoreFile != null) {
        signingConfigs {
            create("upload") {
                storeFile = file(uploadStoreFile)
                storePassword = providers.gradleProperty("FOYER_UPLOAD_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("FOYER_UPLOAD_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("FOYER_UPLOAD_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // The MediaPipe LLM runtime ships ~103MB of native libs across
            // four ABIs, but no 32-bit or x86 phone can actually run an
            // on-device model — release is arm64-only so those devices
            // never install an app whose core feature can't work. Debug
            // keeps every ABI for emulators.
            ndk {
                abiFilters += "arm64-v8a"
            }
            if (uploadStoreFile != null) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
    }
    androidResources {
        // The UI is English-only; drop the ~85 locales AndroidX libraries carry.
        localeFilters += "en"
    }
    dependenciesInfo {
        // Play-only encrypted dependency blob; useless to anyone else and
        // F-Droid strips it — leave it out of both artifact types.
        includeInApk = false
        includeInBundle = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true // android.util.Log no-ops in local tests
    }
}

// After a signed release build, advance versionCode in version.properties so
// the next build can't reuse a code Play has already seen. doLast on the
// anchor tasks fires only when the whole build succeeded, so failed builds
// don't burn codes — and without the upload key nothing bumps at all.
if (uploadStoreFile != null) {
    tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
        // Plain File local: the action below is serialized by the configuration
        // cache, which forbids references back into the build script.
        val propsFile = versionPropsFile.asFile
        doLast {
            val props = Properties().apply { propsFile.inputStream().use { load(it) } }
            val built = props.getProperty("versionCode").trim().toInt()
            propsFile.writeText("versionCode=${built + 1}\nversionName=${props.getProperty("versionName").trim()}\n")
            logger.lifecycle("Signed release built as versionCode $built; version.properties advanced to ${built + 1}.")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.animation)
    implementation(libs.androidx.material3) // settings surfaces only; home stays foundation
    implementation(libs.tasks.genai)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json) // real org.json for parser tests (android.jar ships stubs)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
