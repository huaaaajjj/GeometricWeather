# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

GeometricWeather is an Android weather app — a maintained fork of [WangDaYeeeeee/GeometricWeather](https://github.com/WangDaYeeeeee/GeometricWeather), modernized to build on current toolchains (Gradle 8.x / AGP 8.x / Kotlin 1.9 / compileSdk 35) and updated with working weather providers. It is a single Gradle module (`app`) under package `wangdaye.com.geometricweather`.

**`AI_CONTEXT.md` is the running maintenance log** — read it first. It records the project's goals, constraints, completed work, known bugs (with file:line), and the version matrix. **Append a one-line entry there after completing each task** — the log, the README, and commit messages are written in **Chinese**, so match that language when appending.

## Build & test

```bash
./gradlew assemblePubDebug        # debug build (pub flavor: amap + bugly)
./gradlew assemblePubRelease      # release build (signed, used for GitHub Releases)
./gradlew assembleFdroidDebug     # what CI builds on non-tag pushes/PRs
./gradlew test                    # all JVM unit tests (src/test/java)
./gradlew testPubDebugUnitTest    # unit tests for one variant
./gradlew :app:testPubDebugUnitTest --tests "basic.LocationTest"   # single test class
```

Requires **JDK 17**. CI (`.github/workflows/android.yml`) builds `assembleFdroidDebug` on pushes/PRs and `assemblePubRelease` on `v*` tags. Tagged releases are published as **Prerelease** by default.

### Product flavors (`store` dimension)

Each flavor swaps in different proprietary-vs-open source dirs (see `sourceSets` in `app/build.gradle`):

- **pub** — AMap location (`src/amap`) + Bugly crash reporting (`src/bugly`) + `src/proprietary`. Primary distribution.
- **gplay** — Google Play Services location (`src/noamap`) + no Bugly (`src/nobugly`) + `src/proprietary`.
- **fdroid** — fully open source: `src/noamap` + `src/nobugly` + `src/opensource`. No proprietary deps.

When touching location or crash-reporting code, check whether the class lives in a flavor-specific source dir — the same class name often has parallel implementations across `amap`/`noamap` and `bugly`/`nobugly`/`opensource`.

## API keys

Provider keys are **base64-encoded in `app/build.gradle`** (`decodeBase64(...)` → `buildConfigField`) so they survive secret scanning, with non-secret URLs/keys in `gradle.properties`. All are exposed via `BuildConfig.*`. To override locally, set the property in `local.properties` (takes precedence over the embedded value). Don't hardcode keys in source — add a `buildConfigField` instead.

## Architecture

MVVM + Hilt DI throughout. UI is a **hybrid**: legacy Views/RecyclerView/XML (main weather screen, widgets) coexist with Jetpack Compose (settings, allergen screens, theming). Async is **Kotlin Coroutines via `AsyncHelper`** — RxJava was fully removed (the empty `common/rxjava/` package is a leftover).

### Weather provider pipeline

The core abstraction for fetching weather. To add or fix a provider, all four layers must line up:

1. **`weather/apis/*Api.java`** — Retrofit interfaces returning `Call<T>` (not RxJava `Observable`). Built in `weather/di/ApiModule.java`, one `Retrofit` instance per provider with its own `BuildConfig` base URL.
2. **`weather/json/<provider>/*`** — Gson DTOs mirroring the API response.
3. **`weather/converters/*ResultConverter.java`** — map provider DTOs → the app's unified `common/basic/models/weather/Weather` model. **These are a recurring source of crashes** — provider fields are frequently null; guard everything (the model uses `@NonNull` assertions that throw on null constructor args).
4. **`weather/services/*WeatherService.java`** — orchestrates the calls, extends `WeatherService`. Simple providers do one call; AccuWeather/OWM/MF fan out multiple parallel calls using `CountDownLatch` + `AtomicReference`. Use a **`List<Call>` field, not a single field**, to avoid concurrent requests overwriting each other.

`WeatherServiceSet` (Hilt-injected) maps a `WeatherSource` enum → the right service. Current sources: ACCU, OWM, MF (France only), CAIYUN, OPEN_METEO, WEATHERAPI. `WeatherHelper` is the entry point callers use.

### Data flow

`MainActivity` → `MainActivityViewModel` → `MainActivityRepository` → `WeatherHelper` (network) + `DatabaseHelper` (cache). Location resolution goes through `LocationHelper` → `location/services/LocationService.kt` (flavor-specific) with `BaiduIPLocationService` as IP fallback. Note coordinates from Chinese providers are **GCJ-02 and converted to WGS-84**.

### Persistence (Room)

`db/` — `GeometricWeatherDatabase` (schema version 63), `WeatherDatabaseDao`, entities, and `RoomTypeConverters`. Migrated from GreenDAO.

- **Do not change the database schema** (a hard project constraint — see `AI_CONTEXT.md`).
- Child entities (`DailyEntity`, `HourlyEntity`, etc.) store `weatherSource` as a plain `String` (converted via `source.getId()` on write); `LocationEntity` uses strongly-typed `WeatherSource`/`TimeZone` via converters. Match the existing pattern.
- **Never touch Room on the main thread** — wrap DB access in `AsyncHelper.runOnIO` (this has caused many crashes; `AsyncHelper` emitter callbacks are posted back to the main thread to mimic the old `observeOn(mainThread)`).

### Background / widgets

- `background/polling/` — `WorkManager` workers + permanent foreground services for scheduled weather updates.
- `background/receiver/widget/` + `remoteviews/` — home-screen widgets and notifications (RemoteViews-based, separate from the Compose/View UI).

### Theming

`theme/` — `weatherView/` draws the animated geometric backgrounds (Material/Pixel renderers); `theme/compose/` holds Compose day/night palettes; `theme/resource/` provides swappable icon packs.

## Conventions & constraints

- **New code in Kotlin.** When fixing a bug in a `.java` file, opportunistically migrate it to Kotlin — but no bulk rewrites; keep it incremental.
- **Don't rewrite the UI** and **preserve the original visual style** (project constraints).
- Versioning: `3.2.x` = small fixes, `3.x` = larger changes. Bump `versionCode`/`versionName` in `app/build.gradle`.
- SDK levels: `compileSdk`/`targetSdk` 35, `minSdk` **21** (held at 21 deliberately — only raise to 24 if a required library forces it). Namespace `wangdaye.com.geometricweather`.
- `assemble*` writes `GeometricWeather-v<versionName>.apk` per variant (e.g. `GeometricWeather-v3.4.3_pub.apk`).
- The repo root contains build artifacts and JVM crash dumps (`hs_err_pid*.log`, `*.apk`, `index.html`) — ignore them; they're not source.

## Release workflow

Build and verify the release APK **locally** before publishing — CI is unreliable here (jitpack 403s). `master` is the current line: rebuilt on v3.3.6 (the 3.3.7–3.3.13 work was rolled back for location regressions and preserved in `backup/3.3.13-fixes` + tags `v3.3.7`..`v3.3.13`). Daily releases publish as **Prerelease**.
