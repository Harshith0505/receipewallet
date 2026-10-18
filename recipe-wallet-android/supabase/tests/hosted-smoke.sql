-- Run as the project database administrator in SQL Editor.
-- Synthetic identities and recipe writes are rolled back; no accounts or recipes remain.
begin;
insert into auth.users(id) values
 ('c1893c19-81dd-4e79-b9bf-20d2891c46a1'),
 ('c1893c19-81dd-4e79-b9bf-20d2891c46b2');
set local role authenticated;
select set_config('request.jwt.claim.sub', 'c1893c19-81dd-4e79-b9bf-20d2891c46a1', true);
do $$
declare saved jsonb;
begin
  saved := public.save_recipe('c1893c19-81dd-4e79-b9bf-20d2891c46c3', 'Schema smoke test', array['2 eggs'], 10, 0);
  if (saved->>'version')::integer <> 1 then raise exception 'Insert version failed'; end if;
  saved := public.save_recipe('c1893c19-81dd-4e79-b9bf-20d2891c46c3', 'Edited smoke test', array['3 eggs'], 15, 1);
  if (saved->>'version')::integer <> 2 then raise exception 'Update version failed'; end if;
  begin
    perform public.save_recipe('c1893c19-81dd-4e79-b9bf-20d2891c46c3', 'Stale edit', array['egg'], 10, 1);
    raise exception 'Stale edit was accepted';
  exception when sqlstate 'PT409' then null;
  end;
  perform set_config('request.jwt.claim.sub', 'c1893c19-81dd-4e79-b9bf-20d2891c46b2', true);
  if exists (select 1 from public.recipes where id='c1893c19-81dd-4e79-b9bf-20d2891c46c3') then
    raise exception 'Cross-user read was accepted';
  end if;
  update public.recipes set name='Hijacked' where id='c1893c19-81dd-4e79-b9bf-20d2891c46c3';
  if found then raise exception 'Cross-user update was accepted'; end if;
  delete from public.recipes where id='c1893c19-81dd-4e79-b9bf-20d2891c46c3';
  if found then raise exception 'Cross-user delete was accepted'; end if;
  begin
    insert into public.recipes(user_id,name,ingredients) values
      ('c1893c19-81dd-4e79-b9bf-20d2891c46a1','Forged owner',array['egg']);
    raise exception 'Forged-owner insert was accepted';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;
rollback;
select 'PASS: save, edit, stale-version protection and cross-user isolation; all test data rolled back' as verification;
