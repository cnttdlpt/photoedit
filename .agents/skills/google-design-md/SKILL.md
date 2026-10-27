---
name: google-design-md
description: Official Google Labs specification, token syntax, and guidelines for authoring DESIGN.md files. Use when formatting, validating, structuring, or updating DESIGN.md files to adhere to Google Stitch design token schema (YAML frontmatter, interleaved token blocks, token references).
---

# Google DESIGN.md Specification & Reference

The official plain-text design system format introduced by Google Stitch and Google Labs, bridging human design rationale and AI coding agents.

## Specification Overview

A valid `DESIGN.md` combines **human-readable markdown prose** with **machine-readable YAML design tokens**.

### Token Placement

Tokens may be defined in two ways:
1. **YAML Front Matter:** At the top of the file enclosed between `---` markers.
2. **Interleaved YAML Code Blocks:** Fenced ` ```yaml ` code blocks embedded in their respective markdown sections.

Both formats can coexist. All YAML blocks are parsed and merged into a single design token tree.

### Core Token Categories

```yaml
---
name: "Product Design System"
version: "1.0.0"
description: "Core visual language and tokens."

colors:
  primary: "#1A73E8"
  paper: "#FFFFFF"
  panel: "#F8F9FA"
  ink: "#202124"
  muted: "#5F6368"
  border: "#DADCE0"
  dark-paper: "#121212"
  dark-panel: "#1E1E1E"
  dark-ink: "#E8EAED"
  dark-muted: "#9AA0A6"

typography:
  headline:
    fontFamily: "Google Sans Flex, sans-serif"
    fontSize: "32px"
    lineHeight: 1.2
    letterSpacing: "-0.02em"
  body:
    fontFamily: "Google Sans Flex, sans-serif"
    fontSize: "14px"
    lineHeight: 1.5

rounded:
  chip: "8px"
  control: "12px"
  card: "24px"
  pill: "9999px"

spacing:
  tight: "8px"
  small: "12px"
  medium: "16px"
  large: "24px"

components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "#FFFFFF"
    rounded: "{rounded.pill}"
    padding: "10px 20px"
---
```

## Token Cross-Referencing Syntax

Use `{path.to.token}` syntax to reference previously defined tokens (e.g. `backgroundColor: "{colors.primary}"` or `rounded: "{rounded.card}"`).

## Documentation Files
- Detailed specification: `docs/spec.md`
- Token schema examples: `examples/`
