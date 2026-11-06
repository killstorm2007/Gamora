-- ============================================================================
-- Gamora · Esquema inicial de Supabase
-- Pégalo completo en Supabase → SQL Editor → Run.
-- Es idempotente: puedes volver a ejecutarlo sin perder datos.
-- ============================================================================

-- gen_random_uuid() es parte del núcleo de Postgres 13+, no hace falta
-- habilitar pgcrypto.

-- ---------------------------------------------------------------------------
-- 1. Perfiles (1:1 con auth.users)
-- ---------------------------------------------------------------------------
create table if not exists public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    full_name text not null default '',
    paternal_surname text not null default '',
    maternal_surname text not null default '',
    phone text not null default '',
    currency text not null default 'MXN',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- 2. Movimientos (ingresos y gastos)
-- ---------------------------------------------------------------------------
create table if not exists public.movements (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid()
        references auth.users (id) on delete cascade,
    description text not null check (length(trim(description)) > 0),
    category text not null,
    amount_cents bigint not null check (amount_cents > 0),
    is_income boolean not null,
    occurred_at timestamptz not null default now(),
    created_at timestamptz not null default now()
);

create index if not exists movements_user_occurred_idx
    on public.movements (user_id, occurred_at desc);

-- ---------------------------------------------------------------------------
-- 3. Metas de ahorro y sus aportaciones
-- ---------------------------------------------------------------------------
create table if not exists public.goals (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid()
        references auth.users (id) on delete cascade,
    name text not null check (length(trim(name)) > 0),
    target_cents bigint not null check (target_cents > 0),
    saved_cents bigint not null default 0 check (saved_cents >= 0),
    deadline date,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists goals_user_idx
    on public.goals (user_id);

create table if not exists public.goal_contributions (
    id uuid primary key default gen_random_uuid(),
    goal_id uuid not null references public.goals (id) on delete cascade,
    user_id uuid not null default auth.uid()
        references auth.users (id) on delete cascade,
    amount_cents bigint not null check (amount_cents > 0),
    note text,
    created_at timestamptz not null default now()
);

create index if not exists goal_contributions_goal_idx
    on public.goal_contributions (goal_id, created_at desc);

-- ---------------------------------------------------------------------------
-- 4. Triggers
-- ---------------------------------------------------------------------------

-- Mantiene goals.saved_cents sincronizado con las aportaciones.
create or replace function public.sync_goal_saved_cents()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if tg_op = 'INSERT' then
        update public.goals
        set saved_cents = saved_cents + new.amount_cents,
            updated_at = now()
        where id = new.goal_id;
        return new;
    elsif tg_op = 'DELETE' then
        update public.goals
        set saved_cents = greatest(saved_cents - old.amount_cents, 0),
            updated_at = now()
        where id = old.goal_id;
        return old;
    end if;
    return null;
end;
$$;

drop trigger if exists goal_contribution_insert on public.goal_contributions;
create trigger goal_contribution_insert
    after insert on public.goal_contributions
    for each row execute function public.sync_goal_saved_cents();

drop trigger if exists goal_contribution_delete on public.goal_contributions;
create trigger goal_contribution_delete
    after delete on public.goal_contributions
    for each row execute function public.sync_goal_saved_cents();

-- Actualiza updated_at en cada cambio.
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists goals_set_updated_at on public.goals;
create trigger goals_set_updated_at
    before update on public.goals
    for each row execute function public.set_updated_at();

drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at
    before update on public.profiles
    for each row execute function public.set_updated_at();

-- Crea el perfil automáticamente al registrarse.
-- Los datos llegan desde el registro en la app (options.data).
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles (
        id,
        full_name,
        paternal_surname,
        maternal_surname,
        phone
    )
    values (
        new.id,
        coalesce(new.raw_user_meta_data ->> 'full_name', ''),
        coalesce(new.raw_user_meta_data ->> 'paternal_surname', ''),
        coalesce(new.raw_user_meta_data ->> 'maternal_surname', ''),
        coalesce(new.raw_user_meta_data ->> 'phone', '')
    )
    on conflict (id) do nothing;

    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- 5. Analíticas mensuales (vista calculada, siempre respeta RLS)
-- ---------------------------------------------------------------------------
create or replace view public.monthly_analytics
with (security_invoker = true) as
select
    user_id,
    date_trunc('month', occurred_at)::date as month,
    sum(case when is_income then amount_cents else 0 end) as income_cents,
    sum(case when is_income then 0 else amount_cents end) as expense_cents,
    sum(case when is_income then amount_cents else -amount_cents end) as net_cents,
    count(*) as movements_count
from public.movements
group by user_id, date_trunc('month', occurred_at)::date;

-- ---------------------------------------------------------------------------
-- 6. Row Level Security: cada usuario solo ve sus propios datos
-- ---------------------------------------------------------------------------
alter table public.profiles enable row level security;
alter table public.movements enable row level security;
alter table public.goals enable row level security;
alter table public.goal_contributions enable row level security;

drop policy if exists "profiles_select_own" on public.profiles;
create policy "profiles_select_own" on public.profiles
    for select to authenticated using (id = auth.uid());

drop policy if exists "profiles_insert_own" on public.profiles;
create policy "profiles_insert_own" on public.profiles
    for insert to authenticated with check (id = auth.uid());

drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own" on public.profiles
    for update to authenticated
    using (id = auth.uid())
    with check (id = auth.uid());

drop policy if exists "movements_select_own" on public.movements;
create policy "movements_select_own" on public.movements
    for select to authenticated using (user_id = auth.uid());

drop policy if exists "movements_insert_own" on public.movements;
create policy "movements_insert_own" on public.movements
    for insert to authenticated with check (user_id = auth.uid());

drop policy if exists "movements_update_own" on public.movements;
create policy "movements_update_own" on public.movements
    for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists "movements_delete_own" on public.movements;
create policy "movements_delete_own" on public.movements
    for delete to authenticated using (user_id = auth.uid());

drop policy if exists "goals_select_own" on public.goals;
create policy "goals_select_own" on public.goals
    for select to authenticated using (user_id = auth.uid());

drop policy if exists "goals_insert_own" on public.goals;
create policy "goals_insert_own" on public.goals
    for insert to authenticated with check (user_id = auth.uid());

drop policy if exists "goals_update_own" on public.goals;
create policy "goals_update_own" on public.goals
    for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists "goals_delete_own" on public.goals;
create policy "goals_delete_own" on public.goals
    for delete to authenticated using (user_id = auth.uid());

drop policy if exists "goal_contributions_select_own" on public.goal_contributions;
create policy "goal_contributions_select_own" on public.goal_contributions
    for select to authenticated using (user_id = auth.uid());

drop policy if exists "goal_contributions_insert_own" on public.goal_contributions;
create policy "goal_contributions_insert_own" on public.goal_contributions
    for insert to authenticated
    with check (
        user_id = auth.uid()
        and exists (
            select 1
            from public.goals goals
            where goals.id = goal_id
              and goals.user_id = auth.uid()
        )
    );

drop policy if exists "goal_contributions_delete_own" on public.goal_contributions;
create policy "goal_contributions_delete_own" on public.goal_contributions
    for delete to authenticated using (user_id = auth.uid());

-- ---------------------------------------------------------------------------
-- 7. Permisos: solo usuarios autenticados, y RLS filtra por usuario
-- ---------------------------------------------------------------------------
revoke all on public.profiles from anon;
revoke all on public.movements from anon;
revoke all on public.goals from anon;
revoke all on public.goal_contributions from anon;
revoke all on public.monthly_analytics from anon;

grant select, insert, update, delete on public.profiles to authenticated;
grant select, insert, update, delete on public.movements to authenticated;
grant select, insert, update, delete on public.goals to authenticated;
grant select, insert, update, delete on public.goal_contributions to authenticated;
grant select on public.monthly_analytics to authenticated;
