# Privacidad para México y Estados Unidos — diseño

Fecha: 2026-11-14 · Estado: propuesto

## Por qué

Gamora se publicará en México y en Estados Unidos (con la app en inglés como opción). El aviso de
privacidad y los términos actuales (v1.0) se escribieron solo para México, y uno de sus compromisos
("si la política cambia te lo informaremos en la app") no está implementado. Antes de publicar, los
textos deben cumplir con ambos países y todo lo que prometen debe ser cierto en la app.

## Objetivos

1. Un solo aviso de privacidad y unos solos términos para todas las personas (opción A, elegida),
   versión **1.1**, con la ley mexicana como base y una sección para residentes de Estados Unidos.
2. Versión en español (la que rige) y versión en inglés, ambas generadas como páginas web desde el
   proyecto (`docs/privacidad.html`, `docs/privacy.html`, etc.).
3. La app pide aceptar la nueva versión a quien aceptó una anterior, y registra el consentimiento.
4. Cada afirmación de los textos coincide con lo que la app hace (revisión línea por línea).

## No es parte de esto

- Traducir la interfaz de la app (proyecto aparte, después de seguridad).
- Revisión legal: se deja una lista de puntos para que la revise un abogado (en especial GLBA en EE. UU.).
- Cambiar qué datos se recopilan.

## Sería un fracaso si…

- Un texto promete algo que la app no hace (o al revés: la app recopila algo que el texto no menciona).
- La versión en inglés dice algo distinto a la española.
- Alguien con la v1.0 aceptada puede seguir usando la app sin ver ni aceptar la v1.1.

## Contenido nuevo o corregido del aviso de privacidad

- **Responsable y contacto:** los datos que el usuario dará después (nombre o razón social, domicilio,
  correos); siguen como marcadores hasta entonces.
- **Transferencias:** los datos se guardan en servidores de Google, que pueden estar fuera de México
  (p. ej. Estados Unidos); Google actúa como encargado; no se transfieren a terceros para sus propios fines.
- **Derechos ARCO:** cómo pedirlos, plazo de respuesta (20 días hábiles) y que se puede acudir a la
  autoridad de protección de datos (hoy la Secretaría Anticorrupción y Buen Gobierno, que sustituyó al INAI).
- **Conservación:** cuenta y datos mientras exista la cuenta; reportes de Crashlytics 90 días; analítica
  según la retención configurada en Google Analytics (verificarla en la consola).
- **Para residentes de Estados Unidos:**
  - categorías de datos y fuentes (tabla corta);
  - no vendemos ni compartimos datos para publicidad entre sitios; no hay publicidad;
  - derechos para todas las personas: saber, acceder, corregir, borrar y no ser discriminadas por usarlos;
  - Do Not Track: la app no rastrea entre sitios, así que no cambia nada con esa señal;
  - menores: no está dirigida a menores de 13 (COPPA) y exige 18 años;
  - fecha de vigencia y cómo se avisan los cambios (CalOPPA).
- **Cambios:** se publica la nueva versión con su fecha; si cambia lo que se recopila o para qué, la app
  pide aceptarla de nuevo (objetivo 3).

## Términos

- Versión 1.1, también en inglés.
- Gamora no da asesoría financiera, fiscal ni de inversión (ya implícito; hacerlo explícito).
- Ley aplicable: México, sin quitar los derechos de consumidor que la ley del país o estado de la persona
  no permita renunciar.
- Limitación de responsabilidad y uso del asistente de IA, revisados para EE. UU.

## Reaceptar la nueva versión en la app

- Una constante con la versión vigente (`"1.1"`) reemplaza los tres `"1.0"` de `AuthRepository`.
- Al abrir la app con sesión, si la última versión aceptada (de `consentimientos`) es menor, se muestra
  un diálogo que no se puede omitir: qué cambió (2–3 líneas), enlaces al aviso y a los términos, y
  botones **Aceptar** / **Cerrar sesión**. Aceptar guarda un nuevo consentimiento con la versión 1.1.
- Sin conexión: se pide de nuevo la próxima vez; no bloquea leer los datos ya guardados en el teléfono.

## Páginas web

- `scripts/generar_paginas_legales.py` genera también las versiones en inglés (`privacy.html`,
  `terms.html`, `delete-account.html`) desde `values-en/strings.xml`, con un enlace para cambiar de idioma.
- La versión en inglés indica que, si hay diferencias, rige la española.

## Para el abogado (lista que se entrega, no se resuelve aquí)

- ¿Gamora es "institución financiera" para la GLBA (Safeguards Rule) en EE. UU.?
- ¿Hace falta un aviso específico para algún estado además de California?
- Redacción de limitación de responsabilidad y ley aplicable para usuarios de EE. UU.

## Pruebas

- La versión de política vigente es "1.1" en todos los lugares que la usan (prueba).
- Comparación de versiones ("1.0" < "1.1" < "1.10") (prueba).
- En el teléfono: con una cuenta que aceptó la 1.0, aparece el diálogo; al aceptar no vuelve a salir.
- Las páginas en español e inglés se generan y tienen las mismas secciones.

## Decisiones del implementador

- Redacción exacta (dentro de lo anterior) y orden de secciones.
- Dónde se hace la verificación de versión al iniciar (Home o Main).
