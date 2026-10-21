---
name: IRO Silver Print
description: Quiet photo editing surfaces with clear tools and restrained green selection.
colors:
  primary: "#365f50"
  paper: "#f4f4f2"
  panel: "#ffffff"
  raised: "#f0f1ee"
  ink: "#202322"
  muted: "#606762"
  border: "#d8ddd8"
  stage: "#e8eae6"
  focus: "#29684f"
  dark-paper: "#171918"
  dark-panel: "#202321"
  dark-raised: "#2b2f2c"
  dark-ink: "#f1f3ef"
  dark-muted: "#b1b9b2"
  dark-border: "#414942"
  dark-primary: "#afcbb8"
  dark-stage: "#111312"
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
  chip: "6px"
  control: "8px"
  card: "10px"
  surface: "12px"
spacing:
  tight: "8px"
  small: "12px"
  medium: "16px"
  large: "24px"
components:
  button-primary:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.paper}"
    rounded: "{rounded.control}"
    padding: "10px 18px"
  button-secondary:
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "9px 14px"
  input:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "10px 12px"
  chip:
    textColor: "{colors.muted}"
    rounded: "{rounded.chip}"
    padding: "7px 12px"
  recipe:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "10px"
---

# Design System: IRO Silver Print

## Overview

**Creative North Star: "IRO silver print"**

Quiet neutral paper surfaces frame the photograph. Restrained green selection and clear Vietnamese labels distinguish controls without competing with the image. The current web implementation uses flat surfaces, thin borders and compact rounded controls.

This record describes the implemented web system in `web/studio.css` and `web/index.html`. Native iOS and Android share the intent and vocabulary while retaining native controls; native device appearance has not been verified by this record.

**Key Characteristics:**

- The photograph supplies the colour.
- Neutral surfaces with restrained green selection.
- Clear controls and visible keyboard focus.
- Native platform conventions remain native.

## Colors

### Primary

Muted green identifies selection, focus and secondary actions. Primary call-to-action buttons use ink against paper. The dark theme lightens the green and reverses ink and ground roles.

**The Photo Colour Rule.** Keep interface surfaces neutral so image colour remains easy to judge.

### Neutral

Paper is the page ground, panel is the inspector and field surface, and raised is the subtle hover or selected fill. Ink and muted distinguish main and supporting text. The stage uses a separate quiet grey. Borders define the tool surfaces without relying on shadows.

## Typography

Google Sans Flex is the shared web family with system-ui and sans-serif fallbacks. Headlines are restrained; the editor is governed by body and label sizes rather than display type. Recipe names use medium emphasis with supporting film information beneath. The mobile home headline is fixed at 32px.

**The Readable Label Rule.** Use the label role for supporting recipe information; do not inherit smaller legacy microtext as a new standard.

## Layout

Desktop editor uses a flexible photo stage and 376px inspector, with 24px outer spacing and gap. At 1100px the inspector becomes 340px and spacing becomes 16px. At 760px and below, the stage and inspector stack with 12px spacing. The phone stage is 38dvh with a 260px minimum; the full image stays within its available space. The inspector scrolls within its pane.

The home page is limited to 1200px. Recipe choices form a two-column inline grid in the editor. Four tool tabs share one row; the redundant mobile navigation is hidden. These editor compositions are surface-specific, not requirements for every future IRO screen.

## Elevation & Depth

Stage, controls, buttons and recipe cards are flat. Thin borders and tonal separation express hierarchy. Menus and loading notices alone use a soft neutral shadow to distinguish temporary content. Reduced-motion preference removes CSS animation and transitions; Morphicons respects the user's motion preference.

**The Quiet Surface Rule.** Reuse flat borders and tonal fills for persistent tools; reserve elevation for transient overlays.

## Shapes

Controls use gently rounded corners, chips use a tighter radius, and larger tool surfaces use the surface radius. The image itself has square corners. Thin borders recur on fields, recipe choices and tool surfaces; active tabs use a green bottom line.

## Components

### Buttons

Primary buttons use ink fill; secondary buttons use a transparent ground with a thin border. The main action and icon controls have at least 44px height. Compact filter chips and reset links currently use 36px height. Keyboard focus is a 2px green outline with 3px offset. Disabled buttons reduce opacity and show the default cursor.

### Inputs

Fields use panel fill, ink text, a thin border and control corners. Search stretches across the available inspector width. Placeholder text uses the muted role.

### Chips

Filter chips use neutral text and border. Selected chips gain the raised fill and stronger border. Horizontal overflow remains scrollable.

### Recipe choices

Each recipe is a real button with name and supporting film text. Selected recipes add an accent border and inset outline. Labels wrap; the full catalog remains inline with search rather than requiring a second browsing surface.

### Navigation and icons

Four editor tabs use text and authored Morphicons. Selected state has a bottom line, and the implementation exposes tab semantics. Icon-only header actions retain accessible labels. Preserve emojis within actual recipe names as content; do not use them as new interface icons.

## Do's and Don'ts

- **Do** use semantic light and dark roles for new tools.
- **Do** retain visible focus, readable labels and reduced-motion support.
- **Do** preserve the whole photo in the stage at each viewport.
- **Don't** copy legacy gradient text, coloured glows or decorative emoji into new chrome.
- **Don't** turn this web record into a claim that native device layouts have been verified.
