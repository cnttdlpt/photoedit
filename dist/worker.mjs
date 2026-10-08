import {processPixels} from './engine.mjs';
self.onmessage=({data:d})=>{try{const pixels=processPixels(d.pixels,d.width,d.height,d.settings,d.lut);self.postMessage({id:d.id,pixels,width:d.width,height:d.height},[pixels.buffer])}catch(e){self.postMessage({id:d.id,error:e.message})}};
