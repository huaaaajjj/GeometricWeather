# GeometricWeather

![Geometric Weather](/work/preview-header-android.png?raw=true)

**这是一个勉强能用的修改版本。** 基于上游仓库 [WangDaYeeeeee/GeometricWeather](https://github.com/WangDaYeeeeee/GeometricWeather) 修改。

## 最新版本：3.4.0

在 v3.3.6 基础上重建（回退了 3.3.7–3.3.13 引入的定位回归），并修复了一批崩溃与数据问题：

- **彩云天气**：移除多余的签名拦截（标准 v2.6 接口不需签名，曾返回 HTTP 400），修正 air_quality 解析 → 恢复可用
- **空安全**：`Current`/`Daily` 对 AirQuality/Pollen/日出日落 兜底，消除主页、每日卡片、数据库写入等处的连环崩溃（坐标型数据源返回 null 时）
- **动态壁纸**：修复主线程读库崩溃、`getDisplay()` 异常、Surface 释放崩溃及绘制循环日志泄漏
- **设置 · 数据提供商**：补全简体中文界面

→ [下载 3.4.0](https://github.com/huaaaajjj/GeometricWeather/releases/tag/v3.4.0)

## 版本号说明

- **3.2.x** — 小更新（Bug 修复、小功能调整）
- **3.x** — 大更新（架构变更、大功能新增）

## 与原版的区别

- 修复 Gradle/AGP/Kotlin 版本兼容性，可在 Android 14+ 上编译运行
- 移除失效的天气提供商（QWeather、Visual Crossing），新增可用的提供商
- 修复 AccuWeather、OpenWeather、彩云天气等 API 兼容性
- 设置界面性能优化及 UI 调整
- 修复 MIUI 系统兼容性问题
- 持续修复崩溃、提升稳定性（空安全、动态壁纸、定位等）

## 可用天气提供商

| 提供商 | 是否需要 API Key | 状态 |
|--------|-----------------|------|
| Open-Meteo | 免费，无需 Key | ✅ 正常 |
| AccuWeather | 内置 Key | ✅ 正常 |
| OpenWeather | 内置 Key | ✅ 正常 |
| WeatherAPI | 内置 Key | ✅ 正常 |
| CaiYun (彩云天气) | 内置 Key | ✅ 正常（仅中国地区） |
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
