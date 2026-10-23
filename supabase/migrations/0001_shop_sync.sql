-- Phone Shop Manager: shared shop data and online backup.
-- Run this once in the Supabase SQL Editor of YOUR OWN project. It is safe to run again.

-- 1. Shops and who may use them -------------------------------------------------
create table if not exists public.shops (
  id uuid primary key default gen_random_uuid(),
  name text not null default '',
  created_at timestamptz not null default now()
);

create table if not exists public.shop_members (
  shop_id uuid not null references public.shops(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null default 'CASHIER' check (role in ('OWNER', 'MANAGER', 'CASHIER')),
  primary key (shop_id, user_id)
);

alter table public.shops enable row level security;
alter table public.shop_members enable row level security;

create or replace function public.is_member(p_shop uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.shop_members where shop_id = p_shop and user_id = auth.uid());
$$;

create or replace function public.is_owner(p_shop uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.shop_members where shop_id = p_shop and user_id = auth.uid() and role = 'OWNER');
$$;

drop policy if exists "members see their shop" on public.shops;
create policy "members see their shop" on public.shops for select to authenticated using (public.is_member(id));

drop policy if exists "members see the team" on public.shop_members;
create policy "members see the team" on public.shop_members for select to authenticated using (public.is_member(shop_id));

-- 2. The shared rows: one row per record of the app (products, bills, money ...) ---
create sequence if not exists public.sync_seq;

create table if not exists public.sync_rows (
  shop_id uuid not null references public.shops(id) on delete cascade,
  tbl text not null,
  id text not null,
  data jsonb not null,
  updated_at bigint not null,
  deleted boolean not null default false,
  device text not null default '',
  seq bigint not null default 0,
  primary key (shop_id, tbl, id)
);
create index if not exists sync_rows_pull on public.sync_rows (shop_id, seq);

alter table public.sync_rows enable row level security;

drop policy if exists "members read rows" on public.sync_rows;
create policy "members read rows" on public.sync_rows for select to authenticated using (public.is_member(shop_id));
drop policy if exists "members add rows" on public.sync_rows;
create policy "members add rows" on public.sync_rows for insert to authenticated with check (public.is_member(shop_id));
drop policy if exists "members change rows" on public.sync_rows;
create policy "members change rows" on public.sync_rows for update to authenticated
  using (public.is_member(shop_id)) with check (public.is_member(shop_id));
-- Nobody deletes rows: records are marked deleted/voided instead.

create or replace function public.stamp_seq() returns trigger language plpgsql as $$
begin
  new.seq := nextval('public.sync_seq');
  return new;
end $$;

drop trigger if exists sync_rows_seq on public.sync_rows;
create trigger sync_rows_seq before insert or update on public.sync_rows
  for each row execute function public.stamp_seq();

-- Phones send changes here. When two phones changed the same record, the newer edit wins.
create or replace function public.push_rows(p_shop uuid, p_device text, p_rows jsonb)
returns integer language plpgsql security invoker set search_path = public as $$
declare n integer;
begin
  insert into public.sync_rows as s (shop_id, tbl, id, data, updated_at, deleted, device)
  select p_shop, r->>'tbl', r->>'id', r->'data', (r->>'updated_at')::bigint,
         coalesce((r->>'deleted')::boolean, false), p_device
  from jsonb_array_elements(p_rows) r
  on conflict (shop_id, tbl, id) do update
    set data = excluded.data, updated_at = excluded.updated_at,
        deleted = excluded.deleted, device = excluded.device
    where s.updated_at <= excluded.updated_at;
  get diagnostics n = row_count;
  return n;
end $$;

-- 3. Making a shop and adding people ----------------------------------------------
create or replace function public.create_shop(p_name text)
returns uuid language plpgsql security definer set search_path = public as $$
declare sid uuid;
begin
  if auth.uid() is null then raise exception 'Not logged in'; end if;
  insert into public.shops (name) values (coalesce(p_name, '')) returning id into sid;
  insert into public.shop_members (shop_id, user_id, role) values (sid, auth.uid(), 'OWNER');
  return sid;
end $$;

create or replace function public.add_member(p_shop uuid, p_email text, p_role text)
returns void language plpgsql security definer set search_path = public as $$
declare uid uuid;
begin
  if not public.is_owner(p_shop) then raise exception 'Only the owner can add people'; end if;
  select id into uid from auth.users where lower(email) = lower(p_email);
  if uid is null then raise exception 'No login with that email. Add the user in Supabase Authentication first.'; end if;
  insert into public.shop_members (shop_id, user_id, role) values (p_shop, uid, p_role)
  on conflict (shop_id, user_id) do update set role = excluded.role;
end $$;

-- 4. Private place for whole-shop backup files ---------------------------------------
insert into storage.buckets (id, name, public) values ('backups', 'backups', false)
on conflict (id) do nothing;

drop policy if exists "shop owner reads own backups" on storage.objects;
create policy "shop owner reads own backups" on storage.objects for select to authenticated
  using (bucket_id = 'backups' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists "shop owner adds own backups" on storage.objects;
create policy "shop owner adds own backups" on storage.objects for insert to authenticated
  with check (bucket_id = 'backups' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists "shop owner replaces own backups" on storage.objects;
create policy "shop owner replaces own backups" on storage.objects for update to authenticated
  using (bucket_id = 'backups' and (storage.foldername(name))[1] = auth.uid()::text);

-- 5. Only logged-in people may call these ------------------------------------------------
revoke execute on function public.create_shop(text) from public, anon;
revoke execute on function public.add_member(uuid, text, text) from public, anon;
revoke execute on function public.push_rows(uuid, text, jsonb) from public, anon;
revoke execute on function public.is_member(uuid) from public, anon;
revoke execute on function public.is_owner(uuid) from public, anon;
grant execute on function public.create_shop(text) to authenticated;
grant execute on function public.add_member(uuid, text, text) to authenticated;
grant execute on function public.push_rows(uuid, text, jsonb) to authenticated;
grant execute on function public.is_member(uuid) to authenticated;
grant execute on function public.is_owner(uuid) to authenticated;
