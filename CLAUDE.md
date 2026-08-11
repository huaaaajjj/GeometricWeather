# CLAUDE.md

Guidance for Claude Code (claude.ai/code) in this repository. **Coding principles, workflow, and review live in [`AGENTS.md`](AGENTS.md)** — this file is project structure, commands, and process. **Project status, history, and TODOs live in [`AI_CONTEXT.md`](AI_CONTEXT.md).**

## Project overview

GeometricWeather is an Android weather app — a maintained fork of [WangDaYeeeeee/GeometricWeather](https://github.com/WangDaYeeeeee/GeometricWeather), modernized onto current toolchains (Gradle 8.x / AGP 8.x / Kotlin 1.9 / compileSdk 35) with working weather providers. Single Gradle module (`app`), package `wangdaye.com.geometricweather`.

## Build & test

```bash
./gradlew assemblePubDebug        # debug build (pub flavor: amap + bugly)
./gradlew assemblePubRelease      # release build (signed, used for GitHub Releases)
./gradlew assembleFdroidDebug     # what CI builds on non-tag pushes/PRs
./gradlew test                    # all JVM unit tests (src/test/java)
./gradlew testPubDebugUnitTest    # unit tests for one variant
./gradlew :app:testPubDebugUnitTest --tests "basic.LocationTest"   # single test class
```

Requires **JDK 17**. CI (`.github/workflows/android.yml`) builds `assembleFdroidDebug` on pushes/PRs and `assemblePubRelease` on `v*` tags. `assemble*` writes `GeometricWeather-v<versionName>_<flavor>.apk` per variant (e.g. `GeometricWeather-v3.4.3_pub.apk`).

## Project structure

MVVM + Hilt DI throughout. UI is a **hybrid**: legacy Views/RecyclerView/XML (main weather screen, widgets) coexist with Jetpack Compose (settings, allergen screens, theming). Async is **Kotlin Coroutines via `AsyncHelper`** — RxJava was fully removed.

### Weather provider pipeline

To add or fix a provider, all four layers must line up:

1. **`weather/apis/*Api.java`** — Retrofit interfaces returning `Call<T>` (not RxJava `Observable`). Built in `weather/di/ApiModule.java`, one `Retrofit` instance per provider with its own `BuildConfig` base URL.
2. **`weather/json/<provider>/*`** — Gson DTOs mirroring the API response.
3. **`weather/converters/*ResultConverter.java`** — map provider DTOs → the unified `common/basic/models/weather/Weather` model. **A recurring crash source** — provider fields are frequently null; guard everything (the model uses `@NonNull` assertions that throw on null constructor args).
4. **`weather/services/*WeatherService.java`** — orchestrate the calls, extend `WeatherService`. Simple providers do one call; AccuWeather/OWM/MF fan out parallel calls using `CountDownLatch` + `AtomicReference`. Use a **`List<Call>` field, not a single field**, to avoid concurrent requests overwriting each other.

`WeatherServiceSet` (Hilt-injected) maps a `WeatherSource` enum → the right service. Current sources: ACCU, OWM, MF (France only), CAIYUN, OPEN_METEO, WEATHERAPI, CMA. `WeatherHelper` is the entry point.

### Data flow

`MainActivity` → `MainActivityViewModel` → `MainActivityRepository` → `WeatherHelper` (network) + `DatabaseHelper` (cache). Location resolution: `LocationHelper` → `location/services/LocationService.kt` (flavor-specific), with `BaiduIPLocationService` as IP fallback. Coordinates from Chinese providers are **GCJ-02, converted to WGS-84**.

### Persistence (Room)

`db/` — `GeometricWeatherDatabase` (schema version 63), `WeatherDatabaseDao`, entities, `RoomTypeConverters`. Migrated from GreenDAO.

- **Do not change the database schema** (hard constraint).
- Child entities (`DailyEntity`, `HourlyEntity`, …) store `weatherSource` as a plain `String` (via `source.getId()` on write); `LocationEntity` uses strongly-typed `WeatherSource`/`TimeZone` via converters. Match the existing pattern.
- **Never touch Room on the main thread** — wrap DB access in `AsyncHelper.runOnIO` (a repeated crash source; `AsyncHelper` emitter callbacks post back to the main thread to mimic the old `observeOn(mainThread)`).

### Background / widgets / theming

- `background/polling/` — `WorkManager` workers + permanent foreground services for scheduled updates.
- `background/receiver/widget/` + `remoteviews/` — home-screen widgets and notifications (RemoteViews-based, separate from the Compose/View UI).
- `theme/` — `weatherView/` draws the animated geometric backgrounds (Material/Pixel renderers); `theme/compose/` holds Compose day/night palettes; `theme/resource/` provides swappable icon packs.

## Product flavors (`store` dimension)

Each flavor swaps proprietary-vs-open source dirs (see `sourceSets` in `app/build.gradle`):

- **pub** — AMap location (`src/amap`) + Bugly crash reporting (`src/bugly`) + `src/proprietary`. Primary distribution.
- **gplay** — Google Play Services location (`src/noamap`) + no Bugly (`src/nobugly`) + `src/proprietary`.
- **fdroid** — fully open source: `src/noamap` + `src/nobugly` + `src/opensource`. No proprietary deps.

When touching location or crash-reporting code, check whether the class lives in a flavor-specific source dir — the same class name often has parallel implementations across `amap`/`noamap` and `bugly`/`nobugly`/`opensource`.

## API keys

Provider keys are **base64-encoded in `app/build.gradle`** (`decodeBase64(...)` → `buildConfigField`) so they survive secret scanning; non-secret URLs/keys sit in `gradle.properties`. All exposed via `BuildConfig.*`. Override locally by setting the property in `local.properties` (takes precedence over the embedded value). Don't hardcode keys in source — add a `buildConfigField`.

## Conventions

- SDK levels: `compileSdk`/`targetSdk` **35**, `minSdk` **21** (held at 21 deliberately — only raise to 24 if a required library forces it). Namespace `wangdaye.com.geometricweather`.
- **Don't rewrite the UI; preserve the original visual style** (project constraint).
- Versioning: `3.2.x` = small fixes, `3.x` = larger changes. Bump `versionCode`/`versionName` in `app/build.gradle` when shipping.
- The repo root can accumulate build artifacts and JVM crash dumps (`hs_err_pid*.log`, `*.apk`) — not source; they are gitignored, ignore them.

(General coding style, the Kotlin-migration rule, and the review checklist are in [`AGENTS.md`](AGENTS.md).)

## Release workflow

Build and verify the release APK **locally** before publishing — CI is unreliable here (jitpack 403s). `master` is the current line: rebuilt on v3.3.6 (the 3.3.7–3.3.13 work was rolled back for location regressions and preserved in `backup/3.3.13-fixes` + tags `v3.3.7`..`v3.3.13`). Daily releases publish as **Prerelease**; formal releases are created manually (not via Action auto-publish).

## AI_CONTEXT.md update rules

`AI_CONTEXT.md` is the running maintenance log — **read it first**, before starting a task. It holds goals, status, completed work, known issues, TODOs, version matrix, and key `file:line` references.

- **Append a one-line entry after completing each task.**
- The log, the README, and commit messages are written in **Chinese** — match that language when appending.
