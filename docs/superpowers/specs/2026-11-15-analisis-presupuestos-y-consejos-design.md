# Análisis, presupuestos y consejos — diseño

Fecha: 2026-11-15 · Estado: implementado

## Por qué

Registrar gastos solo sirve si la persona entiende a dónde se va su dinero y recibe ayuda concreta
para gastar menos. La pantalla de análisis junta gráficas, límites por categoría y consejos.

## Objetivos

1. Ver en qué categorías se gasta (dona) e ingresos contra gastos de los últimos 6 meses (barras).
2. Poner un límite mensual por categoría y ver el avance (barra con "te quedan $X" o "te pasaste por $X").
3. Recibir consejos calculados con los datos reales, sin depender de la IA.
4. Recibir avisos cuando un presupuesto llega al 80 % y al 100 % (ver perfil: notificaciones).

## No es parte de esto

- Presupuestos de periodos distintos al mensual.
- Gráficas interactivas (tocar una porción) o exportarlas como imagen.

## Sería un fracaso si…

- Aportar o retirar de una meta cuenta como gasto o como ingreso nuevo (inflaría o bajaría el ahorro).
- El diálogo de presupuestos esconde el botón Guardar en pantallas chicas.
- Guardar presupuestos sin internet deja el diálogo esperando para siempre.

## Diseño

### Gráficas (`Graficas.kt`)
- `DonaView` y `BarrasMensualesView` son vistas propias dibujadas en `onDraw` (sin librerías), con
  colores por categoría y sin crear objetos en cada cuadro.
- Meses sin movimientos aparecen en cero.

### Presupuestos por categoría (`feature/budget/Presupuestos.kt`)
- Un documento `users/{uid}/preferencias/presupuestos` → `{ categorias: { "comida": 300000, … } }`
  con claves de `Categoria`; las reglas solo aceptan claves de la lista.
- `PresupuestoStore` escucha en vivo; `avancePresupuestos()` ordena primero las categorías más cerca
  de pasarse. El periodo empieza el día elegido en Preferencias financieras (`inicioPeriodo`).
- El diálogo usa `contenidoDesplazable()`: la explicación y los 12 campos se desplazan juntos y los
  botones quedan siempre a la vista. Guardar usa `alGuardar()`: sin internet se da por hecho y se
  sube al reconectar.

### Consejos (`ConsejosAhorro.kt`)
Reglas fijas sobre el mes en curso: balance y tasa de ahorro, categoría con más gasto, gastos
prescindibles, avance del presupuesto mensual y cuánto apartar al mes para cada meta con fecha.

### Dinero de metas
`esAhorro` = categoría "Ahorro" en cualquier dirección. Aportar (gasto) y retirar (ingreso) solo
mueven dinero entre el saldo y las metas: no cuentan como consumo, ni como ingreso, ni en el ahorro
acumulado (`ahorroAcumulado()`), pero sí en el balance general.

## Pruebas

`ConsejosAhorroTest`, `PresupuestosTest`, `AhorroAcumuladoTest`, `CambioDeMonedaTest` (retiros).
