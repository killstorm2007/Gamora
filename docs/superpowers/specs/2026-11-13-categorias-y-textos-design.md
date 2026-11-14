# Categorías como claves y textos en recursos — diseño

Fecha: 2026-11-13 · Estado: aprobado

## Por qué

Antes de pasar Gamora a multiplataforma y agregar un selector de idioma, los datos no deben depender
del idioma y todos los textos deben estar en un solo lugar. Hoy las categorías se guardan en Firestore
con su nombre en español ("Comida") y hay ~250 textos fijos repartidos en pantallas y código. Si el
selector llegara así, los datos de cada persona quedarían en español y cada plataforma tendría que
copiar la lista de nombres.

## Objetivos

1. Una sola definición de categorías (clave, nombre traducible, emoji, si aplica a ingreso/gasto).
2. Firestore guarda la **clave** (`comida`), nunca el nombre, en movimientos, recurrentes y presupuestos.
3. Sin migración: los datos actuales en Firestore son de prueba y se borrarán antes de empezar a usar
   la nueva versión (decisión del usuario).
4. Todos los textos que ve la persona salen de `strings.xml` (con `plurals` y argumentos donde haga falta).

## No es parte de esto

- Traducir al inglés u otro idioma, ni la pantalla del selector (se hará en la versión multiplataforma).
- Traducir la política de privacidad y los términos (es trabajo legal).
- Cambiar el diseño visual: las pantallas deben verse igual que hoy.
- Categorías personalizadas por usuario.

## Sería un fracaso si…

- Una alerta de presupuesto, el ahorro acumulado o las gráficas cuentan distinto que antes.
- Algo se ve diferente o un texto queda vacío o con `%1$s` visible.

## Restricciones

- Reglas de Firestore actuales: `categoria` es string de 1–40 caracteres; presupuestos es un mapa de
  hasta 20 entradas. Las claves cumplen; no hace falta cambiar reglas.

## Enfoques considerados

- A. Entender ambos + migración automática. Al leer, "Comida" y `comida` son lo mismo; al
  iniciar sesión, una vez, se reescriben los documentos viejos. La versión multiplataforma ya solo
  verá claves.
- B. Solo entender ambos. Más simple ahora, pero cada plataforma futura cargaría la compatibilidad.
- **C. Solo claves, sin migración (elegido).** Se eligió A primero; luego el usuario aclaró que los datos
  actuales son de prueba y los borrará, así que no hace falta migrar ni cargar compatibilidad.

## Diseño

### Modelo
`enum class Categoria(val clave: String, @StringRes val nombre: Int, val emoji: String, val ingreso: Boolean, val gasto: Boolean)`
en `core/`:

| clave | nombre (es) | emoji | ingreso | gasto |
|---|---|---|---|---|
| salario | Salario | 💼 | ✓ | |
| ventas | Ventas | (el actual) | ✓ | |
| negocio | Negocio | (el actual) | ✓ | |
| inversiones | Inversiones | 📈 | ✓ | |
| regalo | Regalo | 🎁 | ✓ | |
| comida | Comida | 🍽️ | | ✓ |
| transporte | Transporte | 🚗 | | ✓ |
| vivienda | Vivienda | 🏠 | | ✓ |
| servicios | Servicios | 💡 | | ✓ |
| salud | Salud | 🩺 | | ✓ |
| educacion | Educación | (el actual) | | ✓ |
| entretenimiento | Entretenimiento | 🎬 | | ✓ |
| compras | Compras | 🛍️ | | ✓ |
| ahorro | Ahorro | 🐷 | | ✓ |
| otros | Otros | (el actual) | ✓ | ✓ |

Mismo orden que las listas actuales `CATEGORIAS_INGRESO` / `CATEGORIAS_GASTO`.

`Categoria.de(texto: String?): Categoria` acepta la clave, o el nombre en español sin importar
mayúsculas ni acentos (solo para respuestas de Gemini, que podría devolver el nombre); cualquier otra
cosa → `OTROS`.

### Uso en la app
- `GamoraMovement.category` y `Recurrente.categoria` pasan a ser `Categoria`; al leer de Firestore se usa
  `Categoria.de(...)`, al escribir `categoria.clave`.
- Lista del diálogo, filtros, emojis, gráficas, consejos (`ENTRETENIMIENTO`, `COMPRAS`), `esAhorro`,
  metas (`AHORRO`), lector de estados, CFDI y revisión de importación usan el enum.
- El buscador de movimientos busca en el **nombre mostrado** de la categoría.
- Avisos (WorkManager) leen la clave con `Categoria.de` y muestran el nombre traducido.
- Presupuestos: el mapa en Firestore usa claves; en memoria `Map<Categoria, Long>`.
- Gemini (lector y chatbot): el prompt lista las categorías como `clave (Nombre)` y la respuesta se
  interpreta con `Categoria.de`, así sirve si devuelve la clave o el nombre.

### Textos
- Textos de layouts (`HardcodedText`) y de código (`SetTextI18n`, toasts, diálogos, avisos,
  notificaciones) pasan a `res/values/strings.xml`, agrupados por pantalla con prefijo
  (`movimientos_…`, `importar_…`).
- Conteos con `plurals` ("1 meta activa" / "3 metas activas"); valores con argumentos (`%1$s`).
- Prompts a Gemini y palabras clave del lector de estados **no** se mueven: no son texto que se muestre.
- Al final, lint sin advertencias `HardcodedText` ni `SetTextI18n`.

## Pruebas
- `Categoria.de`: claves, nombre en español, sin acento, mayúsculas, desconocido → `OTROS`.
- Las pruebas existentes (ahorro acumulado, presupuestos, consejos, lector) se ajustan al enum y deben
  seguir dando los mismos resultados.
- En el teléfono (con los datos de prueba borrados): crear movimientos, recurrentes, metas y presupuestos;
  las pantallas se ven igual que antes y Firestore guarda claves.

## Decisiones del implementador
- Nombres exactos de recursos y agrupación dentro de `strings.xml`.
- Orden de los pasos y cómo dividir el trabajo en commits.
