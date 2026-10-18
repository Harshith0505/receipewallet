-- Recipe Wallet: private recipe storage. Apply once through Supabase migrations.
begin;

create function public.rw_valid_ingredients(value text[])
returns boolean language sql immutable parallel safe set search_path = '' as $$
  select coalesce(array_ndims(value) = 1 and cardinality(value) between 1 and 40, false)
    and not exists (
      select 1 from unnest(value) line
      where line is null or char_length(btrim(line)) = 0 or char_length(line) > 200
    );
$$;

create table public.recipes (
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  id uuid not null default gen_random_uuid(),
  name text not null check (char_length(btrim(name)) between 1 and 80),
  ingredients text[] not null check (public.rw_valid_ingredients(ingredients)),
  prep_minutes integer check (prep_minutes between 1 and 999),
  version integer not null default 1 check (version > 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, id)
);
create index recipes_owner_updated_idx on public.recipes(user_id, updated_at desc, id);

alter table public.recipes enable row level security;
revoke all on public.recipes from anon, authenticated;
grant select, insert, update, delete on public.recipes to authenticated;

create policy recipes_read_own on public.recipes for select to authenticated
  using ((select auth.uid()) = user_id);
create policy recipes_insert_own on public.recipes for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy recipes_update_own on public.recipes for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy recipes_delete_own on public.recipes for delete to authenticated
  using ((select auth.uid()) = user_id);

create function public.rw_recipe_updated()
returns trigger language plpgsql set search_path = '' as $$
begin
  if new.user_id <> old.user_id or new.id <> old.id then
    raise exception 'Recipe ownership and identity cannot be changed';
  end if;
  new.created_at := old.created_at;
  new.updated_at := clock_timestamp();
  new.version := old.version + 1;
  return new;
end;
$$;
create trigger recipe_updated before update on public.recipes
  for each row execute function public.rw_recipe_updated();

-- The caller supplies no owner ID. This function runs with the caller's RLS,
-- and refuses stale edits rather than silently overwriting another device.
create function public.save_recipe(
  p_recipe_id uuid, p_name text, p_ingredients text[],
  p_prep_minutes integer default null, p_expected_version integer default 0
)
returns jsonb language plpgsql security invoker set search_path = '' as $$
declare saved public.recipes;
begin
  if auth.uid() is null then
    raise sqlstate '42501' using message = 'Sign in to save recipes';
  end if;
  if p_expected_version < 0 then
    raise sqlstate '22023' using message = 'Invalid recipe version';
  end if;
  if p_expected_version = 0 then
    insert into public.recipes(user_id, id, name, ingredients, prep_minutes)
      values (auth.uid(), p_recipe_id, p_name, p_ingredients, p_prep_minutes)
      on conflict (user_id, id) do nothing returning * into saved;
  else
    update public.recipes set name = p_name, ingredients = p_ingredients,
      prep_minutes = p_prep_minutes
      where user_id = auth.uid() and id = p_recipe_id and version = p_expected_version
      returning * into saved;
  end if;
  if not found then
    raise sqlstate 'PT409' using message = 'This recipe changed on another device';
  end if;
  return to_jsonb(saved);
end;
$$;

revoke all on function public.rw_valid_ingredients(text[]) from public, anon;
grant execute on function public.rw_valid_ingredients(text[]) to authenticated;
revoke all on function public.rw_recipe_updated() from public, anon, authenticated;
revoke all on function public.save_recipe(uuid, text, text[], integer, integer) from public, anon;
grant execute on function public.save_recipe(uuid, text, text[], integer, integer) to authenticated;

comment on table public.recipes is 'Private saved recipes; prices and payment credentials are not stored here.';
commit;
