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

android {
    namespace = "com.foyerlauncher.foyer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.foyerlauncher.foyer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Upload-key credentials live in ~/.gradle/gradle.properties (never in the
    // repo); release builds fall back to unsigned when they're absent so
    // contributors and CI can still compile.
    val uploadStoreFile = providers.gradleProperty("FOYER_UPLOAD_STORE_FILE").orNull
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
            if (uploadStoreFile != null) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
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
