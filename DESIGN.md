---
name: IRO Playful Classic Claymorphism
description: Soft 3D inflated surfaces, dual inset specular bevels, candy pastel tones and tactile squishy depth across Web, Android, Desktop, and iOS.
colors:
  primary: "#ff6b8b"
  paper: "#ebf1fa"
  panel: "#ffffff"
  raised: "#f5f8fd"
  ink: "#2c3e50"
  muted: "#7f8c8d"
  border: "#d2dbe8"
  stage: "#e4ebf5"
  focus: "#ff6b8b"
  amber: "#feca57"
  sky: "#48dbfb"
  mint: "#1dd1a1"
  grape: "#9b59b6"
  dark-paper: "#1e1f29"
  dark-panel: "#282a37"
  dark-raised: "#323546"
  dark-ink: "#f8f9fd"
  dark-muted: "#a0a6b8"
  dark-border: "#3d4256"
  dark-primary: "#ff7597"
  dark-stage: "#16171f"
typography:
  headline:
    fontFamily: "Google Sans Flex, system-ui, sans-serif"
    fontSize: "clamp(28px, 3.2vw, 42px)"
    lineHeight: 1.16
    letterSpacing: "-0.03em"
  title:
    fontFamily: "Google Sans Flex, system-ui, sans-serif"
    fontSize: "20px"
    letterSpacing: "-0.02em"
  body:
    fontFamily: "Google Sans Flex, system-ui, sans-serif"
    fontSize: "14px"
    lineHeight: 1.5
  recipe-name:
    fontSize: "13px"
    fontWeight: 600
    lineHeight: 1.4
  label:
    fontSize: "12px"
    lineHeight: 1.5
rounded:
  chip: "16px"
  control: "20px"
  card: "28px"
  surface: "32px"
  pill: "9999px"
spacing:
  tight: "8px"
  small: "12px"
  medium: "16px"
  large: "24px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "#ffffff"
    rounded: "{rounded.pill}"
    padding: "12px 24px"
  button-secondary:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.ink}"
    rounded: "{rounded.pill}"
    padding: "10px 18px"
  input:
    backgroundColor: "{colors.raised}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "10px 16px"
  chip:
    textColor: "{colors.ink}"
    rounded: "{rounded.pill}"
    padding: "8px 16px"
  recipe:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.ink}"
    rounded: "{rounded.card}"
    padding: "14px"
---

# Design System: IRO Playful Classic Claymorphism

## Overview

**Creative North Star: "Playful Classic Claymorphism"**

IRO features soft 3D inflated clay surfaces, plump rounded geometry, dual inset specular bevels, and candy pastel accents (Coral `#FF6B8B`, Butter Yellow `#FECA57`, Sky Blue `#48DBFB`, Mint `#1DD1A1`, Lilac `#9B59B6`) laid upon marshmallow cloud foundations (`#EBF1FA` in Light mode, `#1E1F29` in Dark mode).

This unified aesthetic is synchronized 100% across all project branches:
- **Web:** `web/studio.css` & `frontend/studio.css` (Multi-layered CSS box-shadows with dual inset bevels & active squishy sink)
- **Android:** `StudioStyle.java` (`ClayDrawable` with outer soft drop shadow, ambient glow, and dual specular bevels)
- **Desktop:** `DesktopWidgets.java` (Java Swing `Graphics2D` rendering with 3D rounded card panels and tactile pill buttons)
- **iOS:** `Studio.swift` (SwiftUI multi-layered shadow overlays, gradient specular borders, and spring haptic feedback)

## Key Characteristics

1. **Candy Pastel Palette:**
   - Light Mode: Powder Cloud Ground `#EBF1FA`, Plump Clay Surface `#FFFFFF`, Coral `#FF6B8B`, Butter `#FECA57`, Sky `#48DBFB`, Mint `#1DD1A1`.
   - Dark Mode: Dark Slate Clay Ground `#1E1F29`, Clay Elevated Surface `#282A37`, Neon Coral `#FF7597`, Butter `#FED36E`, Sky `#54E1FF`, Mint `#2EE6B5`.

2. **3D Extrusion & Dual Inset Bevels:**
   - Exterior: Soft diffuse drop shadow downwards-right combined with a gentle ambient highlight upwards-left.
   - Interior: Bright specular reflection along the top-left inner border, paired with subtle depth shadow along the bottom-right.

3. **Chunky Plump Geometry:**
   - Extra-large corner radiuses: Cards `24px - 32px`, Bento blocks `28px`, Pill controls `9999px` (full capsules).
   - Chunky touch targets (>= 44px) and prominent slider thumbs.

4. **Squishy Tactile Interactions:**
   - Active / Pressed states sink down by 1-2px with `scale(0.96 - 0.97)`.
   - Inner bevel inverts to a sunken cavity (dark inner shade top-left, subtle shine bottom-right).
