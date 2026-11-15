# Diálogos y accesibilidad — diseño

Fecha: 2026-11-15 · Estado: implementado

## Por qué

Los diálogos se veían como los de Android por defecto y algunos escondían sus botones en pantallas
chicas. Además, una app de dinero debe poder usarse con lector de pantalla (TalkBack) y letra grande.

## Objetivos

1. Diálogos con el estilo de Gamora: ícono en un círculo de color, título centrado y botones en píldora.
2. Acciones peligrosas (eliminar) claramente en rojo.
3. Botones siempre a la vista aunque el diálogo tenga muchos campos o el teclado esté abierto.
4. TalkBack no lee emojis decorativos y puede saltar entre secciones.

## Sería un fracaso si…

- Un diálogo pierde su botón Guardar o un texto se sale de los márgenes.
- Un botón queda con texto del mismo color que su fondo.

## Diseño

### Diálogos (`core/ui/Dialogos.kt`, `res/values/themes.xml`)
- `dialogo(context, IconoDialogo.X)` en lugar de `MaterialAlertDialogBuilder` directo.
- `IconoDialogo`: dibujo, color y fondo del círculo (48 dp con ícono de 24 dp); `ELIMINAR` es peligroso.
- Temas: `ThemeOverlay.Gamora.Dialog` (sin ícono), `.Icono` (centrado) y `.Peligro` (botón principal rojo).
  Esquinas de 28 dp, título 20 sp, texto 15 sp; principal relleno, secundario con borde, neutral de texto.
  No cambiar el color del texto del botón principal a mano (en `.Peligro` el fondo ya es rojo).
- Márgenes laterales de 24 dp en todo contenido propio (campos, interruptores).
- `contenidoDesplazable(context, mensaje, campos)`: con varios campos, el mensaje va dentro del mismo
  desplazamiento que los campos (en lugar de `setMessage`), así los botones siempre se ven.
- `enCampo()` / `enDialogo()`: campos con contorno y márgenes del diálogo.

### Accesibilidad (`core/ui/Accesibilidad.kt`)
- `textoParaLector()`: quita la decoración inicial ("👤     Información personal" se lee
  "Información personal"). Se aplica a toda pantalla desde `Application.ajustarPantallasParaLectores()`.
- Títulos de 24 sp o más en negrita y `Gamora.SectionLabel` se marcan como encabezados.
- Filas de movimientos con descripción completa; contraste de colores revisado (WCAG AA).
- Textos que no caben en inglés: una línea con márgenes ajustados (p. ej. el botón "Expenses").

## Pruebas

`AccesibilidadTest`. Revisado en teléfono en modo claro: íconos, botones, interruptores alineados,
diálogos largos.
