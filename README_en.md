# GeometricWeather

[中文](README.md) · English

![Geometric Weather](/work/preview-header-android.png?raw=true)

An Android weather app, forked from [WangDaYeeeeee/GeometricWeather](https://github.com/WangDaYeeeeee/GeometricWeather).

Upstream stopped shipping: the original no longer builds against current Android, and most of its weather providers broke as their APIs changed. This fork moves the toolchain to current versions (Gradle 8 / AGP 8 / Kotlin 1.9 / compileSdk 35), replaces or repairs the providers, and keeps fixing crashes. **The UI deliberately stays in the original's style** — this is not a redesign.

The app's own interface is fully translated; this README and the maintenance log are the parts written in Chinese first.

## Screenshots

| Main | Daily / hourly | Air quality |
| --- | --- | --- |
| ![Main screen](docs/screenshots/01-main.png) | ![Daily and hourly overview](docs/screenshots/02-trends.png) | ![Air quality](docs/screenshots/03-air-quality.png) |

| Details | Providers |
| --- | --- |
| ![Details](docs/screenshots/04-details.png) | ![Weather providers](docs/screenshots/05-sources.png) |

Taken on 3.5.13, in Chinese. Note the provider credited in each card's title — that is the multi-source mode telling you who supplied that particular block.

## Download

Grab the latest `GeometricWeather-vX.Y.Z_pub.apk` from [Releases](https://github.com/huaaaajjj/GeometricWeather/releases).

Every release is built and signed locally, and verified on a real device (cold start, live fetch, location) before it is published. **Since 3.5.13 the app can check for new versions itself: About → Check for updates.**

Versioning: the last component is a small fix or tweak, the middle one a larger change.

## Weather providers

The default is **WeatherAPI**; for the most complete data pick **Multi-source**.

| Provider | Key | Coverage | Data | Status |
| --- | --- | --- | --- | --- |
| **Multi-source** | bundled | worldwide (most complete inside China) | 16 days · 384 hours · AQI · alerts | ✅ recommended |
| Open-Meteo | free, no key | worldwide | 16 days · 384 hours (no AQI / alerts) | ✅ |
| WeatherAPI | bundled | worldwide | 3 days · 72 hours · AQI · alerts | ✅ default |
| MET Norway | free, no key | worldwide | ~11 days · ~90 points (no feels-like / sunrise) | ✅ |
| Xiaomi Weather | free, no key | worldwide (richest inside China) | China 15 days · 23 hours · AQI · alerts · minutely; abroad 5 days | ✅ |
| CaiYun | bundled (trial token) | China only | 3 days · 48 hours · AQI · UV | ✅ |
| China Weather (APIHZ) | bundled | China only | 7 days · 56 hours (3-hour steps) | ✅ |
| CMA (China Meteorological Administration) | no key | China only | 7 days · hourly (scraped from the web page) | ✅ |
| OpenWeather | bundled | worldwide | 5 days / 40 points (3-hour steps) · AQI | ✅ |
| Météo France | bundled | France only | 15 days · 73 hours | ✅ |
| AccuWeather | bundled key **expired** | worldwide | — | ❌ unusable |

AccuWeather used to be the richest source (15 days / hourly / UV / AQI / minutely precipitation / alerts), but the bundled key now returns 403. Your own key can be dropped into `local.properties` to bring it back.

### What multi-source does

It asks several providers at once and takes each block from whoever is best at it, rather than falling back to one provider wholesale:

- **Hourly** → Open-Meteo: 384 hours, hour by hour, finer than the 3-hour steps the Chinese sources give
- **Daily** → China Weather: a domestic forecast for a domestic place, reaching 7 days; days 8..16 are appended from Open-Meteo so the range is not lost, and the chance of rain, its amount and the wind — which that source omits — are grafted in from the others
- **Air quality and the "now" reading** → CaiYun: measured Chinese AQI (Open-Meteo carries none at all), plus feels-like, humidity, pressure and visibility
- **Alerts** → the union of everyone; WeatherAPI is the one that reliably has them

Each card's title credits the provider behind it. A provider that fails or times out simply drops through to the next one, and the refresh succeeds on whoever is left — so a place outside China, where the Chinese sources have nothing to say, still gets a full forecast from Open-Meteo and WeatherAPI. The cost is a few more requests per refresh.

## Differences from upstream

**Toolchain**

- Gradle 8.7 / AGP 8.4 / Kotlin 1.9.24 / compileSdk & targetSdk 35 (minSdk stays at 21)
- RxJava fully migrated to Kotlin coroutines; GreenDAO migrated to Room
- Builds as-is with current Android Studio and JDK 17

**Providers**

- Dropped the dead ones (QWeather, Visual Crossing)
- Added China Weather, CMA, WeatherAPI, Open-Meteo, MET Norway, Xiaomi Weather and the multi-source mode
- Repaired CaiYun (removed a needless signature check, fixed AQI parsing), OpenWeather and Météo France

**Fixes and polish**

- A run of crashes: null-safety fallbacks (coordinate-based providers return null where the model asserts non-null), Room off the main thread, live wallpaper
- Location: county-level resolution inside China (the CaiYun source used to collapse every county onto its prefecture city), MIUI compatibility
- UI: the details card is now a two-column grid of gauges, daily/hourly overviews start at the current time, and the Chinese translations were completed

The full per-version record lives in the changelog inside [`AI_CONTEXT.md`](AI_CONTEXT.md) (Chinese).

## Building

Requires **JDK 17**.

```bash
./gradlew assemblePubDebug      # debug
./gradlew assemblePubRelease    # release (signed + R8)
./gradlew test                  # all JVM unit tests
```

Output lands in `app/build/outputs/apk/<flavor>/<type>/`, named like `GeometricWeather-v3.5.13_pub.apk`.

The three flavors differ only in location and crash reporting:

| Flavor | Location | Crash reporting | Notes |
| --- | --- | --- | --- |
| `pub` | platform location by default (Play Services where present), AMap / Baidu SDKs selectable | Bugly | this is what Releases ships |
| `gplay` | the same, minus the AMap SDK (picking AMap falls back to platform) | none | |
| `fdroid` | platform location and the Baidu IP fallback only | none | fully open source, no proprietary dependencies |

Provider keys are base64-encoded in `app/build.gradle`. To use your own, set the same property in `local.properties` — no code change needed.

## Developer notes

- [`AGENTS.md`](AGENTS.md) — coding conventions and review checklist
- [`CLAUDE.md`](CLAUDE.md) — project layout, build commands, release process
- [`AI_CONTEXT.md`](AI_CONTEXT.md) — current state, per-version changelog, known issues and TODOs

## Known limitations

- **AccuWeather's bundled key has expired**, so that provider is unusable (your own key restores it)
- CaiYun runs on a trial token: daily forecast only reaches 3 days, and there is no minutely precipitation
- WeatherAPI's free tier only returns 3 days
- CMA sits behind a WAF that can refuse a development machine (real devices are fine)
- The database schema is pinned at v63; no migrations
- minSdk is still 21

## License

[LGPL-3.0](/LICENSE), same as upstream.

## Credits

- Original author [WangDaYeeeeee](https://github.com/WangDaYeeeeee/GeometricWeather)
- Upstream's contributors and translators (listed in the app's About page)
