plugins { id("com.android.application") }

val releaseStorePath = System.getenv("NETWATCH_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("NETWATCH_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("NETWATCH_KEY_ALIAS")
val releaseKeyPassword = System.getenv("NETWATCH_KEY_PASSWORD")
val hasReleaseSigning = !releaseStorePath.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.netwatch.phone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.netwatch.phone"
        minSdk = 29
        targetSdk = 35
        versionCode = 8
        versionName = "0.6.1"
        buildConfigField("String", "DEFAULT_CONTACT_CENTER_URL", "\"\"")
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        if (hasReleaseSigning) {
            create("netwatchRelease") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("netwatchRelease")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
