# Gamora

App de finanzas personales (Android, Kotlin con vistas XML) para México y Estados Unidos, en español
(idioma base) e inglés. Backend: Firebase (proyecto `gamora-67`, plan gratuito Spark): Auth, Firestore,
App Check (Play Integrity), AI Logic (Gemini), Crashlytics, Analytics (solo con consentimiento).

## Compilar y probar

- Windows (Git Bash): `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest :app:assembleDebug -q`
- Mac: `./gradlew :app:testDebugUnitTest :app:assembleDebug` (JDK de Android Studio).
- Lint debe quedar sin errores: `./gradlew :app:lintDebug` (incluye `MissingTranslation`).
- La versión de depuración solo incluye `arm64-v8a` (para instalar rápido por cable).
- Probar la versión de producción (R8) en el teléfono sin desinstalar: `./gradlew :app:assembleRelease` y volver
  a firmar `app-release.apk` con la llave de depuración (`apksigner sign --ks ~/.android/debug.keystore`). En esa
  instalación el asistente falla por App Check (Play Integrity solo funciona si la app viene de Play).
- Reglas de Firestore: `firebase deploy --only firestore:rules --project gamora-67` (pedir permiso antes).

## Archivos que no están en git (pásalos a mano)

- `app/google-services.json` (desde la consola de Firebase).
- `gamora-produccion.jks` y `keystore.properties` (firma de producción; nunca a git).
- `local.properties` (Android Studio lo crea), `scripts/` (generador de páginas legales).

## Reglas del proyecto

- Todo texto visible va en `res/values/strings.xml` y su traducción en `res/values-en/strings.xml`.
  En código: `texto(R.string.x, args)` / `textoPlural(...)` (`core/Textos.kt`). Las pruebas usan `TextosDePrueba`.
- Errores para la persona: `mensajeDeError(error, R.string.porDefecto)` (`core/Errores.kt`), nunca
  `error.localizedMessage` (Firebase responde en inglés técnico).
- Escrituras con una pantalla esperando: `.alGuardar(...)` (sin internet no se queda colgada). Las demás
  escrituras no esperan al servidor: Firestore las aplica en el teléfono y las sube al reconectar.
- Lecturas: cuidar la cuota gratuita (50,000 al día por proyecto). Movimientos: solo 13 meses en vivo y el
  historial desde el caché (`GamoraTransactionStore`). Errores de carga → `EstadoDatos.reportar(...)`.
- Diálogos: `dialogo(context, IconoDialogo.X)` (`core/ui/Dialogos.kt`); con varios campos,
  `contenidoDesplazable(...)` en lugar de `setMessage`; márgenes de 24 dp; no cambiar el color del texto
  del botón principal (en acciones peligrosas el fondo ya es rojo).
- Antes de abrir otra app (elegir archivo, compartir, correo, ajustes): `SalidaBreve.permitir()`, para que
  el bloqueo con huella no se pida al volver.
- Categorías: `core/Categoria.kt`. Firestore guarda la **clave** (`comida`), nunca el nombre. Las reglas
  de Firestore listan las mismas claves (`ReglasCategoriasTest` lo verifica).
- Dinero: siempre centavos en `Long`. "Ahorro" en cualquier dirección (aportar o retirar de una meta) no es
  consumo ni ingreso nuevo (`esAhorro`).
- Versión del aviso y los términos: `core/legal/Politicas.kt` (`VERSION_POLITICAS`). Al subirla, cambiar
  también `firestore.rules` (consentimientos) y la app pedirá aceptarla de nuevo.
- Textos legales: `politica_privacidad` y `terminos_condiciones` en ambos `strings.xml`; las páginas de
  `docs/` (GitHub Pages) se generan con `scripts/generar_paginas_legales.py` (correr después de cambiarlos).
- No subir llaves, contraseñas, estados de cuenta reales ni datos personales; las pruebas usan datos inventados.
- No cambiar el diseño visual sin que el usuario lo pida.
- El usuario hace sus propios commits salvo que pida lo contrario.

## Estructura

- `core/`: autenticación y eliminación de cuenta (`auth/AuthRepository`), categorías, textos, errores y
  conexión (`Errores.kt`, `Conexion.kt`), seguridad (bloqueo con huella), avisos, formato, diálogos y
  accesibilidad (`ui/`).
- `feature/`: inicio, movimientos y recurrentes, análisis, metas, presupuestos, perfil (`ProfileFragment`
  más un archivo por tema: `PerfilCuenta`, `PerfilPreferencias`, `PerfilPrivacidad`, `PerfilRecurrentes`,
  `PerfilAyuda`, `CambioDeMoneda`), asistente (Gemini), exportar (CSV/PDF), bienvenida, importar estados
  de cuenta (`importar/sinia/` lee PDFs y fotos en el teléfono; Gemini solo si no reconoce el formato).
- `docs/superpowers/specs` y `plans`: diseños de cada función y planes aprobados. Leer el diseño de una
  función antes de cambiarla.

## Pendiente antes de publicar

- Revisión de abogado: `play/para-abogado.md` (confirmar si basta la ciudad como domicilio del responsable).
  Formulario de Play: `play/seguridad-de-datos.md`.
- Prueba interna de Play: subir el bundle firmado con la llave de producción (subir `versionCode` en cada
  envío) y agregar en Firebase la huella SHA-1/SHA-256 de la llave de firma de Play.
- Consolas: App Check obligatorio (después de confirmar la prueba interna), restringir la llave de API,
  límite por usuario en AI Logic. Ya activos: protección contra enumeración de correos y contraseñas de 8+
  con números y caracteres especiales.

## Siguiente

Versión multiplataforma (Android + iOS) con Kotlin Multiplatform para reutilizar la lógica; iOS se compila
en la nube (la Mac de 2015 no corre el Xcode actual). Después: suscripción (RevenueCat, prueba de 5 días,
$99/$249/$399 MXN) y límite diario de Gemini (con servidor necesita el plan Blaze; mientras, un límite en la app).
