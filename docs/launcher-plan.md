# Launcher Plan

## Assumptions

- The repository already contains a working Android launcher foundation with official HOME role support, app discovery, app launch, persistence, and benchmark scaffolding.
- This pass intentionally changes product direction. `LavescarOS` is no longer an iOS-inspired premium launcher; it now becomes a minimalist phone launcher.
- The launcher remains portrait-first and phone-first, and it still uses official Android APIs only.
- Existing workspace, widget, folder, and wallpaper-first systems stay in the repo for now, but they are no longer the primary product surface.

## Chosen Architecture

- `:app` owns the new minimal product orchestration:
  - one `MinimalLauncherViewModel`
  - search-first home state
  - one-time migration from the old workspace model into pinned favorites
  - local app search ranking
  - app action-sheet state
- `:launcher-core` now renders a new text-only launcher surface:
  - black background
  - one top search field
  - pinned favorites on Home
  - swipe-up and tap entry into a plain-text App Library
  - a simple text action sheet for pin, app info, and uninstall
- `:system-integration` still owns Android framework boundaries:
  - app discovery from `LauncherApps`
  - app launch
  - app info and uninstall flows
  - HOME role/default launcher flow
- `:data` now has two durable paths:
  - legacy Room-backed workspace state kept for migration compatibility
  - new DataStore-backed pinned favorites plus minimal-mode preference flags
- `:benchmark` targets the new product behaviors:
  - cold start
  - swipe-up to App Library
  - home search keystroke-to-results
  - launch of the first search result

## Scope For This Pass

- Replace the existing premium home shell with a black, text-only minimal launcher.
- Keep only two primary surfaces:
  - `Home`
  - `App Library`
- Home behavior:
  - top search field (does not auto-focus keyboard on launch/resume)
  - blank query stays clean/black unless the user manually pins apps
  - non-blank query shows ranked local app results
  - system and lock wallpapers are forced to pure black to prevent OEM transition bleed
- App Library behavior:
  - opens from home via swipe up or the bottom `App Library` text affordance
  - full-screen black surface
  - plain text alphabetical app list
  - local app search only
- Long press on a row shows only:
  - `Pin to home` / `Remove from home`
  - `App info`
  - `Uninstall` when supported
- Add a one-time migration:
  - prefer old workspace shortcuts when available
  - force `Firefox` into seeded favorites when it is installed
  - otherwise fall back to browser / camera / phone / messages / WhatsApp heuristics and then alphabetical apps
- Keep the official default-launcher flow reachable from the minimal home surface.

## Intentionally Deferred

- Widgets on the new minimal surface
- Folder creation, open, rename, and editing on the new minimal surface
- Launcher shortcuts on the new minimal surface
- Rich theming, icon packs, wallpaper management, and glass effects
- Multi-page home layout
- Old workspace editing UX and drag/drop polish
- Tablet and foldable layouts
- Notification dots or badges
- Work profile polish
- Recents takeover, lock-screen replacement, control-center clones, overlays, root-only integrations, and accessibility misuse

