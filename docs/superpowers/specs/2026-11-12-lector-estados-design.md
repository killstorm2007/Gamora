# Lector de estados de cuenta y recibos de nómina

Fecha: 12 de noviembre de 2026 · Estado: borrador para aprobación

## Para qué

Registrar movimientos a mano es lo que más cansa en una app de finanzas. Gamora debe poder leer el estado de cuenta de cualquier banco y los recibos de nómina, y proponer los movimientos para que la persona solo los revise y confirme.

Primero se construye **sin restricción de suscripción**, para comprobar que funciona con documentos reales. Después se convierte en función Premium (otro diseño).

## Objetivos

- Importar desde un **PDF** (estado de cuenta o recibo de nómina), una **foto** (JPG/PNG) o un **XML de CFDI de nómina**.
- PDF con contraseña: pedirla, abrir el archivo en el teléfono y continuar.
- Mostrar una **pantalla de revisión** antes de guardar: casillas, edición con el formulario existente y aviso de posibles repetidos.
- Guardar solo lo que la persona confirma, en `users/{uid}/movimientos`, con el formato y las reglas actuales.

## No objetivos (por ahora)

- Restricción Premium, Cloud Functions o plan Blaze.
- CSV o Excel de bancos, y conexión directa con bancos (Belvo y similares).
- Varios archivos a la vez.
- Recordar de qué banco era cada movimiento o guardar el documento.

## Anti-objetivos (sería un fracaso aunque "funcione")

- Que la **contraseña**, el **archivo original** o la **copia sin contraseña** queden guardados en cualquier lugar: Firestore, archivos del teléfono, caché, logs o historial del chat.
- Guardar movimientos sin que la persona los vea y confirme.
- Duplicar movimientos que ya existían sin avisar.
- Pedir permisos de almacenamiento o de archivos.

## Restricciones

- Android, Kotlin y pantallas XML, con el diseño actual. Mantener colores, tarjetas y estilos existentes.
- Gemini por Firebase AI Logic, igual que el chat (`firebase-ai`), con la misma lista de modelos y respaldo.
- Reglas de Firestore sin cambios: cada movimiento ya se valida con `movimientoValido`.
- Tamaño máximo de archivo: 15 MB.

## Enfoques considerados

1. **Gemini desde la app + XML leído en el teléfono — elegido.** Funciona hoy sin infraestructura nueva y sirve para cualquier banco. Contra: hasta que exista la suscripción, cualquiera con la app puede usarlo gratis. Se resuelve después, moviendo la llamada a una Cloud Function.
2. Gemini desde una Cloud Function desde el inicio. Queda listo para Premium, pero requiere Blaze antes de poder probar.
3. Un lector propio para cada banco, sin IA. Es frágil y caro de mantener.

## Cómo funciona

### Entrada
- Botón nuevo con borde **"📄 Importar estado de cuenta"** en Movimientos, debajo de "+ Agregar movimiento".
- Abre el selector de documentos del sistema (`ActivityResultContracts.OpenDocument`) con los tipos `application/pdf`, `image/*` y `text/xml`/`application/xml`. No pide permisos.
- La primera vez muestra un aviso de consentimiento: "Tu documento se enviará a Gemini (Google) solo para leerlo. No se guarda." con los botones Aceptar y Cancelar. La aceptación se recuerda en el teléfono.

### Lectura
- Se lee el archivo a memoria (`ByteArray`). Si pasa de 15 MB, se muestra un error.
- **XML:** si es CFDI de nómina (`cfdi:Comprobante` con complemento `nomina12:Nomina`), se lee en el teléfono:
  - Un ingreso con categoría "Salario", el neto pagado (`Total` del comprobante), la fecha de pago (`FechaPago`) y como descripción "Nómina · {Nombre del emisor}".
  - Si el XML no es de nómina, se muestra un error claro.
- **PDF:**
  - Se intenta abrir con PdfBox-Android.
  - Si está cifrado, se muestra un diálogo de contraseña con el campo oculto. Si es incorrecta, se vuelve a pedir con un mensaje.
  - Ya abierto, se quita la seguridad y se guarda la copia en un `ByteArrayOutputStream`. La contraseña se borra de la variable al terminar.
- **PDF sin cifrar o imagen:** se envía a Gemini con `inlineData` y un prompt que pide JSON con la forma:
  `{ banco, periodo, movimientos: [{ fecha "AAAA-MM-DD", descripcion, montoCentavos, esIngreso, categoria }] }`
  La categoría debe ser una de `CATEGORIAS_INGRESO` o `CATEGORIAS_GASTO`; si no corresponde a ninguna, se usa "Otros". Se usa `responseMimeType = application/json` y un esquema de respuesta.
- Se valida cada fila con los mismos límites que las reglas: descripción de 1 a 200 caracteres, monto mayor a 0 y menor o igual a 100,000,000,000 centavos, y fecha válida. Las filas inválidas se descartan y se avisa cuántas fueron.
- Mientras tanto se muestra "Leyendo tu documento…", que se puede cancelar.

### Revisión (decidido contigo)
- **Destino nuevo de la navegación** (`ImportReviewFragment` en `gamora_nav_graph`), sin pestaña en la barra inferior, con flecha para regresar. Los datos leídos se pasan en memoria (un `ViewModel` compartido con la actividad u objeto en memoria), nunca por Firestore ni archivos.
- Encabezado: "Revisa tus movimientos", el banco, el periodo y cuántos se encontraron.
- **Tabla** con columnas ✓ · Fecha · Descripción · Categoría · Monto:
  - El encabezado de la tabla queda fijo al desplazarte y su casilla marca o desmarca todos.
  - Las filas alternan un fondo ligeramente distinto.
  - Los montos van alineados a la derecha, en verde los ingresos y en rojo los gastos.
  - Las descripciones largas se cortan con "…".
- Al tocar una fila se abre el formulario de movimiento existente en modo edición **local**: los cambios se quedan en la tabla y no se guardan en Firestore.
- **Posibles repetidos:** mismo tipo, mismo monto y fecha a ±2 días de un movimiento que ya existe, o repetido dentro del mismo documento. Esas filas aparecen desmarcadas con "⚠ repetido".
- **Pie fijo:** totales de lo seleccionado ("Seleccionados: N · Ingresos +$X · Gastos -$Y") y los botones "Cancelar" y "Guardar N movimientos".
- Al salir con la flecha o con Cancelar, si hay algo pendiente: "¿Descartar estos movimientos?".
- Se guarda en lotes (`WriteBatch` de hasta 500). Al terminar se muestra un aviso con el total y se regresa a Movimientos.

### Privacidad y legal
- **Política de privacidad:** el documento elegido (y la versión sin contraseña) se envía a Gemini solo para extraer los movimientos. No se guarda el archivo ni la contraseña. Puede contener datos sensibles como número de cuenta, domicilio o RFC.
- **Términos:** la lectura la hace una IA y puede equivocarse, y la persona debe revisar los movimientos antes de guardarlos.
- La versión de las políticas se mantiene en 1.0, porque la app aún está en beta.

## Decisiones mías (puedes cambiarlas)

- Los movimientos importados no se marcan como importados: quedan igual que uno manual.
- La nómina en XML registra solo el neto, no el desglose de percepciones y deducciones.
- El consentimiento para enviar el documento se recuerda solo en el teléfono, no en Firestore.
- Si Gemini no encuentra movimientos, se muestra "No encontramos movimientos en este documento" y no se abre la revisión.
- Las fotos se envían tal cual; no se recortan ni se mejoran.
- En estados de **tarjeta de crédito**, los pagos a la propia tarjeta se omiten: no son un ingreso, solo mueven dinero entre tus cuentas.

## Queda a criterio de quien construye

- La redacción exacta del prompt y el esquema.
- Las pruebas unitarias de: lectura del CFDI, validación de filas, detección de repetidos y conversión de la respuesta JSON.
