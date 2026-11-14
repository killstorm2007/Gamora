package com.gamora

import android.app.Application
import com.gamora.core.notifications.crearCanalNotificaciones
import com.gamora.core.ui.Moneda
import com.google.firebase.FirebaseApp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class GamoraApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // App Check debe instalarse antes de cualquier llamada a Firebase (Gemini lo exige).
        FirebaseApp.initializeApp(this)
        instalarAppCheck()

        Moneda.iniciar(this)
        crearCanalNotificaciones(this)
        PDFBoxResourceLoader.init(applicationContext)
    }
}
