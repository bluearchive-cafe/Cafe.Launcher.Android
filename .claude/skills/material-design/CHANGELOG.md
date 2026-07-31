# Changelog

## v1.1.0 — 2026-06-27

### Added
- `references/material-2.md` — full M2 legacy reference (color, typography, elevation,
  components, theming, M2-to-M3 migration cautions).
- `references/anti-patterns.md` — common Material Design mistakes across color,
  components, theme, platform, M3 Expressive, and process categories.
- `agents/openai.yaml` — display metadata for skill listings.
- Version gate with M2/M3/M3 Expressive routing modes.
- Source of truth rules with priority ordering.
- Do-not-use section specifying when the skill should NOT trigger.
- Systematic reference loading rules (when to load each reference file).
- Output templates: component specification, design audit, M2 output mode.
- Task-oriented accessibility section in component spec and audit templates.
- Platform decision checklist (verify before recommending a library).
- Maintenance metadata (`last_verified`, `scope`, `requires_current_verification`)
  on all reference files.
- CHANGELOG.md (this file).

### Changed
- **SKILL.md**: Title changed from "Material Design 3 Skill" to "Material Design Skill".
- **SKILL.md**: Slimmed from 441 lines to 341 lines. Moved encyclopedic content to
  references; main file now focuses on workflow, decisions, and routing.
- **Frontmatter description**: Narrowed to avoid false triggering on non-Material
  design systems (Ant Design, Fluent, etc.).
- **Platform guides**: Removed hardcoded version numbers. All platform sections now
  instruct verification against current official docs.
- **Version compatibility matrix**: Replaced static version table with capability-based
  table + verification checklist.
- **M3 Expressive motion description**: Changed from "replacing" to "introduces" with
  platform-support caveats.
- **Expanded evals.json**: From 3 tests to 12 tests covering positive M3, negative
  (non-Material), M2 mode, M2-to-M3 migration, M3 Expressive caution, platform
  version verification, accessibility, component-library-vs-spec boundary,
  and unspecified-design-system scenarios.

### Fixed
- P0-01: Frontmatter description too broad — narrowed, added do-not-use rules.
- Angular Material section now splits M3 theming from M2 legacy; removed misleading `m2-define-palette` example under M3 heading.
- Platform selection flow softened: Android Compose line now says "verify current stable vs experimental API status" instead of hard "full M3 + Expressive support."
- P0-02: No M2 mode — added version gate + `material-2.md`.
- P0-03: Hardcoded platform versions — replaced with verification rules.
- P0-04: No source-of-truth rules — added priority ordering and verification requirements.
- P0-05: M3 Expressive language too absolute — softened with platform caveats.
- P0-06: No M2 output template — added M2 output mode.
- P1-01: SKILL.md too heavy — slimmed to workflow/decision focus.
- P1-02: Reference loading rules unsystematic — added indexed table.
- P1-03: Component-library vs spec boundary weak — hardened with explicit distinctions.
- P1-04: Accessibility rules not task-oriented — embedded in component and audit templates.
- P1-05: Eval coverage insufficient — expanded to 12 tests.
- P1-06: Static version matrix — replaced with capability-based + verification checklist.
- P1-07: Missing maintenance metadata — added to all reference files.
- P2-01: Added `agents/openai.yaml`.
- P2-02: Added `CHANGELOG.md` (this file).
- P2-04: Added `references/anti-patterns.md`.

## v1.0.0 — 2026-06-27

### Initial release
- `SKILL.md` with M3 design guidance.
- `references/color-system.md` — tonal palettes, color roles, dark theme, dynamic color.
- `references/components.md` — 30+ component catalog with usage rules and states.
- `references/design-tokens.md` — token catalog, naming conventions, platform mappings.
- `references/platform-guides.md` — Android, Flutter, Web, React, Angular, Vue guides.
- `evals/evals.json` with 3 test cases.
