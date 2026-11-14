# Puntos para revisión legal antes de publicar en México y Estados Unidos

Textos a revisar: `app/src/main/res/values/strings.xml` (`politica_privacidad`, `terminos_condiciones`,
versión 1.1) y su traducción en `values-en/strings.xml`. Páginas públicas en `docs/`.

## Estados Unidos

1. **GLBA (Gramm-Leach-Bliley Act) y la Safeguards Rule de la FTC.** ¿Gamora (app de presupuesto personal
   que guarda ingresos, gastos y lee estados de cuenta, sin conectarse a bancos ni mover dinero) se
   considera "financial institution"? Si sí: aviso de privacidad GLBA y programa de seguridad escrito.
2. **Leyes estatales de privacidad** (California CPRA, Virginia, Colorado, Texas, etc.). Hoy no se
   alcanzarían los umbrales, pero el aviso ya ofrece derechos de saber, corregir y borrar a todos.
   ¿Hace falta algo más (p. ej. aviso "Notice at Collection" de California)?
3. **Limitación de responsabilidad y ley aplicable** (términos, secciones 10 y 14): validez frente a
   consumidores de EE. UU.; si conviene arbitraje o foro específico.
4. **Uso de IA (Gemini)** con datos financieros: si se requiere algún aviso adicional.

## México

5. **Nueva LFPDPPP (2025):** confirmar el nombre de la autoridad (Secretaría Anticorrupción y Buen
   Gobierno), plazos ARCO (20 días hábiles) y que el aviso tenga todos los elementos obligatorios.
6. **Envío de datos a Google fuera de México** como "remisión" a encargados (sin consentimiento) y no
   como transferencia.
7. **PROFECO / NOM-151 u otras** obligaciones para apps de suscripción (cuando se agregue el cobro).

## Datos que faltan en los textos

- Nombre completo o razón social, domicilio, correo de privacidad y correo de soporte.
