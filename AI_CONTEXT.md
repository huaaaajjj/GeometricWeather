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
- 修复 Room 主线程数据库访问崩溃 (20处，10个文件)
  - MainActivityViewModel.init() → AsyncHelper.runOnIO 回调
  - WeatherHelper/LocationHelper 回调中 DB 写入 → IO 线程
  - SearchActivity/WidgetConfigActivity onCreate → 异步加载
  - AllergenActivity Compose → LaunchedEffect + Dispatchers.IO
  - ServiceProviderSettingsScreen → AsyncHelper.runOnIO
  - TileService.refreshTile() → IO 线程
- 工具链升级: Gradle 7.6.4 → 8.7, AGP 7.4.2 → 8.4.0, Kotlin 1.8.22 → 1.9.24
  - Compose Compiler 1.4.8 → 1.5.14
  - compileSdk/targetSdk 34 → 35
  - OkHttp 3.12.12 → 4.12.0
  - Hilt 2.48 → 2.51.1
  - AndroidX 全套依赖升至最新
  - 修复 WorkManager Configuration.Provider API 变更
  - 修复 OkHttp Util.platformTrustManager() 移除
  - 添加 buildConfig true (AGP 8.x 默认关闭)
  - lintOptions → lint
  - 合并 kapt 块，移除废弃的 kotlin-stdlib-jdk7/jdk8 force
- 修复 compileSdk 35 deprecation 警告 (22处，9个文件)
  - DisplayUtils: SYSTEM_UI_FLAG → WindowInsetsControllerCompat
  - PackageManager int-flag → ApplicationInfoFlags/ResolveInfoFlags (API 33+)
  - TimeObserverService: registerReceiver 添加 RECEIVER_NOT_EXPORTED (API 33+)
  - MaterialLiveWallpaperService: getDefaultDisplay().getRefreshRate() → getDisplay().getRefreshRate() (API 30+)
  - MainActivity: getParcelableExtra → 带 Class 参数版本 (API 33+)
  - Location: readSerializable → 带 Class 参数版本 (API 33+)
  - FitSystemBarComposeWrappers: SmallTopAppBar → TopAppBar, ArrowBack → AutoMirrored
- RxJava → Coroutines 完全迁移 (28个文件，-256行代码)
  - AsyncHelper.java → AsyncHelper.kt (Kotlin Coroutines: Job, Dispatchers, delay)
  - 8个 Retrofit API 接口: Observable<T> → Call<T>
  - 6个 Weather Service: 迁移到 AsyncHelper + Call.execute()
    - 简单服务: OpenMeteo, WeatherApi, CaiYun, BaiduIP
    - 复杂并行服务: AccuWeather(6路), OWM(3路), MF(6路) — CountDownLatch + AtomicReference
  - WeatherHelper.requestLocation(): Observable.zip → CountDownLatch
  - 删除: SchedulerTransformer, BaseObserver, ObserverContainer
  - 移除依赖: rxjava, rxandroid, adapter-rxjava2, room-rxjava2
- 修复 AsyncHelper Emitter 回调线程问题
  - Emitter.send() 在非主线程时通过 Handler post 到主线程
  - 匹配原 RxJava observeOn(mainThread()) 行为
  - 修复 "Cannot invoke setValue on a background thread" 崩溃

## 3.3.6 回退线修复（分支 rollback/3.3.6，HEAD=v3.3.6）

> 背景：3.3.13 引入了定位"显示默认"等回归，故回退到 v3.3.6 重新开发（3.3.13 的全部改动备份在提交 4054781 与 stash）。在 3.3.6 基础上做有针对性修复，**不带回** 3.3.13 的定位改动（保留 3.3.6 的北京兜底，实测定位更好）。

- 数据提供商中文界面补全：`values-zh-rCN/arrays.xml` 的 `weather_sources`/`weather_source_voices` 补 Open-Meteo、WeatherAPI（4→6）；`strings.xml` 补 3 个分区标题 + 高级页 14 条 API KEY 文案
- 全链路诊断日志：LocHelper（定位/逆地理编码/北京兜底）、WeatherHelper、AccuWeatherService（geoPosition HTTP 码 + 异常）、CaiYunService（HTTP 码 + 解析结果）
- 修复彩云 HTTP 400：移除 `CaiYunSignatureInterceptor`（标准 v2.6 token 接口不需签名，签名头反被拒）；改用 local.properties 的有效 token；删除 CAIYUN_APP_SECRET
- 修复彩云解析崩溃：v2.6 的 `hourly.air_quality`（未用，改 JsonElement 容错）与 `daily.air_quality`（由 List 改为对象 + 同步改 converter）实际是对象不是数组
- 全面空安全（治本）：`Current`/`Daily` 构造器对 @NonNull 但实际可能为 null 的字段（AirQuality/Pollen/Astro sun&moon）强制兜底空对象 → 一次性消除 HeaderViewHolder/MainAdapter/DailyViewHolder/WeatherEntityGenerator/DailyEntityGenerator/Weather.isDaylight 等处的 NPE/越界
- 修复动态壁纸：① onVisibilityChanged 主线程读 Room → AsyncHelper.runOnIO + 空列表兜底 ② getDisplay() 在 WallpaperService(非视觉 Context) 抛异常 → 改用 DisplayManager ③ 绘制 runnable 加 Surface.isValid() + try/finally 防 "Surface has already been released" ④ onDestroy 无条件取消绘制 interval + quitSafely，interval 重建前先 cancel 且仅在线程存活时 post → 消除 12 万条/120s "dead thread" 刷屏
- 实测（小米 HyperOS 真机）：定位解析到天津、彩云 HTTP 200 + 转换 OK、动态壁纸正常渲染、0 崩溃、0 dead-thread 刷屏
- 清理本次调试日志（LocationHelper/WeatherHelper 还原无日志；Accu/CaiYun 去日志保留空安全防护）
- 设为主分支并发布：master 重置到该修复线并 force-push（3.3.7-3.3.13 保留在 tag v3.3.7~v3.3.13 与分支 backup/3.3.13-fixes）；版本 3.3.6 → **3.4.0**（versionCode 30400，高于旧 30313）；发布 v3.4.0 Release
- 修复动态壁纸配置页内容被顶栏遮挡（Material3Scaffold innerPadding 未应用到 LazyColumn）；发布 **3.4.1**（30401）
- 回填 3.3.7–3.3.13 的非定位崩溃修复到 3.4.x（不带回定位回归）：MainThemeColorProvider 空安全、MainActivityViewModel 空 validList 越界（首装崩溃）、DefaultResourceProvider 图标缺失 NPE（getDrawable 兜底 SunDrawable）、Baidu/AMap 服务补 foregroundServiceType=location；发布 **3.4.2**（30402）
- P3 清理：GeometricWeather.setDayNightMode 的 observeForever 改为稳定 Observer + 先 remove 再加（防累积）；MfResultConverter 的 Europe/Paris 去掉 TODO（MF 仅法国、搜索结果无 tz，真实 tz 随预报返回，故 Paris 正确）；发布 **3.4.3**（30403）
- 新增 Android（16）预测性返回：manifest `<application>` 开 `enableOnBackInvokedCallback="true"`（全 app 生效，绝大多数页面不拦截 back，自动获得预测返回动画）；`SearchActivity`/`AbstractWidgetConfigActivity` 的 legacy `onBackPressed()` 覆写迁移到 `OnBackPressedDispatcher` + `OnBackPressedCallback`（真机验证 "OnBackInvokedCallback is not enabled" 警告消失、0 崩溃）
- 修复搜索位置闪退：`WeatherHelper.requestLocation` 的成功/失败回调在 `Dispatchers.IO` 线程直接触发，上层 `SearchActivityViewModel` 在回调里 `LiveData.setValue()` → 后台线程调用抛 `IllegalStateException` 崩溃（RxJava→Coroutines 迁移时漏掉 `observeOn(mainThread)`，姊妹方法 `requestWeather` 已正确处理）；改为 `AsyncHelper.delayRunOnUI` 回主线程后再回调
- 修复"只有定位地区能换天气源"：`ServiceProviderSettingsScreen` 切换全局天气源时只重写定位地区（`indexOfFirst { isCurrentPosition }`），手动添加的地区不受影响；改为重设**所有**地区为新源（用旧源删旧缓存、按 formattedId 去重防同城多源塌缩、整表重写后重载自动拉取）；代价：放弃"同城用不同源各存一个"。发布 **3.4.4**（30404）
- 新增天气源 **中国气象局（CMA, weather.cma.cn）**：无需 Key，基于监测站 ID。流水线 4 层 + 注册：`weather/json/cma/{CmaWeatherResult,CmaLocationResult}`、`apis/CmaApi`（autocomplete/weather.view/逐小时 HTML）、`converters/CmaResultConverter`、`services/CmaWeatherService`；`WeatherSource` 加 CMA、`WeatherServiceSet`/`ApiModule` 注册、`build.gradle` 加 `CMA_BASE_URL`、`values`+`values-zh-rCN` 的 weather_sources/voices/values 加项。要点：① autocomplete 只认拼音/英文 → 中文用 `android.icu.Transliterator`（API24+，<24 透传）转拼音；② `now` 无天气文字 → 取当天 daily 按昼夜推导；③ 逐小时无 JSON → 抓 `web/weather/{id}.html` 的 hour-table 正则解析，图标号经 daily 的 code→text 自洽映射为 WeatherCode；④ 定位地区用 stationid 空（按 IP）兜底；⑤ 风速 m/s→km/h(×3.6)；⑥ 中文文字→WeatherCode 映射。顺带把 `Utils.getName/getVoice` 的 `!!` 改 `?: id` 防御（消除其余 13 个 locale 源名数组缺项的潜在 NPE）。发布 **3.4.5**（30405）
- 修复 CMA"获取天气数据失败"：① **根因**——weather.cma.cn 的 WAF 对默认 `okhttp/4.12.0` UA 返回 **403**（浏览器 UA 返回 200）；给 `CmaApi` 三个接口加 `@Headers("User-Agent: Mozilla/5.0 …Chrome…")`（仅 CMA，不动共享 OkHttp）。② **健壮性**——被全局换源切到 CMA 的旧地区 cityId 不是 CMA 站点 ID → `requestWeather` 首次无 daily 时按地名（拼音→autocomplete）/IP 兜底解析站点 ID 重试，逐小时也用实际命中的站点 ID 抓取。发布 **3.4.6**（30406）
- 修复 CMA 定位错误 + 搜索地点无天气（真机 logcat 定位）：① **定位错**——`weather/view?stationid=` 留空走 IP，手机出口 IP 可能跨省（南开→开州）；改为用 GPS 坐标在 `api/map/weather/1`（全国 2439 站点带经纬度，进程内缓存）里找**最近站点**，IP 仅作最后兜底。② **搜索/换源地点无天气**——CMA 对非本站 ID 返回 `data:""`（空字符串非对象），Gson 抛异常逃到外层 catch，使我之前加的重试从未触发；改用 `tryGetWeather`（吞解析异常→null）+ 坐标/地名/IP 重解析站点重试。autocomplete 仅匹配拼音/英文且模糊（`tianjin` 不返回天津、`nankai` 首条是南川），故自动解析一律走坐标最近站点而非取 autocomplete 首条。真机验证：南开 → 最近站 54517 黑牛城（天津），当前/7天/逐小时/0 崩溃。发布 **3.4.7**（30407）



## 当前版本

| 组件             | 版本   |
| ---------------- | ------ |
| Gradle           | 8.7    |
| AGP              | 8.4.0  |
| Kotlin           | 1.9.24 |
| Compose Compiler | 1.5.14 |
| compileSdk       | 35     |
| targetSdk        | 35     |
| minSdk           | 21     |

## 版本号策略

- **3.2.x** — 小更新（Bug 修复、小功能调整）
- **3.x** — 大更新（架构变更、大功能新增）

## 发布策略

- 日常 Release 发布为 **Prerelease**
- 正式版 Release 手动创建（不使用 Action 自动发布）
- 每次完成一项任务写入本文件

### 修复记录

- 修复彩云天气: 小米市场API失效 → 官方 v2.6 (api.caiyunapp.com)
- 修复彩云天气: Wind/@NonNull 传 null 导致转换失败
- 修复 OpenWeather: One Call API 废弃 → 免费端点 weather/forecast/air_pollution

## 待完成

- [x] 升级 compileSdk 34 → 35
- [ ] 升级 minSdk 21 → 24（暂不升级，仅在关键库必须要求时升级）
- [x] GreenDAO → Room（已删除GreenDAO依赖、新建8个Room Entity、DAO、Database类、TypeConverter、重写DatabaseHelper、编译器通过）
  - Room 版本 2.6.1，数据库 schema 版本 63
  - 注意：子实体（DailyEntity/HourlyEntity等）weatherSource 字段仍用 String 类型，写入时通过 source.getId() 转换
  - 注意：LocationEntity 使用 WeatherSource/TimeZone 强类型（由 RoomTypeConverters 处理）
  - 本地 Microsoft JDK 17 kapt 有 InvocationTargetException 问题，添加 kapt.useWorkerApi=false 后解决
- [x] 升级 Gradle 8.x (需先完成 GreenDAO → Room)
- [x] 升级 AGP 8.x
- [x] 升级 Kotlin 1.9+
- [x] RxJava → Coroutines
- [ ] Java → Kotlin 逐步迁移
