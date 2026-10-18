# 0005. Navigation 3

- Status: Accepted
- Date: 2026-10-18

## Context

The app needs one back stack per bottom tab, shared-element transitions (grid -> album, mini player -> Now Playing), predictive back, and later adaptive layouts (tablet, foldable, TV). Navigation 3 (`androidx.navigation3`) is Compose-first; 1.2.0 is stable since 2026-09-23 and supports shared elements between scenes, results between entries, deep links and predictive back.

## Decision

- Use Navigation 3 (`navigation3-runtime`, `navigation3-ui`), latest stable 1.2.x.
- Destinations are `@Serializable` `NavKey`s in `:core:navigation`.
- `:app` owns the back stacks; features navigate through a small `Navigator` interface.
- Details in the `architecture` skill.

## Consequences

- Navigation 2 (`navigation-compose`) is not used.
- Back stacks are plain state we own, which keeps navigation testable without a device.
