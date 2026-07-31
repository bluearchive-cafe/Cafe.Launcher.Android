---
last_verified: 2026-06-27
scope: Material Design 3 color system, tonal palettes, dark theme, dynamic color
requires_current_verification: false
---

# Material Design 3 — Color System Deep Dive

## Table of contents

- [Tonal palette generation](#tonal-palette-generation)
- [Color role assignment](#color-role-assignment)
- [Light theme mapping](#light-theme-mapping)
- [Dark theme mapping](#dark-theme-mapping)
- [Dynamic color (Material You)](#dynamic-color)
- [Contrast requirements](#contrast-requirements)
- [High-contrast mode](#high-contrast-mode)
- [Brand color strategy](#brand-color-strategy)
- [Common mistakes](#common-mistakes)

---

## Tonal palette generation

M3 generates a tonal palette from a seed color using the HCT (Hue-Chroma-Tone) color
space. The palette spans 13 tones (0–100) for each key color.

### Key colors and their roles

| Key Color | Role |
|-----------|------|
| **Primary** | Main brand color. Highest emphasis. |
| **Secondary** | Complementary to primary. Used less prominently. |
| **Tertiary** | Additional accent. Used sparingly for variety. |
| **Neutral** | Backgrounds, surfaces, text, icons. |
| **Neutral Variant** | Subtle variation for outlines, weaker surfaces. |
| **Error** | Destructive actions, error states, validation failures. |

### Generating a tonal palette

Use the [Material Theme Builder](https://m3.material.io/theme-builder) or the
[`material-color-utilities`](https://github.com/material-foundation/material-color-utilities)
library:

```javascript
import { Hct, TonalPalette } from '@material/material-color-utilities';

const seed = Hct.fromInt(0xFF6750A4); // #6750A4
const primaryPalette = TonalPalette.of(seed.hue, seed.chroma);
primaryPalette.get(40);  // Primary tone 40
primaryPalette.get(90);  // Primary tone 90
```

### Tone usage pattern

For light theme, key tones: 40 (high-emphasis container fill), 80–90 (low-emphasis
container fill), 100 (white/on-color content).

For dark theme, key tones: 80 (high-emphasis content on dark), 20–30 (containers on
dark surfaces), 10 (near-black).

---

## Color role assignment

### Primary group

```
Light theme:                      Dark theme:
  primary          → tone 40        primary          → tone 80
  onPrimary        → tone 100       onPrimary        → tone 20
  primaryContainer → tone 90        primaryContainer → tone 30
  onPrimaryContainer → tone 10      onPrimaryContainer → tone 90
  inversePrimary   → tone 80        inversePrimary   → tone 40
```

### Secondary group

```
Light theme:                      Dark theme:
  secondary         → tone 40       secondary         → tone 80
  onSecondary       → tone 100      onSecondary       → tone 20
  secondaryContainer → tone 90      secondaryContainer → tone 30
  onSecondaryContainer → tone 10    onSecondaryContainer → tone 90
```

### Tertiary group

Same pattern as secondary, but using the tertiary palette.

### Error group

```
Light theme:                      Dark theme:
  error             → tone 40       error             → tone 80
  onError           → tone 100      onError           → tone 20
  errorContainer    → tone 90       errorContainer    → tone 30
  onErrorContainer  → tone 10       onErrorContainer  → tone 90
```

### Surface group

```
Light theme:                      Dark theme:
  surface                   → neutral 98    surface                   → neutral 6
  onSurface                 → neutral 10    onSurface                 → neutral 90
  surfaceVariant            → neutralV 90   surfaceVariant            → neutralV 30
  onSurfaceVariant          → neutralV 30   onSurfaceVariant          → neutralV 80
  surfaceContainerLowest    → neutral 100   surfaceContainerLowest    → neutral 4
  surfaceContainerLow       → neutral 96    surfaceContainerLow       → neutral 10
  surfaceContainer          → neutral 94    surfaceContainer          → neutral 12
  surfaceContainerHigh      → neutral 92    surfaceContainerHigh      → neutral 17
  surfaceContainerHighest   → neutral 90    surfaceContainerHighest   → neutral 22
  inverseSurface            → neutral 20    inverseSurface            → neutral 90
  inverseOnSurface          → neutral 95    inverseOnSurface          → neutral 20
```

### Outline group

```
Light theme:                      Dark theme:
  outline        → neutralV 50     outline        → neutralV 60
  outlineVariant → neutralV 80     outlineVariant → neutralV 30
```

### Utility

```
  scrim  → neutral 0 (always)
  shadow → neutral 0 (always)
```

---

## Light theme mapping

Full CSS example for a light theme:

```css
:root {
  /* Primary */
  --md-sys-color-primary: #6750A4;
  --md-sys-color-on-primary: #FFFFFF;
  --md-sys-color-primary-container: #EADDFF;
  --md-sys-color-on-primary-container: #21005D;

  /* Secondary */
  --md-sys-color-secondary: #625B71;
  --md-sys-color-on-secondary: #FFFFFF;
  --md-sys-color-secondary-container: #E8DEF8;
  --md-sys-color-on-secondary-container: #1D192B;

  /* Tertiary */
  --md-sys-color-tertiary: #7D5260;
  --md-sys-color-on-tertiary: #FFFFFF;
  --md-sys-color-tertiary-container: #FFD8E4;
  --md-sys-color-on-tertiary-container: #31111D;

  /* Error */
  --md-sys-color-error: #B3261E;
  --md-sys-color-on-error: #FFFFFF;
  --md-sys-color-error-container: #F9DEDC;
  --md-sys-color-on-error-container: #410E0B;

  /* Surface */
  --md-sys-color-surface: #FEF7FF;
  --md-sys-color-on-surface: #1D1B20;
  --md-sys-color-surface-variant: #E7E0EC;
  --md-sys-color-on-surface-variant: #49454F;
  --md-sys-color-surface-container-lowest: #FFFFFF;
  --md-sys-color-surface-container-low: #F7F2FA;
  --md-sys-color-surface-container: #F3EDF7;
  --md-sys-color-surface-container-high: #ECE6F0;
  --md-sys-color-surface-container-highest: #E6E0E9;

  /* Outline */
  --md-sys-color-outline: #79747E;
  --md-sys-color-outline-variant: #CAC4D0;

  /* Utility */
  --md-sys-color-scrim: #000000;
  --md-sys-color-shadow: #000000;
  --md-sys-color-inverse-surface: #322F35;
  --md-sys-color-inverse-on-surface: #F5EFF7;
  --md-sys-color-inverse-primary: #D0BCFF;
}
```

---

## Dark theme mapping

```css
@media (prefers-color-scheme: dark) {
  :root {
    /* Primary */
    --md-sys-color-primary: #D0BCFF;
    --md-sys-color-on-primary: #381E72;
    --md-sys-color-primary-container: #4F378B;
    --md-sys-color-on-primary-container: #EADDFF;

    /* Secondary */
    --md-sys-color-secondary: #CCC2DC;
    --md-sys-color-on-secondary: #332D41;
    --md-sys-color-secondary-container: #4A4458;
    --md-sys-color-on-secondary-container: #E8DEF8;

    /* Tertiary */
    --md-sys-color-tertiary: #EFB8C8;
    --md-sys-color-on-tertiary: #492532;
    --md-sys-color-tertiary-container: #633B48;
    --md-sys-color-on-tertiary-container: #FFD8E4;

    /* Error */
    --md-sys-color-error: #F2B8B5;
    --md-sys-color-on-error: #601410;
    --md-sys-color-error-container: #8C1D18;
    --md-sys-color-on-error-container: #F9DEDC;

    /* Surface */
    --md-sys-color-surface: #141218;
    --md-sys-color-on-surface: #E6E0E9;
    --md-sys-color-surface-variant: #49454F;
    --md-sys-color-on-surface-variant: #CAC4D0;
    --md-sys-color-surface-container-lowest: #0F0D13;
    --md-sys-color-surface-container-low: #1D1B20;
    --md-sys-color-surface-container: #211F26;
    --md-sys-color-surface-container-high: #2B2930;
    --md-sys-color-surface-container-highest: #36343B;

    /* Outline */
    --md-sys-color-outline: #938F99;
    --md-sys-color-outline-variant: #49454F;

    /* Utility */
    --md-sys-color-inverse-surface: #E6E0E9;
    --md-sys-color-inverse-on-surface: #322F35;
    --md-sys-color-inverse-primary: #6750A4;
  }
}
```

---

## Dynamic color (Material You)

Dynamic color extracts a source color from the user's wallpaper and generates a full
light + dark color scheme. Available natively on Android 12+.

### When to use dynamic color

- **Use**: Android apps where brand identity is secondary to user personalization.
  Media players, note apps, weather widgets, personal tools.
- **Don't use**: Brand-critical apps (banking, e-commerce with strict brand guidelines),
  cross-platform apps where Android-only dynamic color creates inconsistency.

### Implementation (Android Compose)

```kotlin
// Let the system handle dynamic color
DynamicTheme {
    MaterialTheme(
        colorScheme = if (useDynamicColor) {
            dynamicLightColorScheme(LocalContext.current)
        } else {
            lightColorScheme(/* brand colors */)
        }
    ) {
        AppContent()
    }
}
```

---

## Contrast requirements

### WCAG AA minimums

| Element | Ratio | Notes |
|---------|-------|-------|
| Body text (< 18pt / 24px) | ≥ 4.5:1 | Applies to most content |
| Large text (≥ 18pt bold or 24pt) | ≥ 3:1 | Headlines, display text |
| UI components / icons | ≥ 3:1 | Buttons, inputs, icons |
| Disabled text | No requirement | But must be distinguishable |

### Checking contrast

Use the Material Theme Builder's built-in contrast checker, browser DevTools, or:

- [WebAIM Contrast Checker](https://webaim.org/resources/contrastchecker/)
- Figma plugins: Stark, Contrast, Able
- Code: `material-color-utilities` contrast API

---

## High-contrast mode

For users who need enhanced contrast (low vision, outdoor use):

- Increase primary/secondary/tertiary saturation.
- Shift surface tones further apart (wider container hierarchy spread).
- Darken outline colors.
- Increase text-to-background contrast beyond minimum.
- Test with system high-contrast mode enabled.

---

## Brand color strategy

### Strong brand identity

1. Use the exact brand color as the seed.
2. Generate the full tonal palette from it.
3. Map to color roles using the standard algorithm.
4. Verify that the generated palette works in both light and dark themes.
5. If contrast is insufficient, adjust the tonal assignments for specific roles.

### Subtle brand presence

1. Use a neutral/near-brand color as the seed.
2. Let the primary be a softer expression of the brand.
3. Reserve brand-intense color for key moments (logo, splash, hero).

---

## Common mistakes

1. **Using raw hex values instead of tokens** — blocks theme switching.
2. **Same primary in light and dark** — the primary tone must shift (40 → 80 typical).
3. **Not testing surface container contrast** — cards and sheets should be distinguishable
   from the page background.
4. **Pure black surfaces in dark theme** — use tonal neutrals (neutral 4–22) instead.
5. **Relying on shadows in dark theme** — shadows are invisible; use surface tones and
   borders instead.
6. **Skipping onPrimary contrast check** — text on primary-colored backgrounds must meet
   minimum contrast; this often fails with lighter primary colors.
7. **Mixing color systems** — using M3 roles in some places and hardcoded colors in
   others creates the worst of both worlds.
