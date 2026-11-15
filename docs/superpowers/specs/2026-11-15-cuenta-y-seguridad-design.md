# Cuenta, acceso y seguridad — diseño

Fecha: 2026-11-15 · Estado: implementado

## Por qué

Gamora guarda información financiera: entrar debe ser simple, los datos deben estar protegidos en el
teléfono y la persona debe poder borrar todo cuando quiera, sin que quede nada a medias.

## Objetivos

1. Registro con correo y contraseña o con Google, aceptando términos y aviso de privacidad.
2. Bloqueo opcional con huella, rostro o PIN del teléfono.
3. Eliminar la cuenta y todos los datos desde Perfil, de forma segura y completa.

## No es parte de esto

- Inicio de sesión con Apple u otros proveedores (llegará con iOS).
- Verificación en dos pasos propia (la da Google o el PIN del teléfono).

## Sería un fracaso si…

- Eliminar la cuenta deja datos en Firestore o una cuenta de acceso sin perfil.
- El bloqueo cierra la app cuando solo se apagó la pantalla, o pide la huella al volver de elegir un archivo.
- Los saldos se ven en "apps recientes" o en capturas con el bloqueo activo.

## Diseño

### Registro e inicio de sesión (`core/auth/`, `feature/auth/`)
- Correo: `AuthRepository.registrarUsuario` crea la cuenta y, en un batch, el perfil y el
  consentimiento; si el batch falla, borra la cuenta recién creada.
- Google: Credential Manager (`GoogleAuth.kt`); la primera vez pide consentimientos y crea el perfil.
- En consola: protección contra enumeración de correos y contraseñas de 8+ con números y caracteres
  especiales (activado).
- Errores en palabras simples (`mensajeDeError`): credenciales incorrectas, demasiados intentos, sin conexión.

### Bloqueo (`core/security/BloqueoApp.kt`, `HomeActivity`)
- `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`; si el teléfono ya no tiene huella ni PIN, no se deja fuera a nadie.
- Se pide al abrir y al volver de segundo plano. `SalidaBreve.permitir()` antes de abrir otra app
  (elegir PDF, compartir, correo, ajustes): al volver en menos de 5 minutos no se pide otra vez.
- Resultado: "Cancelar" cierra la app; si el sistema lo interrumpe (pantalla apagada, otra app
  encima) se vuelve a pedir al regresar; si sigue al frente (demasiados intentos) se ofrece reintentar.
- Con el bloqueo activo: `FLAG_SECURE` (sin capturas ni vista previa en recientes).

### Eliminar la cuenta (`AuthRepository`)
1. Requiere internet y confirmar identidad (contraseña o Google) por la regla de inicio reciente.
2. Anota en el teléfono `eliminando_uid` (`gamora_cuenta`).
3. Borra las subcolecciones leídas del servidor (`Source.SERVER`), en batches de 500; luego el perfil;
   al final la cuenta de acceso.
4. Al terminar: quita la marca, cierra la sesión y borra la copia local de Firestore
   (`terminate()` + `clearPersistence()`) y el historial guardado.

Si se interrumpe, se termina después: al abrir la app (`terminarEliminacionPendiente`) o al volver a
entrar (`iniciarSesion` detecta un perfil faltante; `iniciarSesionConGoogle` revisa la marca). Repetir
el borrado es seguro.

`AuthRepository.db` es un getter (no se guarda la instancia) porque Firestore se cierra al eliminar.

### Respaldo del teléfono
`allowBackup="false"` y reglas de extracción que excluyen todo: los datos no salen a respaldos de Google.
