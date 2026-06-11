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
- 修复彩云天气 API: 从 demo token 迁移到 App Key & Secret 签名认证
  - 新增 CaiYunSignatureInterceptor (HMAC-SHA256 请求签名)
  - 替换 demo token (TAkhjf8d1nlSlspN) 为注册的 AppKey (xgsrrjaqfmra3ewz)
  - 签名算法: HMAC-SHA256(method:path:query:app_key:nonce:timestamp, app_secret)
  - 添加 x-cy-nonce, x-cy-timestamp, x-cy-signature 请求头
- 清理 proguard-rules.pro: 移除 18 个未解析类名 (GreenDAO, RxJava internal, DataStore protobuf 等)
- 添加缺失权限: POST_NOTIFICATIONS, FOREGROUND_SERVICE_LOCATION, SCHEDULE_EXACT_ALARM
- 修复 JVM OOM 崩溃: gradle.properties 内存 4096M → 2048M
- 修复 MainAdapter 和 MainThemeColorProvider 的 NPE 崩溃
  - MainAdapter: onBindViewHolder 中添加 mLocation null 检查，getItemCount/getItemViewType 添加 mViewTypeList null 检查
  - MainThemeColorProvider: getColor()/getContext() 方法添加 instance null 检查，返回安全默认值
  - 防止 RecyclerView 布局时和 MainThemeColorProvider 未绑定时的 NPE 崩溃

## 禁止

- 改变数据库结构
- 重写 UI

## Java → Kotlin 迁移策略

- **新代码一律用 Kotlin 编写**
- **修 bug 时顺便将相关 Java 文件迁移为 Kotlin**
- 不做全量迁移，渐进式推进
- 已迁移的关键文件: AsyncHelper.kt, MainThemeColorProvider.kt

## 当前版本

| 组件 | 版本 |
|------|------|
| Gradle | 8.7 |
| AGP | 8.4.0 |
| Kotlin | 1.9.24 |
| Compose Compiler | 1.5.14 |
| compileSdk | 35 |
| targetSdk | 35 |
| minSdk | 21 |

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

## 已知 Bug（待修复）

### P0 — 会崩溃

- [x] **AccuWeatherService:149** — `currentResult.get().get(0)` 未 null/空列表检查，NPE
- [x] **PollingUpdateHelper.kt:110** — Toast.makeText().show() 在 IO 线程执行，Looper 未 prepare → 崩溃
- [x] **LocationHelper.java:178** — `requestLocationFailed` 回调在 IO 线程直接触发，级联到 UI 操作崩溃
- [x] **SearchActivity.java:138** — `mCurrentList` IO 线程写入/主线程读取，无同步，点击过快 NPE

### P1 — 天气数据静默丢失/错误

- [x] **CaiyunResultConverter.java:83** — `r.wind` 无 null 检查，访问 `.direction`/`.speed` 被 try-catch 静默吞掉
- [x] **MfWeatherService.java:179,235** — 硬编码 GPS 坐标 48.86d, 2.34d（巴黎），替换为 46.5/2.5（法国中心）
- [x] **MfWeatherService.java:113,123** — `location.getProvince()` 可能 null，添加 null 检查
- [x] **全部 7 个天气服务** — 添加 `response.isSuccessful()` 检查（CaiYun/OpenMeteo/WeatherApi）；Accu/OWM/MF 已有 body null 检查+日志
- [x] **AccuWeatherService.java:109-135** — minute/alert/aqi 空 catch 添加 Log.e
- [x] **OwmWeatherService.java:84-85** — air pollution 空 catch 添加 Log.e

### P2 — 并发/资源/代码错误

- [ ] **WeatherApiWeatherService/OpenMeteoWeatherService/CaiYunWeatherService/BaiduIPLocationService** — 单 Controller 被并发覆盖，旧协程泄漏
- [ ] **CaiYunSignatureInterceptor.java:89** — 签名失败静默发送未签名请求
- [ ] **AccuResultConverter.java:367** — `airAndPollen` 列表未 null 检查，迭代时 NPE
- [ ] **GeometricWeather.kt:132** — BufferedReader 异常路径未 close，文件句柄泄漏
- [ ] **MainActivity.kt:56-57** — Intent action 拼写错误：geomtricweather → geometricweather

### P3 — 低风险/代码质量

- [ ] **GeometricWeather.kt:226** — observeForever 从未 removeObserver
- [ ] **MainThemeColorProvider.kt:66** — static 持有 Activity 引用
- [ ] **ObjectUtils.java:13-20** — ObjectStream 未 close
- [ ] **MfResultConverter.java:116,138** — 硬编码 Europe/Paris 时区（有 TODO 未修复）
