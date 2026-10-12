// Original, editable vector mark. Regenerate Android vectors and SVG with Node.
import fs from 'node:fs';
const root=new URL('../app/src/main/res/',import.meta.url);
const colors=['#D7E9AE','#C3DDA3','#A6C798','#83AE8D','#679380','#E5EDC6'];
const paths=[];
for(let i=0;i<6;i++){
 const angle=i*Math.PI/3;
 const points=[[0,-29],[23,-15],[13,-6],[0,-16]].map(([x,y])=>[+(54+x*Math.cos(angle)-y*Math.sin(angle)).toFixed(3),+(54+x*Math.sin(angle)+y*Math.cos(angle)).toFixed(3)]);
 paths.push({color:colors[i],points,d:'M'+points.map(p=>p.join(',')).join(' L')+' Z'});
}
const letter='M54,43 A3,3 0,1 1,53.999,43 Z M51,53 L57,53 L57,66 L51,66 Z';
const vector=(mono=false)=>`<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n${paths.map(p=>`    <path android:fillColor="${mono?'#FFFFFF':p.color}" android:pathData="${p.d}"/>`).join('\n')}\n    <path android:fillColor="${mono?'#FFFFFF':'#F4F1DE'}" android:pathData="${letter}"/>\n</vector>\n`;
fs.mkdirSync(new URL('drawable/',root),{recursive:true});fs.writeFileSync(new URL('drawable/ic_iro_foreground.xml',root),vector());fs.writeFileSync(new URL('drawable/ic_iro_monochrome.xml',root),vector(true));
fs.writeFileSync(new URL('iro-icon.svg',import.meta.url),`<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" viewBox="0 0 108 108"><rect width="108" height="108" rx="25" fill="#17251F"/>${paths.map(p=>`<path fill="${p.color}" d="${p.d}"/>`).join('')}<path fill="#F4F1DE" d="${letter}"/></svg>`);
fs.writeFileSync(new URL('icon-geometry.json',import.meta.url),JSON.stringify(paths));
