# Lector de estados de cuenta y nómina — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Importar movimientos desde un PDF, una foto o un XML de nómina (CFDI), revisarlos en una tabla y guardar solo los confirmados.

**Architecture:**
- La lógica pura vive en `feature/importar/`, sin Android, y tiene pruebas unitarias: leer la respuesta de Gemini, leer el CFDI y marcar repetidos.
- Los PDF con contraseña se abren en el teléfono con PdfBox-Android y la copia sin contraseña solo vive en memoria.
- Gemini se llama con Firebase AI Logic y respuesta JSON.
- El resultado pasa en memoria a un destino nuevo de navegación, `importReviewFragment`, que guarda en lote en `users/{uid}/movimientos`.

**Tech Stack:**
- Kotlin, vistas XML, Navigation, Firebase AI Logic (`firebase-ai`) y Firestore.
- `com.tom-roush:pdfbox-android:2.0.27.0`.
- `javax.xml.parsers` para el CFDI y `org.json` (`org.json:json:20240303` solo para pruebas).

**Spec:** `docs/superpowers/specs/2026-11-12-lector-estados-design.md`

## Global Constraints

- Archivos permitidos: `application/pdf`, `image/*`, `text/xml`, `application/xml`. Máximo **15 MB** (15 * 1024 * 1024 bytes).
- Nunca persistir la contraseña, el archivo original ni la copia sin contraseña: nada en Firestore, ni en archivos o caché, ni en logs, ni en el chat. La contraseña se maneja como `CharArray` y se rellena con `'\u0000'` después de usarla. No se registran bytes ni contenido del documento en `Log`.
- Sin permisos nuevos en el manifiesto: se usa el selector del sistema (`ActivityResultContracts.OpenDocument`).
- Las filas válidas cumplen las reglas de Firestore: descripción de 1 a 200 caracteres después de `trim`, `0 < montoCentavos <= 100_000_000_000`, y categoría en `CATEGORIAS_INGRESO` o `CATEGORIAS_GASTO` según el tipo. Si no está en la lista, se usa `"Otros"`.
- Repetido: mismo `esIngreso`, mismo `montoCentavos` y fecha a ±2 días (`2 * 24 * 60 * 60 * 1000` ms) de un movimiento existente o de una fila anterior del mismo documento.
- Textos fijos de la interfaz:
  - botón "📄 Importar estado de cuenta"
  - "Leyendo tu documento…"
  - "Revisa tus movimientos"
  - "⚠ repetido"
  - "Guardar N movimientos"
  - "¿Descartar estos movimientos?"
  - "No encontramos movimientos en este documento"
- Consentimiento: "Tu documento se enviará a Gemini (Google) solo para leerlo. No se guarda." Se recuerda en SharedPreferences `gamora_importar`, clave `consentimiento_gemini`.
- Mantener el diseño actual: colores `gamora_*`, `bg_dashboard_card`, `Widget.Gamora.Button.Outlined`.
- **No hacer commits:** el usuario decide cuándo. Cada tarea termina con compilación y pruebas en verde.
- Comando de verificación: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest :app:assembleDebug -q`

## Review Focus

1. **Gemini devuelve JSON con campos faltantes, montos negativos o como texto ("1,234.50"), o fechas sin año.** Esas filas se descartan o se normalizan y se cuentan en `descartados`; nunca deben provocar un cierre. Prueba en la Tarea 1.
2. **CFDI que no es de nómina, XML mal formado o un CFDI 3.3 sin `FechaPago`.** Debe dar un error claro (`NoEsNominaException`) y no cerrar la app. Prueba en la Tarea 2.
3. **Contraseña incorrecta varias veces o diálogo cancelado.** Se vuelve a pedir o se cancela sin dejar rastro. Verificación manual en la Tarea 6.
4. **Salir de la revisión con giro de pantalla o proceso terminado.** La revisión en memoria se pierde y la pantalla regresa a Movimientos con un aviso, sin cerrarse. Prueba en la Tarea 5: `ImportacionEnCurso` vacío lleva a `popBackStack`.
5. **Documento con más de 500 movimientos.** Se guarda en varios lotes. Prueba en la Tarea 1: `enLotes`.

---

### Task 1: Modelo y lectura de la respuesta de Gemini

**Files:**
- Create: `app/src/main/java/com/gamora/feature/importar/Importacion.kt`
- Modify: `app/build.gradle.kts`: agregar `testImplementation("org.json:json:20240303")`
- Test: `app/src/test/java/com/gamora/ImportacionTest.kt`

**Interfaces:**
- Produces:
  - `data class MovimientoImportado(val fecha: Long, val descripcion: String, val montoCentavos: Long, val esIngreso: Boolean, val categoria: String, val seleccionado: Boolean = true, val repetido: Boolean = false)`
  - `data class ResultadoLectura(val banco: String, val periodo: String, val movimientos: List<MovimientoImportado>, val descartados: Int)`
  - `fun leerRespuestaGemini(json: String): ResultadoLectura`. Formato de entrada: `{banco, periodo, movimientos:[{fecha:"AAAA-MM-DD", descripcion, montoCentavos, esIngreso, categoria}]}`. La fecha se interpreta a las 12:00 locales. Lanza `IllegalArgumentException` si el JSON no se puede leer.
  - `fun marcarRepetidos(nuevos: List<MovimientoImportado>, existentes: List<GamoraMovement>): List<MovimientoImportado>`. Los repetidos quedan con `repetido = true` y `seleccionado = false`.
  - `fun MovimientoImportado.aMovimiento(): GamoraMovement`
  - `fun <T> List<T>.enLotes(tamano: Int = 500): List<List<T>>`

- [ ] **Step 1: Escribir pruebas que fallen** en `ImportacionTest`:
  - `leeMovimientosValidos`: 2 filas, montos 8400 y 1250000, `esIngreso` false y true. El banco "BBVA" y la fecha "2026-10-03" quedan en el `Calendar` local como día 3, mes 9, año 2026.
  - `descartaFilasInvalidas`: monto 0, monto -5, monto 100_000_000_001, descripción vacía, fecha "03/10" y monto como texto "1,234.50". Resultado: `movimientos.size == 0` y `descartados == 6`.
  - `categoriaDesconocidaPasaAOtros`: "Gasolina" en un gasto da "Otros", y "Comida" se conserva.
  - `jsonInvalidoLanzaError`: `assertThrows(IllegalArgumentException::class.java) { leerRespuestaGemini("no es json") }`.
  - `marcaRepetidoContraExistente`: un existente de -$30.00 el 7 de octubre y uno nuevo de -$30.00 el 8 de octubre dan `repetido = true` y `seleccionado = false`. Si el nuevo es el 10 de octubre, no es repetido.
  - `marcaRepetidoDentroDelDocumento`: dos filas iguales; solo la segunda es repetida.
  - `ingresoYGastoMismoMontoNoSonRepetidos`.
  - `enLotesParte1201En3`: los tamaños son `[500, 500, 201]`.
- [ ] **Step 2: Correr** `./gradlew :app:testDebugUnitTest --tests com.gamora.ImportacionTest`. Esperado: no compila o falla.
- [ ] **Step 3: Implementar** en `Importacion.kt` con `org.json.JSONObject` y `optString`/`optLong`. Un `montoCentavos` que no sea número entero es inválido. Usar `CATEGORIAS_INGRESO` y `CATEGORIAS_GASTO` de `MovementDialog.kt`.
- [ ] **Step 4: Correr las pruebas.** Esperado: PASS, y siguen pasando las 19 anteriores.

### Task 2: Nómina CFDI en XML

**Files:**
- Create: `app/src/main/java/com/gamora/feature/importar/NominaCfdi.kt`
- Test: `app/src/test/java/com/gamora/NominaCfdiTest.kt`

**Interfaces:**
- Consumes: `MovimientoImportado`, `ResultadoLectura` (Task 1)
- Produces:
  - `class NoEsNominaException(mensaje: String) : Exception(mensaje)`
  - `fun leerNominaCfdi(xml: ByteArray): ResultadoLectura`. Devuelve un único movimiento:
    - `esIngreso = true`, categoría `"Salario"` (debe existir en `CATEGORIAS_INGRESO`; si no, usar la de ingresos que corresponda a sueldo y anotarlo).
    - monto: `Total` del `cfdi:Comprobante` convertido con `centavosDe`.
    - fecha: `FechaPago` de `nomina12:Nomina` a las 12:00.
    - descripción: `"Nómina · " + cfdi:Emisor@Nombre`, o `"Nómina"` si no hay nombre.
    - banco: el nombre del emisor; periodo: `"FechaInicialPago al FechaFinalPago"`; `descartados = 0`.

- [ ] **Step 1: Pruebas que fallen:**
  - `leeNominaValida`: CFDI 4.0 mínimo en la prueba, con `Total="12500.00"`, `Emisor Nombre="EMPRESA SA"` y `FechaPago="2026-10-15"`. Resultado: 1 movimiento, 1250000 centavos, "Nómina · EMPRESA SA", día 15.
  - `facturaSinNominaLanzaNoEsNomina`: un CFDI sin el complemento `nomina12:Nomina`.
  - `xmlMalFormadoLanzaNoEsNomina`: `"<cfdi:Comprobante"`.
  - `nominaSinFechaPagoUsaFechaDelComprobante`: sin `FechaPago`, se toma `Comprobante@Fecha="2026-10-15T10:00:00"`.
- [ ] **Step 2: Correr y ver que fallan.**
- [ ] **Step 3: Implementar** con `DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }`. Buscar por nombre local (`getElementsByTagNameNS("*", "Nomina")`). Desactivar entidades externas con `setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)` dentro de un `try`. Envolver cualquier `Exception` del parser en `NoEsNominaException("Este XML no es un recibo de nómina.")`.
- [ ] **Step 4: Correr las pruebas.** Esperado: PASS.

### Task 3: Leer documentos (PDF con contraseña y Gemini)

**Files:**
- Create:
  - `app/src/main/java/com/gamora/feature/importar/DocumentoPdf.kt`
  - `app/src/main/java/com/gamora/feature/importar/LectorGemini.kt`
- Modify:
  - `app/build.gradle.kts`: `implementation("com.tom-roush:pdfbox-android:2.0.27.0")`
  - `app/src/main/java/com/gamora/GamoraApp.kt`: `PDFBoxResourceLoader.init(this)`
  - `app/src/main/java/com/gamora/feature/chatbot/ChatbotFragment.kt`: mover `MODELOS_GEMINI` a `internal val` en `feature/chatbot/Modelos.kt`, junto con `estaSaturado(error)` y `noDisponible(error)` como funciones `internal` top-level, para reusarlas.

**Interfaces:**
- Consumes: `leerRespuestaGemini` (Task 1)
- Produces:
  - `class ContrasenaRequeridaException : Exception()` y `class ContrasenaIncorrectaException : Exception()`
  - `fun prepararPdf(bytes: ByteArray, contrasena: CharArray?): ByteArray`:
    - Sin cifrado, devuelve los mismos bytes.
    - Cifrado y sin contraseña, lanza `ContrasenaRequeridaException`.
    - Contraseña incorrecta (`InvalidPasswordException`), lanza `ContrasenaIncorrectaException`.
    - Si se abre bien, `setAllSecurityToBeRemoved(true)` y `save` en un `ByteArrayOutputStream`.
    - En todos los casos: `contrasena?.fill('\u0000')` en un `finally` y `close()` del documento.
  - `suspend fun leerConGemini(bytes: ByteArray, mime: String): ResultadoLectura`:
    - Usa `Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(modelName, generationConfig = generationConfig { responseMimeType = "application/json"; responseSchema = <Schema.obj del formato de la Tarea 1> }, systemInstruction = content { text(PROMPT_LECTOR) })`.
    - Envía `content { inlineData(bytes, mime); text("Extrae los movimientos.") }`.
    - Mismo respaldo entre modelos y reintentos que el chat.
  - Lo que debe pedir `PROMPT_LECTOR`:
    - Todos los cargos y abonos del periodo, con montos en centavos y positivos, y `esIngreso` según si es abono.
    - Fecha `AAAA-MM-DD`, deduciendo el año del periodo.
    - Categoría elegida solo de la lista (interpolar `CATEGORIAS_INGRESO` y `CATEGORIAS_GASTO`).
    - Omitir saldos, totales, comisiones ya incluidas en otro renglón y **pagos a la propia tarjeta** en estados de tarjeta de crédito.
    - Si es un recibo de nómina en PDF o foto, un solo ingreso por el neto pagado.
    - Responder solo JSON.

- [ ] **Step 1: Agregar las dependencias y mover los helpers del chat.** Correr las pruebas: esperado PASS y el chat sigue compilando.
- [ ] **Step 2: Implementar `prepararPdf`.** No lleva prueba unitaria, porque PdfBox-Android necesita Android; se verifica en el teléfono en la Tarea 6.
- [ ] **Step 3: Implementar `leerConGemini`.** Compilar. Esperado: `assembleDebug` OK.

### Task 4: Formulario de movimiento en modo local

**Files:**
- Modify: `app/src/main/java/com/gamora/feature/transactions/MovementDialog.kt:63-66`

**Interfaces:**
- Produces: `fun Fragment.mostrarDialogoMovimiento(esIngreso: Boolean, existente: GamoraMovement? = null, alGuardarLocal: ((GamoraMovement) -> Unit)? = null)`.
  - Con `alGuardarLocal` distinto de `null`: no escribe en Firestore, no muestra "Repetir cada mes" ni "Eliminar", y al guardar llama `alGuardarLocal(movimientoEditado)` y cierra.
  - Las validaciones son las mismas.
  - El selector de fecha conserva `maxDate = ahora`.

- [ ] **Step 1: Implementar y compilar.** Los llamados actuales no cambian, porque el parámetro tiene valor por defecto.

### Task 5: Pantalla de revisión (destino nuevo)

**Files:**
- Create:
  - `app/src/main/java/com/gamora/feature/importar/ImportacionEnCurso.kt`
  - `app/src/main/java/com/gamora/feature/importar/ImportReviewFragment.kt`
  - `app/src/main/res/layout/fragment_import_review.xml`
- Modify: `app/src/main/res/navigation/gamora_nav_graph.xml`: `<fragment android:id="@+id/importReviewFragment" android:name="com.gamora.feature.importar.ImportReviewFragment" android:label="Revisar importación" />`
- Test: `app/src/test/java/com/gamora/ImportacionTest.kt` (resumen)

**Interfaces:**
- Consumes: Task 1 (`marcarRepetidos`, `aMovimiento`, `enLotes`), Task 4 (`alGuardarLocal`), `GamoraTransactionStore.movements`, `GamoraTransactionStore.nuevaReferencia()` y `GamoraTransactionStore.datos(mov)`.
- Produces:
  - `object ImportacionEnCurso { var resultado: ResultadoLectura?; fun limpiar() }`, solo en memoria.
  - `data class Resumen(val seleccionados: Int, val ingresosCentavos: Long, val gastosCentavos: Long)` y `fun resumen(lista: List<MovimientoImportado>): Resumen` en `Importacion.kt`.

- [ ] **Step 1: Prueba que falle:** `resumenSoloCuentaSeleccionados`, con 3 filas (un ingreso de 100000, un gasto de 8400 y un gasto de 3000 deseleccionado). Esperado: `Resumen(2, 100000, 8400)`.
- [ ] **Step 2: Implementar `resumen` y correr.** Esperado: PASS.
- [ ] **Step 3: Layout:**
  - Barra superior con flecha y "Revisa tus movimientos", más un subtítulo con banco, periodo y "N encontrados".
  - Fila de encabezado fija, con casilla y las columnas Fecha · Descripción · Categoría · Monto (pesos de columna 0.6 / 1 / 2.4 / 1.4 / 1.6).
  - `ScrollView` con `LinearLayout` para las filas.
  - Pie fijo con el texto de resumen y los botones "Cancelar" (`Widget.Gamora.Button.Outlined`) y "Guardar N movimientos" (`Widget.Gamora.Button`).
  - Insets como en los demás fragments.
- [ ] **Step 4: Fragment:**
  - En `onViewCreated`, si `ImportacionEnCurso.resultado` es `null`: Toast "La revisión se perdió; vuelve a importar el documento." y `findNavController().popBackStack()`.
  - Si no, aplica `marcarRepetidos` contra `GamoraTransactionStore.movements` y dibuja las filas:
    - Fondos alternos `gamora_surface` y `gamora_surface_variant`.
    - Monto con `dinero()` en `gamora_income` o `gamora_expense`.
    - "⚠ repetido" en la descripción, con `maxLines=1` y `ellipsize=end`.
  - Al tocar una fila: `mostrarDialogoMovimiento(esIngreso, mov.aMovimiento(), alGuardarLocal = { … })` reemplaza la fila.
  - La casilla del encabezado marca o desmarca todo.
  - "Guardar": desactiva el botón y escribe los seleccionados en lotes con `FirebaseFirestore.batch()`, usando `nuevaReferencia()` y `datos()`. Al terminar todos: `ImportacionEnCurso.limpiar()`, Toast "Se registraron N movimientos" y `popBackStack()`. Si hay error, reactiva el botón y muestra un Toast.
  - Atrás o "Cancelar" con algo seleccionado: diálogo "¿Descartar estos movimientos?". Al confirmar, `limpiar()` y `popBackStack()`. Usar `OnBackPressedCallback`.
  - La barra inferior debe ocultarse en este destino si `HomeActivity` la controla por destino. Si no, se deja visible y se anota.
- [ ] **Step 5: Compilar y correr las pruebas.** Esperado: PASS.

### Task 6: Botón, selector, contraseña y lectura

**Files:**
- Create: `app/src/main/java/com/gamora/feature/importar/ImportarDocumento.kt`
- Modify:
  - `app/src/main/res/layout/fragment_transactions.xml`: botón `btnImportar`, "📄 Importar estado de cuenta", `Widget.Gamora.Button.Outlined`, debajo de "+ Agregar movimiento", con `marginTop` de 12dp.
  - `app/src/main/java/com/gamora/feature/transactions/TransactionsFragment.kt`: conectar el botón.

**Interfaces:**
- Consumes: Tasks 1, 2, 3 y 5 (`ImportacionEnCurso`, `R.id.importReviewFragment`)
- Produces: `class ImportadorDocumentos(fragment: Fragment)`. Se crea como propiedad del fragment, para que `registerForActivityResult` quede registrado antes de `STARTED`. Expone `fun iniciar()`.

- [ ] **Step 1: Implementar `iniciar()`:**
  - Si no hay consentimiento, mostrar el diálogo (Aceptar o Cancelar) y guardar la respuesta.
  - Abrir `OpenDocument` con `arrayOf("application/pdf", "image/*", "text/xml", "application/xml")`.
- [ ] **Step 2: Al recibir el `Uri`:**
  - Leer el tamaño (`OpenableColumns.SIZE`) y rechazar más de 15 MB con un Toast.
  - Leer los bytes con `contentResolver.openInputStream` en `Dispatchers.IO`.
  - Tipo con `contentResolver.getType(uri)`. Si es XML, `leerNominaCfdi`.
  - Si es PDF, `prepararPdf(bytes, null)`:
    - Con `ContrasenaRequeridaException`, mostrar un diálogo con `TextInputEditText` de tipo `TYPE_TEXT_VARIATION_PASSWORD` y reintentar con `campo.text.toString().toCharArray()`. Después, limpiar el campo con `setText("")`.
    - Con `ContrasenaIncorrectaException`, mostrar el mismo diálogo con el error "Contraseña incorrecta".
    - Después, `leerConGemini(pdf, "application/pdf")`.
  - Si es imagen: `leerConGemini(bytes, mime)`.
  - Durante la lectura, un diálogo no cancelable con `CircularProgressIndicator`, "Leyendo tu documento…" y el botón "Cancelar", que cancela el `Job`.
  - Si el resultado no tiene movimientos: "No encontramos movimientos en este documento".
  - Si sí tiene: `ImportacionEnCurso.resultado = r` y `findNavController().navigate(R.id.importReviewFragment)`.
  - Si `descartados > 0`, mostrar el Toast "N renglones no se pudieron leer".
  - Errores de Gemini o de red: mostrar un Toast con un mensaje amable y registrar en `Log` solo la clase del error.
- [ ] **Step 2b: Instalar en el teléfono (con permiso del usuario) y probar:**
  - un PDF de estado de cuenta sin contraseña
  - uno con contraseña, incluyendo la contraseña incorrecta y cancelar
  - una foto
  - un XML de nómina
  - un XML que no es de nómina
  - un archivo de más de 15 MB
  - Revisar en la tabla que los repetidos salen desmarcados y que, al guardar, aparecen en Movimientos.

### Task 7: Política de privacidad y términos

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (`politica_privacidad`, `terminos_condiciones`)

- [ ] **Step 1: Política:**
  - En la sección 2, agregar "Documentos que importas (estados de cuenta y recibos de nómina)".
  - Nueva subsección en la 4: el documento elegido, o su copia sin contraseña, se envía a Gemini solo para extraer los movimientos. No se guarda el archivo ni la contraseña. Puede contener número de cuenta, domicilio o RFC. Solo se guardan los movimientos que confirmes.
  - Nota: los XML de nómina se leen en el teléfono y no se envían a Gemini.
- [ ] **Step 2: Términos:**
  - En la sección 2, agregar "importar movimientos desde estados de cuenta y recibos de nómina".
  - En la sección 5 (o una nueva): la lectura la hace una IA, puede omitir o equivocar movimientos, y debes revisarlos antes de guardar.
- [ ] **Step 3: Correr el comando de verificación global.** Esperado: pruebas en PASS y APK generado.
