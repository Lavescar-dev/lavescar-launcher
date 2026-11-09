# Architecture

## Overview

The launcher now has two product layers:

- active minimal product surfaces
- retained legacy launcher infrastructure

The active path is:

- `:app`
  - `LauncherActivity` boots the HOME activity
  - `MinimalLauncherViewModel` owns all visible launcher state
  - migration from legacy workspace data into pinned favorites happens here
- `:launcher-core`
  - `MinimalLauncherRootView`
  - `MinimalLauncherUiState`
  - text-only row rendering, minimal search field, swipe-up App Library entry, and text action sheet
- `:system-integration`
  - `LauncherAppsCatalogRepository` supplies launchable activities
  - `AndroidAppLauncher` launches apps
  - `AndroidAppActionController` handles app info and uninstall
  - `AndroidHomeRoleController` handles the official default-launcher flow
- `:data`
  - `PinnedAppsRepository` stores ordered pinned favorites in DataStore
  - `LauncherPreferencesRepository` now also stores `minimalModeEnabled` and `minimalModeMigrated`
  - legacy `WorkspaceRepository` remains only as migration input for this pass
- `:benchmark`
  - signature-protected fixture receiver seeds either an empty minimal home or a populated pinned-favorites home

## Active Data Flow

1. `LauncherActivity` creates `MinimalLauncherViewModel`.
2. `MinimalLauncherViewModel` combines:
   - installed launchable apps from `AppCatalogRepository`
   - pinned favorites from `PinnedAppsRepository`
   - launcher preferences from `LauncherPreferencesRepository`
   - transient UI state: current surface, home query, library query, and action sheet
3. The combined state is mapped into `MinimalLauncherUiState`.
4. `MinimalLauncherRootView` renders:
   - Home rows
   - App Library rows
   - default-launcher text action
   - text-only action sheet
5. User intents flow back to the view model:
   - update query
   - open/close library
   - launch app
   - show actions
   - pin/unpin
6. Process death restore works because pinned favorites and preferences persist immediately.

## Minimal Product Contract

- Home
  - black background
  - top search field
  - pinned favorites when query is blank
  - search results when query is non-blank
- App Library
  - swipe up from home
  - full-screen black surface
  - plain text alphabetical app list
  - local app search only
- App actions
  - pin/unpin
  - app info
  - uninstall when allowed

There are no icons, dock, widgets, folders, category cards, spotlight overlays, wallpaper surfaces, or heavy transitions in the active product path.

## Migration Strategy

The repo still contains old Room-backed workspace data. This pass does not delete that schema. Instead:

- on first minimal launch, the view model checks `minimalModeMigrated`
- if pinned favorites are empty, it reads the old workspace once
- direct shortcuts and folder contents are flattened into ordered component keys
- the seed planner then builds up to six pinned favorites
- `Firefox` is forced in first when it exists
- old workspace data is left untouched after migration

This keeps the rewrite incremental and reversible while still letting the launcher feel like a new product.

## Search And Ranking

Both Home search and App Library search share the same local ranking rules:

- label prefix
- label word prefix
- search-key contains
- alphabetical tie-break

The search backend stays cheap and deterministic because it reuses the already available launchable-activity catalog and a lowercase `searchKey`.

## Legacy Boundaries

The following systems still exist in code but are not part of the active minimal shell:

- workspace grid rendering
- folders
- widgets
- sample wallpaper flows
- glass / App Library premium surfaces
- old benchmark scenarios tied to those surfaces

They remain in the repo for now to avoid a destructive rewrite, but the app entrypoint no longer uses them.

## Performance Notes

The new active shell is intentionally lighter:

- no icon rendering or `getBadgedIcon()` decoding in the catalog or UI hot path
- `LauncherApps.Callback` keeps the app catalog cached in memory without re-querying `PackageManager` on every `onResume()`
- `windowShowWallpaper = false`, zero-duration activity transitions, and forced black system/lock wallpaper eliminate OEM wallpaper bleed on Home/Recents transitions
- no live blur
- no widget host rendering in the primary home surface
- one RecyclerView-backed text list per surface state with item animations disabled

The main remaining risks are:

- large installed-app lists without diff-aware paging
- search field and list churn on very fast typing
- old legacy code still compiling even though it is off the main path

