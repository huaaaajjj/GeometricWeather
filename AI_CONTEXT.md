# GeometricWeather Maintenance

## 目标

- 保持原 UI 风格
- 使用 Kotlin
- 使用 Retrofit
- 使用 MVVM
- compileSdk=35
- minSdk=24

## 已完成

- 修复 Gradle 7.3.3 → 7.6.4
- 升级 AGP 7.2.0 → 7.4.2
- 升级 Kotlin 1.5.31 → 1.8.22
- 升级 AndroidX 全套依赖
- 升级 compileSdk/targetSdk 32 → 34
- 移除 CyanogenMod SDK
- 移除 jcenter() 仓库
- 移除 armeabi ABI
- 修复 proguard-rules.pro
- 修复 Widget Intent Filter 拼写
- 替换 BubbleSeekBar → Material Slider
- 配置 GitHub Actions CI/CD
- 发布 v3.102-modernize Release
- 修复 API keys 读取问题 (local.properties 优先)
- 修复坐标系转换 (GCJ-02 → WGS-84)
- 发布 v3.103 Release (3个APK)
- 添加 Open-Meteo 天气提供商 (免费，无需API Key)
- 添加 WeatherAPI 提供商
- 发布 v3.104 Release (含新天气提供商)
- 添加新提供商 API Key 设置界面
- 修复设置界面位置服务下拉菜单 bug
- 内置 API Keys (AccuWeather, OpenWeather, 彩云, WeatherAPI)
- 移除 QWeather 和 Visual Crossing (API不可用)
- 重写"数据提供商"设置页面（分区布局，Section Header）
- 修复 MIUI Activity recreate 崩溃 (ClassCastException BinderProxy)
- 全面 null 安全改造 (CaiYun/AccuWeather/OWM/MF 转换器)
- 修复 MfResultConverter province/country 字段 null 崩溃
- 更换 AccuWeather base URL → dataservice.accuweather.com (api. 域名 key 失效)
- 设置页面顶部栏 MediumTopAppBar → SmallTopAppBar (减少遮挡)
- 修复设置页面 Scaffold innerPadding 未传入 NavHost 导致内容被顶部栏遮挡
- 发布版本切换为 Prerelease
- 优化设置页面滑动性能 (移除不必要的 nestedScroll、Card → Surface、pinnedScrollBehavior)
- 修复彩云天气: 小米市场API失效 → 官方 v2.6 API (api.caiyunapp.com)
- 修复彩云天气: Wind构造器传null导致转换失败 (Hourly/Daily)
- 修复彩云天气: Weather构造器minutely/alert传null导致@NonNull断言失败
- 移除 OpenWeather (OWM) — One Call API 3.0 已废弃，免费 Key 无法使用

## 禁止

- 改变数据库结构
- 重写 UI

## 当前版本

| 组件 | 版本 |
|------|------|
| Gradle | 7.6.4 |
| AGP | 7.4.2 |
| Kotlin | 1.8.22 |
| Compose Compiler | 1.4.8 |
| compileSdk | 34 |
| targetSdk | 34 |
| minSdk | 21 |

## 发布策略

- 所有远程 Release 都发布为 **Prerelease**
- 每次完成一项任务写入本文件

## 待完成

- [ ] 升级 compileSdk 34 → 35
- [ ] 升级 minSdk 21 → 24
- [ ] GreenDAO → Room
- [ ] 升级 Gradle 8.x (需先完成 GreenDAO → Room)
- [ ] 升级 AGP 8.x
- [ ] 升级 Kotlin 1.9+
- [ ] RxJava → Coroutines
- [ ] Java → Kotlin 逐步迁移
