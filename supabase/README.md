# Base de datos en Supabase

Gamora guarda usuarios, movimientos, metas y aportaciones en Supabase
Postgres. La app habla con Supabase directamente (Auth + Postgrest) usando la
`anon key`; la seguridad real la aplica **Row Level Security (RLS)** en la base
de datos, no el cliente.

## 1. Crear el proyecto

1. Entra a https://supabase.com/dashboard y crea un proyecto nuevo.
2. Espera a que termine de aprovisionarse.

## 2. Crear el esquema

1. Abre **SQL Editor → New query**.
2. Pega el contenido completo de [`migrations/0001_init.sql`](migrations/0001_init.sql).
3. Ejecuta (**Run**).

El script es idempotente: puedes volver a ejecutarlo sin borrar datos.

Esto crea:

| Objeto | Para qué sirve |
| --- | --- |
| `public.profiles` | Datos del usuario (nombre, apellidos, teléfono, moneda). 1:1 con `auth.users`. |
| `public.movements` | Ingresos y gastos con importe en centavos. |
| `public.goals` | Metas de ahorro con objetivo y ahorro acumulado. |
| `public.goal_contributions` | Aportaciones por meta; un trigger actualiza `goals.saved_cents`. |
| `public.monthly_analytics` | Vista de analíticas mensuales (ingresos, gastos, neto, número de movimientos). |
| triggers | Perfil automático al registrarse, `updated_at` y suma de aportaciones. |
| políticas RLS | Cada usuario solo puede leer y escribir filas con su propio `user_id`. |

## 3. Conectar la app

Copia las credenciales a `local.properties` en la raíz del proyecto (ese
archivo está en `.gitignore`):

```properties
SUPABASE_URL=https://tu-proyecto.supabase.co
SUPABASE_ANON_KEY=tu-anon-public-key
```

Puedes tomarlas de **Project Settings → API** (Project URL y anon public key).
Gradle las convierte en campos de `BuildConfig`; no se escriben en el código ni
se suben al repositorio. También se aceptan como variables de entorno con el
mismo nombre.

> La `service_role key` **nunca** debe usarse en la app: se salta RLS.

## 4. Autenticación por correo

En **Authentication → Providers → Email**:

- Con **Confirm email** activado (recomendado), el registro devuelve la sesión
  vacía y la app pide confirmar el correo antes de iniciar sesión.
- Con **Confirm email** desactivado, la app entra directo después de registrarse.

El perfil se crea solo con el trigger `handle_new_user`, usando los metadatos
que envía la app (`full_name`, `paternal_surname`, `maternal_surname`, `phone`).

## 5. Comprobar que RLS funciona

Con dos usuarios distintos, cada uno debe ver únicamente sus filas:

```sql
-- Ejecutado como usuario autenticado A
select count(*) from public.movements; -- solo los de A
```

Si una consulta devuelve `0` filas o un error `42501`, revisa que el usuario
tenga sesión activa y que la política `using (user_id = auth.uid())` exista.

## Esquema de datos

```
auth.users (Supabase Auth)
   │ id
   ├── profiles (id)
   ├── movements (user_id)
   ├── goals (user_id)
   └── goal_contributions (user_id, goal_id → goals.id)
```

Todos los importes se guardan como **enteros en centavos** (`amount_cents`,
`target_cents`, `saved_cents`) para evitar errores de redondeo con decimales.
