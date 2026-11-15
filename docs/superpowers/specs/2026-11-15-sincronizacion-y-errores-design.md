# Sincronización, conexión y errores — diseño

Fecha: 2026-11-15 · Estado: implementado

## Por qué

Una revisión de la app "como si no la conociéramos" encontró que, con usuarios reales, podía fallar
en cosas que no se ven en pruebas: la cuota gratuita de Firestore se agotaba rápido, un error de
carga se veía como "no tienes datos", algunas pantallas se colgaban sin internet y los errores
salían en inglés técnico.

## Objetivos

1. Gastar pocas lecturas de Firestore (plan gratuito: 50,000 lecturas al día por proyecto).
2. Nunca mostrar una lista vacía cuando en realidad hubo un error.
3. Ninguna pantalla espera para siempre por estar sin internet.
4. Mensajes de error claros, en el idioma de la app.

## No es parte de esto

- Servidor propio o Cloud Functions (requieren el plan Blaze).
- Modo sin conexión para iniciar sesión o leer estados de cuenta con Gemini.

## Diseño

### Menos lecturas
Movimientos: solo 13 meses en vivo; el historial desde el caché y del servidor una vez por semana
(ver movimientos). Chat: últimos 60 mensajes. Metas y presupuestos son pocos documentos.

### Estado de los datos (`core/Conexion.kt`)
- `Conexion.enLinea`: `ConnectivityManager` con `registerDefaultNetworkCallback`, iniciado en `GamoraApp`.
- `EstadoDatos`: cada store reporta su error (`movimientos`, `metas`, `presupuestos`) o lo marca resuelto.
- `HomeActivity` combina ambos y muestra un aviso abajo (sobre la barra de navegación):
  error → mensaje + "Reintentar" (vuelve a conectar los listeners con `forzar = true`);
  sin internet → "Lo que registres se guarda en el teléfono…" + "Entendido".

### Escrituras sin esperar al servidor (`core/Errores.kt`)
- Firestore aplica los cambios al instante en el teléfono y los sube al reconectar, pero la
  confirmación del servidor puede no llegar nunca sin red.
- `Task.alGuardar()`: sin conexión da la escritura por hecha enseguida; con conexión espera como
  mucho 8 segundos. Se usa en presupuestos, perfil, preferencias y consentimientos. Movimientos,
  metas e importaciones ya no esperaban.

### Mensajes de error
- `mensajeDeError(error, porDefecto)` clasifica (`tipoDeError`): sin conexión, sesión, demanda alta
  (cuota), demasiados intentos, credenciales, cuenta no disponible, contraseña débil, volver a entrar.
  El detalle técnico va al log, nunca a la pantalla.
- `ErrorParaUsuario`: error cuyo mensaje ya está escrito para la persona.

## Riesgos que quedan

- Gemini comparte una sola cuota gratuita para todo el proyecto (ver asistente).
- Conversiones de moneda con más de 500 cambios van en varios batches: si falla a la mitad, quedan a medias.

## Pruebas

`VentanaEnVivoTest`. Probado en teléfono: modo avión, presupuesto sin internet, aviso de conexión.
