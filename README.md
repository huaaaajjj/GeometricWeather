# GeometricWeather

![Geometric Weather](/work/preview-header-android.png?raw=true)

**这是一个勉强能用的修改版本。** 基于上游仓库 [WangDaYeeeeee/GeometricWeather](https://github.com/WangDaYeeeeee/GeometricWeather) 修改。

## 版本号说明

- **3.2.x** — 小更新（Bug 修复、小功能调整）
- **3.x** — 大更新（架构变更、大功能新增）

## 与原版的区别

- 修复 Gradle/AGP/Kotlin 版本兼容性，可在 Android 14+ 上编译运行
- 移除失效的天气提供商（QWeather、Visual Crossing），新增可用的提供商
- 修复 AccuWeather、OpenWeather、彩云天气等 API 兼容性
- 设置界面性能优化及 UI 调整
- 修复 MIUI 系统兼容性问题
- 所有 Release 默认以 Prerelease 发布（除非特别说明）

## 可用天气提供商

| 提供商 | 是否需要 API Key | 状态 |
|--------|-----------------|------|
| Open-Meteo | 免费，无需 Key | ✅ 正常 |
| AccuWeather | 内置 Key | ✅ 正常 |
| OpenWeather | 内置 Key | ✅ 正常 |
| WeatherAPI | 内置 Key | ✅ 正常 |
| CaiYun (彩云天气) | 内置 Key | ✅ 正常 |
| Meteo France | 内置 Key | ⚠️ 仅限法国地区 |

## 下载

- [GitHub Releases](https://github.com/huaaaajjj/GeometricWeather/releases)

## 构建方法

```bash
# 调试版
./gradlew assemblePubDebug

# 发布版
./gradlew assemblePubRelease
```

## 许可证

- [LICENSE](/LICENSE)

## 致谢

- 原项目作者 [WangDaYeeeeee](https://github.com/WangDaYeeeeee/GeometricWeather)
