import { processPixels, rotatePixels90, computeHistogramAndStats } from './engine.mjs';

self.onmessage = ({ data: d }) => {
  try {
    const rotated = d.settings?.rotation
      ? rotatePixels90(d.pixels, d.width, d.height, d.settings.rotation)
      : { pixels: d.pixels, width: d.width, height: d.height };
    const pixels = processPixels(rotated.pixels, rotated.width, rotated.height, d.settings, d.lut, d.frameSeed || 0);
    const hist = d.withHistogram ? computeHistogramAndStats(pixels) : null;
    self.postMessage(
      { id: d.id, pixels, width: rotated.width, height: rotated.height, hist },
      [pixels.buffer]
    );
  } catch (e) {
    self.postMessage({ id: d.id, error: e.message });
  }
};
