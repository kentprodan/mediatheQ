# 0004. Material 3 Expressive on material3 1.5.x

- Status: Accepted
- Date: 2026-10-18

## Context

Material 3 Expressive APIs are not in the stable `material3` 1.4.0; they exist only on the 1.5 line. On 2026-10-18 the latest is `1.5.0-beta01` (2026-10-07). Google announced 1.5.0 stable, promoting Expressive APIs, for later in 2026. Many Expressive components are already non-experimental in 1.5 (theme, motion scheme, buttons, ButtonGroup, toolbars, list items, flexible app bars, menus); `MaterialShapes` and `LoadingIndicator` are still experimental.

## Decision

- Use `androidx.compose.material3:material3:1.5.0-beta01`, pinned.
- Move to `1.5.0` stable as soon as it ships (one issue).
- Experimental APIs only inside `:core:designsystem`, wrapped in `Mq*` components.
- Rules in the `expressive-design` skill.

## Consequences

- A beta dependency in production code until 1.5.0 stable. Accepted because Expressive is a core product goal.
- API renames between beta and stable are absorbed in `:core:designsystem` wrappers.
