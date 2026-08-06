# GeometricWeather 项目上下文

> 项目记忆与当前状态。编码规范见 [`AGENTS.md`](AGENTS.md)，Claude Code 指令/构建/发布见 [`CLAUDE.md`](CLAUDE.md)。
> 每完成一项任务，在「变更日志」追加一行（中文）。

## 当前目标

- 保持原 UI 风格
- 使用 Kotlin
- 使用 Retrofit
- 使用 MVVM
- compileSdk=35
- minSdk=24（当前实际为 21，仅在关键库强制要求时升级）

## 实现状态

- 当前发布版本：**3.5.1**（versionCode 30501）；master 已升到 **3.5.2**（30502，未发布）。主分支 `master`（基于 v3.3.6 重建线）。
- 8 个天气源全部可用：ACCU、OWM、MF（仅法国）、CAIYUN、OPEN_METEO、WEATHERAPI、CMA、APIHZ（中国天气网）。
- 工具链已现代化（见版本矩阵）；RxJava 已全部迁移到 Coroutines；GreenDAO 已迁移到 Room。

### 版本矩阵

| 组件             | 版本   |
| ---------------- | ------ |
| Gradle           | 8.7    |
| AGP              | 8.4.0  |
| Kotlin           | 1.9.24 |
| Compose Compiler | 1.5.14 |
| compileSdk       | 35     |
| targetSdk        | 35     |
| minSdk           | 21     |

## 已完成（里程碑）

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
- 修复 OpenWeather: One Call API 废弃 → 免费端点 weather/forecast/air_pollution
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
- GreenDAO → Room 迁移（删除 GreenDAO 依赖、新建 8 个 Room Entity/DAO/Database/TypeConverter、重写 DatabaseHelper）
  - Room 2.6.1，数据库 schema 版本 63
  - 子实体 weatherSource 用 String（写入时 source.getId()）；LocationEntity 用 WeatherSource/TimeZone 强类型（RoomTypeConverters）
  - 本地 Microsoft JDK 17 kapt 的 InvocationTargetException → 加 `kapt.useWorkerApi=false` 解决

## 变更日志（按版本）

### 3.3.6 回退线（分支 rollback/3.3.6，HEAD=v3.3.6）

> 背景：3.3.13 引入了定位"显示默认"等回归，故回退到 v3.3.6 重新开发（3.3.13 的全部改动备份在提交 4054781 与 stash）。在 3.3.6 基础上做有针对性修复，**不带回** 3.3.13 的定位改动（保留 3.3.6 的北京兜底，实测定位更好）。

- 数据提供商中文界面补全：`values-zh-rCN/arrays.xml` 的 `weather_sources`/`weather_source_voices` 补 Open-Meteo、WeatherAPI（4→6）；`strings.xml` 补 3 个分区标题 + 高级页 14 条 API KEY 文案
- 全链路诊断日志：LocHelper（定位/逆地理编码/北京兜底）、WeatherHelper、AccuWeatherService（geoPosition HTTP 码 + 异常）、CaiYunService（HTTP 码 + 解析结果）
- 修复彩云 HTTP 400：移除 `CaiYunSignatureInterceptor`（标准 v2.6 token 接口不需签名，签名头反被拒）；改用 local.properties 的有效 token；删除 CAIYUN_APP_SECRET
- 修复彩云解析崩溃：v2.6 的 `hourly.air_quality`（未用，改 JsonElement 容错）与 `daily.air_quality`（由 List 改为对象 + 同步改 converter）实际是对象不是数组
- 全面空安全（治本）：`Current`/`Daily` 构造器对 @NonNull 但实际可能为 null 的字段（AirQuality/Pollen/Astro sun&moon）强制兜底空对象 → 一次性消除 HeaderViewHolder/MainAdapter/DailyViewHolder/WeatherEntityGenerator/DailyEntityGenerator/Weather.isDaylight 等处的 NPE/越界
- 修复动态壁纸：① onVisibilityChanged 主线程读 Room → AsyncHelper.runOnIO + 空列表兜底 ② getDisplay() 在 WallpaperService(非视觉 Context) 抛异常 → 改用 DisplayManager ③ 绘制 runnable 加 Surface.isValid() + try/finally 防 "Surface has already been released" ④ onDestroy 无条件取消绘制 interval + quitSafely，interval 重建前先 cancel 且仅在线程存活时 post → 消除 12 万条/120s "dead thread" 刷屏
- 实测（小米 HyperOS 真机）：定位解析到天津、彩云 HTTP 200 + 转换 OK、动态壁纸正常渲染、0 崩溃、0 dead-thread 刷屏
- 清理本次调试日志（LocationHelper/WeatherHelper 还原无日志；Accu/CaiYun 去日志保留空安全防护）

### 3.4.x（master）

- **3.4.0**：设为主分支并发布——master 重置到该修复线并 force-push（3.3.7-3.3.13 保留在 tag v3.3.7~v3.3.13 与分支 backup/3.3.13-fixes）；版本 3.3.6 → 3.4.0（versionCode 30400，高于旧 30313）
- **3.4.1**：修复动态壁纸配置页内容被顶栏遮挡（Material3Scaffold innerPadding 未应用到 LazyColumn）
- **3.4.2**：回填 3.3.7–3.3.13 的非定位崩溃修复（不带回定位回归）：MainThemeColorProvider 空安全、MainActivityViewModel 空 validList 越界（首装崩溃）、DefaultResourceProvider 图标缺失 NPE（getDrawable 兜底 SunDrawable）、Baidu/AMap 服务补 foregroundServiceType=location
- **3.4.3**：P3 清理——GeometricWeather.setDayNightMode 的 observeForever 改为稳定 Observer + 先 remove 再加（防累积）；MfResultConverter 的 Europe/Paris 去掉 TODO（MF 仅法国、搜索结果无 tz，真实 tz 随预报返回，故 Paris 正确）
- 新增 Android（16）预测性返回：manifest `<application>` 开 `enableOnBackInvokedCallback="true"`（全 app 生效，绝大多数页面不拦截 back，自动获得预测返回动画）；`SearchActivity`/`AbstractWidgetConfigActivity` 的 legacy `onBackPressed()` 覆写迁移到 `OnBackPressedDispatcher` + `OnBackPressedCallback`（真机验证 "OnBackInvokedCallback is not enabled" 警告消失、0 崩溃）
- 修复搜索位置闪退：`WeatherHelper.requestLocation` 的成功/失败回调在 `Dispatchers.IO` 线程直接触发，上层 `SearchActivityViewModel` 在回调里 `LiveData.setValue()` → 后台线程调用抛 `IllegalStateException` 崩溃（RxJava→Coroutines 迁移时漏掉 `observeOn(mainThread)`，姊妹方法 `requestWeather` 已正确处理）；改为 `AsyncHelper.delayRunOnUI` 回主线程后再回调
- **3.4.4**：修复"只有定位地区能换天气源"——`ServiceProviderSettingsScreen` 切换全局天气源时只重写定位地区（`indexOfFirst { isCurrentPosition }`），手动添加的地区不受影响；改为重设**所有**地区为新源（用旧源删旧缓存、按 formattedId 去重防同城多源塌缩、整表重写后重载自动拉取）；代价：放弃"同城用不同源各存一个"
- **3.4.5**：新增天气源 **中国气象局（CMA, weather.cma.cn）**——无需 Key，基于监测站 ID。流水线 4 层 + 注册：`weather/json/cma/{CmaWeatherResult,CmaLocationResult}`、`apis/CmaApi`（autocomplete/weather.view/逐小时 HTML）、`converters/CmaResultConverter`、`services/CmaWeatherService`；`WeatherSource` 加 CMA、`WeatherServiceSet`/`ApiModule` 注册、`build.gradle` 加 `CMA_BASE_URL`、`values`+`values-zh-rCN` 的 weather_sources/voices/values 加项。要点：① autocomplete 只认拼音/英文 → 中文用 `android.icu.Transliterator`（API24+，<24 透传）转拼音；② `now` 无天气文字 → 取当天 daily 按昼夜推导；③ 逐小时无 JSON → 抓 `web/weather/{id}.html` 的 hour-table 正则解析，图标号经 daily 的 code→text 自洽映射为 WeatherCode；④ 定位地区用 stationid 空（按 IP）兜底；⑤ 风速 m/s→km/h(×3.6)；⑥ 中文文字→WeatherCode 映射。顺带把 `Utils.getName/getVoice` 的 `!!` 改 `?: id` 防御（消除其余 13 个 locale 源名数组缺项的潜在 NPE）
- **3.4.6**：修复 CMA"获取天气数据失败"——① 根因：weather.cma.cn 的 WAF 对默认 `okhttp/4.12.0` UA 返回 **403**（浏览器 UA 返回 200）；给 `CmaApi` 三个接口加 `@Headers("User-Agent: Mozilla/5.0 …Chrome…")`（仅 CMA，不动共享 OkHttp）。② 健壮性：被全局换源切到 CMA 的旧地区 cityId 不是 CMA 站点 ID → `requestWeather` 首次无 daily 时按地名（拼音→autocomplete）/IP 兜底解析站点 ID 重试，逐小时也用实际命中的站点 ID 抓取
- **3.4.7**：修复 CMA 定位错误 + 搜索地点无天气（真机 logcat 定位）——① 定位错：`weather/view?stationid=` 留空走 IP，手机出口 IP 可能跨省（南开→开州）；改为用 GPS 坐标在 `api/map/weather/1`（全国 2439 站点带经纬度，进程内缓存）里找**最近站点**，IP 仅作最后兜底。② 搜索/换源地点无天气：CMA 对非本站 ID 返回 `data:""`（空字符串非对象），Gson 抛异常逃到外层 catch，使之前加的重试从未触发；改用 `tryGetWeather`（吞解析异常→null）+ 坐标/地名/IP 重解析站点重试。autocomplete 仅匹配拼音/英文且模糊，故自动解析一律走坐标最近站点而非取 autocomplete 首条。真机验证：南开 → 最近站 54517 黑牛城（天津），当前/7天/逐小时/0 崩溃
- 维护清理：CMA `resolveStation()` 去重（`requestWeather`/`requestLocation` 共用一套坐标/地名/IP 兜底）；删除无引用的 `LogHelper.java`；移除空的 `common/rxjava/` 目录
- **3.4.9**：中国天气网 APIHZ 接入按城市接口 `tqyb.php`（`sheng`+`place`），补足 `tqybip.php` 仅按 IP 的短板。`ApihzApi` 加 `getWeatherByPlace`（原 IP 法改名 `getWeatherByIp`）；`requestWeather` 改为**按地名取数**：`fetchForLocation` 把地区省/区/市归一化后**依次尝试** 省+地点 → 仅地点 → IP 兜底；`requestLocation(query)` 改为**真实搜索**（`tqyb.php` 仅地点查，命中即按 `sheng/shi/经纬度` 建 `Location`，cityId=省+市）。复用既有 DTO/转换器，无新增源/arrays/枚举。要点（实测接口怪癖）：① `place` 带「区」会 400（`海淀区`✗/`海淀`✓）→ 去尾「区」；② 直辖市 `sheng` 带「市」会 400（`北京市`✗/`北京`✓）→ 去尾「市」（省/自治区保留）；③ 接口**区级覆盖不全**（`海淀`✓ 但 `天河`/`渝中`✗）→ 区不中再退到市（→`广州`/`重庆`），故按 区→市 两级候选；④ 仅地点查最宽松，搜索与兜底都走它；⑤ Retrofit `@Query` 自动 UTF-8 编码中文（Node 实测北京海淀/南京/上海/重庆/广州/深圳均命中，国外退 IP）。已 `assemblePubDebug` 通过
- **3.5.1**：正式发布版（`assemblePubRelease` 已签名 + R8 minify/shrinkResources）。汇总本会话：APIHZ(中国天气网)源、定位精确到区县、天气源可用性实测页、WeatherAPI 错地区预警过滤 + 缓存键修复 + 孤儿行清理。真机验证（MI 9，卸 debug 装 release）：启动 0 崩溃、自动定位「南开区」、WeatherAPI 联网拉取并 Gson 解析正常（证明 R8 没误删 DTO——`proguard-rules.pro:20` 的 `weather.json.**` 通配覆盖了新增 `weather.json.apihz`）、当前/体感/AQI/5天预报齐全。
- **3.5.2**：① 默认天气源 ACCU → WEATHERAPI（`SettingsManager.weatherSource` 的 `getString("weather_source", …)` 兜底值）。ACCU 的内置 Key 已过期且暂无新 Key，新装用户首次进 App 会直接落到死源、拉不到天气。全仓库只有 `WeatherSource` 枚举一处 `"accu"` 字面量，无第二处硬编码默认值；`getInstance("weatherapi")` 命中 WEATHERAPI（`contains` 匹配）。仅影响**新装**（老用户 prefs 里已存的源不变）。② `CmaWeatherService.mCalls` 由 `ArrayList` 改 `CopyOnWriteArrayList`：三处 `add()` 都在 `runOnIO` 的 lambda 体内（IO 线程），而 `cancel()` 在调用线程 `for` 迭代同一个裸 list → `ConcurrentModificationException`（刷新中途退出/切换地区时触发）。每次请求只 add 1~3 个，COW 复制开销可忽略。审查 APIHZ 时顺带查出，APIHZ 本身无缺陷（`Base.cityId` 用的 `location.getCityId()`，空值已兜底，回调线程由 `WeatherHelper.java:62` 的 `delayRunOnUI` 统一切回主线程）。`assemblePubDebug` 通过。
- **3.4.15**：一次性清理 3.4.14 之前残留的孤儿 weather 行（按接口地名 Tianjin/Wangdingdi/Shuchenghsien 或旧源 cityId 存的、不属于任何已存地区的行）。`WeatherDatabaseDao` 加 `@Query("DELETE FROM weather WHERE cityId NOT IN (SELECT cityId FROM location)") deleteOrphanWeather()`、`DatabaseHelper.cleanupOrphanWeather()`、`GeometricWeather.onCreate` 主进程内 `cleanupOrphanWeatherOnce()`（`app_init` 偏好 `orphan_weather_cleaned` 一次性守卫 + `AsyncHelper.runOnIO`）。只清 weather 表（daily/hourly/alert/history 本就按 `location.getCityId()` 存，无孤儿）。真机验证：weather 行 36(6 个 cityId)→1(仅 54517_tj)、0 崩溃。注意：验证时 DELETE 落在 `-wal` 里，必须连 `-wal`/`-shm` 一起 pull 才能看到合并后的真实状态（只 cat 主 db 文件会看到旧数据，是假象）。
- **3.4.14**：修复 3.4.13 遗留的 WeatherAPI 缓存键错配。`WeatherApiResultConverter.convert` 里 `Base.cityId` 改用 `location.getCityId()`（原来用 `result.location.name`=Tianjin/Wangdingdi… 随接口地名漂移）。根因：`WeatherEntityGenerator` 按 `weather.getBase().getCityId()` 存 weather 行，而 `readWeather`/`deleteWeather` 按 `location.getCityId()` 读删 → 键对不上：① WeatherAPI 天气**永远读不到缓存**（冷启动每次重新拉）；② weather 表按接口地名无界累积。改后与其余所有转换器一致（CMA/APIHZ 本就用 `location.getCityId()`）。真机验证：刷新后 weather 行落到 `54517_tj`，连刷两次稳定 1 行（不再累积）、预警仍 0、0 崩溃。旧的 Tianjin/Wangdingdi 孤儿行是修复前残留，不再增长、不被读取（未做迁移清理）。
- **3.4.13**：修复「定位南开区但预警显示延庆区」。双根因：① **WeatherAPI 接口数据张冠李戴**——对天津坐标返回了北京延庆区预警（`areas=北京市`、`headline=延庆区气象台…`，接口自己 `location` 都识别成 Tianjin 却附了北京预警）。在 `WeatherApiResultConverter.convertAlertList` 加按行政区过滤：`areas` 非空、且 `areas+headline` 既不含本地 province 也不含 city 词根（去掉 省/市/自治区/特别行政区/地区/盟 后缀）时丢弃该预警（只在 isChina 且有地名可比时才判，避免误删）。② **`DatabaseHelper.deleteWeather` 漏删 alert 表**（只删了 weather/daily/hourly/minutely/history）→ 每次刷新预警累积（实测同一条延庆预警攒了 26 份）。补 `selectAlertListByCityIdAndSource`+`deleteAlertList`，对所有源生效。真机验证（MI 9，weatherapi 源）：南开区刷新后 `(54517_tj,weatherapi)` 预警 26→0、天气页无预警横幅、头部显示「南开区」27°、0 崩溃。**遗留 BUG（未改）**：`WeatherApiResultConverter` 把 `Base.cityId` 设成 `result.location.name`（Tianjin/Wangdingdi…随接口地名变）而非 `location.getCityId()`，致 weather 表按接口地名累积、与 `deleteWeather` 按 cityId 删除不匹配（weather 行无界增长）；alert 走 `location.getCityId()` 不受影响，故本次预警修复完整。
- **3.4.11 / 3.4.12**：设置里新增「天气源可用性」页（`settings/compose/WeatherSourceStatusScreen.kt`），横向滚动表格。**3.4.12 改为实测版**：页面带「实测刷新」按钮，点了才**复用 App 的 `WeatherServiceSet` 真实联网**——每个源走 `requestLocation→requestWeather`（与 App 同一流程；ACCU 用 `location.getCityId()` 当 Key 必须先 resolve，坐标源 requestLocation 直接 echo），从返回的 `Weather` 模型抽取 天/时/体感(realFeel)/UV(isValidIndex)/气压/湿度/降水/AQI(isValid)/预警条数/日出，逐源带 25s 超时 + generation 守卫防重入。`WeatherServiceSet` 经 Hilt `@EntryPoint`(`EntryPointAccessors.fromApplication`) 取得（无 hilt-navigation-compose 依赖）。测试点：北京坐标，MF 用巴黎。入口 设置→数据提供商→天气源可用性（`ServiceProviderSettingsScreen` 加 `clickablePreferenceItem`；`SettingsScreenRouter`+`SettingsActivity` NavHost 注册；strings 加 `settings_title/summary_weather_source_status`）。注意：每次刷新真实消耗各源配额；预警列是「当前生效条数」（多数地区 0 条不代表不支持）。真机实测全部联网成功（UV/AQI/日出放宽为 current/daily/hourly 任一）、CMA 抓到逐小时 56 点、国际源偶因 25s 超时显示不可用。`assemblePubDebug` 通过、真机 0 崩溃
- **3.4.10**：定位精确到区县。定位 SDK（百度/高德，`setIsNeedAddress`/`setNeedAddress` 本就开着）回传的 省/市/区 之前被 `LocationService.Result` 丢弃（只留经纬度）→ 当前定位只到市级（「天津市」）。修复：`Result` 加 `country/province/city/district`（`@JvmOverloads constructor` 兼容其余服务的旧 2 参构造）；`BaiduLocationService`/`AMapLocationService` 回填这四项；`LocationHelper` 把 `Location.copy(lat,lon,tz)` 换成实例 `location.copy(null,lat,lon,tz,country,province,city,district)`（copy 对 null 入参保留原值，所以只覆盖非空地址）。`getCityName` 本就优先显示 district → 头部显示「南开区」。真机验证（MI 9，百度定位）：CURRENT_POSITION province/city=天津市、**district=南开区**、0 崩溃。注意：① 原生定位 `AndroidLocationService` 的回调在 `Looper.getMainLooper()` 主线程，不能阻塞调 `Geocoder`，故原生只给坐标→市级，区县级需用 百度/高德定位；② 坐标型源(WeatherAPI/Open-Meteo/OWM)天气本就按 GPS 精确，名称受定位服务影响；③ APIHZ/CMA 无区县级天气数据（apihz `place=南开`→400，回退到市/最近站），区只影响显示名

## 已知问题 / 约束

- **转换器是高发崩溃区**：`weather/converters/*ResultConverter.java` —— provider 字段常为 null，`Weather` 模型 `@NonNull` 断言会抛；新增/改源必须逐字段兜底。
- **数据库 schema 锁定 v63，禁止修改**（`db/GeometricWeatherDatabase` line 33）。
- **Room 禁止主线程访问**，一律 `AsyncHelper.runOnIO`（历史多次崩溃来源）。
- **CMA**：weather.cma.cn WAF 拦截默认 okhttp UA（已加浏览器 UA，仅限 CMA）；其无坐标→站点接口，靠全国站点图找最近站点。
- **APIHZ（中国天气网）**：主走 `tqyb.php`（`sheng`+`place`）按城市取数，`tqybip.php` 仅作 IP 兜底。接口名怪癖：`place` 去尾「区」、直辖市 `sheng` 去尾「市」；区级覆盖不全，按 区→市→IP 依次兜底（见 3.4.9）。海外地名查不到 → 退 IP（国外 IP 接口默认返回北京）。
- **ACCU 的 Key 已过期**（2026-06-28 实测：geoposition/currentconditions 均返回 403 `"This API Key has expired"`）→ AccuWeather 源当前完全不可用，需在 `build.gradle:30-32` 换新 Key（三个：`EMBEDDED_ACCU_WEATHER_KEY`/`EMBEDDED_ACCU_CURRENT_KEY`/`EMBEDDED_ACCU_AQI_KEY`，base64 编码；也可在 `local.properties` 覆盖，优先级更高）。本是功能最全的源（15天/24时/UV/AQI/分钟级/预警）。**3.5.2 起默认源已改为 WEATHERAPI**，避免新装用户落到死源。
- **CAIYUN 为试用 Token**（`HYMo2dWkbB73N7rt`）：daily 上限仅 **3 天**、无 minutely 分钟级降水块；realtime/daily 字段齐全（体感/UV(life_index)/AQI/能见度）。如需 15 天+分钟级须换正式 Token。
- **各源数据丰富度实测**（2026-06-28，北京/MF用巴黎）：OPEN_METEO 16天·384时·全字段(无AQI/预警)；WEATHERAPI 14天·336时·含AQI+预警(最全可用源)；CAIYUN 3天·48时·含AQI/UV；APIHZ 7天·56时(逐3h)·无UV/AQI/能见度；CMA 7天·逐时(网页抓取)·无UV/AQI；OWM 5天/40点(3h步长)·有AQI·2.5无UV；MF(法)15天·98时。
- minSdk 实际为 21（目标 24，暂不升级）。

## 待完成（TODO）

- [x] 升级 compileSdk 34 → 35
- [ ] 升级 minSdk 21 → 24（暂不升级，仅在关键库必须要求时升级）
- [x] GreenDAO → Room
- [x] 升级 Gradle 8.x
- [x] 升级 AGP 8.x
- [x] 升级 Kotlin 1.9+
- [x] RxJava → Coroutines
- [ ] Java → Kotlin 逐步迁移

## 重要文件引用

- `db/GeometricWeatherDatabase` line 33 —— `version = 63`（schema 锁定）
- `weather/WeatherServiceSet.java` line 44–59 —— `WeatherSource` → service 映射（7 个源）
- `weather/apis/CmaApi.java` line 27 起 —— 三个接口的浏览器 `@Headers("User-Agent: …")`（绕 WAF 403）
- `weather/converters/*ResultConverter.java` —— null 防御集中地（崩溃高发区）
- `common/utils/helpers/AsyncHelper.kt` —— IO/UI 线程封装；Emitter 回调 post 回主线程
- `location/services/LocationService.kt` —— 定位服务（按 flavor 分实现），IP 兜底 `BaiduIPLocationService`
