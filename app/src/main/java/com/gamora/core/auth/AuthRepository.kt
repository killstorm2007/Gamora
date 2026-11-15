
package com.gamora.core.auth

import com.gamora.core.mensajeDeError
import com.gamora.core.alGuardar
import com.gamora.core.ErrorParaUsuario
import com.gamora.core.Conexion
import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
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

    // Sin guardar la instancia: al eliminar la cuenta se cierra Firestore y se crea una nueva.
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

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
                                mensaje
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
                    mensajeDeError(error, R.string.auth_repository_no_se_pudo_crear)
            }

            onError(mensaje)
        }
    }

    fun iniciarSesion(
        context: Context,
        correo: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        auth.signInWithEmailAndPassword(
            correo.trim(),
            password
        ).continueWithTask { inicio ->
            inicio.exception?.let { throw it }
            // ¿Tiene perfil? Si no, la cuenta quedó a medio eliminar. Si no se puede saber, se sigue.
            db.collection("users").document(inicio.result.user!!.uid).get(Source.SERVER)
                .continueWith { perfil -> if (perfil.isSuccessful) perfil.result.exists() else null }
        }.addOnSuccessListener { perfilExiste ->
            terminarSiEstabaEliminandose(context, auth.currentUser!!.uid, perfilExiste, onSuccess, onError)
        }.addOnFailureListener { error ->
            onError(
                mensajeDeError(error, R.string.auth_repository_no_se_pudo_iniciar)
            )
        }
    }

    /** Entra con Google. [onSuccess] indica si la persona ya tiene perfil en Gamora. */
    fun iniciarSesionConGoogle(
        context: Context,
        credencial: AuthCredential,
        onSuccess: (perfilExiste: Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        auth.signInWithCredential(credencial)
            .continueWithTask { inicio ->
                inicio.exception?.let { throw it }
                db.collection("users").document(inicio.result.user!!.uid).get()
            }
            .addOnSuccessListener { perfil ->
                // Sin perfil es una cuenta nueva (se registra); solo se borra si este teléfono la estaba eliminando.
                terminarSiEstabaEliminandose(context, perfil.id, null, { onSuccess(perfil.exists()) }, onError)
            }
            .addOnFailureListener { error ->
                onError(mensajeDeError(error, R.string.auth_repository_no_se_pudo_iniciar_2))
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
                onError(mensajeDeError(error, R.string.auth_repository_no_se_pudo_crear_2))
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
                    mensajeDeError(error, R.string.auth_repository_no_se_pudo_enviar)
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
                    mensajeDeError(error, R.string.auth_repository_no_se_pudo_cargar)
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
            .alGuardar(R.string.auth_repository_no_se_pudo_actualizar, onSuccess, onError)
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
                    mensajeDeError(error, R.string.auth_repository_no_se_pudieron_cargar)
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
            .alGuardar(R.string.auth_repository_no_se_pudieron_guardar, onSuccess, onError)
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
                onError(mensajeDeError(error, R.string.auth_repository_no_se_pudieron_cargar_2))
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
            .alGuardar(R.string.auth_repository_no_se_pudieron_guardar_2, onSuccess, onError)
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
                onError(mensajeDeError(error, R.string.auth_repository_no_se_pudieron_cargar_3))
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
            .alGuardar(
                R.string.auth_repository_no_se_pudo_guardar_2,
                onSuccess = {
                    aplicarAnalitica(consentimiento.analiticaAutorizada)
                    onSuccess()
                },
                onError = onError
                )
    }

    // ---- Eliminar la cuenta ----
    //
    // Firebase no deja borrar todo de una sola vez desde el teléfono: primero los datos (hace falta la
    // sesión para que las reglas lo permitan) y al final la cuenta de acceso. Si algo se interrumpe a la
    // mitad (sin red, app cerrada), queda anotado en el teléfono y se termina después:
    // - al abrir la app ([terminarEliminacionPendiente]);
    // - al volver a iniciar sesión ([iniciarSesion] / [iniciarSesionConGoogle]).
    // El perfil (users/{uid}) se borra al final de los datos: si falta, los datos ya se borraron.

    private const val PREFERENCIAS_CUENTA = "gamora_cuenta"
    private const val CLAVE_ELIMINANDO = "eliminando_uid"
    private val SUBCOLECCIONES = listOf("movimientos", "recurrentes", "metas", "chat", "preferencias", "consentimientos")

    private fun preferenciasCuenta(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCIAS_CUENTA, Context.MODE_PRIVATE)

    /** Uid de una cuenta cuya eliminación quedó a medias en este teléfono, o null. */
    fun eliminacionPendiente(context: Context): String? =
        preferenciasCuenta(context).getString(CLAVE_ELIMINANDO, null)

    /** Borra las subcolecciones y después el perfil. Repetirlo es seguro: lo ya borrado no falla. */
    private fun borrarDatos(uid: String): Task<Void> {
        val perfilRef = db.collection("users").document(uid)

        // Del servidor, no del caché: así no se queda nada sin borrar.
        return Tasks.whenAllSuccess<QuerySnapshot>(
            SUBCOLECCIONES.map { perfilRef.collection(it).get(Source.SERVER) }
        ).continueWithTask { consultas ->
            consultas.exception?.let { throw it }
            val referencias = consultas.result.flatMap { it.documents }.map { it.reference }

            // Un batch admite como máximo 500 escrituras.
            Tasks.whenAll(referencias.chunked(500).map { grupo ->
                db.batch().apply { grupo.forEach { delete(it) } }.commit()
            })
        }.continueWithTask { borrado ->
            borrado.exception?.let { throw it }
            perfilRef.delete()
        }
    }

    /** Borra los datos y la cuenta de acceso de quien ya inició sesión (sin pedir contraseña otra vez). */
    private fun borrarTodoDeLaSesion(context: Context, limpiarCache: Boolean): Task<Void> {
        val usuario = auth.currentUser ?: return Tasks.forException(ErrorParaUsuario(texto(R.string.auth_repository_no_hay_una_sesion)))
        preferenciasCuenta(context).edit().putString(CLAVE_ELIMINANDO, usuario.uid).commit()

        return borrarDatos(usuario.uid).continueWithTask { borrado ->
            borrado.exception?.let { throw it }
            usuario.delete()
        }.addOnSuccessListener {
            preferenciasCuenta(context).edit().remove(CLAVE_ELIMINANDO).apply()
            aplicarAnalitica(false)
            auth.signOut()
            if (limpiarCache) borrarCacheLocal(context) {}
        }
    }

    /**
     * Borra la copia de los datos que Firestore guarda en el teléfono (y el historial de movimientos).
     * Antes hay que dejar de escuchar los datos: Firestore se cierra y la app crea una instancia nueva.
     */
    fun borrarCacheLocal(context: Context, alTerminar: () -> Unit) {
        context.applicationContext.getSharedPreferences("gamora_historial", Context.MODE_PRIVATE).edit().clear().apply()
        val firestore = FirebaseFirestore.getInstance()
        firestore.terminate()
            .continueWithTask { firestore.clearPersistence() }
            .addOnCompleteListener { alTerminar() }
    }

    /**
     * Borra users/{uid} con sus subcolecciones y después la cuenta de acceso.
     * Pide confirmar la identidad (contraseña o Google) porque Firebase exige un inicio de sesión reciente.
     * Necesita internet: así nunca se queda a medias por estar sin conexión.
     */
    fun eliminarCuenta(
        context: Context,
        credencial: AuthCredential,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val usuario = auth.currentUser ?: return onError(texto(R.string.auth_repository_no_hay_una_sesion))
        if (!Conexion.enLinea.value) return onError(texto(R.string.error_sin_conexion))

        usuario.reauthenticate(credencial)
            .continueWithTask { reautenticacion ->
                reautenticacion.exception?.let { error ->
                    throw if (error is FirebaseAuthInvalidCredentialsException) {
                        ErrorParaUsuario(texto(R.string.auth_contrasena_incorrecta))
                    } else error
                }
                borrarTodoDeLaSesion(context, limpiarCache = false)
            }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                // Si ya se había empezado a borrar, se avisa que falta terminar (se reintenta solo).
                val empezado = eliminacionPendiente(context) == usuario.uid
                onError(
                    if (empezado && error !is ErrorParaUsuario) texto(R.string.cuenta_eliminacion_incompleta)
                    else mensajeDeError(error, R.string.auth_repository_no_se_pudo_eliminar)
                )
            }
    }

    /**
     * Al abrir la app: si una eliminación quedó a medias, la termina. [alTerminar] recibe el mensaje para
     * la persona, o null si no había nada pendiente. Sin una sesión reciente Firebase no deja borrar la
     * cuenta de acceso: entonces se cierra la sesión y se termina al volver a entrar.
     */
    fun terminarEliminacionPendiente(context: Context, alTerminar: (String?) -> Unit) {
        val pendiente = eliminacionPendiente(context)
        val usuario = auth.currentUser

        when {
            pendiente == null -> alTerminar(null)
            usuario == null || usuario.uid != pendiente -> alTerminar(null)
            else -> borrarTodoDeLaSesion(context, limpiarCache = true)
                .addOnSuccessListener { alTerminar(texto(R.string.cuenta_eliminacion_terminada)) }
                .addOnFailureListener {
                    auth.signOut()
                    alTerminar(texto(R.string.cuenta_eliminacion_inicia_sesion))
                }
        }
    }

    /** Recién iniciada la sesión: si la cuenta estaba a medio eliminar, termina de borrarla y lo explica. */
    private fun terminarSiEstabaEliminandose(context: Context, uid: String, perfilExiste: Boolean?, continuar: () -> Unit, onError: (String) -> Unit) {
        val aMedias = eliminacionPendiente(context) == uid || perfilExiste == false
        if (!aMedias) return continuar()

        borrarTodoDeLaSesion(context, limpiarCache = true)
            .addOnSuccessListener { onError(texto(R.string.cuenta_ya_eliminada)) }
            .addOnFailureListener {
                auth.signOut()
                onError(texto(R.string.cuenta_eliminacion_incompleta))
            }
    }
}
