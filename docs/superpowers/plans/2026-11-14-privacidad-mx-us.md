# Privacidad México y EE. UU. — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aviso y términos v1.1 válidos para México y EE. UU., en español e inglés, y que la app pida aceptarlos a quien aceptó la v1.0.

**Architecture:** Una constante de versión en `core/legal/Politicas.kt`; `AuthRepository` la usa al guardar y la devuelve al leer; `HomeActivity` compara al iniciar y muestra un diálogo de reaceptación. Textos legales en `values/strings.xml` (es) y `values-en/strings.xml` (en); el script de páginas genera ambos idiomas.

**Tech Stack:** Kotlin, Android Views, Firestore (+ reglas), Python (script de páginas).

**Spec:** `docs/superpowers/specs/2026-11-14-privacidad-mx-us-design.md`

## Global Constraints

- Versión vigente: `"1.1"`; fecha de vigencia de los textos: 14 de noviembre de 2026.
- Datos del responsable siguen como marcadores (`[Nombre completo o razón social]`, `[Domicilio]`, `[Correo de privacidad]`, `[Correo de soporte]`).
- La versión española rige; la inglesa lo dice.
- Publicar reglas de Firestore solo con permiso del usuario. Sin commits.
- `values-en` solo trae los textos legales por ahora (`tools:ignore="MissingTranslation"` hasta el proyecto de traducción).

## Review Focus

1. Cuenta sin documento de consentimiento (o sin campo `versionPoliticas`) → se trata como versión vieja y pide aceptar (prueba en Tarea 1).
2. Comparación numérica de versiones: "1.10" > "1.9" (prueba en Tarea 1).
3. Sin conexión al iniciar → no bloquea; se vuelve a revisar en el siguiente inicio (revisión en teléfono, Tarea 2).
4. Reglas de Firestore aceptan `'1.1'` y rechazan `'1.0'` en consentimientos nuevos (Tarea 2).
5. Las páginas es/en tienen el mismo número de secciones (Tarea 4).

---

### Task 1: Versión vigente y comparación

**Files:** Create `app/src/main/java/com/gamora/core/legal/Politicas.kt`; Modify `core/auth/AuthRepository.kt` (3× `"1.0"`, `ConsentData`, `obtenerConsentimiento`); Test `app/src/test/java/com/gamora/PoliticasTest.kt`

**Interfaces — Produces:** `const val VERSION_POLITICAS = "1.1"`; `fun requiereAceptar(aceptada: String?): Boolean` (true si null, vacía o menor que la vigente, comparando por números separados por punto); `ConsentData.versionPoliticas: String?`.

- [ ] Prueba: `requiereAceptar(null)`, `("")`, `("1.0")` → true; `("1.1")` → false; comparación `"1.10"` vs `"1.9"` mediante una función interna `compararVersiones(a, b): Int` (1.10 > 1.9).
- [ ] Ver que falla; implementar; ver que pasa.
- [ ] `AuthRepository` guarda `VERSION_POLITICAS` y lee `versionPoliticas`.

### Task 2: Reaceptación en la app y reglas

**Files:** Modify `feature/home/HomeActivity.kt` (donde ya se llama `obtenerConsentimiento` al iniciar), `res/values/strings.xml` (textos del diálogo), `firestore.rules` (`versionPoliticas == '1.1'`).

- [ ] Si `requiereAceptar(datos.versionPoliticas)`: diálogo no cancelable con título "Actualizamos el aviso de privacidad y los términos", 2–3 líneas de qué cambió, botones para leer cada documento (diálogo legal existente), **Aceptar** → `actualizarConsentimiento` con los mismos permisos de analítica e IA que tenía; **Cerrar sesión** → el cierre de sesión existente.
- [ ] Error de red al leer el consentimiento: no mostrar nada.
- [ ] Reglas: cambiar `'1.0'` por `'1.1'`; validar con el emulador/validador si está disponible; publicar solo con permiso.

### Task 3: Textos v1.1 en español e inglés

**Files:** Modify `res/values/strings.xml` (`politica_privacidad`, `terminos_condiciones`); Create `res/values-en/strings.xml` (las mismas dos + `correo_soporte` si aplica, con `tools:ignore="MissingTranslation"`).

- [ ] Español con todo lo de "Contenido nuevo o corregido" y "Términos" del diseño; encabezado "Última actualización: 14 de noviembre de 2026 · Versión 1.1".
- [ ] Inglés equivalente, misma numeración de secciones, con la nota de que rige la versión española.
- [ ] Revisar cada afirmación contra el código (datos, proveedores, borrado, retención, edad).

### Task 4: Páginas en dos idiomas y lista para el abogado

**Files:** Modify `scripts/generar_paginas_legales.py`; Create `play/para-abogado.md`.

- [ ] El script genera `privacy.html`, `terms.html`, `delete-account.html` desde `values-en`, con enlace ES/EN en cada página; `index.html` enlaza ambos idiomas.
- [ ] Imprime una advertencia si las versiones es/en tienen distinto número de secciones.
- [ ] `play/para-abogado.md` con los puntos del diseño.
- [ ] Compilar, pruebas, lint; instalar en el teléfono (con permiso) y probar el diálogo con la cuenta actual (que aceptó 1.0).
