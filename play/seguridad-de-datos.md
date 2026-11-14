# Play Console › Seguridad de los datos — respuestas para Gamora

Borrador basado en el código y en el aviso de privacidad (versión con Crashlytics, noviembre 2026).
Revísalo antes de enviarlo: si la app cambia (p. ej. la suscripción con RevenueCat), hay que actualizarlo.

## 1. Recopilación y seguridad

| Pregunta | Respuesta |
|---|---|
| ¿Tu app recopila o comparte alguno de los tipos de datos del usuario requeridos? | **Sí** |
| ¿Todos los datos recopilados se encriptan en tránsito? | **Sí** (Firebase usa TLS) |
| ¿Qué métodos de creación de cuenta admite tu app? | **Nombre de usuario y contraseña** (correo) y **OAuth** (Google) |
| ¿Ofreces una forma de solicitar que se borren los datos? | **Sí** — en la app (Perfil › Eliminar mis datos) y en la web |
| URL para borrar la cuenta | `https://killstorm2007.github.io/Gamora/eliminar-cuenta.html` |
| ¿Se pueden borrar datos sin borrar la cuenta? | **Sí**: movimientos, metas, recurrentes, presupuestos y el historial del asistente se borran uno por uno en la app. Si prefieres no comprometerte a esto, responde **No**. |

## 2. Datos compartidos

**Ninguno.** Google (Firebase, Gemini, Crashlytics, Analytics) actúa como proveedor de servicios
que procesa los datos en nombre de Gamora; según la definición de Play eso **no** cuenta como
"compartir". No hay otros terceros.

## 3. Tipos de datos recopilados

Para cada tipo, Play pregunta: ¿se recopila?, ¿se procesa de forma efímera?, ¿es obligatorio u
opcional?, y ¿para qué se usa?

| Categoría › Tipo | Efímero | Obligatorio / opcional | Finalidad |
|---|---|---|---|
| Información personal › **Nombre** | No | Obligatorio | Funcionalidad de la app, Administración de la cuenta |
| Información personal › **Dirección de correo electrónico** | No | Obligatorio | Funcionalidad de la app, Administración de la cuenta |
| Información personal › **ID de usuario** (UID de Firebase) | No | Obligatorio | Funcionalidad de la app, Administración de la cuenta, Seguridad, prevención de fraudes y cumplimiento |
| Información personal › **Número de teléfono** | No | **Opcional** | Administración de la cuenta |
| Información financiera › **Otra información financiera** (ingresos, gastos, metas, presupuestos, recurrentes) | No | Obligatorio (es el propósito de la app) | Funcionalidad de la app, Personalización (consejos) |
| Mensajes › **Otros mensajes en la app** (conversación con Gamora AI) | No | **Opcional** | Funcionalidad de la app |
| Fotos y videos › **Fotos** (foto de un estado de cuenta, solo si la eliges) | **Sí** | **Opcional** | Funcionalidad de la app |
| Archivos y documentos › **Archivos y documentos** (PDF/XML de estados de cuenta y nómina) | **Sí** | **Opcional** | Funcionalidad de la app |
| Actividad en la app › **Interacciones con la app** (Google Analytics) | No | **Opcional** (solo con consentimiento) | Estadísticas |
| Información y rendimiento de la app › **Registros de fallas** (Crashlytics) | No | Obligatorio | Estadísticas, Funcionalidad de la app |
| Información y rendimiento de la app › **Diagnóstico** (Crashlytics: modelo, versión del sistema y de la app) | No | Obligatorio | Estadísticas, Funcionalidad de la app |
| Dispositivo u otros identificadores › **Dispositivo u otros identificadores** (ID de instalación de Firebase, App Check/Play Integrity) | No | Obligatorio | Seguridad, prevención de fraudes y cumplimiento; Estadísticas |

Por qué fotos y documentos son "efímeros": se leen en memoria (en el teléfono o, si hace falta, con
Gemini) y no se guardan en ningún lado; solo se guardan los movimientos que la persona confirma, que
ya están declarados como información financiera.

## 4. Lo que **no** se recopila (marcar "No")

Ubicación; contactos; calendario; salud y actividad física; audio; información web (historial);
información financiera de pagos (números de tarjeta o cuenta bancaria), historial de compras,
puntuación crediticia; dirección, raza, religión, orientación sexual, afiliación política; apps
instaladas; búsquedas en la app; otro contenido generado por el usuario aparte del chat.

## 5. Antes de enviar

- [ ] GitHub Pages activado y las URL de privacidad y de borrado abren sin error.
- [ ] Llenados en `strings.xml` (y regeneradas las páginas): nombre o razón social, domicilio, correo
      de privacidad y correo de soporte.
- [ ] URL del aviso de privacidad en la ficha de Play: `https://killstorm2007.github.io/Gamora/privacidad.html`
- [ ] Si agregas la suscripción (RevenueCat), sumar: Información financiera › **Historial de compras**
      (Funcionalidad de la app) y revisar si RevenueCat cuenta como proveedor de servicios.
