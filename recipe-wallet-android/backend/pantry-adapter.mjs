import {catalog,products} from './pricing.mjs';
const normalized=s=>s.toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();
const productFor=name=>products.map(p=>({p,score:Math.max(0,...p.aliases.filter(a=>(' '+normalized(name)+' ').includes(' '+normalized(a)+' ')).map(a=>a.length))})).filter(v=>v.score).sort((a,b)=>b.score-a.score)[0]?.p.id;
export function studioScan(result) {
 const ingredients=[...new Map(catalog.recipes.flatMap(r=>r.ingredients).map(i=>[i.id,i])).values()];
 const ingredientIds=ingredients.filter(i=>result.ingredients.some(row=>normalized(row.name)===normalized(i.name)||(productFor(row.name)&&productFor(row.name)===productFor(i.name)))).map(i=>i.id);
 return {...result,ingredientIds,requiresConfirmation:true};
}
