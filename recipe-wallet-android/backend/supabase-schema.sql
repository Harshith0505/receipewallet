-- Run once in the Supabase SQL editor.
create table if not exists purchases (
  payment_intent text   not null,
  user_id        text   not null,
  ingredient_id  text   not null,
  recipe_id      text   not null,
  bought_at      bigint not null,           -- milliseconds since epoch
  primary key (payment_intent, ingredient_id)
);
create index if not exists purchases_user on purchases (user_id, bought_at);

create table if not exists used_up (
  user_id       text   not null,
  ingredient_id text   not null,
  at            bigint not null,
  primary key (user_id, ingredient_id)
);

-- The backend uses the service key, which bypasses RLS. Enable RLS with no policies
-- so the public anon key can never read or write these tables.
alter table purchases enable row level security;
alter table used_up   enable row level security;
