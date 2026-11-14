# Gamora en inglés — diseño

Fecha: 2026-11-14 · Estado: propuesto

## Por qué
Gamora se publica en México y Estados Unidos; el inglés es opcional para quien lo prefiera. Los textos
ya están en `strings.xml` y las categorías se guardan como claves, así que traducir no toca datos.

## Objetivos
1. `values-en/strings.xml` completo (todas las cadenas y plurales; ya trae los textos legales).
2. Selector de idioma en Perfil: **Igual que el teléfono / Español / English**. También funciona desde
   Ajustes de Android › Apps › Gamora › Idioma (Android 13+).
3. Fechas en el formato del idioma ("13 de noviembre" / "November 13").
4. Gamora AI responde en el idioma de la app.
5. Quitar la desactivación temporal de `MissingTranslation` en lint.

## No es parte de esto
- Leer estados de cuenta de bancos de EE. UU. sin IA (el lector del teléfono conoce formatos mexicanos;
  los demás siguen yendo a Gemini).
- Cambiar monedas (ya existen MXN, USD y EUR) ni el diseño de las pantallas.

## Sería un fracaso si…
- Algún texto queda en español con la app en inglés (o se corta un botón porque el inglés es más largo).
- Cambiar de idioma reinicia la app a medias o pierde la sesión.

## Enfoque
Idiomas por app de AndroidX (`AppCompatDelegate.setApplicationLocales` + `locales_config.xml`, con
guardado automático para Android 12 o menos). Es el mecanismo estándar: no requiere reiniciar la app a
mano y Android 13+ lo muestra en sus Ajustes. Alternativas descartadas: cambiar `Locale` a mano en cada
Activity (frágil, no se integra con Ajustes) o esperar a la versión multiplataforma (el usuario quiere
el inglés antes de publicar).

## Detalles
- Fechas: patrones fijos con `LOCALE_MX` pasan a `DateFormat.getBestDateTimePattern(locale, …)` con el
  idioma actual. Los montos siguen la moneda elegida, como hoy.
- Gamora AI: las instrucciones agregan "Responde en {idioma}"; los datos que se le mandan no cambian.
- Avisos (notificaciones) en Android 12 o menos pueden salir en el idioma del teléfono: limitación de
  Android aceptada.
- Revisar en el teléfono las pantallas en inglés buscando textos cortados.

## Pruebas
- Toda cadena de `values/strings.xml` existe en `values-en` (lint `MissingTranslation` sin desactivar).
- Formatos con argumentos iguales en ambos idiomas (lint `StringFormatMatches`).
- En el teléfono: cambiar a English y de regreso desde Perfil; recorrer todas las pantallas.
