# AGENTS.md

## Project goal

Build a motion-first, iOS-inspired premium Android launcher for personal use. Primary target: Realme 11-class phone, 90 Hz display, 90 fps target quality.

## Product boundaries

This is a launcher, not a system-level OS replacement. Do not implement:

- root-only recents takeover

- lock-screen replacement

- control-center clone

- unsafe overlays

- Accessibility misuse

## Tech direction

- Kotlin

- modern AndroidX stack

- Compose for settings/search/secondary surfaces

- controlled launcher surface for workspace/drawer/folder motion core

- official Android APIs only

- Play-safe package visibility strategy

## Architecture expectations

Prefer clean separation between:

- launcher-core

- system integration

- data

- benchmark

## Performance expectations

Design for 90 fps on 90 Hz devices. Avoid expensive blur and overdraw in hot paths. Benchmark cold start, drawer open, and folder open/close.

## Documentation

Whenever you make major changes, update:

- README.md

- docs/launcher-plan.md

- docs/architecture.md

## Working style

- inspect before editing

- prefer incremental, reviewable changes

- do not rewrite working code without strong reason

- document assumptions and deferred work clearly

- run available tests and linters before finishing

