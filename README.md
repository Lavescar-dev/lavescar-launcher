# LavescarOS Launcher

LavescarOS is now a minimalist phone launcher. The active product is a black, text-only launcher with a search-first home screen, a swipe-up App Library, and a very small action surface for pinning and app management.

## Modules

- `:app` - HOME activity, minimal launcher view model, migration, manifest wiring
- `:launcher-core` - text-only launcher surface and minimal action sheet
- `:system-integration` - app discovery, app launch, app info, uninstall, default launcher flow
- `:data` - DataStore-backed pinned favorites and legacy workspace persistence kept for migration
- `:benchmark` - Macrobenchmark and baseline profile scaffolding for the minimal launcher path

## Download

A prebuilt APK is available on the [Releases](https://github.com/Lavescar-dev/lavescar-launcher/releases) page. It is signed with a debug key, so Android may warn before installing it, and later builds signed with a different key will need an uninstall first.

## Build And Run

1. Install JDK 17 or JDK 21.
2. Install Android SDK Platform 35, Build Tools 35.0.0, and platform-tools.
3. Point Gradle at your SDK with `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or a local ignored `local.properties` containing `sdk.dir=/path/to/Android/Sdk`.
4. Build the app with `./gradlew :app:assembleDebug`.
5. Install it with `./gradlew :app:installDebug`.
6. Open the app on a device and, if needed, tap `Set default launcher`.

## Current Behavior

- Home
  - pure black background with system wallpaper isolation (`windowShowWallpaper = false` and forced black system/lock wallpaper)
  - one top search field without stealing initial keyboard focus
  - empty by default when search is blank (shows only manually pinned apps, if any)
  - live local app search when you type
- App Library
  - open with swipe up or the `App Library` footer text
  - full-screen black surface
  - plain text alphabetical app list (zero icon decoding; cached in memory and updated via `LauncherApps.Callback`)
  - local app search only
- Long press on an app row
  - `Pin to home` or `Remove from home`
  - `App info`
  - `Uninstall` when supported by Android

## Default Launcher Flow

1. Open LavescarOS.
2. Tap `Set default launcher` if it appears.
3. On Android 10+, the app uses the official `RoleManager` HOME request flow.
4. On older supported versions, it opens the system Home settings page.

## Migration And Favorites

- The launcher now stores favorites in a dedicated pinned-app DataStore.
- On the first minimal launch, the app reads the old workspace once and seeds a small favorite set.
- `Firefox` is forced into favorites when it is installed.
- If the old workspace is empty, the seed falls back to browser / camera / phone / messages / WhatsApp heuristics, then alphabetical apps.

## Validation Commands

- Compile the main modules: `./gradlew :launcher-core:compileDebugKotlin :data:compileDebugKotlin :app:compileDebugKotlin`
- Unit tests for the new minimal logic: `./gradlew :app:testDebugUnitTest`
- Build the app: `./gradlew :app:assembleDebug`
- Install to a connected device: `./gradlew :app:installDebug`

## Benchmark Commands

- Assemble benchmark APKs: `./gradlew :benchmark:assembleBenchmarkBenchmark`
- Run Macrobenchmarks on a connected device: `./gradlew :benchmark:connectedCheck`
- Collect baseline profiles on a connected device: `./gradlew :benchmark:collectNonMinifiedBenchmarkBaselineProfile`

The benchmark path is now aligned with the minimal launcher:

- cold start to home
- swipe up to App Library
- home search keystroke-to-results
- launch first visible search result

## Deferred

- Widgets on the active minimal surface
- Folders on the active minimal surface
- Launcher shortcuts on the active minimal surface
- Rich themes, icon packs, wallpaper tools, and glass effects
- Multi-page home editing
- Tablet and foldable optimization
- Work profile polish
- Notification badges
- Recents takeover, lock-screen replacement, control-center clones, overlays, root-only integrations

## Known Limitations

- The repo still contains legacy workspace, widget, and premium-surface code so the rewrite stays incremental; that code is no longer the active launcher shell.
- The minimal surface is intentionally text-only and does not currently expose widgets, folders, or shortcuts.
- Pinned favorites are restored across process death, but legacy workspace editing is not part of the active UI anymore.
- The benchmark scaffolding is updated, but connected-device execution is still required for real measurements.

## License

MIT. See [LICENSE](LICENSE).
