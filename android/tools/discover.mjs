import fs from 'node:fs';
import {fileURLToPath} from 'node:url';import path from 'node:path';
const toolDir=path.dirname(fileURLToPath(import.meta.url));
const html=fs.readFileSync(process.argv[2]||path.join(toolDir,'cache/index.html'),'utf8');
const links=[...new Set([...html.matchAll(/href=["'](https:\/\/fujixweekly\.com\/[^"']+)["']/g)].map(m=>m[1]))];
const categories=links.filter(u=>/^https:\/\/fujixweekly.com\/(fujifilm-[^/]+recipes|full-spectrum-recipes|video-recipes)\/$/.test(u)).map(url=>({id:url.split('/')[3],url}));
fs.writeFileSync(path.join(toolDir,'categories.json'),JSON.stringify(categories,null,2));console.log(categories);
