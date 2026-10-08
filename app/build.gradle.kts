import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import com.google.gms.googleservices.GoogleServicesTask
import groovy.json.JsonSlurper

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// CI may verify compilation of unsigned release APK/AAB without production signing keys.
// The normal release variant still requires the canonical live Firebase registration.
val ciUnsignedRelease = providers.gradleProperty("ciUnsignedRelease").orElse("false").get().toBooleanStrict()
if (ciUnsignedRelease) {
  require(System.getenv("CI") == "true") {
    "ciUnsignedRelease is only supported in an isolated CI build. Production releases must be signed."
  }
}

android {
  namespace = "com.batuhanduran.burada"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.batuhanduran.burada"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
    buildConfigField("String", "FIRESTORE_DATABASE_ID", "\"mahallem\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      buildConfigField("boolean", "USE_FIREBASE_EMULATORS", "false")
      buildConfigField("String", "EMULATOR_HOST", "\"\"")
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = if (ciUnsignedRelease) null else signingConfigs.getByName("release")
    }
    debug {
      buildConfigField("boolean", "USE_FIREBASE_EMULATORS", providers.gradleProperty("firebaseEmulators").orElse("false").get())
      val emulatorHost = providers.gradleProperty("emulatorHost").orElse("10.0.2.2").get()
      require(emulatorHost.matches(Regex("[A-Za-z0-9.:-]+")))
      buildConfigField("String", "EMULATOR_HOST", "\"$emulatorHost\"")
      // Android creates its standard debug key when no project-specific key is supplied.
      signingConfig = if (file("${rootDir}/debug.keystore").exists()) {
        signingConfigs.getByName("debugConfig")
      } else {
        signingConfigs.getByName("debug")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

// Emulator builds use programmatic demo-only FirebaseOptions and do not read a real app config.
// All live/debug and release builds must use the canonical Firebase Android registration.
val firebaseEmulatorBuild = providers.gradleProperty("firebaseEmulators").orElse("false").get().toBooleanStrict()
googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.ERROR }
val liveFirebaseConfig = layout.projectDirectory.file("google-services.json").asFile
tasks.withType<GoogleServicesTask>().configureEach {
  val isLocalEmulatorDebug = firebaseEmulatorBuild && name.contains("Debug")
  val isCiUnsignedRelease = ciUnsignedRelease && name.contains("Release")
  enabled = !(isLocalEmulatorDebug || isCiUnsignedRelease)
  if (enabled) {
    doFirst {
      val config = liveFirebaseConfig
      check(config.isFile) { "Register com.batuhanduran.burada in Firebase and provide app/google-services.json" }
      val parsed = JsonSlurper().parse(config) as Map<*, *>
      val projectInfo = parsed["project_info"] as? Map<*, *>
      val projectId = projectInfo?.get("project_id") as? String
      check(!projectId.isNullOrBlank() && !projectId.startsWith("demo-")) {
        "Live builds require a real Firebase project, never demo-*"
      }
      val clients = parsed["client"] as? List<*> ?: emptyList<Any>()
      val hasCanonicalApp = clients.any { entry ->
        val info = (entry as? Map<*, *>)?.get("client_info") as? Map<*, *>
        val androidInfo = info?.get("android_client_info") as? Map<*, *>
        androidInfo?.get("package_name") == "com.batuhanduran.burada"
      }
      check(hasCanonicalApp) { "google-services.json does not register com.batuhanduran.burada; download the correct Firebase app configuration" }
    }
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.firestore)

  implementation(libs.firebase.auth)
  // Optional dependencies for Google Sign-In via Credential Manager:
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.playintegrity)
  debugImplementation(libs.firebase.appcheck.debug)
  implementation("com.google.firebase:firebase-messaging")
  implementation("com.google.firebase:firebase-storage")
  implementation("com.google.firebase:firebase-functions")
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.moshi.kotlin.codegen)
}
