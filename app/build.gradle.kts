import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes from the environment (set by release.yml from repo secrets).
val releaseKeystore: String? = System.getenv("PREISLER_KEYSTORE_PATH")

android {
    namespace = "dev.strlst.preisler"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.strlst.preisler"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = System.getenv("PREISLER_VERSION_NAME") ?: "0.0.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("PREISLER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PREISLER_KEY_ALIAS")
                keyPassword = System.getenv("PREISLER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

dependencies {
    implementation(project(":receipt-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}

/**
 * Privacy guard (AGENTS.md §3): fails the build if the merged manifest asks for
 * storage or network access. The INTERNET entry is removed from this list when the
 * LAN export goal lands, and the README must be updated at the same time.
 */
abstract class VerifyPrivacyManifest : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:Input
    abstract val forbidden: ListProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val text = manifest.get().asFile.readText()
        val requested =
            Regex("""<uses-permission(?:-sdk-23)?\s+[^>]*android:name="([^"]+)"""")
                .findAll(text)
                .map { it.groupValues[1] }
                .toSet()
        val violations = requested.filter { it in forbidden.get() }
        check(violations.isEmpty()) { "Privacy violation, forbidden permissions in merged manifest: $violations" }
        report.get().asFile.writeText(requested.sorted().joinToString("\n", postfix = "\n"))
    }
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val task =
            tasks.register<VerifyPrivacyManifest>("verify${name}PrivacyManifest") {
                manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                forbidden.set(
                    listOf(
                        "android.permission.INTERNET",
                        "android.permission.READ_EXTERNAL_STORAGE",
                        "android.permission.WRITE_EXTERNAL_STORAGE",
                        "android.permission.MANAGE_EXTERNAL_STORAGE",
                        "android.permission.READ_MEDIA_IMAGES",
                        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
                    ),
                )
                report.set(layout.buildDirectory.file("reports/privacy/${variant.name}-permissions.txt"))
            }
        tasks.named("check") { dependsOn(task) }
    }
}
