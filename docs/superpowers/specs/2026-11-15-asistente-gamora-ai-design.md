# Asistente Gamora AI — diseño

Fecha: 2026-11-15 · Estado: implementado (límite diario pendiente)

## Por qué

Muchas preguntas de dinero no caben en una gráfica ("¿me alcanza para el viaje?", "¿en qué recorto?").
Un asistente que conoce los números de la persona (si lo autoriza) responde con cifras concretas.

## Objetivos

1. Conversación con memoria (los últimos 60 mensajes), en el idioma de la app.
2. Con permiso de la persona, respuestas basadas en sus datos reales; sin permiso, consejos generales.
3. Seguir funcionando cuando un modelo de Gemini está saturado.
4. Que nadie pueda usar la llave de la app para llamar a Gemini por su cuenta.

## No es parte de esto

- Que el asistente registre o modifique movimientos.
- Asesoría de inversión personalizada o recomendar productos financieros específicos.

## Sería un fracaso si…

- Se mandan datos financieros a Gemini sin el consentimiento de IA.
- Un error técnico en inglés aparece en pantalla en la versión de producción.

## Diseño

- **Modelo:** Firebase AI Logic con la API de Gemini (`GenerativeBackend.googleAI()`), modelos en
  `Modelos.kt` (`MODELOS_GEMINI`, en orden de preferencia). `conModeloDeRespaldo()` reintenta con
  espera si el modelo está saturado y pasa al siguiente; un modelo que no existe se salta.
- **Seguridad:** App Check con tokens de un solo uso (`useLimitedUseAppCheckTokens = true`). Con una
  instalación que no viene de Play (APK suelto), Play Integrity falla y el asistente no responde: es
  lo esperado.
- **Instrucciones** (`ChatbotFragment.instrucciones()`): se arman en cada mensaje para que lleven los
  datos al día; idioma según Perfil › Idioma; sin Markdown; moneda de la cuenta.
- **Contexto** (`ContextoFinanciero.kt`): solo si `iaAutorizada` en el consentimiento: totales de los
  últimos 3 meses, gastos por categoría del mes, metas y movimientos recientes.
- **Memoria** (`ChatRepository`): `users/{uid}/chat/{id}` con `rol`, `texto` (máx. 8000), `fecha`.
  Los mensajes no se editan; "Borrar conversación" los borra en batches de 500.
- **Errores:** mensajes simples (sin conexión, demasiada demanda, límite alcanzado); el detalle
  técnico solo en la versión de depuración.

## Pendiente

- **Límite diario por persona.** La cuota gratuita de Gemini es del proyecto completo: una persona
  puede agotarla para todos. El límite real necesita servidor (plan Blaze); mientras tanto se puede
  poner un límite en la app y configurar el límite por usuario de AI Logic en la consola.

## Pruebas

`ContextoFinancieroTest`, `LimiteCuotaTest`.
