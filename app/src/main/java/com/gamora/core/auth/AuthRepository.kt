
package com.gamora.core.auth

import com.gamora.core.legal.VERSION_POLITICAS
import com.gamora.R
import com.gamora.core.texto
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

data class RegisterData(
    val nombre: String,
    val apellidoPaterno: String,
    val apellidoMaterno: String,
    val correo: String,
    val telefono: String
)

data class ConsentData(
    val terminosAceptados: Boolean,
    val privacidadRevisada: Boolean,
    val analiticaAutorizada: Boolean,
    val iaAutorizada: Boolean,
    // Versión del aviso y los términos que aceptó; null si no se sabe (p. ej. al crear la cuenta).
    val versionPoliticas: String? = null
)

data class PerfilUsuario(
    val nombre: String,
    val apellidoPaterno: String,
    val apellidoMaterno: String,
    val correo: String,
    val telefono: String
)

data class PreferenciasFinancieras(
    val moneda: String = "MXN",
    val ingresoMensualCentavos: Long = 0L,
    val presupuestoMensualCentavos: Long = 0L,
    val diaInicioPeriodo: Int = 1,
    val objetivoAhorroMensualCentavos: Long = 0L
)

object AuthRepository {

    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    fun registrarUsuario(
        datos: RegisterData,
        password: String,
        consentimientos: ConsentData,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!consentimientos.terminosAceptados ||
            !consentimientos.privacidadRevisada
        ) {
            onError(texto(R.string.auth_repository_debes_aceptar_los_terminos))
            return
        }

        auth.createUserWithEmailAndPassword(
            datos.correo.trim(),
            password
        ).addOnSuccessListener { resultado ->

            val usuario = resultado.user

            if (usuario == null) {
                onError(texto(R.string.auth_repository_no_fue_posible_obtener))
                return@addOnSuccessListener
            }

            val uid = usuario.uid

            val perfil = hashMapOf(
                "uid" to uid,
                "nombre" to datos.nombre.trim(),
                "apellidoPaterno" to datos.apellidoPaterno.trim(),
                "apellidoMaterno" to datos.apellidoMaterno.trim(),
                "correo" to datos.correo.trim(),
                "telefono" to datos.telefono.trim(),
                "proveedor" to "password",
                "fechaCreacion" to FieldValue.serverTimestamp()
            )

            val registroConsentimiento = hashMapOf(
                "terminosAceptados" to true,
                "privacidadRevisada" to true,
                "analiticaAutorizada" to consentimientos.analiticaAutorizada,
                "iaAutorizada" to consentimientos.iaAutorizada,
                "versionPoliticas" to VERSION_POLITICAS,
                "fechaRegistro" to FieldValue.serverTimestamp(),
                "fechaRevocacion" to null
            )

            val perfilRef = db.collection("users")
                .document(uid)

            val consentimientoRef = perfilRef
                .collection("consentimientos")
                .document()

            // Ambas escrituras se ejecutan como una sola operación.
            val batch = db.batch()

            batch.set(perfilRef, perfil)
            batch.set(consentimientoRef, registroConsentimiento)

            batch.commit()
                .addOnSuccessListener {
                    onSuccess()
                }
                .addOnFailureListener { error ->

                    // Intentamos cancelar la cuenta recién creada
                    // si no fue posible completar el almacenamiento.
                    usuario.delete()
                        .addOnCompleteListener { eliminacion ->

                            auth.signOut()

                            val mensaje = if (eliminacion.isSuccessful) {
                                texto(R.string.auth_repository_no_se_pudo_guardar)
                            } else {
                                texto(R.string.auth_repository_no_se_pudo_completar)
                            }

                            onError(
                                mensaje + " Detalle: " +
                                        (error.localizedMessage ?: texto(R.string.auth_repository_error_desconocido))
                            )
                        }
                }

        }.addOnFailureListener { error ->

            val mensaje = when {
                error.message?.contains(
                    "email address is already in use",
                    true
                ) == true ->
                    texto(R.string.auth_repository_este_correo_ya_esta)

                error.message?.contains(
                    "badly formatted",
                    true
                ) == true ->
                    texto(R.string.auth_repository_el_correo_electronico_no)

                else ->
                    error.localizedMessage ?: texto(R.string.auth_repository_no_se_pudo_crear)
            }

            onError(mensaje)
        }
    }

    fun iniciarSesion(
        correo: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        auth.signInWithEmailAndPassword(
            correo.trim(),
            password
        ).addOnSuccessListener {
            onSuccess()
        }.addOnFailureListener { error ->
            onError(
                error.localizedMessage ?: texto(R.string.auth_repository_no_se_pudo_iniciar)
            )
        }
    }

    /** Entra con Google. [onSuccess] indica si la persona ya tiene perfil en Gamora. */
    fun iniciarSesionConGoogle(
        credencial: AuthCredential,
        onSuccess: (perfilExiste: Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        auth.signInWithCredential(credencial)
            .continueWithTask { inicio ->
                inicio.exception?.let { throw it }
                db.collection("users").document(inicio.result.user!!.uid).get()
            }
            .addOnSuccessListener { perfil -> onSuccess(perfil.exists()) }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: texto(R.string.auth_repository_no_se_pudo_iniciar_2))
            }
    }

    /** Primera vez con Google: crea el perfil y guarda los consentimientos, igual que el registro normal. */
    fun completarRegistroGoogle(
        nombre: String,
        apellidos: String,
        consentimientos: ConsentData,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val usuario = auth.currentUser
        val correo = usuario?.email

        if (usuario == null || correo == null) {
            onError(texto(R.string.auth_repository_no_hay_una_sesion))
            return
        }

        // "Pérez López" → paterno "Pérez", materno "López".
        val partes = apellidos.trim().split(Regex("\\s+"), limit = 2)
        val perfilRef = db.collection("users").document(usuario.uid)

        db.batch().apply {
            set(
                perfilRef,
                hashMapOf(
                    "uid" to usuario.uid,
                    "nombre" to nombre.trim().ifBlank { "Usuario" }.take(60),
                    "apellidoPaterno" to partes.getOrElse(0) { "" }.take(60),
                    "apellidoMaterno" to partes.getOrElse(1) { "" }.take(60),
                    "correo" to correo,
                    "telefono" to "",
                    "proveedor" to "google",
                    "fechaCreacion" to FieldValue.serverTimestamp()
                )
            )
            set(
                perfilRef.collection("consentimientos").document(),
                hashMapOf(
                    "terminosAceptados" to true,
                    "privacidadRevisada" to true,
                    "analiticaAutorizada" to consentimientos.analiticaAutorizada,
                    "iaAutorizada" to consentimientos.iaAutorizada,
                    "versionPoliticas" to VERSION_POLITICAS,
                    "fechaRegistro" to FieldValue.serverTimestamp(),
                    "fechaRevocacion" to null
                )
            )
        }.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: texto(R.string.auth_repository_no_se_pudo_crear_2))
            }
    }

    /** Si no acepta los términos, no se queda una cuenta de acceso sin perfil. */
    fun cancelarRegistroGoogle() {
        auth.currentUser?.delete()?.addOnCompleteListener { auth.signOut() } ?: auth.signOut()
    }

    /** Cuenta que entra solo con Google (sin contraseña). */
    fun esCuentaGoogle(): Boolean {
        val proveedores = auth.currentUser?.providerData.orEmpty().map { it.providerId }
        return "google.com" in proveedores && "password" !in proveedores
    }

    fun credencialConPassword(password: String): AuthCredential? =
        obtenerCorreo()?.let { EmailAuthProvider.getCredential(it, password) }

    fun cerrarSesion() {
        aplicarAnalitica(false)
        auth.signOut()
    }

    /**
     * La analítica arranca apagada (ver AndroidManifest) y solo se enciende con el consentimiento
     * de la persona; se apaga al cerrar sesión o al retirarlo.
     */
    fun aplicarAnalitica(autorizada: Boolean) {
        FirebaseAnalytics.getInstance(FirebaseApp.getInstance().applicationContext)
            .setAnalyticsCollectionEnabled(autorizada)
    }

    fun obtenerUid(): String? {
        return auth.currentUser?.uid
    }

    fun obtenerCorreo(): String? {
        return auth.currentUser?.email
    }

    fun recuperarPassword(
        correo: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        auth.sendPasswordResetEmail(correo.trim())
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { error ->
                onError(
                    error.localizedMessage ?: texto(R.string.auth_repository_no_se_pudo_enviar)
                )
            }
    }

    fun obtenerPerfil(
        onSuccess: (PerfilUsuario) -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = obtenerUid()

        if (uid == null) {
            onError(texto(R.string.auth_repository_no_hay_una_sesion))
            return
        }

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { documento ->

                if (!documento.exists()) {
                    onError(texto(R.string.auth_repository_no_se_encontro_el))
                    return@addOnSuccessListener
                }

                val perfil = PerfilUsuario(
                    nombre = documento.getString("nombre") ?: "",
                    apellidoPaterno =
                        documento.getString("apellidoPaterno") ?: "",
                    apellidoMaterno =
                        documento.getString("apellidoMaterno") ?: "",
                    correo = documento.getString("correo")
                        ?: obtenerCorreo().orEmpty(),
                    telefono = documento.getString("telefono") ?: ""
                )

                onSuccess(perfil)
            }
            .addOnFailureListener { error ->
                onError(
                    error.localizedMessage
                        ?: texto(R.string.auth_repository_no_se_pudo_cargar)
                )
            }
    }

    fun actualizarPerfil(
        nombre: String,
        apellidoPaterno: String,
        apellidoMaterno: String,
        telefono: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = obtenerUid()

        if (uid == null) {
            onError(texto(R.string.auth_repository_no_hay_una_sesion))
            return
        }

        val nombreLimpio = nombre.trim()
        val paternoLimpio = apellidoPaterno.trim()
        val maternoLimpio = apellidoMaterno.trim()
        val telefonoLimpio = telefono.trim()

        if (nombreLimpio.isBlank() || paternoLimpio.isBlank()) {
            onError(texto(R.string.auth_repository_el_nombre_y_el))
            return
        }

        if (nombreLimpio.length > 60 ||
            paternoLimpio.length > 60 ||
            maternoLimpio.length > 60
        ) {
            onError(texto(R.string.auth_repository_los_nombres_y_apellidos))
            return
        }

        if (telefonoLimpio.length > 25) {
            onError(texto(R.string.auth_repository_el_numero_de_telefono))
            return
        }

        val cambios = mapOf(
            "nombre" to nombreLimpio,
            "apellidoPaterno" to paternoLimpio,
            "apellidoMaterno" to maternoLimpio,
            "telefono" to telefonoLimpio,
            "fechaActualizacion" to FieldValue.serverTimestamp()
        )

        db.collection("users")
            .document(uid)
            .update(cambios)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { error ->
                onError(
                    error.localizedMessage
                        ?: texto(R.string.auth_repository_no_se_pudo_actualizar)
                )
            }
    }

    fun obtenerPreferenciasFinancieras(
        onSuccess: (PreferenciasFinancieras) -> Unit,
        onError: (String) -> Unit
    ){
        val uid = obtenerUid();

        if(uid == null) {
            onError(texto(R.string.auth_repository_no_hay_una_sesion_2))
            return
        }

        db.collection("users")
            .document(uid)
            .collection("preferencias")
            .document("financieras")
            .get()
            .addOnSuccessListener { documento ->
                if(!documento.exists()){
                        onSuccess(PreferenciasFinancieras())
                    return@addOnSuccessListener
                }

                val preferencias = PreferenciasFinancieras(
                    moneda = documento.getString("moneda") ?: "MXN",
                    ingresoMensualCentavos = documento.getLong("ingresoMensualCentavos") ?: 0L,
                    presupuestoMensualCentavos = documento.getLong("presupuestoMensualCentavos") ?: 0L,
                    diaInicioPeriodo = (documento.getLong("diaInicioPeriodo") ?: 1L).toInt(),
                    objetivoAhorroMensualCentavos = documento.getLong("objetivoAhorroMensualCentavos") ?: 0L
                )

                onSuccess(preferencias)
            }
            .addOnFailureListener { error ->
                onError(
                    error.localizedMessage ?: texto(R.string.auth_repository_no_se_pudieron_cargar)
                )
            }

    }

    fun guardarPreferenciasFinancieras(
        preferencias: PreferenciasFinancieras,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = obtenerUid()

        if(uid == null){
            onError(texto(R.string.auth_repository_no_hay_una_sesion_2))
            return
        }

        val monedasPermitidas = setOf("MXN", "USD", "EUR")

        if(preferencias.moneda !in monedasPermitidas){
            onError(texto(R.string.auth_repository_selecciona_una_moneda_valida))
            return
        }

        if (
            preferencias.ingresoMensualCentavos < 0 ||
            preferencias.presupuestoMensualCentavos < 0 ||
            preferencias.objetivoAhorroMensualCentavos < 0
        ) {
            onError(texto(R.string.auth_repository_las_cantidades_no_pueden))
            return
        }

        if (
            preferencias.ingresoMensualCentavos > 100_000_000_000L ||
            preferencias.presupuestoMensualCentavos > 100_000_000_000L ||
            preferencias.objetivoAhorroMensualCentavos > 100_000_000_000L
        ) {
            onError(texto(R.string.auth_repository_una_de_las_cantidades))
            return
        }

        if(preferencias.diaInicioPeriodo !in 1..28){
            onError(texto(R.string.auth_repository_el_dia_de_inicio))
            return
        }

        val datos = hashMapOf(
            "moneda" to preferencias.moneda,
            "ingresoMensualCentavos" to preferencias.ingresoMensualCentavos,
            "presupuestoMensualCentavos" to preferencias.presupuestoMensualCentavos,
            "diaInicioPeriodo" to preferencias.diaInicioPeriodo,
            "objetivoAhorroMensualCentavos" to preferencias.objetivoAhorroMensualCentavos,
            "fechaActualizacion" to FieldValue.serverTimestamp()
        )

        db.collection("users")
            .document(uid)
            .collection("preferencias")
            .document("financieras")
            .set(datos)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { error ->
                onError(
                    error.localizedMessage ?: texto(R.string.auth_repository_no_se_pudieron_guardar)
                )
            }
    }

    /** Lee users/{uid}/preferencias/{documento}; entrega null si aún no existe. */
    fun obtenerPreferencia(
        documento: String,
        onSuccess: (Map<String, Any>?) -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = obtenerUid() ?: run {
            onError(texto(R.string.auth_repository_no_hay_una_sesion))
            return
        }

        db.collection("users").document(uid)
            .collection("preferencias").document(documento)
            .get()
            .addOnSuccessListener { onSuccess(it.data) }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: texto(R.string.auth_repository_no_se_pudieron_cargar_2))
            }
    }

    fun guardarPreferencia(
        documento: String,
        datos: Map<String, Any>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = obtenerUid() ?: run {
            onError(texto(R.string.auth_repository_no_hay_una_sesion))
            return
        }

        db.collection("users").document(uid)
            .collection("preferencias").document(documento)
            .set(datos + ("fechaActualizacion" to FieldValue.serverTimestamp()), SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: texto(R.string.auth_repository_no_se_pudieron_guardar_2))
            }
    }

    /** Consentimiento vigente: el registro más reciente de users/{uid}/consentimientos. */
    fun obtenerConsentimiento(
        onSuccess: (ConsentData) -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = obtenerUid() ?: run {
            onError(texto(R.string.auth_repository_no_hay_una_sesion))
            return
        }

        db.collection("users").document(uid)
            .collection("consentimientos")
            .orderBy("fechaRegistro", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { resultado ->
                val doc = resultado.documents.firstOrNull()
                onSuccess(
                    ConsentData(
                        terminosAceptados = doc?.getBoolean("terminosAceptados") ?: true,
                        privacidadRevisada = doc?.getBoolean("privacidadRevisada") ?: true,
                        analiticaAutorizada = doc?.getBoolean("analiticaAutorizada") ?: false,
                        iaAutorizada = doc?.getBoolean("iaAutorizada") ?: false,
                        versionPoliticas = doc?.getString("versionPoliticas")
                    )
                )
            }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: texto(R.string.auth_repository_no_se_pudieron_cargar_3))
            }
    }

    /** Cada cambio agrega un registro nuevo para conservar el historial, igual que en el registro. */
    fun actualizarConsentimiento(
        consentimiento: ConsentData,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = obtenerUid() ?: run {
            onError(texto(R.string.auth_repository_no_hay_una_sesion))
            return
        }

        db.collection("users").document(uid)
            .collection("consentimientos")
            .add(
                hashMapOf(
                    "terminosAceptados" to consentimiento.terminosAceptados,
                    "privacidadRevisada" to consentimiento.privacidadRevisada,
                    "analiticaAutorizada" to consentimiento.analiticaAutorizada,
                    "iaAutorizada" to consentimiento.iaAutorizada,
                    "versionPoliticas" to VERSION_POLITICAS,
                    "fechaRegistro" to FieldValue.serverTimestamp(),
                    "fechaRevocacion" to null
                )
            )
            .addOnSuccessListener {
                aplicarAnalitica(consentimiento.analiticaAutorizada)
                onSuccess()
            }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: texto(R.string.auth_repository_no_se_pudo_guardar_2))
            }
    }

    /**
     * Borra users/{uid} con sus subcolecciones y después la cuenta de acceso.
     * Pide confirmar la identidad (contraseña o Google) porque Firebase exige un inicio de sesión reciente.
     */
    fun eliminarCuenta(
        credencial: AuthCredential,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val usuario = auth.currentUser

        if (usuario == null) {
            onError(texto(R.string.auth_repository_no_hay_una_sesion))
            return
        }

        val perfilRef = db.collection("users").document(usuario.uid)
        val subcolecciones = listOf("movimientos", "recurrentes", "metas", "chat", "preferencias", "consentimientos")

        usuario.reauthenticate(credencial)
            .continueWithTask { reautenticacion ->
                reautenticacion.exception?.let { error ->
                    throw if (error is FirebaseAuthInvalidCredentialsException) {
                        IllegalStateException(texto(R.string.auth_contrasena_incorrecta))
                    } else error
                }
                Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(
                    subcolecciones.map { perfilRef.collection(it).get() }
                )
            }
            .continueWithTask { consultas ->
                consultas.exception?.let { throw it }
                val referencias = consultas.result.flatMap { it.documents }.map { it.reference } + perfilRef

                // Un batch admite como máximo 500 escrituras.
                Tasks.whenAll(
                    referencias.chunked(500).map { grupo ->
                        db.batch().apply { grupo.forEach { delete(it) } }.commit()
                    }
                )
            }
            .continueWithTask { borrado ->
                borrado.exception?.let { throw it }
                usuario.delete()
            }
            .addOnSuccessListener {
                aplicarAnalitica(false)
                auth.signOut()
                onSuccess()
            }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: texto(R.string.auth_repository_no_se_pudo_eliminar))
            }
    }
}
