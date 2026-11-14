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
        debug {
            // Para instalar por cable: solo el procesador de los teléfonos actuales (APK más ligero).
            ndk { abiFilters += "arm64-v8a" }
        }
        release {
            // R8: reduce y ofusca el código y quita recursos sin usar.
            optimization {
                enable = true
            }
            proguardFiles("proguard-rules.pro")
        }
    }

    packaging {
        resources {
            // Tablas de criptografía post-cuántica de BouncyCastle (vienen con pdfbox-android): no se usan.
            excludes += "org/bouncycastle/pqc/**"
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

    // Abrir PDF con contraseña en el teléfono (lector de estados de cuenta)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Reconocimiento de texto en el teléfono (estados de cuenta escaneados y fotos). El modelo viene
    // dentro de la app: funciona desde la primera lectura y sin internet.
    implementation("com.google.mlkit:text-recognition:16.0.1")

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
    // org.json real para probar la lectura de respuestas de Gemini
    testImplementation("org.json:json:20240303")
}
