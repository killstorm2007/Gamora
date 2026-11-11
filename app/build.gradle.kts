plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.gamora"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.gamora"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    // Firebase (el BoM fija versiones compatibles entre sí)
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore:27.1.0")
    implementation("com.google.firebase:firebase-ai")

    // App Check: Play Integrity en publicación, proveedor de depuración solo en desarrollo
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")

    // Interfaz con vistas XML
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Navegación entre pantallas
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Bloqueo con huella, rostro o PIN
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Corrutinas ligadas al ciclo de vida y preferencias locales
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    // Pruebas
    testImplementation(libs.junit)
}
