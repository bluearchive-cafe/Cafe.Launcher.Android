---
last_verified: 2026-06-27
scope: Material Design 3 component catalog with usage rules, anatomy, states, and platform examples
requires_current_verification: false
---

# Material Design 3 — Component Reference

## Table of contents

- [Buttons](#buttons)
- [FAB (Floating Action Button)](#fab)
- [Text Fields](#text-fields)
- [Selection Controls](#selection-controls)
- [Chips](#chips)
- [Cards](#cards)
- [Lists](#lists)
- [Navigation](#navigation)
- [Dialogs](#dialogs)
- [Bottom Sheets](#bottom-sheets)
- [Snackbar](#snackbar)
- [Progress Indicators](#progress-indicators)
- [Tabs](#tabs)
- [Menus](#menus)
- [Tooltips](#tooltips)
- [Banners](#banners)
- [Dividers](#dividers)
- [Badges](#badges)
- [Carousel](#carousel)
- [Date & Time Pickers](#date--time-pickers)
- [Search](#search)
- [M3 Expressive new components](#m3-expressive-new-components)
- [Component state checklist](#component-state-checklist)

---

## Buttons

### Types and emphasis levels

| Type | Emphasis | Use case |
|------|----------|----------|
| **Filled Button** | Highest | Primary page action, form submit, "Save", "Buy", "Next" |
| **Filled Tonal Button** | High | Important but not primary, "Add to cart", "Start trial" |
| **Elevated Button** | Medium-high | Action that needs to rise above surrounding content |
| **Outlined Button** | Medium | Secondary action, "Cancel", "Learn more", "View details" |
| **Text Button** | Low | Tertiary action, inline actions, "See all", "Dismiss" |
| **Icon Button** | Variable | Toolbar actions, close, search, menu toggle |
| **Segmented Button** | Equal | Mutually exclusive toggle options (M3 Expressive) |
| **Split Button** | Primary + menu | Primary action with related dropdown (M3 Expressive) |

### Button usage rules

1. **One primary per view**: A single filled button for the most important action. If
   multiple actions seem equally important, re-evaluate the information architecture.
2. **Verb-first labels**: "Save changes", not "Save"; "Continue to payment", not "Next".
3. **Dangerous actions**: Use Outlined or Text Button (not Filled) for destructive
   actions. Add confirmation dialog for irreversible operations.
4. **Consistent order**: In button groups, primary action on the trailing edge (right in
   LTR, left in RTL). "Cancel" before "Confirm".
5. **Loading state**: Replace label with a spinner of matching size; disable interaction
   during loading. Keep button width stable.
6. **Minimum touch target**: 48dp height for all button types.

### Anatomy

```
[ Icon (optional) ] [ Label text ]    — Filled / Tonal / Elevated / Outlined
[ Label text ]                         — Text Button
[ Icon ]                               — Icon Button
[ Icon ] [ Label ] [ ▼ ]              — Split Button
```

### State layers

All buttons use state layers (semi-transparent overlay) for:
- Hover: 8% opacity of `onSurface`
- Focus: 12% opacity
- Pressed: 12% opacity
- Dragged: 16% opacity

Disabled: reduce overall opacity to 38% (content + container), remove all state layers,
make non-interactive and non-focusable.

---

## FAB

### Sizes

| Size | Height | Use |
|------|--------|-----|
| Small | 40dp | Compact surfaces, paired with lists |
| Default | 56dp | Standard page-level action |
| Large | 96dp | Hero action, primary create flow (M3 Expressive) |

### Variants

| Variant | Description |
|---------|-------------|
| **FAB** | Circular, icon only |
| **Small FAB** | Compact circular |
| **Large FAB** | Oversized, icon or icon+label |
| **Extended FAB** | Icon + label, rectangular with full rounding |

### FAB usage rules

1. **One FAB per page maximum**. The FAB represents the single most important
   create/compose action.
2. Use for: compose email, create document, start recording, new task.
3. Do NOT use for: delete, settings, filter, back, or any destructive/meta action.
4. Position: bottom-end (bottom-right in LTR), 16dp from edges. Do not obscure critical
   content or navigation.
5. On scroll: FAB can hide to reveal content, reappear on scroll-up. Use a smooth
   scale + fade transition.
6. FAB Menu (M3 Expressive): Tap to expand related actions. Max 4-5 items.

---

## Text Fields

### Types

| Type | Description |
|------|-------------|
| **Filled Text Field** | Solid background, more visual weight |
| **Outlined Text Field** | Border-based, lighter visual weight |

### Anatomy

```
[Leading icon?] [Label] [Input text] [Trailing icon?] [Counter?]
[Supporting text (helper/error)]                    [Character counter]
```

### Input variants

- **Single-line**: Default. For short inputs (name, email, search).
- **Multi-line**: For descriptions, messages. Show resize handle.
- **With prefix/suffix**: Currency symbol, unit, domain suffix.
- **With counter**: Show remaining characters for constrained inputs.
- **Password**: Provide show/hide toggle as trailing icon.

### States

| State | Visual |
|-------|--------|
| Enabled | Default outline/fill, label above or within |
| Focused | Accent-colored outline/label, visible cursor |
| Hovered | Subtle state layer on container |
| Disabled | Reduced opacity, non-interactive |
| Error | Red outline, red label, error icon, error text below |
| Success | Green checkmark (optional), clear text |
| Read-only | No cursor, distinguishable from editable |

### Text field rules

1. **Always have a label** — do not use placeholder alone as the label.
2. **Error text must explain how to fix the problem**, not just say "Invalid input."
3. **Mark required fields** with an asterisk or "(required)" text.
4. **Pre-format input where helpful** (auto-spacing credit card numbers, phone formatting)
   but never interfere with user typing or paste.
5. **Validate on blur** (when user leaves the field), not on every keystroke.
6. **On form submit**, scroll to and focus the first field with an error.

---

## Selection Controls

| Control | Use | Behavior |
|---------|-----|----------|
| **Checkbox** | Multi-select from list | Checked/unchecked, supports indeterminate parent |
| **Radio Button** | Single select, 2–7 options | One selected at a time |
| **Switch** | Instant on/off toggle | Changes take effect immediately |
| **Slider** | Value in a continuous range | Discrete or continuous, with label |
| **Segmented Button** | 2–5 mutually exclusive options | Like radio but more visual |

### Selection rules

1. **Switch**: Use for settings that take effect immediately (Wi-Fi, Bluetooth, Dark mode).
   NOT for form fields that require submission.
2. **Checkbox**: Use for multi-select contexts and form submissions. Always pair with a label.
3. **Radio**: Best for 2–7 options. Beyond 7, use a dropdown or autocomplete.
4. **Slider**: Label the range endpoints. Show current value for precise adjustments.
5. **Segmented Button**: Use for view toggles (List / Grid), time ranges (Day / Week / Month).

---

## Chips

| Type | Use | Behavior |
|------|-----|----------|
| **Assist Chip** | Suggested action or smart suggestion | Tappable, leads to action |
| **Filter Chip** | Toggle filter state | Selectable, shows check when active |
| **Input Chip** | Represent entered data (email recipients, tags) | Deletable via trailing icon |
| **Suggestion Chip** | Present dynamic suggestions | Tappable, populates input |

### Chip rules

1. Chips are compact — limit text to 1-2 words.
2. Filter chips must clearly indicate selected vs unselected state.
3. Input chips need a clear delete/remove action.
4. Don't nest chips; use a wrapping flow layout.
5. Avoid mixing chip types in the same group.

---

## Cards

### Types

| Type | Description |
|------|-------------|
| **Elevated Card** | Subtle shadow, sits above surface |
| **Filled Card** | Tonal fill, lighter weight |
| **Outlined Card** | Border only, minimal weight |

### Card anatomy

```
┌──────────────────────────────┐
│ [Media / Image (optional)]   │
│                              │
│ [Header / Title]             │
│ [Subtitle / Supporting text] │
│ [Body / Description]         │
│                              │
│ [Buttons / Actions] [Icons]  │
└──────────────────────────────┘
```

### Card rules

1. Cards group related content and actions. One card = one conceptual unit.
2. The entire card can be tappable, or specific areas within it can be tappable —
   not both, as this creates ambiguity.
3. Keep card height and structure consistent within a list.
4. Don't overload a card with too many unrelated actions (max 2–3 secondary actions).
5. If the card itself is tappable, avoid nested interactive elements that compete.

---

## Lists

### List item types

| Type | Lines | Use |
|------|-------|-----|
| Single-line | 1 | Contacts, settings items |
| Two-line | 2 | Emails, messages with preview |
| Three-line | 3 | Detailed list items (use sparingly) |

### List item anatomy

```
[Leading icon/avatar]  [Primary text]
                       [Secondary text]
                       [Tertiary text / metadata]   [Trailing icon/action]
```

### List rules

1. Maintain consistent structure within a list — all items should have the same anatomy.
2. Primary text is the identifier; secondary text adds context; metadata (time, count)
   goes on the trailing side.
3. Lists > 20 items need search, filter, or section headers.
4. Use dividers sparingly between items — whitespace is usually sufficient.
5. Provide pressed and focus states for tappable list items.

---

## Navigation

### Components by breakpoint

| Breakpoint | Primary Nav | Secondary Nav |
|------------|-------------|---------------|
| Compact | Navigation Bar (bottom) | Navigation Drawer |
| Medium | Navigation Rail (side) | Modal Drawer or top Tabs |
| Expanded+ | Navigation Drawer (persistent) or Rail | Tabs, Breadcrumbs |

### Navigation Bar (Bottom)

- 3–5 destinations max.
- Each destination: icon + label text.
- Active destination: filled icon variant + highlighted label + indicator pill.
- M3 Expressive: flexible height, horizontal items on medium windows, `secondary` color
  for active label.

### Navigation Rail

- Compact side bar (80dp wide) with icons and labels.
- Same 3–5 destination limit.
- Floating action button can sit above or within the rail.

### Navigation Drawer

- Modal (overlay) or Standard (persistent aside).
- For 5+ destinations.
- Use section headers to group related destinations.
- Active destination must be clearly indicated.

### Top App Bar

- Center-aligned or small (left-aligned title).
- Contains: navigation icon (back/menu), title, up to 2 action icons, optional overflow.
- Scroll behavior: can collapse, pin, or scroll away.
- Medium and Large variants for prominent page headers with hero image potential.

### Tabs

- Primary tabs: top-level content switching (fixed or scrollable).
- Secondary tabs: within a section or view.
- 3–5 tabs for fixed; more for scrollable (but prefer fewer).
- Tab content should be at the same conceptual level — don't nest hierarchy in tabs.

### Search

- Search Bar: expandable inline bar, transitions to full search view.
- Search View: full-screen search with suggestions, history, and results.

---

## Dialogs

### Types

| Type | Use |
|------|-----|
| **Basic Dialog** | Confirmation, alert, short input |
| **Full-screen Dialog** | Complex form, multi-step creation flow (mobile) |

### Anatomy

```
┌──────────────────────────────────┐
│ [Icon (optional)]                │
│ Title                            │
│ Content / description            │
│                                  │
│        [Cancel]  [Confirm]       │
└──────────────────────────────────┘
```

### Dialog rules

1. Title must be clear and actionable. Don't write "Are you sure?" — write "Delete
   project 'Q4 Report'?"
2. Content must be brief. If you need more than 2-3 sentences, use a full-screen page.
3. Max 2–3 action buttons. Primary action on the trailing edge.
4. Destructive actions: use clear destructive wording ("Delete", "Remove") and consider
   color-coding the button.
5. Dialog must be dismissible by: action button, "X" icon, or tapping scrim (for
   non-blocking dialogs). Always provide at least one explicit dismiss path.
6. Do NOT use dialogs for: long forms, multi-step flows, large content blocks,
   or anything that requires scrolling to understand.

---

## Bottom Sheets

### Types

| Type | Use |
|------|-----|
| **Standard Bottom Sheet** | Persistent, coexists with main content |
| **Modal Bottom Sheet** | Overlays content, requires dismissal |

### Usage rules

1. Use for contextual actions related to current page (share, filter, sort, more options).
2. Always include a drag handle for modal sheets.
3. Content should be compact — no long scrolling within a sheet.
4. Don't use for: complex forms, multi-step flows, unrelated tools.
5. On large screens, consider replacing with a Side Sheet or Detail Panel.

---

## Snackbar

### Usage rules

1. Brief text (1 line max). One optional action ("Undo", "Retry").
2. Auto-dismiss after 4–10 seconds. Shorter for simple confirmations, longer if there's
   an action. Pause timer on hover.
3. Position: bottom of screen, above navigation bar. Don't overlap critical UI.
4. Only one Snackbar at a time. Queue subsequent messages.
5. Not for: critical alerts (use Dialog), persistent messages (use Banner), multi-action
   flows (use Bottom Sheet).

### Anatomy

```
┌──────────────────────────────────────────┐
│ [Icon?]  Message text here     [Action]  │
└──────────────────────────────────────────┘
```

---

## Progress Indicators

| Type | Use |
|------|-----|
| **Linear Determinate** | Progress with known duration (file upload, form steps) |
| **Linear Indeterminate** | Unknown duration, ongoing (page loading, data fetching) |
| **Circular Determinate** | Known duration, compact (task completion %) |
| **Circular Indeterminate** | Unknown duration, compact (button loading, inline wait) |
| **Loading Indicator** | Brief inline loading (M3 Expressive) |

### Rules

1. Use determinate whenever you can calculate progress. Users prefer knowing.
2. For waits > 2 seconds, provide context: what's happening, estimated time, cancel option.
3. Skeleton screens are for content loading (articles, feeds, dashboards). Progress
   indicators are for actions/processes.
4. Never leave an indeterminate indicator spinning forever — add a timeout and error state.

---

## Tabs

### Types

| Type | Description |
|------|-------------|
| **Primary (fixed)** | Equal-width tabs, 3–5 items |
| **Primary (scrollable)** | Variable-width tabs, any number |
| **Secondary** | Within a content section, lighter weight |

### Rules

1. Tab labels should be short (1–2 words) and clearly descriptive.
2. Active tab must be visually distinct (indicator line + text/icon color change).
3. Tab content should be at the same hierarchy level.
4. Swipe between tabs should work on mobile.
5. Don't use tabs as primary navigation if you already have bottom nav or nav rail.
6. For more than 7 tabs, consider a different pattern (dropdown, sidebar nav).

---

## Menus

### Types

| Type | Use |
|------|-----|
| **Dropdown Menu** | List of actions from a trigger button |
| **Context Menu** | Right-click/long-press actions on a specific item |
| **Exposed Dropdown** | Menu that shows selected item, like a select |

### Rules

1. Menu items: short label + optional shortcut hint + optional leading icon/trailing text.
2. Group related items; use dividers between groups.
3. Destructive actions go at the bottom with a divider above.
4. Max menu height should not exceed 60% of viewport — if it does, use submenus or a
   different pattern.
5. Menu should open below the trigger; if insufficient space, open above.

---

## Tooltips

### Types

| Type | Use |
|------|-----|
| **Plain Tooltip** | Short text label for unlabeled icon buttons |
| **Rich Tooltip** | Title + description, for more context |

### Rules

1. Show on hover (desktop) or long-press (mobile). Delay: ~200ms.
2. Position: above or below the trigger, centered. Avoid obscuring the element.
3. Keep text to 1-2 lines.
4. Don't put essential information in tooltips — they are supplementary.

---

## Banners

### Use when

- A system-wide or page-level message that needs attention but doesn't block usage.
- Persistent until dismissed or resolved.
- Examples: "You're offline", "New version available", "Storage full".

### Rules

1. Place at top of page, below top app bar, pushing content down.
2. One banner at a time.
3. Max 2 actions.
4. Don't use for: critical blocking errors (Dialog), brief confirmations (Snackbar).

---

## Dividers

### Types

| Type | Use |
|------|-----|
| **Full-bleed** | Separate content sections |
| **Inset** | Separate related items within a section (indented to align with text) |
| **Middle Inset** | Separate items but not spanning icon area |

### Rules

1. Default to whitespace. Add dividers only when grouping needs explicit reinforcement.
2. Use full-bleed between sections, inset between items within a section.
3. Outlined cards and surface-based grouping often eliminate the need for dividers.

---

## Badges

### Types

| Type | Use |
|------|-----|
| **Small Badge** | 6dp dot, notification indicator |
| **Large Badge** | Number or short text, notification count |

### Rules

1. Badges should not exceed 3 characters (999+).
2. Position: top-trailing corner of icon or avatar.
3. Don't badge more than 2–3 items in a view — it reduces their signal value.

---

## Carousel

### Use for

- Browsing collections of images or cards.
- Hero content showcases.
- Onboarding flows.

### Rules

1. Clearly indicate that more items exist beyond the visible area (peek, arrows, dots).
2. Support swipe/scroll gestures.
3. Limit the number of items — carousels become unusable with 10+ items.
4. Auto-advance only when appropriate (ambient content display); always provide pause.

---

## Date & Time Pickers

### Date Picker

- **Docked**: Inline calendar, good for forms.
- **Modal**: Overlay calendar, good for quick date selection.
- Support range selection (date range picker).

### Time Picker

- **Dial**: Clock-face interaction, more visual.
- **Input**: Text field with time format, more precise.

### Rules

1. Show today's date clearly highlighted.
2. Support keyboard input as an alternative to picker interaction.
3. Validate dates (no Feb 30, no past dates for bookings, etc.).
4. Localize: date format, first day of week, calendar system.

---

## Search

### Modes

| Mode | Description |
|------|-------------|
| **Search Bar** | Collapsed bar that expands into search view |
| **Search View** | Full-screen search with history, suggestions, results |
| **Persistent Search** | Always-visible search field in toolbar |

### Rules

1. Provide recent searches and suggestions before the user types.
2. Show results as the user types (debounce ~300ms).
3. Empty search state: "No results for [query]. Try [suggestion]."
4. Support voice input on mobile.

---

## M3 Expressive new components

These components were added with M3 Expressive:

| Component | Description |
|-----------|-------------|
| **ButtonGroup** | Group related buttons with shared container |
| **SplitButtonLayout** | Primary action + dropdown in a single control |
| **Floating Toolbar** | Dynamic contextual toolbar (appears on selection) |
| **Docked Toolbar** | Fixed toolbar for frequent actions |
| **Loading Indicator** | Compact inline loading visual for short operations |
| **FAB Menu** | Expandable FAB revealing 3-5 related actions |

---

## Component state checklist

For every interactive component, design and implement these states:

```
[ ] Enabled        — Default interactive state
[ ] Hovered        — Mouse cursor over (desktop)
[ ] Focused        — Keyboard focus visible
[ ] Pressed        — Active press/touch feedback
[ ] Selected       — Chosen / active among peers
[ ] Activated      — Engaged (e.g., toggle on)
[ ] Disabled       — Non-interactive, reduced opacity (38% content)
[ ] Loading        — In-progress, non-interactive, spinner or skeleton
[ ] Error          — Validation failure, destructive state
[ ] Success        — (optional) Positive confirmation state
[ ] Empty          — No content to display
```

Each state must:
- Be visually distinguishable (not just one property changing).
- Work in both light and dark themes.
- Meet contrast requirements.
- Not rely on color alone.
