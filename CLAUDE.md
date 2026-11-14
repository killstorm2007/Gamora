# Gamora

App de finanzas personales (Android, Kotlin con vistas XML) para México y Estados Unidos, en español
(idioma base) e inglés. Backend: Firebase (proyecto `gamora-67`): Auth, Firestore, App Check (Play
Integrity), AI Logic (Gemini), Crashlytics, Analytics (solo con consentimiento).

## Compilar y probar

- Windows (Git Bash): `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest :app:assembleDebug -q`
- Mac: `./gradlew :app:testDebugUnitTest :app:assembleDebug` (JDK de Android Studio).
- Lint debe quedar sin errores: `./gradlew :app:lintDebug` (incluye `MissingTranslation`).
- La versión de depuración solo incluye `arm64-v8a` (para instalar rápido por cable).
- Reglas de Firestore: `firebase deploy --only firestore:rules --project gamora-67` (pedir permiso antes).

## Archivos que no están en git (pásalos a mano)

- `app/google-services.json` (desde la consola de Firebase).
- `gamora-produccion.jks` y `keystore.properties` (firma de producción; nunca a git).
- `local.properties` (Android Studio lo crea), `scripts/` (generador de páginas legales).

## Reglas del proyecto

- Todo texto visible va en `res/values/strings.xml` y su traducción en `res/values-en/strings.xml`.
  En código: `texto(R.string.x, args)` / `textoPlural(...)` (`core/Textos.kt`). Las pruebas usan `TextosDePrueba`.
- Categorías: `core/Categoria.kt`. Firestore guarda la **clave** (`comida`), nunca el nombre. Las reglas
  de Firestore listan las mismas claves (`ReglasCategoriasTest` lo verifica).
- Versión del aviso y los términos: `core/legal/Politicas.kt` (`VERSION_POLITICAS`). Al subirla, cambiar
  también `firestore.rules` (consentimientos) y la app pedirá aceptarla de nuevo.
- Textos legales: `politica_privacidad` y `terminos_condiciones` en ambos `strings.xml`; las páginas de
  `docs/` (GitHub Pages) se generan con `scripts/generar_paginas_legales.py`.
- No subir llaves, contraseñas, estados de cuenta reales ni datos personales; las pruebas usan datos inventados.
- No cambiar el diseño visual sin que el usuario lo pida.
- El usuario hace sus propios commits salvo que pida lo contrario.

## Estructura

- `core/`: autenticación, categorías, textos, seguridad (bloqueo con huella), avisos, formato.
- `feature/`: inicio, movimientos, análisis, metas, presupuestos, perfil, asistente (Gemini), importar
  estados de cuenta (`importar/sinia/` lee PDFs y fotos en el teléfono; Gemini solo si no reconoce el formato).
- `docs/superpowers/specs` y `plans`: diseños y planes aprobados.

## Pendiente antes de publicar

- Datos legales del responsable (nombre, domicilio, correos) en los textos legales.
- Consolas: App Check obligatorio, restringir la llave de API, límite por usuario en AI Logic,
  protección contra enumeración de correos y política de contraseñas (8+).
- Revisión de abogado: `play/para-abogado.md`. Formulario de Play: `play/seguridad-de-datos.md`.

## Siguiente

Versión multiplataforma (Android + iOS). Pendiente decidir tecnología (Kotlin Multiplatform recomendado
para reutilizar la lógica existente). Después: suscripción (RevenueCat, prueba de 5 días, $99/$249/$399 MXN),
límite diario de Gemini con servidor y exportación de datos.
