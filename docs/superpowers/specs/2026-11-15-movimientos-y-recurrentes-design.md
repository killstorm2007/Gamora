# Movimientos y movimientos recurrentes — diseño

Fecha: 2026-11-15 · Estado: implementado

## Por qué

Los movimientos (ingresos y gastos) son la base de todo lo demás: inicio, análisis, presupuestos,
metas, consejos, avisos, asistente y exportación leen la misma lista. Los recurrentes (renta, salario,
suscripciones) evitan registrar a mano lo que se repite cada mes.

## Objetivos

1. Registrar, editar y borrar movimientos con descripción, categoría, monto, tipo y fecha.
2. Buscar y filtrar (todos / gastos / ingresos, por categoría, sin importar acentos).
3. Repetir un movimiento cada mes, editar lo que se registrará y dejar de repetirlo.
4. Que la lista se vea al instante, también sin internet, sin gastar la cuota gratuita de Firestore.

## No es parte de esto

- Cuentas bancarias, tarjetas o saldos por cuenta.
- Movimientos en varias monedas a la vez (toda la cuenta usa una moneda; ver perfil y moneda).
- Recurrencias que no sean mensuales (semanal, quincenal).

## Sería un fracaso si…

- Un recurrente se registra dos veces el mismo mes o se salta un mes.
- Un error de carga se ve como una lista vacía y la persona cree que perdió sus datos.
- Editar un retiro de meta (ingreso en "Ahorro") le cambia la categoría.

## Diseño

### Modelo y almacenamiento
- `GamoraMovement` (`feature/transactions/GamoraMovement.kt`): `description`, `category: Categoria`,
  `amountCents: Long`, `isIncome`, `timestamp`, `id`.
- Firestore `users/{uid}/movimientos/{id}`: `descripcion`, `categoria` (clave), `montoCentavos`,
  `esIngreso`, `fecha` (milisegundos). Montos siempre en centavos (`Long`), nunca `Double`.
- Reglas: descripción 1–200, categoría en la lista de claves, monto 1 a 100,000,000,000 centavos.

### Sincronización (`GamoraTransactionStore`)
- **En vivo:** solo el mes actual y los 12 anteriores (`MESES_EN_VIVO = 13`, `inicioEnVivo()`), con
  `addSnapshotListener`. Es lo que usan inicio, análisis y presupuestos.
- **Historial:** lo anterior se lee del caché del teléfono (`Source.CACHE`) y del servidor como mucho
  una vez por semana (marca `servidor_{uid}` en `gamora_historial`). Así cada apertura no vuelve a
  cobrar todos los documentos (la cuota del plan gratuito es de 50,000 lecturas al día por proyecto).
- `registrarLocal()` refleja al instante lo que se escribe desde el teléfono en el historial (que no
  se escucha en vivo): ediciones, borrados e importaciones de estados de cuenta viejos.
- Errores del listener → `EstadoDatos.reportar("movimientos", …)` → aviso con "Reintentar" en
  `HomeActivity` (ver sincronización y errores).

### Pantalla de movimientos
- `TransactionsFragment` con búsqueda (`FiltroMovimientos.kt`, sin acentos ni mayúsculas), filtro por
  tipo y categoría, y botón para importar estados de cuenta.
- `MovementDialog.kt`: formulario de alta/edición con fecha (`DatePickerDialog`), categoría según el
  tipo y opción "Repetir cada mes". Si el movimiento tiene una categoría que no es del tipo (un retiro
  de meta: ingreso en "Ahorro"), se agrega a la lista para no perderla al editar.
- `MovementRow.kt`: fila con descripción para TalkBack ("Gasto de $120.00 en Comida…").

### Recurrentes (`Recurrentes.kt`)
- `users/{uid}/recurrentes/{id}`: los mismos campos más `dia` (1–28) y `proximaFecha`.
- `procesar()` corre al abrir la app y en la revisión diaria de avisos: registra las fechas
  pendientes (máximo 24) con id `{recurrente}_{AAAAMM}`, así repetirlo no duplica nada, y avanza
  `proximaFecha` en el mismo batch.
- Editar cambia solo lo que se registrará de ahora en adelante (`cambiarDia` conserva el mes). El tipo
  no se cambia: para eso se deja de repetir y se crea otro.

## Pruebas

`RecurrentesTest`, `BusquedaCategoriaTest`, `CentavosTest`, `VentanaEnVivoTest`,
`AhorroAcumuladoTest`.
