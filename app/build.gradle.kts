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
        versionCode = 4
        versionName = "0.3.1"
        buildConfigField("String", "DEFAULT_CONTACT_CENTER_URL", "\"http://10.0.2.2:8767\"")
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
