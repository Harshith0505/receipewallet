import { PGlite } from '@electric-sql/pglite';
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';

const db = new PGlite();
const alice = '11111111-1111-4111-8111-111111111111';
const bob = '22222222-2222-4222-8222-222222222222';
const recipe = '33333333-3333-4333-8333-333333333333';
await db.exec(`
  create role anon nologin;
  create role authenticated nologin;
  create schema auth;
  create table auth.users(id uuid primary key);
  create function auth.uid() returns uuid language sql stable as
    $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
  grant usage on schema public, auth to anon, authenticated;
  grant execute on function auth.uid() to anon, authenticated;
  insert into auth.users values ('${alice}'), ('${bob}');
`);
await db.exec(await readFile(new URL('../migrations/202610170001_recipes.sql', import.meta.url), 'utf8'));
async function asUser(id, role = 'authenticated') {
  await db.exec(`reset role; set role ${role};`);
  await db.query(`select set_config('request.jwt.claim.sub', $1, false)`, [id]);
}
async function save(name, version = 0) {
  const result = await db.query(`select public.save_recipe($1,$2,$3,15,$4) as recipe`, [recipe, name, ['2 eggs', '1 cup flour'], version]);
  return result.rows[0].recipe;
}
async function denied(action, code) {
  await assert.rejects(action, e => !code || e.code === code);
}
await asUser(alice);
const created = await save('Pancakes');
assert.equal(created.user_id, alice);
assert.equal(created.version, 1);
assert.equal((await db.query('select * from public.recipes')).rows.length, 1);
const updated = await save('Better pancakes', 1);
assert.equal(updated.version, 2);
assert.equal(updated.created_at, created.created_at);
await denied(() => save('Stale edit', 1), 'PT409');
await denied(() => save('Duplicate'), 'PT409');
await denied(() => db.query(`insert into public.recipes(user_id,name,ingredients) values ($1,'Forged',array['egg'])`, [bob]), '42501');
await denied(() => db.query(`update public.recipes set user_id=$1`, [bob]));
await denied(() => db.query(`update public.recipes set id=gen_random_uuid()`));
for (const ingredients of [[], [''], [null], ['x'.repeat(201)], Array(41).fill('egg')]) {
  await denied(() => db.query(`insert into public.recipes(name,ingredients) values ('Invalid',$1)`, [ingredients]), '23514');
}
await denied(() => db.query(`insert into public.recipes(name,ingredients) values (' ',array['egg'])`), '23514');
await denied(() => db.query(`insert into public.recipes(name,ingredients,prep_minutes) values ('Invalid',array['egg'],0)`), '23514');
await asUser(bob);
assert.equal((await db.query('select * from public.recipes')).rows.length, 0);
assert.equal((await db.query(`update public.recipes set name='Hijack' returning *`)).rows.length, 0);
assert.equal((await db.query('delete from public.recipes returning *')).rows.length, 0);
await denied(() => save('Hijack', 2), 'PT409');
const bobs = await save('Bob pancakes');
assert.equal(bobs.user_id, bob);
await asUser('', 'anon');
await denied(() => db.query('select * from public.recipes'), '42501');
await denied(() => save('Anonymous'), '42501');
await asUser('');
assert.equal((await db.query('select * from public.recipes')).rows.length, 0);
await denied(() => save('No identity'), '42501');
await asUser(alice);
assert.equal((await db.query('select name from public.recipes')).rows[0].name, 'Better pancakes');
assert.equal((await db.query('delete from public.recipes returning *')).rows.length, 1);
await asUser(bob);
assert.equal((await db.query('select * from public.recipes')).rows.length, 1);
console.log('PASS: migration, owner CRUD, cross-user isolation, anonymous denial, validation and stale-edit protection');
await db.close();
