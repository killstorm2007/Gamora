# Metas de ahorro — diseño

Fecha: 2026-11-15 · Estado: implementado

## Por qué

Ahorrar para algo concreto (viaje, fondo de emergencia) motiva más que un número abstracto. La
persona aparta dinero para cada meta, ve su avance y lo puede sacar cuando lo necesite.

## Objetivos

1. Crear metas con nombre, objetivo y fecha límite opcional.
2. Aportar, con la opción de descontarlo del saldo (gasto en "Ahorro").
3. Retirar (como mucho lo ahorrado), con la opción de regresarlo al saldo (ingreso en "Ahorro").
4. Al eliminar una meta con dinero, elegir si lo ahorrado regresa al saldo.
5. Recordatorios cuando la fecha límite se acerca (ver perfil: notificaciones).

## No es parte de esto

- Metas compartidas entre personas.
- Ligar una meta a una cuenta bancaria real o mover dinero de verdad.

## Sería un fracaso si…

- Dos teléfonos aportan a la vez y se pierde una aportación.
- Se guarda la aportación sin su gasto (o al revés).
- Lo ahorrado queda en negativo.

## Diseño

### Datos
- `users/{uid}/metas/{id}`: `nombre`, `objetivoCentavos`, `ahorradoCentavos`, `fechaLimite`, `fechaCreacion`.
- `GamoraGoalStore` (`SavingsGoal.kt`) escucha en vivo; errores → `EstadoDatos.reportar("metas", …)`.

### Operaciones
- **Aportar:** `FieldValue.increment(monto)` y, si se descuenta, un gasto en "Ahorro"
  ("Aportación a «meta»"), los dos en un batch.
- **Retirar:** `increment(-monto)` y, si se regresa al saldo, un ingreso en "Ahorro"
  ("Retiro de «meta»"), en un batch. La pantalla no deja retirar más de lo ahorrado.
- **Eliminar:** borra la meta y, si se eligió, registra lo ahorrado como ingreso, en un batch.
- La tarjeta muestra "Aportar" (si no está cumplida), "Retirar" (si tiene dinero, aunque esté
  cumplida) y "Eliminar".

### Reglas de Firestore
- Crear: `ahorradoCentavos == 0`.
- Actualizar: solo `ahorradoCentavos` y `objetivoCentavos` (este último al cambiar de moneda);
  ahorrado entre 0 y 100,000,000,000; objetivo mayor que 0. Nombre y fechas no se editan.

### Cómo cuenta en las cifras
Ver análisis: "Dinero de metas". El saldo sí cambia (es dinero apartado o devuelto); el ahorro
acumulado y los consejos no.
