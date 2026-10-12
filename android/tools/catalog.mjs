import fs from 'node:fs';import crypto from 'node:crypto';import path from 'node:path';import {fileURLToPath} from 'node:url';
const ROOT=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const local=p=>path.join(ROOT,p);
export function decode(s){return s.replace(/&#x([0-9a-f]+);/gi,(_,n)=>String.fromCodePoint(parseInt(n,16))).replace(/&#(\d+);/g,(_,n)=>String.fromCodePoint(+n)).replace(/&amp;/g,'&').replace(/&quot;/g,'"').replace(/&apos;|&#039;/g,"'").replace(/&nbsp;/g,' ').replace(/&[lr]squo;/g,"'").replace(/&[lr]dquo;/g,'"').replace(/&ndash;/g,'–').replace(/&mdash;/g,'—');}
export function body(html){return html.split(/<div class="entry-content"[^>]*>/)[1]?.split(/<div class=["']sharedaddy|<div id="jp-post-flair"|<!-- \.entry-content -->/)[0]||'';}
export function plain(html){return decode(html.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi,'').replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi,'').replace(/<br\s*\/?>|<\/(?:p|h[1-6]|li|tr|div)>/gi,'\n').replace(/<[^>]+>/g,'')).split('\n').map(x=>x.trim()).filter(Boolean);}
if(process.argv[2]==='discover'){let items=new Map();for(const cat of JSON.parse(fs.readFileSync(local('tools/categories.json'),'utf8'))){const html=body(fs.readFileSync(local('tools/cache/'+cat.id+'.html'),'utf8'));for(const m of html.matchAll(/<a\b[^>]*href=["'](https:\/\/fujixweekly\.com\/\d{4}\/\d{2}\/\d{2}\/[^"'?#]+\/?)["'][^>]*>([\s\S]*?)<\/a>/g)){const url=m[1],name=plain(m[2]).join(' ');if(!items.has(url))items.set(url,{id:crypto.createHash('sha256').update(url).digest('hex').slice(0,16),url,name,sensors:[]});const it=items.get(url);if(name&&name.length<it.name.length||!it.name)it.name=name;if(!it.sensors.includes(cat.id))it.sensors.push(cat.id)}}fs.writeFileSync(local('tools/urls.json'),JSON.stringify([...items.values()],null,2));console.log('Discovered',items.size,'public article links');}

const settingKey=/^(?:Film Simulation|Dynamic Range|D[ -]?Range Priority|Grain(?: Effect| Size| Strength)?|Color Chrome(?: Effect| FX Blue| Blue)?|Highlight(?: Tone)?|Shadow(?: Tone)?|Color|Saturation|Sharpness|Sharpening|High ISO NR|High ISO Noise Reduction|Noise Reduction(?:\/High ISO NR)?|Clarity|White Balance|WB(?: Shift)?|ISO|Exposure Compensation|Exposure|Monochromatic Color|Monochrome Color|B&W(?: Adjustment| Toning)?|Color Temperature|Smooth Skin Effect|Lens Modulation Optimizer|Long Exposure NR|DR)\s*:/i;
const filmLine=/^(?:Classic (?:Chrome|Neg\.?|Negative)|Nostalgic (?:Neg\.?|Negative)|Reala Ace|Provia|Standard|Velvia|Astia|Pro Neg\.?|Acros|Monochrome|Sepia|Eterna|ETERNA)(?:\b|[+/ ])/i;
export function extractRecipes(html,item){const content=body(html),found=[];const titleMatch=html.match(/<h1\b[^>]*class=["'][^"']*entry-title[^"']*["'][^>]*>([\s\S]*?)<\/h1>/i);const fallbackName=titleMatch?plain(titleMatch[1]).join(' ').split(/\s+[—–]\s+/)[0]:'';
    for(const m of content.matchAll(/<p\b[^>]*>([\s\S]*?)<\/p>/gi)){
        const lines=plain(m[1]);if(lines.filter(l=>settingKey.test(l)).length<4)continue;
        const raw={};let film='';
        for(const line of lines){if(/^[A-Za-z][A-Za-z0-9 &/()\-]*\s*:/.test(line)){const ix=line.indexOf(':');raw[line.slice(0,ix).trim()]=line.slice(ix+1).trim()}else if(line.length<130&&filmLine.test(line))film=line;}
        film=raw['Film Simulation']||film;
        // Some posts print the simulation immediately before the settings paragraph.
        if(!film){const before=plain(content.slice(Math.max(0,m.index-1200),m.index));const last=before.at(-1)||'';if(last.length<130&&filmLine.test(last))film=last;}
        const heading=[...content.slice(0,m.index).matchAll(/<h[2-4]\b[^>]*>([\s\S]*?)<\/h[2-4]>/gi)].at(-1);
        const headingName=heading?plain(heading[1]).join(' '):'';
        const missing=[];if(!film)missing.push('Film Simulation');
        if(!Object.keys(raw).some(k=>/^White Balance|^WB$/i.test(k)))missing.push('White Balance');
        found.push({id:item.id+'-'+(found.length+1),name:item.name||fallbackName||film||'Recipe',sourceUrl:item.url,sensors:item.sensors.map(s=>s.replace('fujifilm-','').replace(/-film-simulation-recipes$|-recipes$/,'').toUpperCase()),film,rawSettings:raw,reviewRequired:missing.length>0,missingFields:missing,_heading:headingName});
    }
    if(found.length>1)for(const r of found)r.name=r._heading&&r._heading.length<100?r._heading:(r.film||item.name||'Recipe')+' · biến thể '+r.id.split('-').at(-1);
    for(const r of found)delete r._heading;
    return found;
}
if(process.argv[2]==='extract'){
    const list=JSON.parse(fs.readFileSync(local('tools/urls.json'),'utf8')),recipes=[],unresolved=[];
    for(const item of list){const file=local('tools/cache/'+item.id+'.html');if(!fs.existsSync(file)){unresolved.push({...item,reason:'download_failed'});continue;}const records=extractRecipes(fs.readFileSync(file,'utf8'),item);if(!records.length)unresolved.push({...item,reason:'no_structured_recipe_found'});recipes.push(...records);}
    const data={schemaVersion:1,source:'Fuji X Weekly public website',indexUrl:'https://fujixweekly.com/recipes/',retrievedOn:'2026-10-08',coverage:'Public articles linked by the 10 sensor / IR / video indexes. Not an export of the app; excludes inaccessible Patron recipes. Extracted automatically; verify source before camera use.',articleCount:list.length,recipeCount:recipes.length,recipes,unresolved};
    fs.mkdirSync(local('app/src/main/assets'),{recursive:true});fs.writeFileSync(local('app/src/main/assets/recipes.json'),JSON.stringify(data,null,2));
    fs.writeFileSync(local('IMPORT-REPORT.json'),JSON.stringify({articleCount:list.length,recipeCount:recipes.length,reviewRequired:recipes.filter(r=>r.reviewRequired).map(r=>({id:r.id,name:r.name,missing:r.missingFields,sourceUrl:r.sourceUrl})),unresolved},null,2));console.log(JSON.stringify({articles:list.length,recipes:recipes.length,reviewRequired:recipes.filter(r=>r.reviewRequired).length,unresolved:unresolved.length}));
}

