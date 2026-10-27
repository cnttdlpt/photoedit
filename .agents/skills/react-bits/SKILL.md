---
name: react-bits
description: Production-ready library of 200+ animated React components, interactive UI elements, dynamic backgrounds, text effects, and micro-interactions. Use this skill when implementing animated web components, interactive UI widgets, particle backgrounds, cursor effects, or expressive typography in React, Next.js, or modern frontend stacks.
---

# React Bits — Animated React Component Library

A curated collection of over 200 animated, responsive, and customizable React components built with Framer Motion, GSAP, and Tailwind CSS.

## Component Categories & Highlights

The complete source code for each component is available directly in `src/content/`:

### 1. Animated Backgrounds (`src/content/Backgrounds/`)
- **`Antigravity`** — Floating anti-gravity particles that react to cursor position.
- **`Aurora`** — Mesmerizing flowing northern lights gradient canvas.
- **`Balatro`** — Hypnotic geometric distortion waves inspired by the game Balatro.
- **`ColorBends`** — Liquid multi-color chromatic warping.
- **`Dither` & `DarkVeil`** — Retro 8-bit dithered matrix and dark cinematic atmospheres.
- **`Ferrofluid` & `MoltenMetal`** — Magnetic fluid and iridescent chrome liquid physics.
- **`GridDistortion` & `GridMotion`** — Elastic interactive mesh warping under mouse movement.
- **`LiquidChrome`** — Glossy 3D reflective chrome surfaces.
- **`Particles` & `Orb`** — 3D particle fields and luminous energy spheres.
- **`Waves` & `Silk`** — Soft flowing silk ribbons and organic wave dynamics.

### 2. Interactive Components (`src/content/Components/`)
- **`ElasticSlider`** — Fluid, squishy draggable slider with overshoot bounce.
- **`FluidGlass` & `GlassSurface`** — Apple-style liquid glass refraction panels.
- **`MagicBento`** — Dynamic bento grid with spotlight hover and tilt physics.
- **`PillNav` & `CardNav`** — Smooth sliding pill indicator and floating card navigation.
- **`ReflectiveCard` & `HoloCard`** — 3D holographic and specular light cards.
- **`SpecularButton`** — Clay/glossy 3D button with reactive light sheen.
- **`SpotlightCard`** — Cursor-following ambient radial illumination.
- **`Stack` & `CardSwap`** — Physical card decks with swipe gestures and spring snapping.
- **`TiltedCard`** — 3D parallax tilt on mouse hover with specular flare.

### 3. Text & Typography Animations (`src/content/TextAnimations/`)
- **`BlurText`** — Letter-by-letter blur-to-sharp entrance transition.
- **`DecryptedText`** — Matrix-style character scrambling until resolving to final text.
- **`GlitchText`** — RGB chromatic split glitch animations.
- **`GradientText`** — Shimmering multi-stop animated gradient typography.
- **`ShinyText`** — High-end reflective specular sheen across characters.
- **`TextPressure`** — Variable font weight and optical size reacting to cursor distance.
- **`TrueFocus`** — Camera depth-of-field lens focus shifting across words.
- **`VariableProximity`** — Fluid responsive letter distortion based on cursor proximity.

### 4. Animation Effects (`src/content/Animations/`)
- **`BlobCursor` & `SplashCursor`** — Fluid metaball and splash cursor trails.
- **`ClickSpark`** — Particle burst on user click.
- **`ElectricBorder`** — Arcing electrical glow along element boundaries.
- **`Magnet`** — Magnetic attraction pulling elements toward cursor with spring physics.
- **`PixelTransition`** — 8-bit mosaic tile dissolve transitions.
- **`RippleDistortion`** — Fluid water droplet ripples across images and containers.

## How to Integrate a Component

1. **Locate the component file:**
   Examine `src/content/<Category>/<ComponentName>/<ComponentName>.tsx` (or `.jsx`).
2. **Review dependencies:**
   Most components depend only on standard React + Framer Motion (`framer-motion`) or lightweight canvas shaders.
3. **Copy and adapt:**
   Import or drop the component file into the target project and customize props (speed, colors, amplitude, easing).
