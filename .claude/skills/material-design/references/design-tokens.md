---
last_verified: 2026-06-27
scope: Material Design 3 design token catalog, naming conventions, and platform mapping tables
requires_current_verification: false
---

# Material Design 3 — Design Token Catalog

## Token architecture

```
Reference tokens (raw values)
    ↓
System tokens (semantic roles)
    ↓
Component tokens (element-specific)
```

## Naming convention

```
{layer}.{category}.{role}.{state?}.{property?}

Examples:
  sys.color.primary
  sys.color.onPrimaryContainer
  sys.typescale.titleMedium.size
  comp.button.filled.container.hover
  comp.card.containerShape
```

---

## Color tokens

### Reference palette tokens

Generated from seed color via tonal palette (0–100):
```
ref.palette.primary40, ref.palette.primary100
ref.palette.secondary40, ref.palette.secondary100
ref.palette.tertiary40, ref.palette.tertiary100
ref.palette.neutral0, ref.palette.neutral100
ref.palette.neutralVariant30, ref.palette.neutralVariant90
ref.palette.error40, ref.palette.error100
```

### System color tokens (full set)

```
Primary:
  sys.color.primary
  sys.color.onPrimary
  sys.color.primaryContainer
  sys.color.onPrimaryContainer
  sys.color.inversePrimary

Secondary:
  sys.color.secondary
  sys.color.onSecondary
  sys.color.secondaryContainer
  sys.color.onSecondaryContainer

Tertiary:
  sys.color.tertiary
  sys.color.onTertiary
  sys.color.tertiaryContainer
  sys.color.onTertiaryContainer

Error:
  sys.color.error
  sys.color.onError
  sys.color.errorContainer
  sys.color.onErrorContainer

Surface:
  sys.color.surface
  sys.color.onSurface
  sys.color.surfaceVariant
  sys.color.onSurfaceVariant
  sys.color.surfaceContainerLowest
  sys.color.surfaceContainerLow
  sys.color.surfaceContainer
  sys.color.surfaceContainerHigh
  sys.color.surfaceContainerHighest
  sys.color.inverseSurface
  sys.color.inverseOnSurface

Outline:
  sys.color.outline
  sys.color.outlineVariant

Utility:
  sys.color.scrim
  sys.color.shadow
```

### CSS custom property mapping

```css
:root {
  --md-sys-color-primary: #6750A4;
  --md-sys-color-on-primary: #FFFFFF;
  --md-sys-color-primary-container: #EADDFF;
  --md-sys-color-on-primary-container: #21005D;
  /* ... continue for all roles */
}

@media (prefers-color-scheme: dark) {
  :root {
    --md-sys-color-primary: #D0BCFF;
    --md-sys-color-on-primary: #381E72;
    --md-sys-color-primary-container: #4F378B;
    --md-sys-color-on-primary-container: #EADDFF;
    /* ... */
  }
}
```

---

## Typography tokens

### System typescale tokens

Each style has: `.font`, `.size`, `.lineHeight`, `.weight`, `.tracking`

```
Display:
  sys.typescale.displayLarge    (57sp / 64 / 400)
  sys.typescale.displayMedium   (45sp / 52 / 400)
  sys.typescale.displaySmall    (36sp / 44 / 400)

Headline:
  sys.typescale.headlineLarge   (32sp / 40 / 400)
  sys.typescale.headlineMedium  (28sp / 36 / 400)
  sys.typescale.headlineSmall   (24sp / 32 / 400)

Title:
  sys.typescale.titleLarge      (22sp / 28 / 400)
  sys.typescale.titleMedium     (16sp / 24 / 500)
  sys.typescale.titleSmall      (14sp / 20 / 500)

Body:
  sys.typescale.bodyLarge       (16sp / 24 / 400)
  sys.typescale.bodyMedium      (14sp / 20 / 400)
  sys.typescale.bodySmall       (12sp / 16 / 400)

Label:
  sys.typescale.labelLarge      (14sp / 20 / 500)
  sys.typescale.labelMedium     (12sp / 16 / 500)
  sys.typescale.labelSmall      (11sp / 16 / 500)
```

### M3 Expressive emphasized variants (additional)

```
  sys.typescale.displayLargeEmphasized
  sys.typescale.headlineLargeEmphasized
  sys.typescale.titleLargeEmphasized
  sys.typescale.bodyLargeEmphasized
  sys.typescale.labelLargeEmphasized
  (and Medium/Small for each group)
```

---

## Shape tokens

```
  sys.shape.corner.none          (0dp)
  sys.shape.corner.extraSmall    (4dp)
  sys.shape.corner.small         (8dp)
  sys.shape.corner.medium        (12dp)
  sys.shape.corner.large         (16dp)
  sys.shape.corner.extraLarge    (28dp)
  sys.shape.corner.extraExtraLarge (48dp)  -- M3 Expressive
  sys.shape.corner.full          (999dp)
```

---

## Motion tokens

### Duration tokens (traditional easing)

```
  sys.motion.duration.short1     (50ms)
  sys.motion.duration.short2     (100ms)
  sys.motion.duration.short3     (150ms)
  sys.motion.duration.short4     (200ms)
  sys.motion.duration.medium1    (250ms)
  sys.motion.duration.medium2    (300ms)
  sys.motion.duration.medium3    (350ms)
  sys.motion.duration.medium4    (400ms)
  sys.motion.duration.long1      (450ms)
  sys.motion.duration.long2      (500ms)
  sys.motion.duration.long3      (550ms)
  sys.motion.duration.long4      (600ms)
```

### Easing tokens

```
  sys.motion.easing.standard
  sys.motion.easing.standardAccelerate
  sys.motion.easing.standardDecelerate
  sys.motion.easing.emphasized
  sys.motion.easing.emphasizedAccelerate
  sys.motion.easing.emphasizedDecelerate
  sys.motion.easing.legacy
  sys.motion.easing.legacyAccelerate
  sys.motion.easing.legacyDecelerate
  sys.motion.easing.linear
```

### Spring tokens (M3 Expressive)

```
  sys.motion.spring.fast.spatial
  sys.motion.spring.default.spatial
  sys.motion.spring.slow.spatial
  sys.motion.spring.fast.effects
  sys.motion.spring.default.effects
  sys.motion.spring.slow.effects
```

---

## Spacing tokens

```
  sys.spacing.1   (4dp)
  sys.spacing.2   (8dp)
  sys.spacing.3   (12dp)
  sys.spacing.4   (16dp)
  sys.spacing.5   (20dp)
  sys.spacing.6   (24dp)
  sys.spacing.7   (28dp)
  sys.spacing.8   (32dp)
  sys.spacing.9   (36dp)
  sys.spacing.10  (40dp)
  sys.spacing.11  (44dp)
  sys.spacing.12  (48dp)
  sys.spacing.13  (56dp)
  sys.spacing.14  (64dp)
```

---

## Elevation tokens

```
  sys.elevation.level0   (0dp)
  sys.elevation.level1   (1dp)
  sys.elevation.level2   (3dp)
  sys.elevation.level3   (6dp)
  sys.elevation.level4   (8dp)
  sys.elevation.level5   (12dp)
```

---

## Component token examples

### Filled Button

```
  comp.button.filled.containerColor
  comp.button.filled.labelTextColor
  comp.button.filled.containerShape
  comp.button.filled.containerHeight
  comp.button.filled.labelTextType
  comp.button.filled.disabled.containerColor
  comp.button.filled.disabled.labelTextColor
  comp.button.filled.hover.containerColor
  comp.button.filled.hover.labelTextColor
  comp.button.filled.focus.containerColor
  comp.button.filled.pressed.containerColor
```

### Text Field (Outlined)

```
  comp.textField.outlined.containerColor
  comp.textField.outlined.outlineColor
  comp.textField.outlined.outlineWidth
  comp.textField.outlined.labelTextColor
  comp.textField.outlined.inputTextColor
  comp.textField.outlined.supportingTextColor
  comp.textField.outlined.error.outlineColor
  comp.textField.outlined.error.labelTextColor
  comp.textField.outlined.error.supportingTextColor
  comp.textField.outlined.focused.outlineColor
  comp.textField.outlined.focused.outlineWidth
  comp.textField.outlined.focused.labelTextColor
```

### Card

```
  comp.card.containerColor
  comp.card.containerShape
  comp.card.containerElevation
  comp.card.containerSurfaceTintColor
```

### Navigation Bar

```
  comp.navigationBar.containerColor
  comp.navigationBar.activeIndicatorColor
  comp.navigationBar.activeLabelTextColor
  comp.navigationBar.activeIconColor
  comp.navigationBar.inactiveLabelTextColor
  comp.navigationBar.inactiveIconColor
```

---

## Platform token mapping

| Token | CSS | Kotlin (Compose) | Swift (UIKit) | Dart (Flutter) |
|-------|-----|------------------|---------------|-----------------|
| `sys.color.primary` | `--md-sys-color-primary` | `MaterialTheme.colorScheme.primary` | `UIColor.md.primary` | `Theme.of(context).colorScheme.primary` |
| `sys.typescale.bodyLarge.size` | `--md-sys-typescale-body-large-size` | `MaterialTheme.typography.bodyLarge.fontSize` | `UIFont.md.bodyLarge()` | `Theme.of(context).textTheme.bodyLarge.fontSize` |
| `sys.shape.corner.medium` | `--md-sys-shape-corner-medium` | `MaterialTheme.shapes.medium` | `CGFloat.md.cornerMedium` | `Theme.of(context).cardTheme.shape` |
| `sys.motion.duration.medium2` | `--md-sys-motion-duration-medium2` | N/A (use spring) | `TimeInterval.md.durationMedium2` | `Duration(milliseconds: 300)` |
| `sys.spacing.4` | `--md-sys-spacing-4` | `16.dp` | `CGFloat.md.spacing4` | `16.0` |

When implementing on a platform without native M3 token support, maintain a token file
(key-value) and generate platform-specific constants from it. Do not inline raw values.
