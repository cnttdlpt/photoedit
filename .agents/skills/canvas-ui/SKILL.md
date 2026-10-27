---
name: canvas-ui
description: Creative WebGL and WebGPU canvas effects that run directly over live HTML DOM using the HTML-in-canvas API. Text remains selectable, buttons stay clickable, while fluid simulations, refractive glass, force fields, and particles distort the interface in real time. Use this skill when building bleeding-edge interactive landing pages, creative developer tools, WebGL background effects, or shader-driven UI.
---

# Canvas UI — Live Interactive WebGL & Shader UI

A library of creative, framework-agnostic components drawn on HTML `<canvas>` using WebGL/WebGPU shaders via the experimental HTML-in-canvas pipeline.

## Key Technical Innovation

Unlike traditional 3D/canvas libraries that isolate elements from the DOM, Canvas UI reads and redraws the live DOM inside WebGL shaders:
- Text remains fully selectable and accessible.
- Inputs, links, and buttons remain fully clickable and interactive.
- The interface itself becomes a live texture that fire, glass refraction, liquid physics, and force-fields distort in real time.
- Graceful GPU overlay fallbacks for environments without HTML-in-canvas support.

## Available Shader Demos & Components (`src/demos/`)

| Shader Effect | File | Description |
| :--- | :--- | :--- |
| **Liquid & Droplets** | `liquid-demo.tsx`, `droplets-demo.tsx`, `ripple-demo.tsx` | Viscous fluid dynamics, rain surface ripples, and flowing liquid distortion. |
| **Glass & Refraction** | `glass-demo.tsx`, `frost-demo.tsx`, `magnify-demo.tsx` | Realistic optical refraction, frosted glass blur, and interactive magnifying glass lens. |
| **Particles & Force Fields** | `particle-reveal-demo.tsx`, `particle-scroll-demo.tsx`, `force-field-demo.tsx` | Particle streams, scroll-driven particle disintegration, and cursor force repulsors. |
| **Retro & Dither** | `retro-dither-demo.tsx`, `asciify-demo.tsx`, `vhs-demo.tsx` | Retro Macintosh dither matrices, real-time ASCII conversion, and VHS tape glitch. |
| **Elements & Physics** | `flame-wrap-demo.tsx`, `cloth-demo.tsx`, `clouds-demo.tsx` | Interactive fire enveloping UI elements, tearing cloth physics, and atmospheric volumetric clouds. |
| **Shatter & Peel** | `shatter-demo.tsx`, `peel-demo.tsx`, `displacement-demo.tsx` | Breaking UI into glass shards on click, peeling sticker layers, and heightmap displacement. |

## How to Integrate Canvas UI Shaders

1. Check `src/demos/<effect-demo>.tsx` for the component wiring and shader uniforms.
2. In React/Next.js/HTML, embed the canvas viewport over the target container.
3. Configure interactive props:
   - `intensity`: strength of the refractive/particle distortion.
   - `speed`: flow velocity of the physics simulation.
   - `color`: shader color grading or tint.
   - `mouseReactive`: whether cursor distance accelerates or repels the effect.
