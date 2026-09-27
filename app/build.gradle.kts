plugins { id("com.android.application") }

android {
    namespace = "com.netwatch.phone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.netwatch.phone"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "DEFAULT_CONTACT_CENTER_URL", "\"http://10.0.2.2:8767\"")
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
