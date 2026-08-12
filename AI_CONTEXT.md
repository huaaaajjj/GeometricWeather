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

- 当前发布版本：**3.5.3**（versionCode 30503，正在发布）。上一发布版 3.5.2（30502）。主分支 `master`（基于 v3.3.6 重建线）。
- 8 个天气源：WEATHERAPI（默认）、OPEN_METEO、CAIYUN、APIHZ（中国天气网）、CMA（中国气象局）、MF（仅法国）、OWM 可用；**ACCU 的内置 Key 已过期，当前不可用**（见「已知问题」）。
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
- **OpenWeather 三连修 + 空 daily 治本**（真机 MI 9/Android 14 定位）：① **可用性页误判**——`WeatherSourceStatusScreen.kt:58` 的 `CHECK_TIMEOUT_MS` 25s **短于**被测对象自身预算（OkHttp connect/read 30s `TLSCompactHelper:93-94`、各服务 `latch.await(30s)`），国内到 `api.openweathermap.org` 的 **TLS 握手**偶发卡死（栈：`ConscryptEngineSocket.doHandshake` ← `RealConnection.connectTls`，三路并行冷启动各自新建连接，随机某路中招）时 OWM **必然**显示"不可用" → 改 35s。加临时 `Log.e("OWMPROBE")` 探针实测三轮确认：Key 有效（直连三端点均 200）、`convert -> wrapper=ok` 每轮成功，故**取数链路本身没问题**，纯粹是仪器预算比被测对象短（探针已回滚）。② **`data/2.5/find` 类型错配**——返回 `{"list":[...]}` 对象却声明成 `Call<List<OwmLocationResult>>`，Gson 抛 `JsonSyntaxException` 被 `OwmWeatherService:156` 的 catch 吞掉 → OWM 源下**搜索城市永远空列表**。改用 `geo/1.0/direct`|`reverse`（返回扁平数组，字段 `{name,lat,lon,country}` 与现有 DTO **完全一致**，故只改 URL + 加 `limit` 参数，**无需包装类**，比加 `OwmFindResult` 更省）；顺带删无调用者的 `getWeatherLocation`。③ **空 daily 列表越界（治本）**——切到 OWM 后主界面与城市管理页**必崩**（`DetailsAdapter:103`、`LocationModel:45` 的 `IndexOutOfBoundsException`）。全仓库扫出 **76 处** `getDailyForecast()/getHourlyForecast().get(0)` 无保护，**不逐处打补丁**，改在两个唯一汇聚点拦：`WeatherHelper.requestWeatherSuccess`（空 daily 视为失败 → 保留旧缓存，而非展示空列表）+ `DatabaseHelper.readWeather`（空 daily 行返回 null，挡住守卫上线前已落库的脏数据）。④ 顺带修 `SelectProviderActivity` 缺注册 `WeatherSourceStatus` 路由——`ServiceProviderSettingsScreen` 被 `SettingsActivity`/`SelectProviderActivity` **两个** Activity 复用，3.4.11 加页面时只注册了前者，从主界面滑动进的那条路径点「天气源可用性」必崩（`IllegalArgumentException: destination ...source_status cannot be found`，崩溃日志里 `startDestination=...providers` 即指认了是哪张图）。真机验证：OWM 实测 **6天/40点 可用**、切 OWM 后主界面+城市管理 0 崩溃、搜索 Shanghai 返回 5 条带正确 `CN`/`IN` 国家码结果。**注**：TLS 握手超时本身是国内网络环境问题、非代码缺陷，改超时只是让仪器别误报。
- 测试：新增 `app/src/test/java/weather/converters/ApihzResultConverterTest.java`（Robolectric，4 个用例）+ 2 份固件 `src/test/resources/apihz/{tqyb_full,tqyb_sparse}.json`。锁住转换器两类历史缺陷：① `Base.cityId` 必须来自 `location.getCityId()` 而非接口地名（3.4.13/3.4.14 的缓存键漂移）；② 稀疏但合法的 200 响应不得触发 `@NonNull` 断言（外层 catch 会把崩溃悄悄变成"无数据"，正是这类回归长期隐身的原因）。另覆盖满响应 7 天 / 12 逐时 / 风速 m/s→km/h(×3.6) / 高温橙色预警 1 条 / 非 200 码与 null 入参返回 null。踩到两个坑：① 真实模型签名是 `Temperature.getTemperature()` 返回原始 `int`（不可 `.intValue()`）、`Daily.sun()`（非 `getSun()`）、`Current.getRelativeHumidity()` 返回 `Float`；② Robolectric 4.12.2 不带 SDK 35 沙箱，targetSdk 35 直接抛 `DefaultSdkPicker` IllegalArgumentException → 类上加 `@Config(sdk = 34)`。`:app:testPubDebugUnitTest` 4/4 通过，`ApihzResultConverter` 本体未改动。（Windows 下用 python 跑 gradle 须 `decode('utf-8')` + `sys.stdout.reconfigure(encoding='utf-8')`，否则中文 javac 报错撞 GBK 解码异常看不到真实错误行）
- **Météo France 整源修复（v1 DTO → GeoJSON v2）**：MF 早已把 `webservice.meteofrance.com` 迁到 GeoJSON（字段全挪到 `properties` 下并改名），仓库里的 DTO 还是 v1 扁平结构 → Gson 解出**全 null 对象** → 转换器踩 `@NonNull` 抛异常被外层 catch 吞成"无数据"，**MF 整个源不可用**（法国用户全灭）。① DTO：删 `MfForecastResult`/`MfHistoryResult`（v1 遗物），`MfCurrentResult` 改 `properties.gridded`、`MfRainResult` 改 `properties.forecast`（`rain_intensity`：1=无雨），统一走已有的 `MfForecastV2Result`。② `MfWeatherApi`：清掉 5 个指向同一 `/v2/forecast` 的死方法（`getForecastV2`/`getForecastInstants`/`getForecastInseepp`/`getWeatherLocation`）；地名搜索改 `places`（**不在 `/v2` 下**，`/v2/forecast?q=` 返的是单个 forecast feature 不是列表）；预警 `/v3/warnings` → **`/v3/warning/full`**（前者实测 404 `"You haven't access to this url"`）。③ `ApiModule`：MF 专用 Gson——MF 一律 UTC ISO-8601（`2026-08-10T09:00:00.000Z`），共享 Gson 的 `yyyy-MM-dd'T'HH:mm:ss` 会**静默丢掉 `.000Z` 并按设备本地时区读**，所有时间整体偏移设备 UTC offset（`'X'` 需 API24，minSdk 21 用不了，故手写两 pattern + `setTimeZone(UTC)`）。④ `MfWeatherService`：`latch` 6→5，预警**串在 forecast 之后**——`domain` 要的是部门号（`75`），只有 forecast 响应带 `french_department`，原来传 `location.getProvince()`（省名/大区名）→ 预警必然拿不到；AtmoAura 那串 13 个 `equals` 收进 `isAtmoAuraDepartment()`，department 兜底仅在 province 形如 `\d{2,3}[AB]?` 时采用。⑤ 转换器：空 daily 直接返回 `wrapper(null)`（配合全局"空 daily 视为失败"约定）、跳过 MF 尾部无温度/无时间的填充条目、`wind_direction=-1` 记为 variable、风速 m/s→km/h(×3.6)。⑥ `WeatherService.WeatherResultWrapper` 加 `getResult()`（原来只有包私有字段，测试读不到）。**实测**（curl 直连 7 个端点）：`v2/forecast`（巴黎 15 天/95 逐时/64 概率）、`v2/observation`、`v3/nowcast/rain`、`v2/ephemeris`、`v3/warning/full`、`places` 全 200，`v3/warnings` 404 确认。**遗留未改**：`daily_forecast[].time` 是 **UTC 午夜**（就是个日期标签），昼夜切分按 `dayStart+6h..+18h` 算即 UTC 06–18，对巴黎夏令时实际是本地 08–20；与 v1 行为完全一致，属上游语义，本次不动。**真机验证**（MI 9/Android 14，设置→数据提供商→天气源可用性→实测刷新，MF 用巴黎坐标走 App 自己的 `WeatherServiceSet`）：Météo France 由「整源不可用」变为 **可用 15 天 / 76 逐时 / UV ✓ / 日出 ✓ / 预警 1 条**、0 崩溃。两处数字正好对上线上载荷，说明改动按预期生效：① 76 = 线上 95 条逐时里剔掉 19 条无 `T` 的填充条目；② 预警 1 条 = `domain=75` 的黄色 Canicule（`color_id=2>1`），旧代码传 `location.getProvince()`（"Île-de-France"）这一列必然是 0。体感/气压/湿度/降水 显示 ✗ 属实——`v2/observation` 只返 T/风/天气文字（实测响应仅 336 字节），逐时里的 `relative_humidity`/`P_sea` 目前没回填进 `Current`（转换器里原有 TODO，本次未做）。
- 测试：新增 `app/src/test/java/weather/converters/MfResultConverterTest.java`（Robolectric，6 个用例）+ 5 份**真实抓取**固件 `src/test/resources/mf/{forecast,observation,rain,ephemeris,warning}.json`（巴黎，仅截短长度）。锁住这次的根因与两处易回归点：v2 载荷必须完整转换（4 天/26 逐时/当前 29℃/UV 6/日照 14~15h）、时间戳必须按 UTC 解析（回退到共享 Gson 立刻挂）、`rain_intensity` 分钟级步长 5 分钟、`phenomenon_max_color_id>1` 才成预警（绿色丢弃）、可选调用（observation/rain/warnings）全 null 仍出 Weather、空 daily 必须判失败。踩到的坑：`Base` 的第 3/4 参是 **publish**（provider 的 `update_time`）、第 5/6 参才是 **update**（设备刷新时刻，`new Date()`），断言 provider 时间要查 `getPublishDate()`——错查 `getUpdateDate()` 会拿到当前时间（与 Accu 等其余转换器同一约定）。`:app:testPubDebugUnitTest` MF 6/6 + APIHZ 4/4 通过，主源码零改动地跑通编译。**注**：全量 `./gradlew test` 另有 4 个**既存**失败（`basic.option._utils.UtilsTest`/`UnitUtilsTest`/`CardDisplayTest`/`DailyTrendDisplayTest`）——PowerMock 在 JDK 17 下 `InaccessibleObjectException: module java.base does not "opens java.lang"`，与本次改动无关（**已在后续「干掉 PowerMock」一条里修掉，现全绿**）。
- **MF 当前天气补全（湿度/气压/体感/降水）**：`v2/observation` 只返 T/风/天气文字（实测响应仅 336 字节），所以可用性页里 MF 的 体感/气压/湿度/降水 四列全是 ✗。这些字段其实都在 `v2/forecast` 的逐时里（`relative_humidity`/`P_sea`/`T_windchill`/`rain_1h`+`snow_1h`），改法：`MfResultConverter` 加 `getNearestHour()`，按**观测时刻**（`gridded.time`，缺则 now）找最近一条逐时，回填 `Current` 的 `relativeHumidity`/`pressure`/`realFeelTemperature`+`windChill`/`precipitation`（复用已有的 `getHourlyPrecipitation`）/`cloudCover`；温度与天气文字仍以观测优先，**观测缺失时退到最近逐时**（原来会显示 0°）。顺带把逐时的 `T_windchill` 同时写进 `realFeelTemperature`——`HourlyWeatherDialog:72` 只读 realFeel，否则「当前」有体感而「逐时详情」没有，同一个源字段两处不一致。落实了转换器里原有的 `TODO: Fill in with observation data instead`。测试加 2 例（共 8 例）：① 固件观测在 11:55Z → 最近步是 12:00Z（`T_windchill` 32.2→32、湿度 35、气压 1017.9），能区分出"取 hour[0]"（09:00Z→28）和"取 11:00Z"（→30）两种错法；② 无观测时温度不得为 0、文字不得为空。真机验证（MI 9，天气源可用性→实测刷新）：**Météo France 可用 15 天/76 逐时，体感✓ UV✓ 气压✓ 湿度✓ 降水✓ 日出✓ 预警 1**，四个 ✗ 全部转 ✓、0 崩溃。AQI 仍 ✗ 属实（只有 Auvergne-Rhône-Alpes 的 AtmoAura 有值，巴黎不在范围内）。同轮 OpenWeather 显示不可用，是已知的国内 TLS 握手偶发超时（上一轮同一台机子是可用），与本次改动无关。
- **3.5.3**：发布版（`assemblePubRelease` 已签名 + R8 minify/shrinkResources）。汇总本次三件事:① **Météo France 整源修复** —— MF 早已迁到 GeoJSON v2 而 DTO 还是 v1 扁平结构，Gson 解出全 null → 转换器抛异常被外层 catch 吞成「无数据」，法国用户整源不可用;修 DTO/接口路径(`places`、`/v3/warning/full`)、MF 专用 UTC Gson、预警改用 forecast 带回的 `french_department` 当 domain。② **MF 当前天气补全** —— 按观测时刻取最近逐时回填 湿度/气压/体感/降水/云量，观测缺失时温度退到最近逐时(原来显示 0°)。③ **测试基础设施** —— 删掉 PowerMock(JDK 17 下必挂)，四个用例迁到 Robolectric + Mockito，`./gradlew test` 六变体全绿、29 例 0 失败。真机验证见各自条目;发版前另做 release 包冒烟(见下)。**release 包冒烟（MI 9/Android 14，卸 debug 装签名 release）**：冷启动 0 崩溃、`logcat -b crash` 空、定位到**舒城县**（区县级）、联网拉取正常——先后经 WEATHERAPI（英文 "Light rain shower"）与 APIHZ（中文「中雨」+ 舒城县本地预警）两个源各拉一次，证明 R8 没误删 Gson DTO；再进「天气源可用性」实测，**Météo France 在 R8 下同样是 可用 15 天/76 逐时 体感✓ UV✓ 气压✓**，与 debug 一致（`proguard-rules.pro:20` 的 `weather.json.**` 通配覆盖了改动后的 `weather.json.mf`）。同轮 OWM 不可用（已知 TLS 抖动）、ACCU 不可用（Key 过期）。**注**：本机重装后天气源自动回到「中国天气网」而非 3.5.2 的新装默认 WEATHERAPI，是 MIUI 对同包名 `allowBackup` 数据的自动恢复，不是默认值失效。
- **测试基础设施：干掉 PowerMock,`./gradlew test` 恢复全绿**。`basic.option._utils.UtilsTest`/`basic.option.unit.UnitUtilsTest`/`basic.option.appearance.CardDisplayTest`/`DailyTrendDisplayTest` 四个用例长期挂在 `InaccessibleObjectException: Unable to make protected native java.lang.Object.clone() accessible: module java.base does not "opens java.lang"` —— PowerMock 2.0.9 靠 javassist 改写 + 自定义 ClassLoader,撞 JDK 17 的模块封装,不是我们的代码问题。**没走 `--add-opens` 那条路**(能糊住一个 open 还会冒下一个,而且把一个已停更的依赖继续养着,JDK 再升一次又炸),改用仓库里**已经装着**的 Robolectric + Mockito,然后**删掉 4 个 PowerMock 依赖**(`powermock-api-mockito2`/`module-junit4`/`module-junit4-rule`/`classloading-xstream`)。逐个改法:① `CardDisplayTest`/`DailyTrendDisplayTest` 原来 `mockStatic(TextUtils.class)` 把 `isEmpty` 恒定打桩成 false,换 `@RunWith(RobolectricTestRunner.class) @Config(sdk = 34)` 后 **TextUtils 是真的**,静态打桩整段不需要了,`Context` 用 `Mockito.mock` 即可(`CardDisplay.getName` 只调 `context.getString(nameId)`);顺带各加一例空串→空列表,把原先被打桩抹掉的 `TextUtils.isEmpty` 分支真正测上。② `UtilsTest` 只 mock `android.content.res.Resources`(普通类,非 static/final),`@RunWith(MockitoJUnitRunner.class)` 就够,不碰 Android 运行时;加一例"值不在数组里→返回 null"(调用方靠它退回原始 id)。③ `UnitUtilsTest` 更离谱:它 `@RunWith(PowerMockRunner.class)` 但**一个 mock 都没用**,而且断言的是**它自己文件里定义的** `formatFloat`/`formatInt` 局部副本 —— 等于在测 JDK 的 `String.format`,对 App 零覆盖。改成指向真的 `Utils.INSTANCE.formatFloat/formatInt`,并补一例整数值不带小数(`14f`→`"14"`)——那正是真实现有、局部副本没有的分支。结果:`./gradlew test` 六个变体(pub/gplay/fdroid × debug/release)全部 BUILD SUCCESSFUL,`testPubDebugUnitTest` **29 例 0 失败**(原 25 例 6 失败),用例数 25→29 且全部真在测 App 代码。
- **3.4.15**：一次性清理 3.4.14 之前残留的孤儿 weather 行（按接口地名 Tianjin/Wangdingdi/Shuchenghsien 或旧源 cityId 存的、不属于任何已存地区的行）。`WeatherDatabaseDao` 加 `@Query("DELETE FROM weather WHERE cityId NOT IN (SELECT cityId FROM location)") deleteOrphanWeather()`、`DatabaseHelper.cleanupOrphanWeather()`、`GeometricWeather.onCreate` 主进程内 `cleanupOrphanWeatherOnce()`（`app_init` 偏好 `orphan_weather_cleaned` 一次性守卫 + `AsyncHelper.runOnIO`）。只清 weather 表（daily/hourly/alert/history 本就按 `location.getCityId()` 存，无孤儿）。真机验证：weather 行 36(6 个 cityId)→1(仅 54517_tj)、0 崩溃。注意：验证时 DELETE 落在 `-wal` 里，必须连 `-wal`/`-shm` 一起 pull 才能看到合并后的真实状态（只 cat 主 db 文件会看到旧数据，是假象）。
- **3.4.14**：修复 3.4.13 遗留的 WeatherAPI 缓存键错配。`WeatherApiResultConverter.convert` 里 `Base.cityId` 改用 `location.getCityId()`（原来用 `result.location.name`=Tianjin/Wangdingdi… 随接口地名漂移）。根因：`WeatherEntityGenerator` 按 `weather.getBase().getCityId()` 存 weather 行，而 `readWeather`/`deleteWeather` 按 `location.getCityId()` 读删 → 键对不上：① WeatherAPI 天气**永远读不到缓存**（冷启动每次重新拉）；② weather 表按接口地名无界累积。改后与其余所有转换器一致（CMA/APIHZ 本就用 `location.getCityId()`）。真机验证：刷新后 weather 行落到 `54517_tj`，连刷两次稳定 1 行（不再累积）、预警仍 0、0 崩溃。旧的 Tianjin/Wangdingdi 孤儿行是修复前残留，不再增长、不被读取（未做迁移清理）。
- **3.4.13**：修复「定位南开区但预警显示延庆区」。双根因：① **WeatherAPI 接口数据张冠李戴**——对天津坐标返回了北京延庆区预警（`areas=北京市`、`headline=延庆区气象台…`，接口自己 `location` 都识别成 Tianjin 却附了北京预警）。在 `WeatherApiResultConverter.convertAlertList` 加按行政区过滤：`areas` 非空、且 `areas+headline` 既不含本地 province 也不含 city 词根（去掉 省/市/自治区/特别行政区/地区/盟 后缀）时丢弃该预警（只在 isChina 且有地名可比时才判，避免误删）。② **`DatabaseHelper.deleteWeather` 漏删 alert 表**（只删了 weather/daily/hourly/minutely/history）→ 每次刷新预警累积（实测同一条延庆预警攒了 26 份）。补 `selectAlertListByCityIdAndSource`+`deleteAlertList`，对所有源生效。真机验证（MI 9，weatherapi 源）：南开区刷新后 `(54517_tj,weatherapi)` 预警 26→0、天气页无预警横幅、头部显示「南开区」27°、0 崩溃。**遗留 BUG（未改）**：`WeatherApiResultConverter` 把 `Base.cityId` 设成 `result.location.name`（Tianjin/Wangdingdi…随接口地名变）而非 `location.getCityId()`，致 weather 表按接口地名累积、与 `deleteWeather` 按 cityId 删除不匹配（weather 行无界增长）；alert 走 `location.getCityId()` 不受影响，故本次预警修复完整。
- **3.4.11 / 3.4.12**：设置里新增「天气源可用性」页（`settings/compose/WeatherSourceStatusScreen.kt`），横向滚动表格。**3.4.12 改为实测版**：页面带「实测刷新」按钮，点了才**复用 App 的 `WeatherServiceSet` 真实联网**——每个源走 `requestLocation→requestWeather`（与 App 同一流程；ACCU 用 `location.getCityId()` 当 Key 必须先 resolve，坐标源 requestLocation 直接 echo），从返回的 `Weather` 模型抽取 天/时/体感(realFeel)/UV(isValidIndex)/气压/湿度/降水/AQI(isValid)/预警条数/日出，逐源带 25s 超时 + generation 守卫防重入。`WeatherServiceSet` 经 Hilt `@EntryPoint`(`EntryPointAccessors.fromApplication`) 取得（无 hilt-navigation-compose 依赖）。测试点：北京坐标，MF 用巴黎。入口 设置→数据提供商→天气源可用性（`ServiceProviderSettingsScreen` 加 `clickablePreferenceItem`；`SettingsScreenRouter`+`SettingsActivity` NavHost 注册；strings 加 `settings_title/summary_weather_source_status`）。注意：每次刷新真实消耗各源配额；预警列是「当前生效条数」（多数地区 0 条不代表不支持）。真机实测全部联网成功（UV/AQI/日出放宽为 current/daily/hourly 任一）、CMA 抓到逐小时 56 点、国际源偶因 25s 超时显示不可用。`assemblePubDebug` 通过、真机 0 崩溃
- **3.4.10**：定位精确到区县。定位 SDK（百度/高德，`setIsNeedAddress`/`setNeedAddress` 本就开着）回传的 省/市/区 之前被 `LocationService.Result` 丢弃（只留经纬度）→ 当前定位只到市级（「天津市」）。修复：`Result` 加 `country/province/city/district`（`@JvmOverloads constructor` 兼容其余服务的旧 2 参构造）；`BaiduLocationService`/`AMapLocationService` 回填这四项；`LocationHelper` 把 `Location.copy(lat,lon,tz)` 换成实例 `location.copy(null,lat,lon,tz,country,province,city,district)`（copy 对 null 入参保留原值，所以只覆盖非空地址）。`getCityName` 本就优先显示 district → 头部显示「南开区」。真机验证（MI 9，百度定位）：CURRENT_POSITION province/city=天津市、**district=南开区**、0 崩溃。注意：① 原生定位 `AndroidLocationService` 的回调在 `Looper.getMainLooper()` 主线程，不能阻塞调 `Geocoder`，故原生只给坐标→市级，区县级需用 百度/高德定位；② 坐标型源(WeatherAPI/Open-Meteo/OWM)天气本就按 GPS 精确，名称受定位服务影响；③ APIHZ/CMA 无区县级天气数据（apihz `place=南开`→400，回退到市/最近站），区只影响显示名
- **仓库瘦身：清掉冗余文件（无功能改动）**。① **旧构建产物出库 68.5 MB** —— `release/3.102/{pub,gplay,fdroid}/release/*.apk` 与 `app/{pub,gplay,fdroid}/release/*.apk` md5 完全相同（同一批上游 3.102 的包存了两份）、外加 `app/release/app.aab` + `output.json`/`output-metadata.json`；`.gitignore` 里本就有 `*.apk` 但对**已跟踪**文件无效，故一直躺在库里。已 `git rm` 并把 `/release/`、`app/*/release/`、`*.aab`、`output*.json` 补进 `.gitignore`。**注**：历史里的 blob 仍占着 `.git`（161 MB），除非重写历史（不做）。② **死代码 5 个文件** —— `common/di/RxModule.java`（RxJava 移除后剩的空 Hilt module，零 `@Provides`）、`common/retrofit/interceptors/CaiYunSignatureInterceptor.java`（从未实例化；`RetrofitModule.java:26` 注释已写明 v2.6 接口带签名头会 400）、`common/utils/ObjectUtils.java`（`deepCopy` 无调用方）、`weather/json/owm/{OwmOneCallResult,OwmOneCallHistoryResult}.java`（24 KB DTO，`OwmApi` 只用 current/forecast/airPollution/location 四个端点）。③ **未引用布局 4 个** —— `activity_allergen.xml`（`AllergenActivity.kt` 已 Compose 化）、`container_alert_display_view.xml`、`spinner_text.xml`、`menu/activity_settings.xml`（设置页已 Compose 化）；判据是按 ViewBinding 生成类名 + `R.layout`/`R.menu` 双向核对，非"文本零命中"。④ 开发残留 `error_log.txt`（UTF-16 乱码的旧编译日志，且已在 `.gitignore` 却仍被跟踪）、`opencode.json`（指向 `127.0.0.1:4000` 的第三方 CLI 个人配置）。⑤ 未跟踪垃圾就地删：13 个 `hs_err_pid*.log`(2.9 MB)、`GeometricWeather-v3.2.1_pub.apk`(16 MB)、空 `logs/`、`app/build/`(2.1 GB)；两份接口 PDF（中国气象局 / apihz）**未删**，移到 `D:\Documents\geoweather-refs\`（未跟踪、删了不可恢复的参考资料）。**刻意没动的**：`res/drawable` 440 个 + `animator` 36 个静态零引用**是假阳性** —— `ResourceHelper.java:137` 拼 `notif_temp_*`、`DefaultResourceProvider`/`IconPackResourcesProvider` 走 `getResId(ctx, name, "drawable"/"animator")` 按名字动态解析，删了编译照过、运行时才崩；`RetrofitModule`/`UtilsModule` 同样"无文本引用"但是注解处理器消费的活模块；`app/libs/BaiduLBS_Android.jar` + 8 个 `.so`（`LocationHelper.java:51` 真在 new `BaiduLocationService`）；`work/`（249 MB 上游 PSD 设计源，全部被跟踪，是库里最大一块可移出内容，但删它等于丢上游原稿，留待决定）；`fastlane/metadata/**/changelogs/` 虽只到 `30013`（当前 30503）但 F-Droid 需要该目录结构。顺带修 `CLAUDE.md` 两处陈旧描述（`common/rxjava/` 空包和根目录 `index.html` 都已不存在）。**验证**：`./gradlew test` 六变体全绿、**174 例 0 失败**；`assemblePubDebug` BUILD SUCCESSFUL 并产出 `GeometricWeather-v3.5.3_pub.apk`。

- **补齐 4 个转换器测试，顺带查出并修掉 8 个真实缺陷**。给 OPEN_METEO / WEATHERAPI / OWM / CAIYUN 各写一套 Robolectric 测试（24 例）+ 真实抓取固件 `src/test/resources/{openmeteo,weatherapi,owm,caiyun}/`（北京/天津南开点，按各服务真实请求的参数抓，仅截短长度）。写测试过程中查出的缺陷（**都已修，测试断言改成修后的正确行为**）：① **WeatherAPI 日出日落落在 1970-01-01** —— astro 只给 `"05:21 AM"` 裸时钟、日期在上一层的 `forecastday.date`，原来只解析时钟 → `Weather.isDaylight()` 拿 now 跟 1970 的日落比，**恒返回 false**；而它是 3.5.2 起的默认源，主界面 `MainThemeColorProvider`、全部 widget、通知、动态壁纸都读这个值 → 全天候夜间配色。改为把时钟锚到当天日期（按 location 的时区），月出月落同理。② **CaiYun 同一类问题** —— `"05:20"` 裸时钟 → 1970，改为锚到 astro 自带的 `date` + payload 的 timezone。③ **CaiYun 湿度/气压单位** —— 接口给的是 0..1 的湿度分数和**帕斯卡**气压（0.76 / 100430.02），模型要百分比和 hPa → 显示成「湿度 0.76%、气压 100430」，改为 ×100 / ÷100。④ **OWM 风速** —— metric 单位下接口返 m/s，转换器当 km/h 直传 → 风速少报 3.6 倍（连带风力等级偏低），current/daily/hourly 三处都补 ×3.6。⑤ **OWM 能见度** —— 接口返米（10000），模型按公里 → 显示「10000 km」，改 ÷1000。⑥ **OWM 降水概率** —— `pop` 是 0..1，模型存百分比 → 100% 的雨显示成 1%，daily/hourly 都 ×100。⑦ **OWM `rain.3h` 恒 0** —— JSON 键是 `"3h"`（Java 标识符写不出），DTO 只声明了 `double _3h` **没加 `@SerializedName("3h")`** → Gson 从来没填过，逐时降水量永远 0 mm。⑧ **OWM 逐时昼夜恒为 true** —— `new Hourly(..., true, ...)` 写死，凌晨 3 点也画太阳图标；改用接口自带的 `sys.pod`（"d"/"n"），缺失时退到 6..18 本地小时（与 daily 分桶同一规则），DTO 补 `SysBean`。另外两处「抓了不用」也一并接上：**OWM 的 `air_pollution` 结果**（服务并行拉了第三路，转换器收下参数却传 `null` 给 `Current` 的 airQuality）现在按 WeatherAPI 的口径映射成 `AirQuality`（index 用 `main.aqi`，pm2.5/pm10/so2/no2/o3/co 照填）；**Open-Meteo 的 `daily.sunrise/sunset`**（service 请求了、DTO 有字段、转换器传 null）现在填进 `Daily` 的 Astro。测试覆盖各源的：cityId 必须来自 `location.getCityId()`（3.4.14 那类缓存键漂移）、稀疏但合法的 200 响应不得触发 `@NonNull` 断言、单位换算、天气码映射、日出日落是真实 instant；WeatherAPI 另锁 3.4.13 的**跨行政区预警过滤**（固件正好抓到线上现象：给天津坐标返回了河北**霸州市**暴雨红警 → 必须丢弃；把 `areas` 改成天津则必须保留，防止「过滤」被写成「全删」）；OWM 另锁 3 小时步长**按本地日分桶**（固件 10 条正好是 2 个完整的 Asia/Shanghai 日：2 条尾日 + 8 条整日）和空 forecast → 空 daily（配合 `WeatherHelper`/`DatabaseHelper` 两处全局守卫）；CaiYun 另锁 v2.6 的 `daily.air_quality` 对象形状、minutely/alert 必须是空列表而非 null。`./gradlew test` 六变体全绿、`testPubDebugUnitTest` **53 例 0 失败**（原 29 例），`assemblePubDebug` BUILD SUCCESSFUL。**未做**：CMA 与 ACCU 的测试 —— 前者 `weather.cma.cn` 的 WAF 在本机直接掐 TLS 握手（curl/python 均 `UNEXPECTED_EOF`，手机上正常），后者 Key 已过期，都抓不到真实固件，按「不写靠猜的固件」跳过。**已知未修**：CaiYun 的 `?alert=true` 是白请求 —— `CaiYunWeatherResult` 里**根本没有 `alert` 字段**、转换器 alertList 恒 `new ArrayList<>()`；本次固件没抓到预警，形状只能靠猜，故不动。

- **AQI 量纲修正：WeatherAPI/OWM 由「档位号」改为真实中国 AQI**。`AirQuality.aqiIndex` 在全 app 都按 **0–500 的中国 AQI** 消费：`getAqiColor` 阈值 50/100/150/200/300、主界面 AQI 环 `container_main_aqi.xml` 是 `max=400` 且**直接把该数字显示出来**、日详情 `AirQualityHolder` 显示 `aqi + " / " + aqiText`；标准是**中国**而非美国 —— 彩云转换器取的是 `aqi.chn` 不是 `usa`（同一固件 `chn=25 优`/`usa=66 良`，差一档），且 `getPm25Color` 的 35/75/115/150/250 是 GB 3095-2012 限值（EPA 为 12/35.4/55.4）。彩云（`aqi.chn`）与 Accu（`aqiResult.Index`）本就合规，**只有 WeatherAPI 塞 `us-epa-index`(1–6)、OWM 塞 `main.aqi`(1–5) 这两个档位号** → 六档全部 ≤50 落进第一档，**空气再脏也恒为绿色**（实测枚举 1~6 全部 `colorLevel_1 #72d572`），且日详情显示成无意义的 "2 / 2"。修法：① `CommonConverter` 加 `getAqiIndexFromConcentration(pm25, pm10)`，按 HJ 633-2012 分段线性插值、取各污染物子指数最大值，分段表与 `getPm25Color`/`getPm10Color` **同源**（否则同一张卡上「数字」和「颜色」互相打架）；② 两个源改用浓度算 index、`aqiText` 走既有的 `CommonConverter.getAqiQuality()`（与彩云/Accu 一致，显示「41 / Fresh air」而非「2 / 2」）；③ `us-epa-index` 仅在**无浓度**时兜底，映射到档位**中值**（取上界会让「良」顶到 100，显得比实际严重）—— OWM 不需要同类兜底，它的 `components` 一旦非空浓度就是基本类型必有值，那条路走不到，故**没写**（避免死代码）。④ 顺带修 `AirQualityHolder.java:33` 的**拆箱 NPE**：`int aqi = airQuality.getAqiIndex()` 而 `getAqiIndex()` 标了 `@Nullable`，入口条件 `DailyWeatherAdapter:100` 的 `isValid()` 只要**任一**字段非空就通过 → 「有 pm2.5 但没有 index」的响应必崩。这是**既存**隐患（非本次改出），但本次会让 index 为 null 更常见，故一并修；同时 text 为空时不再渲染成 "0 / null"。**取舍（已写进代码注释）**：index 只用 PM2.5/PM10 算 —— 中国 AQI 这两项是 **24h 均值**，气体项另有 1h/24h 两套限值，而接口给的是**瞬时浓度**，严格说对不上任何一套；国内首要污染物绝大多数时候也是这两项。其余气体照填进 `AirQuality` 供分项颜色显示但不参与 index。**故结果是指示性的，不等同官方发布值**。**实测对照**（真实固件，天津南开点）：WeatherAPI `us-epa-index=2`/`pm2.5=29.0`/`pm10=32.6` → 旧显示 "2" 🟩，新 **41 / Fresh air** 🟩（该点位空气本就干净，**颜色是巧合正确的**，错的是数字；注意按 EPA 算同一浓度是 87 会显示黄色，但本 app 用中国标准，41 属「优」，绿色才对）；OWM `main.aqi=2`/`pm2.5=11.12` → 旧 "2"，新 **20**。测试：新增 `CommonConverterTest`（7 例，锁分段边界/段内插值/取最大子指数/超上限封顶/缺值退 null/档位中值/越界档位），并把 OWM 与 WeatherAPI 两处原本断言 "2" 的用例改成修后的正确值。`./gradlew test` 六变体全绿、**60 例 0 失败**（原 53），`assemblePubDebug` BUILD SUCCESSFUL。**真机验证**（MI 9/Android 14，debug 包，舒城县）：WeatherAPI 刷新后 `pm2.5=16.3`/`pm10=16.3` → 主界面 AQI 环显示 **23 / 优**（=max(IAQI_pm2.5 23, IAQI_pm10 16)，旧代码是档位号 "2"），环 `max=400` 下弧长与配色正常；头部由档位号变为「空气质量 - 优」。`AirQualityHolder` 改走**彩云**验证 —— WeatherAPI 的 `forecastday[].day` **根本没有 `air_quality` 键**（只有 `current` 有，固件已确认），故其日详情本就不渲染 AQI 卡，与本次改动无关；切彩云后日详情显示 **21 / 优**（`aqi.chn`）正常。全程 `logcat -b crash` 空、0 崩溃，验完已切回 WEATHERAPI。

## 已知问题 / 约束

- **AQI 一律用中国标准（HJ 633-2012 / GB 3095-2012）**：`AirQuality.aqiIndex` 是 **0–500 的中国 AQI 数值**，不是档位号；`aqiText` 走 `CommonConverter.getAqiQuality()`。新增源若接口只给「档位号」（如 us-epa-index 1–6、OWM main.aqi 1–5），**不得原样写入** —— 有浓度就走 `CommonConverter.getAqiIndexFromConcentration(pm25, pm10)`，没有才映射到档位中值。原样写档位号的后果是六档全落在 ≤50 的第一档，空气再脏也显示绿色。
- **转换器是高发崩溃区**：`weather/converters/*ResultConverter.java` —— provider 字段常为 null，`Weather` 模型 `@NonNull` 断言会抛；新增/改源必须逐字段兜底。
- **数据库 schema 锁定 v63，禁止修改**（`db/GeometricWeatherDatabase` line 33）。
- **Room 禁止主线程访问**，一律 `AsyncHelper.runOnIO`（历史多次崩溃来源）。
- **CMA**：weather.cma.cn WAF 拦截默认 okhttp UA（已加浏览器 UA，仅限 CMA）；其无坐标→站点接口，靠全国站点图找最近站点。**开发机上抓不到固件** —— WAF 对非浏览器的 TLS 指纹直接掐握手（curl `schannel: failed to receive handshake` / python `SSL: UNEXPECTED_EOF`，HTTP 也是 empty reply），手机上正常，故 CMA 与 ACCU（Key 过期）目前没有转换器测试。
- **APIHZ（中国天气网）**：主走 `tqyb.php`（`sheng`+`place`）按城市取数，`tqybip.php` 仅作 IP 兜底。接口名怪癖：`place` 去尾「区」、直辖市 `sheng` 去尾「市」；区级覆盖不全，按 区→市→IP 依次兜底（见 3.4.9）。海外地名查不到 → 退 IP（国外 IP 接口默认返回北京）。
- **ACCU 的 Key 已过期**（2026-06-28 实测：geoposition/currentconditions 均返回 403 `"This API Key has expired"`）→ AccuWeather 源当前完全不可用，需在 `build.gradle:30-32` 换新 Key（三个：`EMBEDDED_ACCU_WEATHER_KEY`/`EMBEDDED_ACCU_CURRENT_KEY`/`EMBEDDED_ACCU_AQI_KEY`，base64 编码；也可在 `local.properties` 覆盖，优先级更高）。本是功能最全的源（15天/24时/UV/AQI/分钟级/预警）。**3.5.2 起默认源已改为 WEATHERAPI**，避免新装用户落到死源。
- **CAIYUN 为试用 Token**（`HYMo2dWkbB73N7rt`）：daily 上限仅 **3 天**、无 minutely 分钟级降水块；realtime/daily 字段齐全（体感/UV(life_index)/AQI/能见度）。如需 15 天+分钟级须换正式 Token。**预警未接**：`?alert=true` 是白请求 —— `CaiYunWeatherResult` 没有 `alert` 字段、转换器 alertList 恒空列表。
- **各源数据丰富度实测**（2026-06-28，北京/MF用巴黎）：OPEN_METEO 16天·384时·全字段(无AQI/预警)；WEATHERAPI 14天·336时·含AQI+预警(最全可用源)；CAIYUN 3天·48时·含AQI/UV；APIHZ 7天·56时(逐3h)·无UV/AQI/能见度；CMA 7天·逐时(网页抓取)·无UV/AQI；OWM 5天/40点(3h步长)·有AQI·2.5无UV；MF(法)15天·98时。
- **OWM**：国内到 `api.openweathermap.org` 的 **TLS 握手不稳**（`connectTls`→`doHandshake` 偶发 30s 超时，三路并行冷启动随机某路中招），非代码缺陷。Key 有效。天气源可用性页的检测预算已由 25s 提到 35s，否则会把它误报成"不可用"。
- **空 daily 列表是全局隐含前提**：全仓库 **76 处** `getDailyForecast().get(0)` 不做保护（UI/widget/通知）。已在 `WeatherHelper.requestWeatherSuccess` 与 `DatabaseHelper.readWeather` 两个汇聚点拦截，新增消费点仍应假定「daily 至少一条」由这两处保证；**绕过这两处直接构造 Weather 的新代码必须自己判空**。
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
