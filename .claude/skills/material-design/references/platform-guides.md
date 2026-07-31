---
last_verified: 2026-06-27
scope: Material Design platform implementation guidance — verify library versions before use
requires_current_verification: true
---

# Material Design — Platform Implementation Guides

## Platform support rule

When platform or library support affects the answer, verify the current official docs,
release notes, or repository state before giving version-specific guidance. Do not
hard-code current versions in this file — always note that library support must be
verified at the time of use.

A component library is an **implementation**, not the specification. Always distinguish:
- Material Design guideline → what the spec says.
- Library implementation → what the library actually supports.
- Project-specific design system → your team's chosen subset.
- Custom overrides → intentional deviations.

## Table of contents

- [Platform selection flow](#platform-selection-flow)
- [Android — Jetpack Compose](#android---jetpack-compose)
- [Android — Views (MDC)](#android---views-mdc)
- [Flutter](#flutter)
- [Web — CSS Custom Properties](#web---css-custom-properties)
- [Web — Material Web Components](#web---material-web-components)
- [React — MUI](#react---mui)
- [Angular — Angular Material](#angular---angular-material)
- [Vue — Vuetify](#vue---vuetify)
- [Cross-platform token management](#cross-platform-token-management)
- [Version compatibility matrix](#version-compatibility-matrix)

---

## Platform selection flow

```
New project?
├─ Android → Jetpack Compose + Material 3; best-supported path for M3/M3 Expressive,
│  but verify current stable vs experimental API status
├─ iOS → SwiftUI native components or Flutter for cross-platform
├─ Flutter → Material 3 built-in (`useMaterial3: true`)
├─ Web
│   ├─ React → MUI (M2, map M3 tokens) or community M3 library
│   ├─ Angular → Angular Material + CDK (M2, map M3 tokens)
│   ├─ Vue → Vuetify (check M3 version support)
│   ├─ Svelte → SMUI or custom with CSS tokens
│   └─ Framework-agnostic → Material Web Components (M3 core, maintenance mode; verify before adopting)
└─ Cross-platform → Flutter (mobile + web) or Compose Multiplatform
```

---

## Android — Jetpack Compose

### Setup

**Verify the current stable Compose Material3 version before using these artifacts.**
Check [Android Developers release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)
for the latest stable version and experimental API status.

```kotlin
// build.gradle.kts (Kotlin DSL)
dependencies {
    // Verify current stable version at:
    // https://developer.android.com/jetpack/androidx/releases/compose-material3
    implementation("androidx.compose.material3:material3:<current-stable>")
    // For M3 Expressive (check if stable or experimental):
    implementation("androidx.compose.material3:material3-expressive:<current-stable>")
}
```

### Theme definition

```kotlin
import androidx.compose.material3.*

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    // ... all roles
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    // ... all roles
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
```

### Dynamic color

```kotlin
@Composable
fun AppTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

### M3 Expressive theme

```kotlin
import androidx.compose.material3.expressive.*

MaterialExpressiveTheme(
    colorScheme = AppColorScheme,
    typography = ExpressiveTypography,
    shapes = ExpressiveShapes,
    motionScheme = ExpressiveMotionScheme  // spring-based
) {
    AppContent()
}
```

### Key rules for Compose

1. Always use `MaterialTheme.colorScheme.primary`, never `Color(0xFF...)` in components.
2. Use `Surface` composable at the root — it sets proper background + content colors.
3. Prefer `Button`, `OutlinedButton`, `TextButton` over raw clickable containers.
4. Test with `isSystemInDarkTheme()` and dynamic color enabled/disabled.
5. For M3 Expressive, use spring motion APIs (`animate*AsState` with spring spec).

---

## Android — Views (MDC)

MDC-Android is the Material Components library for traditional Android Views.
It has M2 as its baseline with M3 support available through theme bridging.
Verify the current MDC-Android version and its M3 theme capabilities in the
[official release notes](https://github.com/material-components/material-components-android/releases).

### Setup

```groovy
// build.gradle
dependencies {
    implementation 'com.google.android.material:material:1.13.0'
}
```

### M3 bridge

Use `Material3` theme in XML or programmatically:

```xml
<style name="Theme.MyApp" parent="Theme.Material3.Light.NoActionBar">
    <item name="colorPrimary">@color/m3_primary</item>
    <item name="colorOnPrimary">@color/m3_on_primary</item>
    <!-- ... -->
</style>
```

### Migration from M2 to M3 (Views)

1. Switch theme parent from `Theme.MaterialComponents.*` to `Theme.Material3.*`.
2. Map M2 color attributes to M3 roles.
3. Update component styles to M3 variants.
4. Test all states, especially dark theme and elevation.

---

## Flutter

Flutter has built-in Material Design support through its Material library.
Core M3 support is stable via `useMaterial3: true`. M3 Expressive features (spring
physics, shape morph, new components) may lag behind Compose — verify current
Flutter SDK release notes for M3 Expressive availability.

### Setup

```dart
MaterialApp(
  theme: ThemeData(
    useMaterial3: true,
    colorSchemeSeed: Color(0xFF6750A4),
    // Or explicit ColorScheme:
    // colorScheme: ColorScheme.fromSeed(seedColor: Color(0xFF6750A4)),
    brightness: Brightness.light,
  ),
  darkTheme: ThemeData(
    useMaterial3: true,
    colorSchemeSeed: Color(0xFF6750A4),
    brightness: Brightness.dark,
  ),
  home: MyHomePage(),
)
```

### Explicit ColorScheme

```dart
const colorScheme = ColorScheme(
  brightness: Brightness.light,
  primary: Color(0xFF6750A4),
  onPrimary: Color(0xFFFFFFFF),
  primaryContainer: Color(0xFFEADDFF),
  onPrimaryContainer: Color(0xFF21005D),
  // ... all 30+ roles
);
```

### Key rules for Flutter

1. Set `useMaterial3: true` — this enables M3 component visuals globally.
2. Use `colorSchemeSeed` for quick setup; use explicit `ColorScheme` for precise control.
3. Access colors via `Theme.of(context).colorScheme.primary`.
4. Flutter M3 support is mature for core components; newer M3 Expressive features
   (spring physics, shape morph) may lag behind Compose.

---

## Web — CSS Custom Properties

### Use for: framework-agnostic sites, static sites, design system proofs

### Setup

```css
/* tokens.css */
:root {
  --md-sys-color-primary: #6750A4;
  --md-sys-color-surface: #FEF7FF;
  --md-sys-typescale-body-medium-size: 0.875rem;
  --md-sys-shape-corner-medium: 12px;
  --md-sys-spacing-4: 1rem;
  --md-sys-motion-duration-medium2: 300ms;
}

/* component.css */
.md-button--filled {
  background: var(--md-sys-color-primary);
  color: var(--md-sys-color-on-primary);
  border-radius: var(--md-sys-shape-corner-full);
  padding: 10px 24px;
  min-height: 40px;
  font-family: var(--md-sys-typescale-label-large-font);
  font-size: var(--md-sys-typescale-label-large-size);
  transition: box-shadow var(--md-sys-motion-duration-short3)
              var(--md-sys-motion-easing-standard);
}

.md-button--filled:hover {
  box-shadow: var(--md-sys-elevation-level1);
}

.md-button--filled:focus-visible {
  outline: 3px solid var(--md-sys-color-primary);
  outline-offset: 2px;
}
```

### Dark theme

```css
@media (prefers-color-scheme: dark) {
  :root {
    --md-sys-color-primary: #D0BCFF;
    --md-sys-color-surface: #141218;
    /* ... swap all roles */
  }
}

/* Or with a class toggle */
[data-theme="dark"] {
  --md-sys-color-primary: #D0BCFF;
  /* ... */
}
```

### Reduced motion

```css
@media (prefers-reduced-motion: reduce) {
  :root {
    --md-sys-motion-duration-medium2: 0ms;
    --md-sys-motion-duration-long2: 0ms;
  }
}
```

---

## Web — Material Web Components

Material Web is Google's official M3 web component library. It is currently in
**maintenance mode** — it implements M3 core but does not implement M3 Expressive
features on the Web. Verify the current maintenance status, component coverage,
and release activity before adopting for production. Check the [Material Web
repository](https://github.com/material-components/material-web) and the
[official M3 Web docs](https://m3.material.io) for the most current status.

### Setup

```bash
npm install @material/web
```

```javascript
import '@material/web/all.js';
// Or cherry-pick:
import '@material/web/button/filled-button.js';
import '@material/web/textfield/outlined-text-field.js';
```

### Usage

```html
<md-filled-button>Save</md-filled-button>
<md-outlined-text-field label="Email" type="email"></md-outlined-text-field>
<md-checkbox touch-target="wrapper" aria-label="Accept terms"></md-checkbox>
```

### Theming

```css
:root {
  --md-sys-color-primary: #6750A4;
  --md-sys-color-surface: #FEF7FF;
  /* Theme entire component set via system tokens */
  --md-filled-button-container-shape: 999px;
  --md-outlined-text-field-outline-color: var(--md-sys-color-outline);
}
```

---

## React — MUI

MUI (Material UI) is the dominant Material Design React library. Verify the current
MUI version and its stated Material Design specification support before claiming
M3 compliance. Historically MUI has been based on M2; check the latest release notes
for M3 theming API updates. For strict M3, map M3 tokens into MUI's `createTheme()`
or evaluate community M3 libraries.

### Setup

```bash
npm install @mui/material @emotion/react @emotion/styled
```

### M3 token mapping with MUI

```jsx
import { createTheme, ThemeProvider } from '@mui/material/styles';

const m3Theme = createTheme({
  palette: {
    primary: {
      main: '#6750A4',        // M3 sys.color.primary
      contrastText: '#FFFFFF', // M3 sys.color.onPrimary
    },
    secondary: {
      main: '#625B71',        // M3 sys.color.secondary
    },
    error: {
      main: '#B3261E',        // M3 sys.color.error
    },
    background: {
      default: '#FEF7FF',     // M3 sys.color.surface
    },
  },
  shape: {
    borderRadius: 12,         // M3 sys.shape.corner.medium
  },
  typography: {
    fontFamily: 'Roboto, sans-serif',
    h1: {                     // M3 sys.typescale.displayLarge
      fontSize: '3.5625rem',
      lineHeight: 64 / 57,
    },
    body1: {                  // M3 sys.typescale.bodyLarge
      fontSize: '1rem',
      lineHeight: 24 / 16,
    },
    button: {                 // M3 sys.typescale.labelLarge
      fontSize: '0.875rem',
      fontWeight: 500,
    },
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: 'none', // M3 uses sentence case, not all caps
          borderRadius: 20,      // M3 pill-shaped buttons
        },
      },
    },
  },
});

function App() {
  return (
    <ThemeProvider theme={m3Theme}>
      <YourApp />
    </ThemeProvider>
  );
}
```

### Important MUI ↔ M3 differences

| Aspect | MUI (M2) | M3 |
|--------|----------|----|
| Button text | UPPERCASE by default | Sentence case |
| Button shape | 4px radius typical | 20–24px (pill shape) typical |
| Color system | primary/main, primary/dark, primary/light | primary, onPrimary, primaryContainer |
| Elevation | Shadow-based | Surface tones + shadows |
| Breakpoints | xs/sm/md/lg/xl | Compact/Medium/Expanded/Large/ExtraLarge |
| Font | Roboto | Variable fonts supported (Roboto Flex) |

---

## Angular — Angular Material

Angular Material and CDK are the primary Material Design options for Angular.
Verify the exact version's M2/M3 theming APIs before giving version-specific guidance.
Angular Material's theming system has evolved — recent versions may include M3-related
capabilities. CDK primitives (Overlay, A11y, Layout, Drag & Drop) are valuable
regardless of Material version.

### Setup

```bash
ng add @angular/material
```

### Angular Material — M3 theming

Use the current Angular Material M3 theming API for the exact version in use.
Refer to the [Angular Material changelog](https://github.com/angular/components/blob/main/CHANGELOG.md)
and [official theming guide](https://material.angular.io/guide/theming) for the
correct M3 theme definition pattern. Do **not** use `m2-define-palette` (or any
`m2-` prefix API) for new M3 themes — those APIs are M2-only.

```scss
@use '@angular/material' as mat;

// Use the current M3 theming API (check exact syntax for your installed version):
// $theme: mat.define-theme((
//   color: (
//     theme-type: light,
//     primary: mat.$chartreuse-palette,
//   ),
//   typography: Roboto,
// ));

// @include mat.all-component-themes($theme);
```

### Angular Material — M2 legacy theming

Use `mat.m2-define-palette`, `mat.m2-define-light-theme`, `mat.m2-define-dark-theme`,
and related `m2-` prefix APIs **only** for M2 legacy themes or when maintaining an
existing M2-based Angular project.

```scss
@use '@angular/material' as mat;

// M2 palette definition (M2 only — do not use for new M3 themes)
$m2-primary: mat.m2-define-palette(mat.$indigo-palette);
$m2-accent: mat.m2-define-palette(mat.$pink-palette, A200, A100, A400);
$m2-warn: mat.m2-define-palette(mat.$red-palette);

$m2-theme: mat.m2-define-light-theme((
  color: (
    primary: $m2-primary,
    accent: $m2-accent,
    warn: $m2-warn,
  )
));

@include mat.all-component-themes($m2-theme);
```

### Key CDK features

Angular CDK provides framework-agnostic primitives:
- **Overlay**: Floating panels, dialogs, tooltips.
- **A11y**: Focus trap, live announcer, list key manager.
- **Drag & Drop**: Reorder lists, move items.
- **Layout**: Breakpoint observer, media matcher.

---

## Vue — Vuetify

Vuetify provides Material-style components for Vue. Verify the installed version's
Material Design specification support level before making M2/M3 claims.
Check [Vuetify release notes](https://vuetifyjs.com/) for the current state
of M3 theming and component support.

### Setup

```bash
npm install vuetify
```

```javascript
import { createVuetify } from 'vuetify';
import * as components from 'vuetify/components';

const vuetify = createVuetify({
  theme: {
    defaultTheme: 'light',
    themes: {
      light: {
        colors: {
          primary: '#6750A4',
          'on-primary': '#FFFFFF',
          'primary-container': '#EADDFF',
          surface: '#FEF7FF',
          error: '#B3261E',
        },
      },
    },
  },
});
```

---

## Cross-platform token management

### Recommended approach

Maintain a single source of truth for design tokens (JSON or YAML), then generate
platform-specific output:

```
tokens.json  →  style-dictionary / token-transformer
    ├── css/variables.css
    ├── kotlin/Color.kt
    ├── swift/Colors.swift
    ├── dart/colors.dart
    └── figma/export.json
```

### Tools

- **Style Dictionary**: Industry standard. Define tokens once, output to any platform.
- **Token Transformer / Tokens Studio**: Figma → code pipeline.
- **Material Theme Builder**: Official tool for M3 token export (Android, Flutter, CSS, JSON).
- **design-token-bridge-mcp**: AI-friendly token translation between platforms.

### Example: Style Dictionary config

```json
{
  "source": ["tokens/**/*.json"],
  "platforms": {
    "css": {
      "transformGroup": "css",
      "buildPath": "dist/css/",
      "files": [{ "destination": "variables.css", "format": "css/variables" }]
    },
    "compose": {
      "transformGroup": "compose",
      "buildPath": "dist/compose/",
      "files": [{ "destination": "Color.kt", "format": "compose/object" }]
    }
  }
}
```

---

## Version compatibility matrix

**This matrix must be verified against official docs before use. Last verified: 2026-06-27.**

The ecosystem evolves rapidly. Always check the library's current release notes
and official documentation for the most recent Material Design support status.

| Library | Material version support | Verification source | Notes |
|---------|-------------------------|---------------------|-------|
| Jetpack Compose Material 3 | ✅ M3 + Expressive | [AndroidX release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3) | Native Android, best M3+Expressive support |
| MDC-Android | M2 baseline, M3 via theme bridge | [MDC-Android releases](https://github.com/material-components/material-components-android/releases) | Views-based, verify bridge maturity |
| Flutter Material | ✅ M3 core, ⚠️ Expressive TBD | [Flutter release notes](https://docs.flutter.dev/release/release-notes) | Core M3 solid; verify Expressive |
| Material Web | M3 core, maintenance mode; M3 Expressive not implemented on Web | [Material Web repo](https://github.com/material-components/material-web) / [M3 Web docs](https://m3.material.io) | Verify maintenance status and component coverage before production adoption |
| MUI (React) | Primarily M2; verify current version | [MUI releases](https://github.com/mui/material-ui/releases) | Dominant React choice; check M3 status |
| Angular Material | Verify current version theming API | [Angular Material changelog](https://github.com/angular/components/blob/main/CHANGELOG.md) | Check M2/M3 theming capabilities |
| Vuetify (Vue) | Verify current version | [Vuetify releases](https://github.com/vuetifyjs/vuetify/releases) | Check M3 component/theming support |
| MDC Web | Archived / deprecated | [MDC Web repo](https://github.com/material-components/material-components-web) | Use Material Web instead |

### Platform decision checklist

Before recommending a library, verify:

- [ ] What Material Design version does the current library release support?
- [ ] Are M3 Expressive features (spring motion, shape morph, new components) available?
- [ ] Is the library actively maintained? (Check commit frequency, release cadence, issue tracker)
- [ ] Does the library's theming system align with M3 token architecture, or does it need mapping?
- [ ] Are there known gaps in component coverage vs the M3 spec?
