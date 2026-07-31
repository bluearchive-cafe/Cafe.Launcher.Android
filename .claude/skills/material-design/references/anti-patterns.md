---
last_verified: 2026-06-27
scope: Common Material Design mistakes and anti-patterns to avoid
requires_current_verification: false
---

# Material Design Anti-Patterns

## Color anti-patterns

### Primary color saturation

**Problem**: Primary color applied to every button, header, icon, and accent element.

**Why it's wrong**: If everything is primary, nothing is. Users can't identify the
single most important action on the page.

**Fix**: One Filled Button (primary) per view. Secondary actions use Outlined or
Text Buttons. Reserve primary color for the highest-emphasis element.

### Hardcoded hex values

**Problem**: `background: #6750A4; color: #FFFFFF;` in component CSS.

**Why it's wrong**: Blocks theme switching, dark mode, dynamic color, and
high-contrast mode. Every hardcoded color is technical debt.

**Fix**: Use semantic tokens: `background: var(--md-sys-color-primary); color: var(--md-sys-color-on-primary);`

### Error expressed through color alone

**Problem**: Just turning a text field border red when validation fails.

**Why it's wrong**: ~8% of males have some form of color vision deficiency. Users of
screen readers get no error information.

**Fix**: Red border + error icon + descriptive error text ("Email must contain @")
+ link the error to the field programmatically.

### Pure black in dark theme

**Problem**: `background: #000000` for dark theme surfaces.

**Why it's wrong**: Pure black creates harsh contrast, causes eye strain, and
destroys any visual hierarchy between surface levels.

**Fix**: Use tonal neutrals (neutral 4–22 from the M3 palette). Surface containers
create hierarchy through subtle tonal shifts.

---

## Component anti-patterns

### Placeholder as label

**Problem**: Using `placeholder="Email"` with no visible label on a text field.

**Why it's wrong**: Placeholder disappears when the user types. Users forget what
the field is for. Screen readers may not announce the placeholder as a label.

**Fix**: Always include a persistent label. Placeholder is optional supplementary
hint text, never a label replacement.

### Icon-only buttons without accessible names

**Problem**: `<button><span class="icon-close"></span></button>` with no label.

**Why it's wrong**: Screen readers announce "button" with no context. Users don't
know what the button does.

**Fix**: Add `aria-label="Close"` or `contentDescription="Close"` to every icon button.

### Disabled buttons with no explanation

**Problem**: A grayed-out "Submit" button with no tooltip or helper text.

**Why it's wrong**: Users don't know WHY the button is disabled or what they need
to do to enable it. This creates frustration and abandonment.

**Fix**: Keep the button enabled, show inline validation errors, and explain
what's needed. If a button must be disabled, add a tooltip or helper text.

### Multiple primary buttons

**Problem**: "Save" (Filled) + "Submit" (Filled) + "Continue" (Filled) on the same form.

**Why it's wrong**: Users can't distinguish the primary path from secondary options.

**Fix**: One Filled Button for the primary action. Outlined or Text for alternatives.

### FAB overload

**Problem**: Multiple FABs on one screen, or a FAB that opens a menu of 8 unrelated actions.

**Why it's wrong**: FAB is for the single most important create/compose action.
Overloading it defeats its purpose and creates confusion.

**Fix**: One FAB per page max. If you need more actions, use a toolbar or speed dial
with 3–4 related options.

### Dialog for complex forms

**Problem**: A Dialog containing a multi-field form with dropdowns, validation, and
conditional sections.

**Why it's wrong**: Dialogs are for short, focused interactions. Complex forms in
dialogs are cramped, hard to navigate, and inaccessible.

**Fix**: Use a full-screen page, a Bottom Sheet (mobile), or a side panel (desktop)
for anything beyond 2–3 fields.

---

## Theme anti-patterns

### Designing light theme only

**Problem**: Completing the entire light theme design, then trying to "invert colors"
for dark theme at the end.

**Why it's wrong**: Dark theme uses fundamentally different mechanisms (tonal elevation
instead of shadows, different contrast needs, color role reversal). You can't just
invert and ship.

**Fix**: Design light and dark themes simultaneously. Use the M3 color role system
so theme switching is a token swap, not a redesign.

### Skipping component states

**Problem**: Designing only the default/enabled state for each component.

**Why it's wrong**: Hover, focus, pressed, disabled, loading, and error states are
not optional. Users encounter them constantly. An incomplete component creates
confusion and accessibility failures.

**Fix**: Define every state before a component is considered "done." Use state layers
instead of hardcoded state colors.

### Brand customization that breaks semantics

**Problem**: Making error states brand-green because "green is our brand color."

**Why it's wrong**: Color roles have semantic meaning. `error` means error regardless
of brand. Overriding semantics with brand destroys user understanding.

**Fix**: Customize brand expression within the allowed scope (primary color, font,
corner style, density). Preserve semantic color roles and component structure.

---

## Platform anti-patterns

### Assuming MUI = M3

**Problem**: "We use MUI, so we're following Material Design 3."

**Why it's wrong**: MUI (as of most versions) is based on Material Design 2.
Its color system, typography, shape, and elevation model follow M2, not M3.
Check the current MUI version's stated Material Design support.

**Fix**: Verify the library's actual M2/M3 support. If using MUI with M2 baseline
but targeting M3, map M3 tokens into MUI's theming API explicitly.

### Assuming Material Web is always the right web choice

**Problem**: Recommending Material Web without checking its current maintenance status.

**Why it's wrong**: Material Web is Google's official M3 web component library, but
its maintenance activity, component coverage, and community adoption vary over time.

**Fix**: Check the repository's recent commits, open issues, and release frequency
before recommending for production.

### Hardcoding platform library versions in guidance

**Problem**: "Compose Material3 1.4.0 supports spring motion."

**Why it's wrong**: Version numbers drift. 1.4.0 may be outdated or superseded by
the time someone reads the guidance. The feature support claim may also change.

**Fix**: Reference features by capability ("spring motion is available in recent
Compose Material3 releases"), not by version number. Always tell users to verify
against current release notes.

---

## M3 Expressive anti-patterns

### Expressive everywhere

**Problem**: Applying spring animations, emphasized typography, bold shapes, and
hero color moments to every screen in a banking app.

**Why it's wrong**: M3 Expressive is additive. It increases cognitive load in exchange
for emotional engagement. In productivity, data-dense, or regulated contexts, that
trade-off is wrong.

**Fix**: Use M3 Expressive selectively. Identify 1–2 hero moments per flow where
expression enhances discovery or delight. Keep the rest restrained.

### Spring motion without reduced-motion fallback

**Problem**: Bouncy spring animations with no `prefers-reduced-motion` alternative.

**Why it's wrong**: Users with vestibular disorders can experience nausea from
excessive motion. It's also an accessibility requirement.

**Fix**: Always provide a zero-duration instant transition when
`prefers-reduced-motion: reduce` is active.

---

## Process anti-patterns

### Designing without tokens

**Problem**: Designing pages by picking hex colors and pixel font sizes directly in
Figma without defining tokens first.

**Why it's wrong**: Without tokens, every style decision is an ad-hoc choice.
Consistency drifts. Theme switching becomes impossible. Handoff to development
requires manual translation of every value.

**Fix**: Define tokens first (color, type, shape, spacing). Build components from
tokens. Design pages from components.

### Skipping accessibility in design

**Problem**: Treating accessibility as a developer responsibility or a pre-launch
checklist.

**Why it's wrong**: Many accessibility issues are design decisions (contrast, touch
target size, focus order, label placement, error communication). They can't be fixed
in code alone.

**Fix**: Include accessibility in design reviews. Run the A11y checklist on every
component and page before handoff.
