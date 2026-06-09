# GeometricWeather (Modified)

![Geometric Weather](/work/preview-header-android.png?raw=true)

A feature-rich weather app for Android, based on [GeometricWeather](https://github.com/WangDaYeeeeee/GeometricWeather).

## Features

- Multiple weather providers: AccuWeather, OpenWeather, Open-Meteo, QWeather, WeatherAPI, Visual Crossing
- Built-in API keys for immediate use
- Modern Android stack: Kotlin, MVVM, Material Design
- Support for Android 5.0+ (API 21)

## Download

Download the latest APK from [Releases](https://github.com/huaaaajjj/GeometricWeather/releases).

## Build Variants

- **pub**: Contains all features including Baidu Location and Bugly
- **gplay**: Includes Google Play Services for improved location
- **fdroid**: No closed-source SDKs (open source only)

## Recent Changes (v3.105)

- Upgraded compileSdk to 35
- Added multiple weather providers (Open-Meteo, QWeather, WeatherAPI, Visual Crossing)
- Built-in API keys for all providers
- Fixed coordinate system conversion (GCJ-02 → WGS-84)

## Development

```bash
# Clone the repository
git clone https://github.com/huaaaajjj/GeometricWeather.git

# Build debug APK
./gradlew assembleFdroidDebug

# Build release APK
./gradlew assemblePubRelease
```

## License

- [LICENSE](/LICENSE)

## Credits

Original project by [WangDaYeeeeee](https://github.com/WangDaYeeeeee/GeometricWeather)
