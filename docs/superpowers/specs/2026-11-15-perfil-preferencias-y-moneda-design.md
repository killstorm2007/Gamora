# Perfil, preferencias, moneda, avisos y exportación — diseño

Fecha: 2026-11-15 · Estado: implementado

## Por qué

Perfil concentra lo que la persona ajusta una vez y casi no vuelve a tocar: sus datos, su moneda y
presupuesto, apariencia, idioma, avisos, privacidad y la salida de sus datos (exportar o borrar).

## Objetivos

1. Editar información personal y preferencias financieras (moneda, ingreso, presupuesto mensual,
   día de inicio del periodo, objetivo de ahorro).
2. Cambiar de moneda convirtiendo los montos con un tipo de cambio, o solo el símbolo.
3. Tema claro/oscuro/sistema, idioma (español, inglés o el del sistema).
4. Avisos de presupuesto, recordatorios de metas y resumen semanal.
5. Exportar movimientos a CSV o PDF.
6. Bienvenida de tres pasos para cuentas nuevas.

## No es parte de esto

- Tipo de cambio automático desde un servicio externo (se escribe a mano).
- Respaldos automáticos a la nube de la persona.

## Sería un fracaso si…

- Cambiar de moneda deja montos a medias (unos convertidos y otros no).
- Una exportación se manda a un servidor de Gamora.

## Diseño

### Organización del código (`feature/profile/`)
`ProfileFragment` (resumen, tema y utilidades compartidas) y un archivo por tema con funciones de
extensión: `PerfilCuenta` (información personal y eliminar cuenta), `PerfilPreferencias`
(preferencias financieras y moneda), `PerfilPrivacidad` (bloqueo, privacidad, IA, notificaciones),
`PerfilRecurrentes`, `PerfilAyuda` (idioma, documentos legales, ayuda, cerrar sesión) y
`CambioDeMoneda` (lógica de conversión, sin interfaz).

### Cambio de moneda (`CambioDeMoneda.kt`)
- Monedas: MXN, USD, EUR. El tipo de cambio se pide como se publica: "1 [fuerte] = X [débil]"
  (`monedasDelTipoDeCambio`); `factorDeConversion` da el multiplicador.
- `convertirTodo()` lee del servidor movimientos, recurrentes, metas y presupuestos y los actualiza
  en batches de 500 (con menos de 500 cambios, todo o nada). También se convierten los valores de
  Preferencias financieras. Redondeo al centavo; montos entre 1 centavo y el máximo de las reglas
  (lo ahorrado en metas puede quedar en 0).
- Necesita internet. Después se vuelve a leer el historial. El chat con el asistente no se convierte.
- "Solo el símbolo" para cuando se eligió mal la moneda al registrarse.

### Idioma
AppCompat per-app languages (`setApplicationLocales`, `locales_config.xml`, servicio con
`autoStoreLocales`). Todo texto en `strings.xml` y `values-en/strings.xml`.

### Avisos (`core/notifications/Avisos.kt`)
- WorkManager: revisión diaria con red y revisión inmediata al salir de la app.
- Presupuesto mensual y por categoría al 80 % y al 100 %; metas a 7 días y a 1 día de la fecha
  límite; resumen semanal. Cada aviso se manda una sola vez (se recuerda por clave).
- La revisión diaria también registra los recurrentes aunque no se abra la app.

### Exportar (`feature/exportar/Exportar.kt`)
- Periodo (este mes, 3 meses, este año, todo) y formato. CSV RFC 4180 con BOM para Excel, fecha ISO
  y gastos en negativo; PDF carta con resumen, gasto por categoría y lista.
- Se genera en la caché privada (`cacheDir/exportes`, se limpia en cada exportación) y se comparte
  con `FileProvider` (`${applicationId}.exportes`). Nada pasa por servidores de Gamora.

### Bienvenida (`feature/onboarding/Bienvenida.kt`)
Solo para cuentas sin preferencias ni movimientos: moneda, presupuesto mensual y primer movimiento.
Después se pide el permiso de notificaciones (Android 13+), una sola vez.

## Pruebas

`CambioDeMonedaTest`, `MonedaTest`, `ExportarTest`, `AvisosTest`, `PluralesTest`.
