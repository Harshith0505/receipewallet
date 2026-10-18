plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "com.recipewallet.app"
 compileSdk = 36
 defaultConfig {
  applicationId = if(providers.gradleProperty("previewInstall").orNull=="true") "com.recipewallet.app.preview" else "com.recipewallet.app"
  manifestPlaceholders["appLabel"] = if(providers.gradleProperty("previewInstall").orNull=="true") "Recipe Wallet Preview" else "Recipe Wallet"
  minSdk = 26
  targetSdk = 36
  versionCode = 4
  versionName = "1.4-scrapbook"
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  buildConfigField("String", "BACKEND_URL", "\"${providers.gradleProperty("BACKEND_URL").getOrElse("http://127.0.0.1:4242")}\"")
 }
 sourceSets.getByName("test").resources.srcDir("src/main/assets")
 buildFeatures { compose = true; buildConfig = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.08.00"))
 implementation("androidx.activity:activity-compose:1.12.4")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.ui:ui-tooling-preview")
 debugImplementation("androidx.compose.ui:ui-tooling")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
 implementation("com.stripe:stripe-android:23.23.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.json:json:20250517")
 androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.00"))
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
}
