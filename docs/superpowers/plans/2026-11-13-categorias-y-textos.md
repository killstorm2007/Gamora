# Categorías como claves y textos en recursos — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que Firestore guarde categorías como claves independientes del idioma y que todo texto visible salga de `strings.xml`.

**Architecture:** Un `enum class Categoria` en `core/` reemplaza las listas de nombres (`CATEGORIAS_*`, `iconoCategoria`) y todos los `String` de categoría del modelo. Firestore lee/escribe `categoria.clave`. Los textos de layouts y de Kotlin pasan a `res/values/strings.xml` con `plurals` y argumentos.

**Tech Stack:** Kotlin, Android Views (XML), Firebase Firestore, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-13-categorias-y-textos-design.md`

## Global Constraints

- Sin migración: los datos de prueba de Firestore los borra el usuario; la app solo escribe claves.
- No cambiar el diseño visual: cada pantalla debe verse igual que antes (mismo texto en español).
- No cambiar `firestore.rules` (las claves cumplen `categoria` string de 1–40 y el mapa ≤ 20).
- No hacer commits: el usuario sube los cambios. Cada tarea termina con pruebas y compilación verdes.
- Prompts a Gemini y palabras clave del lector de estados (`LectorTablas`) no son texto visible: se quedan en código.
- No agregar `values-en` ni pantalla de idioma.
- Compilar con `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest :app:assembleDebug -q`.

## Review Focus

1. Gemini responde la categoría como nombre con acento, en mayúsculas, en inglés o inventada → se usa la que corresponda u `OTROS`, nunca falla (prueba en Tarea 1 y Tarea 3).
2. Un documento de Firestore con una categoría desconocida (restos de prueba) → el movimiento se muestra como "Otros"; un límite de presupuesto con clave desconocida se ignora, no se asigna a "Otros" (prueba en Tarea 2).
3. Conteos con plural en 0, 1 y varios ("0 metas activas", "1 meta activa") (revisión en Tarea 6).
4. Textos con `%` literal o argumentos en `strings.xml` mal escapados → lint sin `StringFormatInvalid`/`StringFormatCount` (Tarea 6).
5. Buscar "educacion" (sin acento) en movimientos encuentra los de "Educación" (prueba en Tarea 4).

---

### Task 1: Modelo `Categoria`

**Files:**
- Create: `app/src/main/java/com/gamora/core/Categoria.kt`
- Modify: `app/src/main/res/values/strings.xml` (15 nombres `categoria_<clave>`)
- Test: `app/src/test/java/com/gamora/CategoriaTest.kt`

**Interfaces:**
- Produces:
  - `enum class Categoria(val clave: String, @StringRes val nombre: Int, val emoji: String, val ingreso: Boolean, val gasto: Boolean)` con, en este orden: `SALARIO("salario","💼",ingreso)`, `VENTAS("ventas","🏷️",ingreso)`, `NEGOCIO("negocio","🏪",ingreso)`, `INVERSIONES("inversiones","📈",ingreso)`, `REGALO("regalo","🎁",ingreso)`, `COMIDA("comida","🍽️",gasto)`, `TRANSPORTE("transporte","🚗",gasto)`, `VIVIENDA("vivienda","🏠",gasto)`, `SERVICIOS("servicios","💡",gasto)`, `SALUD("salud","🩺",gasto)`, `EDUCACION("educacion","🎓",gasto)`, `ENTRETENIMIENTO("entretenimiento","🎬",gasto)`, `COMPRAS("compras","🛍️",gasto)`, `AHORRO("ahorro","🐷",gasto)`, `OTROS("otros","💸",ingreso y gasto)`.
  - `companion fun porClave(clave: String?): Categoria?` — estricta, solo claves.
  - `companion fun de(texto: String?): Categoria` — clave o nombre en español sin importar mayúsculas/acentos; si no, `OTROS`.
  - `companion val DE_INGRESO: List<Categoria>` / `DE_GASTO: List<Categoria>` (en el orden de la tabla; `OTROS` al final de ambas).
  - `fun Categoria.nombre(context: Context): String`

- [ ] **Step 1: Escribir `CategoriaTest`**
```kotlin
@Test fun porClaveEsEstricta() {
    assertEquals(Categoria.COMIDA, Categoria.porClave("comida"))
    assertNull(Categoria.porClave("Comida"))
    assertNull(Categoria.porClave(null))
}
@Test fun deAceptaNombreEnEspanol() {
    assertEquals(Categoria.EDUCACION, Categoria.de("Educación"))
    assertEquals(Categoria.EDUCACION, Categoria.de("EDUCACION"))
    assertEquals(Categoria.COMIDA, Categoria.de(" comida "))
    assertEquals(Categoria.OTROS, Categoria.de("Food"))
    assertEquals(Categoria.OTROS, Categoria.de(""))
}
@Test fun listasEnElOrdenDeHoy() {
    assertEquals(listOf("salario","ventas","negocio","inversiones","regalo","otros"), Categoria.DE_INGRESO.map { it.clave })
    assertEquals(listOf("comida","transporte","vivienda","servicios","salud","educacion","entretenimiento","compras","ahorro","otros"), Categoria.DE_GASTO.map { it.clave })
}
@Test fun clavesCumplenLasReglas() {
    Categoria.entries.forEach { assertTrue(it.clave.length in 1..40 && it.clave == it.clave.lowercase()) }
}
```
- [ ] **Step 2:** Correr `./gradlew :app:testDebugUnitTest --tests com.gamora.CategoriaTest` → falla (no existe `Categoria`).
- [ ] **Step 3:** Implementar `Categoria.kt` y los strings `categoria_salario` … `categoria_otros` con los nombres actuales ("Educación" con acento). `de` compara contra la clave y contra el nombre en español sin acentos; como el enum no tiene `Context`, guardar el nombre en español como propiedad privada `nombreEs` para `de`.
- [ ] **Step 4:** Correr la prueba → pasa; compilar.

### Task 2: Datos y lógica con `Categoria`

**Files:**
- Modify: `feature/transactions/GamoraMovement.kt` (`category: Categoria`, lectura `Categoria.porClave(...) ?: Categoria.OTROS`, escritura `.clave`, `esAhorro`)
- Modify: `feature/transactions/Recurrentes.kt` (`categoria: Categoria`, ídem lectura/escritura en líneas ~54, ~70, ~110)
- Modify: `feature/goals/SavingsGoal.kt:128` (`Categoria.AHORRO`)
- Modify: `feature/budget/Presupuestos.kt` (`AvancePresupuesto.categoria: Categoria`, límites `Map<Categoria, Long>`, Firestore con claves)
- Modify: `feature/analytics/ConsejosAhorro.kt:71` (`ENTRETENIMIENTO`, `COMPRAS`)
- Modify: `feature/chatbot/ContextoFinanciero.kt` (nombres vía una función `nombre: (Categoria) -> String` que recibe el contexto)
- Test: ajustar `AhorroAcumuladoTest`, `ConsejosAhorroTest`, `ContextoFinancieroTest`, `PresupuestosTest`

**Interfaces:**
- Consumes: `Categoria`, `porClave`, `de` (Tarea 1).
- Produces: `GamoraMovement.category: Categoria`; `Recurrente.categoria: Categoria`; `PresupuestoStore.limites: Map<Categoria, Long>`; `fun limitesDe(datos: Map<String, Any?>?): Map<Categoria, Long>` (la función que hoy lee el mapa "categorias"); `fun contextoFinanciero(..., nombre: (Categoria) -> String)` (mismo nombre y resto de parámetros que hoy).

- [ ] **Step 1:** Cambiar las pruebas existentes a `Categoria.X` (mismas cifras esperadas) y agregar a `PresupuestosTest`:
```kotlin
@Test fun limitesIgnoranClavesDesconocidas() {
    val datos = mapOf("categorias" to mapOf("comida" to 300000L, "xyz" to 5000L, "Comida" to 1L))
    assertEquals(mapOf(Categoria.COMIDA to 300000L), limitesDe(datos))
}
```
- [ ] **Step 2:** Correr pruebas → fallan por tipos.
- [ ] **Step 3:** Hacer los cambios de la lista de archivos. `limitesDe` usa `porClave` (estricta) y descarta lo desconocido.
- [ ] **Step 4:** Pruebas y compilación verdes (la UI aún puede fallar al compilar: si es así, hacer esta tarea junto con la Tarea 4 antes de compilar).

### Task 3: Importación con `Categoria`

**Files:**
- Modify: `feature/importar/Importacion.kt` (`MovimientoImportado.categoria: Categoria`; `categoriaValida(categoria: Categoria, esIngreso: Boolean): Categoria` → si no aplica al tipo, `OTROS`; `leerRespuestaGemini` usa `Categoria.de`)
- Modify: `feature/importar/LectorGemini.kt:41-45` (el prompt lista `clave (Nombre en español)` de `DE_INGRESO`/`DE_GASTO` y pide la clave; nómina → `salario`)
- Modify: `feature/importar/NominaCfdi.kt:61` (`SALARIO`)
- Modify: `feature/importar/sinia/LectorTablas.kt` (`categoriaSugerida(...): Categoria`)
- Modify: `feature/importar/ImportReviewFragment.kt` (muestra `categoria.nombre(context)`)
- Test: ajustar `ImportacionTest`, `NominaCfdiTest`, `LectorTablasTest` (`"Salario"` → `Categoria.SALARIO`)

**Interfaces:**
- Consumes: Tarea 1; `aMovimiento` produce `GamoraMovement` con `Categoria` (Tarea 2).

- [ ] **Step 1:** Agregar a `ImportacionTest`:
```kotlin
@Test fun geminiPuedeResponderNombreOClave() {
    val json = """{"banco":"X","periodo":"","movimientos":[
      {"fecha":"2026-10-01","descripcion":"a","monto":10,"esIngreso":false,"categoria":"Educación"},
      {"fecha":"2026-10-01","descripcion":"b","monto":10,"esIngreso":false,"categoria":"transporte"},
      {"fecha":"2026-10-01","descripcion":"c","monto":10,"esIngreso":false,"categoria":"Groceries"},
      {"fecha":"2026-10-01","descripcion":"d","monto":10,"esIngreso":true,"categoria":"comida"}]}"""
    assertEquals(listOf(Categoria.EDUCACION, Categoria.TRANSPORTE, Categoria.OTROS, Categoria.OTROS),
        leerRespuestaGemini(json).movimientos.map { it.categoria })
}
```
(Ajustar los nombres de campos del JSON a los que ya usa `leerRespuestaGemini`.)
- [ ] **Step 2:** Correr → falla por tipos.
- [ ] **Step 3:** Hacer los cambios de la lista.
- [ ] **Step 4:** Pruebas verdes.

### Task 4: Pantallas con `Categoria`

**Files:**
- Modify: `feature/transactions/MovementDialog.kt` (quitar `CATEGORIAS_INGRESO`, `CATEGORIAS_GASTO`, `CATEGORIAS`; usar `DE_INGRESO`/`DE_GASTO` mostrando `nombre(context)`)
- Modify: `feature/transactions/MovementRow.kt` (quitar `iconoCategoria`; usar `categoria.emoji` y `nombre(context)`)
- Modify: `feature/transactions/TransactionsFragment.kt` y `feature/dashboard/DashboardFragment.kt` (filtro: `Categoria?`, `null` = todas; el texto "Todas las categorías"/"Todas" sale de `strings.xml`)
- Modify: `feature/analytics/AnalyticsFragment.kt` (gráficas, presupuestos y diálogo de límites con `Categoria`; `DE_GASTO - AHORRO`)
- Modify: `core/notifications/Avisos.kt:145-168` (agrupar por `Categoria.porClave(...) ?: OTROS`, excluir `AHORRO`, mostrar `nombre(context)`; los ids de aviso usan la clave)
- Modify: `feature/profile/ProfileFragment.kt` (recurrentes muestran el nombre)
- Create: `fun String.paraBuscar()` ya existe — el buscador compara contra `movement.category.nombre(context).paraBuscar()`
- Test: `app/src/test/java/com/gamora/BusquedaCategoriaTest.kt`

**Interfaces:**
- Consumes: Tareas 1–3.
- Produces: ningún `String` de categoría queda fuera de `Categoria`; `grep -rn '"Ahorro"\|"Comida"\|"Otros"' app/src/main/java` solo encuentra `LectorTablas` (palabras clave) o nada.

- [ ] **Step 1:** Si el filtrado de `TransactionsFragment` es una función pura, extraerla como `fun filtrarMovimientos(movimientos: List<GamoraMovement>, busqueda: String, categoria: Categoria?, nombre: (Categoria) -> String): List<GamoraMovement>` y probar:
```kotlin
@Test fun buscaPorNombreSinAcento() {
    val m = GamoraMovement("Colegiatura", Categoria.EDUCACION, 100, false)
    assertEquals(listOf(m), filtrarMovimientos(listOf(m), "educacion", null) { "Educación" })
}
```
- [ ] **Step 2:** Implementar los cambios de la lista.
- [ ] **Step 3:** Pruebas, compilación y el `grep` de Interfaces.
- [ ] **Step 4:** Instalar en el teléfono (con permiso del usuario) y comprobar: crear gasto e ingreso, recurrente, aporte a meta y límite de presupuesto; filtros y gráficas se ven igual; en Firestore el campo `categoria` guarda la clave.

### Task 5: Textos de layouts a `strings.xml`

**Files:**
- Modify: todos los `app/src/main/res/layout/*.xml` con `android:text`/`android:hint`/`android:contentDescription` literales.
- Modify: `app/src/main/res/values/strings.xml` (prefijo por pantalla: `inicio_`, `movimientos_`, `metas_`, `analisis_`, `perfil_`, `chat_`, `importar_`, `onboarding_`, `comun_`).

- [ ] **Step 1:** Correr `./gradlew :app:lintDebug` y listar los `HardcodedText` (hoy 141).
- [ ] **Step 2:** Mover cada texto a un recurso con el mismo contenido exacto (emojis y espacios incluidos); reutilizar recursos ya existentes cuando el texto sea idéntico.
- [ ] **Step 3:** Lint sin `HardcodedText`; compilación verde.

### Task 6: Textos de Kotlin a `strings.xml`

**Files:**
- Modify: los `.kt` con toasts, `aviso(...)`, `setTitle`/`setMessage`, `text = "..."`, concatenaciones (`SetTextI18n`, hoy 44) y notificaciones (`Avisos.kt`).
- Modify: `strings.xml` (`<plurals>` para conteos: metas activas, movimientos, renglones no leídos; `%1$s`/`%1$d` para valores; `%%` para porcentaje literal en textos con formato).

- [ ] **Step 1:** Listar con lint (`SetTextI18n`) y `grep -rnE 'Toast.makeText\([^,]+, "|aviso\("|setTitle\("|setMessage\("|text = "' app/src/main/java`.
- [ ] **Step 2:** Mover cada texto usando `getString`/`resources.getQuantityString` (en clases sin `Context`, recibirlo como parámetro o pasar el texto ya resuelto). No mover prompts ni palabras clave del lector.
- [ ] **Step 3:** Revisar a mano los `plurals` en 0, 1 y 3 en las pantallas que los usan.
- [ ] **Step 4:** Lint sin `HardcodedText`, `SetTextI18n`, `StringFormatInvalid`, `StringFormatCount`; pruebas y compilación verdes.
- [ ] **Step 5:** Instalar en el teléfono (con permiso) y recorrer todas las pantallas: ningún texto cambió, ninguno vacío ni con `%1$s` visible.
