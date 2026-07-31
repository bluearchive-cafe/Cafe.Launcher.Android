---
last_verified: 2026-06-27
scope: Material Design 2 legacy guidance and M2-to-M3 migration reference
requires_current_verification: false
---

# Material Design 2 Reference

## When to use M2

- Maintaining an existing M2 app, website, or design system.
- Using a component library that primarily targets M2 (e.g., MUI v5, earlier Angular
  Material versions).
- The user explicitly says "Material Design 2", "M2", "MD2", or "old Material".
- Migration budget is constrained and full M3 adoption is not feasible short-term.

## M2 source status

Material Design 2 is Google's previous-generation specification. It is no longer the
recommended default for new projects. The M2 specification site (material.io) has been
superseded by m3.material.io. M2 components and patterns remain widely deployed in
production — especially via MUI, Angular Material, and legacy MDC-Android projects.

## M2 vs M3: key differences

| Aspect | M2 | M3 |
|--------|----|----|
| Color system | Primary + PrimaryVariant + Secondary + SecondaryVariant | Tonal palette → color roles (primary, primaryContainer, etc.) |
| Surface | Single `surface` color + shadow elevation | Surface container hierarchy (5 levels) + tonal elevation |
| Typography | h1–h6, subtitle1/2, body1/2, button, caption, overline | Display/Headline/Title/Body/Label × Large/Medium/Small |
| Shape | Limited corner variants | Full shape scale + shape morph animations |
| Motion | Duration + easing curves | Duration/easing + spring physics (Expressive) |
| Dynamic color | Not available | Material You dynamic color (Android 12+) |
| Elevation | Shadow-dominant | Shadow + surface tone + borders |
| Dark theme | Dark surface + elevation overlays (white % opacity) | Tonal neutrals + surface container hierarchy |

## M2 color system

### Primary palette

```
primary       — main brand color, app bar, primary buttons
primaryVariant — darker shade of primary, status bar
secondary     — accent color, FAB, selection controls
secondaryVariant — darker shade of secondary

background    — page background
surface       — card, dialog, menu, sheet backgrounds
error         — error states, destructive actions

onPrimary     — text/icons on primary
onSecondary   — text/icons on secondary
onSurface     — text/icons on surface
onBackground  — text/icons on background
onError       — text/icons on error
```

### M2 color usage

```css
:root {
  --mdc-theme-primary: #6200EE;
  --mdc-theme-primary-variant: #3700B3;
  --mdc-theme-secondary: #03DAC6;
  --mdc-theme-secondary-variant: #018786;
  --mdc-theme-background: #FFFFFF;
  --mdc-theme-surface: #FFFFFF;
  --mdc-theme-error: #B00020;
  --mdc-theme-on-primary: #FFFFFF;
  --mdc-theme-on-secondary: #000000;
  --mdc-theme-on-surface: #000000;
  --mdc-theme-on-error: #FFFFFF;
}
```

### M2 dark theme

M2 dark theme uses:
- `background`: `#121212` (near-black)
- `surface`: `#121212` with semi-transparent white overlay for elevation levels
- Elevation expressed through `rgba(255,255,255, N)` overlays (0%–12% based on dp height)

This is fundamentally different from M3's tonal surface container approach.

## M2 typography system

```
h1        — 96sp  Light     — hero headlines
h2        — 60sp  Light     — major section headers
h3        — 48sp  Regular   — section headers
h4        — 34sp  Regular   — sub-section headers
h5        — 24sp  Regular   — card/dialog titles
h6        — 20sp  Medium    — emphasis in body

subtitle1 — 16sp  Regular   — list item primary text
subtitle2 — 14sp  Medium    — list item secondary emphasis

body1     — 16sp  Regular   — body text, paragraphs
body2     — 14sp  Regular   — secondary body text

button    — 14sp  Medium    — button labels (UPPERCASE by convention)
caption   — 12sp  Regular   — helper text, image captions
overline  — 10sp  Regular   — section headers, small labels (UPPERCASE)
```

### M2 typography rules

- Button text is UPPERCASE by convention (this is an M2 default; M3 uses sentence case).
- `subtitle1` and `subtitle2` are the workhorse styles for list content.
- Body text defaults to `body1` (16sp) for reading comfort.

## M2 shape and elevation

### Shape

M2 uses a simpler shape system:
- Small components (chips, buttons): 4dp radius
- Medium components (cards, dialogs, sheets): 4–8dp radius
- No system-wide shape tokens by default; shape is mostly component-specific

### Elevation (shadow-based)

| Level | dp | Use |
|-------|----|-----|
| 0 | 0dp | Flat content |
| 1 | 1dp | Cards, search bar (resting) |
| 2 | 3dp | FAB (resting), raised button (resting) |
| 3 | 4dp | App bar (resting) |
| 4 | 6dp | App bar (scrolled), menu |
| 6 | 8dp | FAB (pressed), raised button (pressed) |
| 8 | 12dp | Bottom navigation |
| 12 | 16dp | Dialog |
| 16 | 24dp | Navigation drawer, modal bottom sheet |

Elevation is purely shadow-based. In dark theme, shadows are invisible — M2 uses
semi-transparent white overlays on elevated surfaces instead.

## M2 component patterns

### Buttons

| Type | Use |
|------|-----|
| Contained Button | Primary action (equivalent to M3 Filled Button) |
| Outlined Button | Secondary action |
| Text Button | Low-emphasis action |
| FAB | Page-level primary action |
| Icon Button | Toolbar/dense actions |

Note: M2 does not have Filled Tonal, Elevated, or Segmented buttons.
M2 buttons default to UPPERCASE labels.

### Text fields

| Type | Description |
|------|-------------|
| Filled Text Field | Solid fill, more visual weight |
| Outlined Text Field | Border-based, lighter weight |

M2 text fields use a different label animation (label moves up on focus/input).
M3 refined this behavior but the pattern is similar.

### Navigation

| Component | M2 usage |
|-----------|----------|
| Bottom Navigation | 3–5 destinations, icon + label |
| Navigation Drawer | Modal (hamburger) or persistent |
| Tabs | Fixed or scrollable |
| Top App Bar | Standard or prominent |

M2 does not have Navigation Rail — use Bottom Navigation or Drawer for medium screens.

### Dialogs

Same structural rules as M3: title, content, actions (max 2–3). M2 dialogs have
sharper corners (4dp default) and shadow-based elevation.

## M2 theming by platform

### Android (MDC / AppCompat)

```xml
<style name="Theme.MyApp" parent="Theme.MaterialComponents.Light.NoActionBar">
    <item name="colorPrimary">@color/primary</item>
    <item name="colorPrimaryVariant">@color/primary_variant</item>
    <item name="colorSecondary">@color/secondary</item>
    <item name="colorSecondaryVariant">@color/secondary_variant</item>
    <item name="colorSurface">@color/surface</item>
    <item name="colorError">@color/error</item>
</style>
```

### MUI v5 (React)

```jsx
const m2Theme = createTheme({
  palette: {
    primary: { main: '#6200EE', dark: '#3700B3' },
    secondary: { main: '#03DAC6', dark: '#018786' },
    background: { default: '#FFFFFF', paper: '#FFFFFF' },
    error: { main: '#B00020' },
  },
  typography: {
    button: { textTransform: 'uppercase' }, // M2 default
  },
});
```

### Angular Material

```scss
$m2-primary: mat.define-palette(mat.$deep-purple-palette);
$m2-accent: mat.define-palette(mat.$teal-palette);
$m2-theme: mat.define-light-theme((
  color: (primary: $m2-primary, accent: $m2-accent)
));
@include mat.all-component-themes($m2-theme);
```

## M2 accessibility checklist

M2 accessibility requirements are similar to M3, but note:

- M2 elevation cannot be perceived in dark theme without additional overrides.
- M2 outlined text fields have lower contrast borders — verify they meet ≥ 3:1.
- M2 button text is UPPERCASE by default, which can reduce readability for some users.
- M2 does not have built-in dynamic color or high-contrast mode settings.

## M2-to-M3 migration cautions

1. **Color roles don't map 1:1** — M2 `primaryVariant` ≈ M3 `primaryContainer` in
   intent, but the tonal values differ. Migrate via seed color, not direct mapping.
2. **Typography tokens are structurally different** — M2 h1–h6 vs M3 Display–Label.
   Remap by visual size, not by name.
3. **Surface elevation must be re-thought** — M2 shadow elevation doesn't translate
   directly to M3 surface containers. Redesign the visual hierarchy.
4. **Button labels change case** — M2 UPPERCASE → M3 Sentence case. This is a
   content and i18n change, not just CSS.
5. **Component availability differs** — M3 has components M2 doesn't (Navigation Rail,
   Segmented Button, Filled Tonal Button). M2 has patterns M3 deprecated.
6. **Dark theme is fundamentally different** — M2's overlay-based dark elevation vs
   M3's tonal surface containers. Cannot migrate incrementally — pick one system.
7. **Library support varies** — if the component library (MUI, Angular Material,
   Vuetify) doesn't support M3, migration means either: mapping tokens manually,
   switching libraries, or waiting for upstream support.

### Recommended migration approach

1. Audit current M2 components and token usage.
2. Generate M3 color scheme from the same brand seed color.
3. Map typography by visual equivalence (M2 h5 ≈ M3 titleLarge, etc.).
4. Migrate one component family at a time (buttons first, then inputs, then cards, etc.).
5. Run visual regression tests at each step.
6. Ship light theme M3 first; dark theme separately (the systems are too different
   to migrate simultaneously).
