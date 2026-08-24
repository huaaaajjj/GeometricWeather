# 从 breezy-weather 移植数据源：实施方案

## 进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| 1 | MET Norway 独立源 | **已完成**（7 处注册点全过，12 例转换器测试，`assemblePubDebug` 通过；release 包真机验证：自检页 11天/87点、AQI ✓、预警 1、体感与日出 ✗ 为设计降级，主界面渲染正常、0 崩溃）|
| 2 | 小米天气 独立源 | **已完成**（7 处注册点全过；13 例转换器 + 9 例服务编排测试，六变体 199 例 0 失败，`assemblePubDebug` 通过。**真机验收未做** —— 下次冒烟时补自检页那行 + 切源看主界面）|
| 3 | FPAS 挂进 COMPOSITE + `merge` 守卫 | **不做（2026-08-24 决定）** |
| 4 | 可选：`CommonConverter` 加太阳计算（方案 B）| **不做（2026-08-24 决定）** |

阶段 1、2 的落地细节与全部接口怪癖记在 `AI_CONTEXT.md` 变更日志；本文档以下内容保留原样，作为背景记录。

**非数据源的借鉴项（2026-08-24 调研，全部「记录待定」，未动代码）见文末[「阶段 5+」](#阶段-5非数据源的借鉴项2026-08-24-调研待定)一节。**

**阶段 3/4 的裁决理由**（读过 `_ref` 实现与本仓库现状后，用户决定两项都不做）：

- **阶段 3（FPAS）成本远高于方案估计**：bbox 那个 GET 只返回 UUID 列表，每条预警还要再取一次 **CAP XML** 详情（N+1 次往返）；XML 要引一整套新栈（breezy 用 `xmlutil` + kotlinx-serialization，本项目只有 Gson，`CapAlert` 是 297 行 DTO）；多边形过滤依赖本项目没有的 maps-utils；breezy 自己标它 beta。收益侧则**对中国基本零覆盖**（FPAS 聚合各国公开 CAP 源，CMA 不发），而境内预警已有 APIHZ / 小米 / CMA / WeatherAPI 四路。它还是四项里唯一要改既有代码的（`WeatherMerger.merge` 在 `results.size == 1` 时直接返回，需加守卫），而那条路径测试最密。**若将来真要补境外预警**，更省的路是把已接好的 METNO metalerts（挪威）与 MF `/v3/warning/full`（法国）挂进 COMPOSITE —— `mergeAlerts` 本就取并集，零新依赖。
- **阶段 4（太阳计算）不做**，但**缺日出日落的源不止 METNO 一个**，见 `AI_CONTEXT.md` 已知问题里新增的那条 —— 缺的是 METNO、CMA、OWM 三个，而 OWM 的日出日落其实**接口已经给了只是没读**。要动的时候先读那个现成字段，再考虑 NOAA 算法。

**阶段 2 与原方案的两处偏差**（都在实现时用真实响应确定）：locationKey 走方案 A（每次刷新重新解析）如原计划；但 **market/intl 主机不需要按条件切换** —— 固定用 market 一个 baseUrl，境内外由 `isGlobal` + key 前缀区分（intl 主机对逐分钟端点限流更紧）。另外**返回坐标虽是 GCJ-02 却不需要转换** —— 服务只取 `locationKey`，位置始终用调用方自己的 WGS-84，坐标字段无人读取。

## 背景

本项目当前 9 个源（ACCU / OWM / MF / CAIYUN / OPEN_METEO / WEATHERAPI / CMA / APIHZ / COMPOSITE）里，**全球覆盖且不要 key 的只有 Open-Meteo 一个**。Open-Meteo 一旦限流或改接口，非中国地区就只剩要 key 的 Accu/OWM 顶着，而那两个 key 是内置的、会被限流。

[breezy-weather](https://github.com/breezy-weather/breezy-weather)（同为 GeometricWeather 的 fork，同 LGPL-3.0，许可上可借鉴）有约 78 个源。参考副本克隆在 `_ref/breezy-weather`（已 gitignore，commit `5e36cd7` / 2026-08-23）。

**先说结论：breezy 仓库里没有任何可用的天气 API key。** 23 个 key 全部走 `local.properties`（默认空串），真值存在 GitHub Actions secrets，构建时才注入（`.github/workflows/push.yml:68-88`）。唯一硬编码的凭证是小米天气那一对（见 #2）。所以本方案只挑**零 key 成本**的源。

本文档只出方案，不含代码。

---

## 结论速览

| # | 项 | 裁决 | 净收益 | 主要成本 |
|---|---|---|---|---|
| 1 | MET Norway | **做，优先** | 第二个免 key 全球源 | 无日出日落，需自算或降级 |
| 2 | 小米天气 | **做** | 中国区第 4 源，维护勤 | 两步请求，locationKey 无处缓存 |
| 3 | Natural Earth | **不做**（按现状） | 极低 | 2 MB 资源 + 只给国家名 |
| 4 | FPAS + WMO | **做 FPAS，WMO 待定** | 全球预警补盲 | 只能挂 COMPOSITE，不能独立 |

---

## 先纠三处与原设想的偏差

1. **#3 替不了百度 IP 兜底。** Natural Earth 只返回 `country` + `countryCode`，**不返回坐标**（`NaturalEarthService.kt:72-99`）。百度 IP 的作用是 GPS 失效时**提供坐标**，两者不是同一件事，无法互换。
2. **#4 做不成独立源。** 只给预警的源没有预报，选中它主界面就是空的。本项目是「一个 location 一个 WeatherSource」，只有 `CompositeWeatherService` 能容纳这种成员。
3. **注册点是 7 处，不是 6 处。** `.claude/skills/add-weather-source/SKILL.md` 写的 6 处漏了 `settings/compose/WeatherSourceStatusScreen.kt:70` 的 `SOURCES` 列表 —— 不改这里，新源在「天气源可用性」自检页里是隐形的，而那正是验收新源要用的页面。**已在阶段 1 顺手把 skill 补成 7 处。**

---

## 7 处注册点（每个新源都要全过一遍）

| # | 文件 | 改什么 | 坑 |
|---|---|---|---|
| 1 | `common/basic/models/options/provider/WeatherSource.kt` | 枚举项 + `getInstance()` 分支 | **只能追加**（`:24` 注释：Location 按 ordinal 序列化）。`caiyun` 分支是 `contains("caiyun") \|\| contains("cn")`，新 id 别带 `cn` |
| 2 | `weather/WeatherServiceSet.java` | 构造器参数 / 数组元素 / `get()` switch | `:51-75` 是**手写下标**，必须追加到末尾 |
| 3 | `weather/di/ApiModule.java` | 一个 `@Provides` + 独立 Retrofit | 需要自定义 UA/日期格式的别改共享 OkHttp |
| 4 | `app/build.gradle` | `buildConfigField` BASE_URL | 顺手删掉死配置 `CN_WEATHER_BASE_URL`（`:90`，全项目无引用） |
| 5 | `res/values/arrays.xml` | 三个数组（`:9` / `:20` / `:31`） | 顺序是**展示顺序（好用的在前）**，不是枚举顺序；三个数组按下标一一对应 |
| 6 | `res/values-zh-rCN/arrays.xml` | 两个数组（`:5` / `:16`） | 无 `weather_source_values`；漏了不崩但界面显示原始 id |
| 7 | `settings/compose/WeatherSourceStatusScreen.kt:70` | `SOURCES` 列表 | `Src(source, name, france=false)`；测试坐标见 `:225-232`（法国→巴黎，否则→北京） |

---

## #1 MET Norway（优先做）

### 已核实

Retrofit 接口 `_ref/.../sources/metno/MetNoApi.kt`，baseUrl `https://api.met.no/weatherapi/`，4 个端点全部**免 key、免注册**：

| 端点 | 参数 | 给什么 |
|---|---|---|
| `locationforecast/2.0/complete.json` | `lat` `lon` | 主预报（日/时） |
| `nowcast/2.0/complete.json` | `lat` `lon` | 分钟级降水，**仅北欧** |
| `airqualityforecast/0.1/` | `lat` `lon` | 空气质量 |
| `metalerts/2.0/current.json` | `lang` `lat` `lon` | 预警 |

**每个请求都必须带 `@Header("User-Agent")`** —— 这是 api.met.no 的 ToS 强制要求（无 UA 会被封）。Breezy 用 `BreezyWeather.instance.userAgent`。

### 实质缺口：没有日出日落

整个 `metno/` 目录里 `sunrise|sunset|Astro` **零匹配** —— MET Norway 的 locationforecast 不含日出日落。而本项目 `weather/converters/CommonConverter.java` 里也**没有太阳计算**（只有 `getWindLevel:17`、`getMoonPhaseAngle:136`、`isDaylight(sunrise,sunset,current):180` —— 最后这个要求你**已经有**日出日落）。

对比：Open-Meteo 是从接口直接取 `sunrise,sunset` 的（`OpenMeteoWeatherService.kt:79` 的 `DAILY_FIELDS`）。

三条路：

- **A（推荐）**：`Daily.sun()` 留空。`Weather.isDaylight()` 在 `sun()` 为 null 时已自动退到 `DisplayUtils.isDaylight(timeZone)`（`Weather.java:146-148`），不崩。代价：自检页「日出」列为空；动态背景昼夜判断用粗略时区版。**零新增代码。**
- **B**：写一个 NOAA 太阳位置算法（约 40 行）放进 `CommonConverter`，**所有源都能受益**（将来接的源大多不给日出日落）。
- **C**：只在 COMPOSITE 里用它 —— `WeatherMerger.fillDaily` 的 `pick(listOf(leader.sun()) + others.map{it.sun()}, Astro::isValid)`（`WeatherMerger.kt:154`）会自动从别的成员补日出日落。但作为**独立源**仍然缺。

建议先 A 落地跑通，B 作为独立的后续改进（它的收益不只属于 MET Norway）。

### 要建的文件

```
weather/json/metno/MetNoForecastResult.kt      (+ AirQuality / Alert / Nowcast)
weather/apis/MetNoApi.java                      Call<T>，不是 Observable
weather/converters/MetNoResultConverter.kt
weather/services/MetNoWeatherService.kt
```

照 `OpenMeteoWeatherService.kt` 写 —— 同样是坐标型、无 key、`requestLocation` 直接回显位置（MET Norway 没有地名搜索）。多路并行用 `RequestScope`（`weather/services/RequestScope.kt`，`execute()` 已把失败降级为 null，比 `CountDownLatch` 那套省事）。

### 风险

- nowcast 仅北欧 → 非北欧必然失败，必须容忍空结果，不能让它拖垮整次刷新（`RequestScope.execute` 已经是这个语义）。
- `symbol_code` 是字符串（如 `partlycloudy_day`）→ 需要映射到本项目 `WeatherCode` 枚举，映射表得照抓到的真实响应列全。
- 独立 UA 要加在 MET Norway 自己的 Retrofit 实例上（注册点 3），**别动共享 OkHttp**（CMA 绕 WAF 的先例：`weather/apis/CmaApi.java:27` 起）。

---

## #2 小米天气（做）

### 已核实

硬编码凭证在 `_ref/.../sources/china/ChinaService.kt:611-614`：

```
CHINA_WEATHER_BASE_URL_MARKET = "https://weatherapi.market.xiaomi.com/wtr-v3/"
CHINA_WEATHER_BASE_URL_INTL   = "https://weatherapi.intl.xiaomi.com/wtr-v3/"
CHINA_APP_KEY = "weather20151024"
CHINA_SIGN    = "zUFJoAR2ZVrDy1vF3D07"
```

端点（`ChinaApi.kt`，全部 `@GET`，凭证走 **query 参数**）：

| 端点 | 参数 |
|---|---|
| `location/city/geo` | `latitude` `longitude` `locale` |
| `location/city/search` | `name` `locale` |
| `weather/all` | `latitude` `longitude` `isLocated` `locationKey` `days` `appKey` `sign` `isGlobal` `locale` |
| `weather/xm/forecast/minutely` | `latitude` `longitude` `locale` `isGlobal` `appKey` `locationKey` `sign` |

### 真摩擦点：两步请求 + locationKey 无处存

`weather/all` 必须带 `locationKey`（格式 `weathercn:101010100`）。Breezy 从 `location.parameters` 这个 per-source 键值缓存里取（`ChinaService.kt:115`），取不到就先调 `location/city/geo` 补（`:117`、`:588-603`）。

**本项目的 `Location` 没有 `parameters` 这种字段，而数据库 schema 冻结在 v63（硬约束，不能加列）。**

两条路：

- **A（推荐）**：每次刷新老实做两步 —— `location/city/geo` 拿 locationKey，再 `weather/all`。多一次往返，但零 schema 改动、零状态。CMA 源本来就是多次请求，量级可接受。
- **B**：复用现有 `Location.cityId` 存 locationKey。**不推荐** —— `cityId` 是天气缓存键（`Base.cityId` 必须是 `location.getCityId()`，见 skill 第三节；3.4.13/3.4.14 的原坑就是缓存键漂移导致 weather 表无界累积）。往里塞源特有的 key 会重演那个 bug。

选 A。

### 其它已核实

- **market vs intl 两个 baseUrl** 有选择条件（`ChinaService.kt:94-98`），具体判断逻辑需要读那几行确认后再定。
- 返回坐标是 GCJ-02 → 按项目惯例要转 WGS-84。
- 有分钟级降水（独立端点）和预警。预警要按行政区过滤（WeatherAPI 曾给天津坐标返回北京延庆的预警）。

### 收益定位

本项目中国区已有 CAIYUN / APIHZ / CMA。小米这个的价值是：**接口维护勤、同时给 current + 日 + 时 + 分钟级 + 预警 + AQI**（一个 `weather/all` 打包），可以作为 COMPOSITE 的中国区成员候选，或在 APIHZ 挂掉时顶上。

---

## #3 Natural Earth（建议不做）

### 它到底是什么

纯离线反向地理编码，**只返回国家名 + ISO 国家码**，无省市（`NaturalEarthService.kt:72-99`）。数据是 `ne_50m_admin_0_countries` 的精简 GeoJSON。

### 为什么不做

1. **和本项目架构错位。** 本项目的反查是**天气源自己做的** —— `LocationHelper.requestAvailableWeatherLocation:180-181` 调 `WeatherService.requestLocation(context, location, callback)`。没有独立 geocoder 层可插。要接入就得先造一层。
2. **替不了百度 IP。** 它不给坐标（见上文「三处偏差」第 1 条）。
3. **成本对收益不划算。** GeoJSON **2,073,561 字节（约 1.98 MiB）**，242 个 feature。res/raw 会被压缩，估计 APK +0.7~0.9 MB，安装后占 2 MB。Breezy 还靠一个 vendored 的 `maps-utils` 模块（1786 行，Apache-2.0）做点在多边形内判断 —— 本项目单模块、无该依赖。
4. **Breezy 的实现有个真 bug，不能照抄。** `isMatchingFeature` 对 `polygon.coordinates` 直接 `.any{}`，把**外环和洞一视同仁**（`NaturalEarthService.kt:105-113`；`GeoJsonPolygon` 明明提供了 `outerBoundaryCoordinates`/`innerBoundaryCoordinates` 却没用）。落在洞里的点（莱索托在南非环内、梵蒂冈/圣马力诺在意大利环内）会同时命中包围国，再叠加「必须恰好命中 1 个」的规则，**这些地方什么都返回不了**。

### 如果将来要做

不引 `maps-utils`、不加新依赖，最小实现约 120-150 行：Gson 的 **流式 `JsonReader`**（避免把 2 MB 解析成 org.json 树，那会有 15-25 MB 瞬时峰值，minSdk 21 的老机器有 OOM 风险）+ 每个 feature 顺手算 bounding box 预筛 + 平面 even-odd 射线法（测地线版对「我在哪个国家」这个精度没必要）+ 外环 AND 非洞（修掉上面那个 bug）+ `Locale.Builder().setRegion(cc).build().displayCountry` 出本地化国名。首次解析必须 `AsyncHelper.runOnIO`。

> 以上体积/内存数字来自一次子代理深读，其中「洞的 bug」和「只返回国家」两条我已独立复核。2 MB 这个数字来自该代理的 shell 实测，我因工具中断未能二次核对，动手前建议自己 `ls -la` 确认一次。

---

## #4 FPAS + WMO Severe Weather（做 FPAS）

### 已核实：FPAS 不需要订阅

我原先担心 FPAS 要 POST 注册订阅区域 —— **不需要**。两个 `@GET`，无 key：

- `FpasJsonApi.kt:24` → `GET alert/area?min_lat&max_lat&min_lon&max_lon`（bbox 查列表）
- `FpasXmlApi.kt:25` → `GET alert/{uuid}`（取单条详情，CAP XML）

两步、纯 GET、服务端按 bbox 过滤。很好移植。

### 只能挂 COMPOSITE

本项目已经具备容纳它的机制，无需改架构：

- `CompositeWeatherService.kt:53-58` 的 `members` map
- `WeatherMerger.mergeAlerts`（`:234-239`）已经是**所有源预警的并集**，按 `description|content` 去重

一个「只有预警」的 `Weather` **可以合法构造**：`Weather` 只要求 `@NonNull Base` + `@NonNull Current`，三个 List 允许为空（`Weather.java:28-40`）。`Base(cityId, timeStamp, publishDate, publishTime, updateDate, updateTime)` 和 `Alert(alertId, date, time, description, content, type, priority, color)` 都是平凡构造。

### 两个必须处理的边界

1. **成员必须放在 `members` map 末尾。** `preferring(block)` = `[该 block 的源] + sources`，而 `sources` 就是 map 的迭代顺序（`CompositeWeatherService.kt:60,78`）。`mergeCurrent` 拿 `currentOrder[0].current` 当 leader（`WeatherMerger.kt:91`）—— 一个只有预警的成员若排在前面，「当前天气」整块会是空的。放末尾则只在所有人都失败时才被选中，那时本来也没数据。
2. **`merge()` 在 `results.size == 1` 时直接 `return results[0]`（`WeatherMerger.kt:67-69`）。** 万一只有这个预警源答了，COMPOSITE 会返回一个没有预报的 Weather → 界面空白而不是失败。**需要加一道守卫**：要么在 `CompositeWeatherService` 里过滤掉「只有预警」的答案再判空，要么给 `merge` 加最小有效性检查。这是本项落地必须动的唯一一处既有代码。

### WMO 待定

WMO Severe Weather 的端点/过滤方式我这轮**没核实**（子代理任务失败）。它的数据是各国气象局转发，和本地源预警**大概率重复** —— 虽然 `mergeAlerts` 会按文本去重，但不同源的措辞不同，去重可能失效，反而刷出一堆重复预警。建议先只上 FPAS，观察去重效果再决定 WMO。

---

## 建议顺序与工作量

| 阶段 | 内容 | 说明 |
|---|---|---|
| 1 | **MET Norway** 独立源，日出日落走方案 A | 一条完整流水线 + 7 处注册点跑通，验证 UA 要求和字段映射 |
| 2 | **小米天气** 独立源，两步请求走方案 A | 复用阶段 1 的套路 |
| 3 | **FPAS** 挂进 COMPOSITE + 补 `merge` 守卫 | 唯一需要改既有代码的一项 |
| 4 | 可选：`CommonConverter` 加太阳计算（方案 B） | 收益归所有源，不只 MET Norway |
| — | **#3 Natural Earth 不做**；**WMO 观察后再定** | |

每阶段独立可发版，互不阻塞。

---

## 验证

每个源落地后：

```bash
./gradlew assemblePubDebug
```

装真机 → 设置 → 数据提供商 → **天气源可用性** → 实测刷新。确认新源那行的「天 / 时 / 体感 / UV / 气压 / 湿度 / 降水 / AQI / 预警 / 日出」各列（列定义见 `WeatherSourceStatusScreen.kt:64-67`）。MET Norway 的「日出」列按方案 A 预期为空 —— 这是已知降级，不是 bug。

注意自检页的测试坐标只有巴黎和北京两个（`:225-232`）。MET Norway 的 nowcast 仅北欧，用北京/巴黎测都不会命中分钟级降水；要验证这一路得临时改坐标到奥斯陆（59.91, 10.75），或给 `Src` 加个类似 `france` 的 `nordic` 标志。

按 `CLAUDE.md`，完成后在 `AI_CONTEXT.md` 变更日志追加一行（中文），记下**接口怪癖**（UA 强制、nowcast 地域限制、locationKey 两步、坐标系）。

---

## 本轮未核实的部分（动手前要补）

一次并行深读子任务因安全分类器中断而失败（6 个里 5 个停滞），以下是**结构性事实已确认、字段级细节未确认**的部分：

- MET Norway 各响应的确切字段名与单位（气温/风速/气压/降水），`symbol_code` → `WeatherCode` 完整映射表
- 小米 `weather/all` 响应的字段名、单位、数组语义，`weatherCode` 映射表，market/intl 的判断条件（`ChinaService.kt:94-98`）
- WMO Severe Weather 的端点与地理过滤方式
- Natural Earth 的 2 MB 与 242 feature 两个数字（来自子代理 shell 实测，未二次核对）

这不影响方案成立：项目 skill 本来就要求 **「写 DTO 之前先拿到真的 JSON，别照文档猜字段」**（文档与实际不符是常态 —— APIHZ 的 `daily.air_quality` 文档说数组、实际是对象）。所以字段级细节应在实现时用真实响应确定，而不是写进方案里：

```bash
curl -s "https://api.met.no/weatherapi/locationforecast/2.0/complete.json?lat=59.91&lon=10.75" \
  -H "User-Agent: GeometricWeather/3.5.13 github.com/WuZhengyang2024" | python -m json.tool | head -80
```

---

## 阶段 5+：非数据源的借鉴项（2026-08-24 调研，待定）

前四个阶段只看「多接哪个源」。这一节是读 breezy 的 README / `docs/SOURCES.md` 后，对着本仓库现状挑出的**架构与功能方向**，按「收益÷成本」排。**全部只是记录，未动代码，未排期。**

| # | 借鉴什么 | 成本 | 动 schema |
|---|---|---|---|
| A | **分要素选源做成用户可配**（breezy 的核心架构） | 小 | 否 |
| B | **Open-Meteo 空气质量/花粉端点** —— 复活整套已存在的花粉 UI | 小 | 否 |
| C | **每个地区各自选源**（breezy 是 per-location，本仓库是全局） | 中 | 否 |
| D | **源能力/覆盖范围静态声明表** | 小 | 否 |
| E | Gadgetbridge 广播对外共享天气 | 小 | 否 |
| F | per-source 参数缓存（breezy 的 `location.parameters`） | 极小 | 否（走 prefs） |
| G | README 里公开「不做清单」 | 零 | — |
| J | IzzyOnDroid + Obtainium 收录 | 极小 | — |
| H | Normals（气候平均值） | 大 | **是** → 跳过 |
| I | 单位格式走 CLDR/ICU | 中 | 否（minSdk 21 挡） |

### A. 分要素选源：`CompositeBlock` 已经是那张表，只差让用户改

breezy 整个架构就是这件事 —— 一个地区的 forecast / air quality / pollen / nowcasting / alerts / normals / address 各自选源（`docs/SOURCES.md` 里「香港 HKO 预报 + EPD 空气」「法国 MF + Atmo 花粉」就是它的产物）。

本仓库 3.5.9 的 `CompositeBlock` **已经是同一个东西**，只是四个块的指派硬编码在枚举里。把它从常量改成读 `SharedPreferences` + 设置页几个下拉，就得到 breezy 那套能力的九成，且**不碰 schema**（全局偏好，不是 per-location）。`CompositeBlock` 兼任卡片署名来源那一层不用动，改的只是数据来源。顺带解决一个死角：现在指派写死，境外用户的「每日→中国天气网」必然落空、靠兜底链救。

### B. Open-Meteo 的空气质量端点（已实测，最省的一件真功能）

`https://air-quality-api.open-meteo.com/v1/air-quality`，免 key。2026-08-24 拿巴黎（48.85, 2.35）实测 `current=`：

```
pm10 13.5  pm2_5 8.3  carbon_monoxide 251  nitrogen_dioxide 15.6  sulphur_dioxide 1.5  ozone 52   (μg/m³，全球)
alder 0  birch 0  grass 1.7  mugwort 1.8  olive 0  ragweed 0.2                                    (grains/m³，仅欧洲)
```

两个收益：

1. **花粉链路当前对所有用户都是死的。** 仓库里有整套 7 个文件（`common/basic/models/weather/Pollen.java`、`common/ui/activities/AllergenActivity.kt`、`main/adapters/main/holder/AllergenViewHolder.java`、`main/adapters/HomePollenAdapter.kt`、`daily/adapter/holder/PollenHolder.java`、`daily/adapter/model/DailyPollen.java`、`common/basic/models/options/unit/PollenUnit.kt`），唯一供数方是 `AccuResultConverter` —— 而 Accu 的 key 已过期，所以这套 UI 谁都看不到。接上后 `tree = max(alder, birch, olive)`、`grass`、`ragweed` 三个槽能填（`mold` 无对应字段、`mugwort` 无槽），**零新 UI、零 schema**（`DailyEntityGenerator` 本来就存 pollen 列）。限欧洲，如实标注。
2. **Open-Meteo 从此有 AQI。** 它现在完全没有 AQI，正是 COMPOSITE 必须拉彩云/WeatherAPI 的原因。该端点给的是**浓度**，直接喂已有的 `CommonConverter.getAqiIndexFromConcentration`，符合「AQI 一律用中国标准、档位号不得原样写入」那条约束 —— 它的 `european_aqi`/`us_aqi` 恰恰是不能用的那类，**弃用**。

成本：一个新 Retrofit 端点 + DTO + 转换器填两块。`OpenMeteoWeatherService` 本来就是 `RequestScope` 多路并行，加一路即可，失败自动降级为 null。

### C. 每个地区各自选源

breezy 是 per-location 选源。本仓库 `LocationEntity.weatherSource` **本来就是强类型列**，per-location 存得下；3.4.4 修「只有定位地区能换天气源」时选的是「全局重设所有地区」，代价明确记着「放弃同城用不同源各存一个」。breezy 的做法是把选源放在**地区管理页的每个地区上**，而不是设置页的全局开关。要处理的仍是那个老问题：`formattedId` 去重防同城多源塌缩。属方向而非顺手。

### D. 源能力/覆盖范围声明表

breezy 用 `SourceFeature` + 一组接口声明每个源能供哪些要素；本仓库对应物是自检页手维护的 `SOURCES` 列表 + 真联网探测。一张静态表（源 → 能供的块 + 覆盖区域）一次买到三样：

- COMPOSITE 现在每次刷新问全部 5 个成员，其中一批是**注定失败**的调用 —— METNO 的 airquality 境外 HTTP 400、nowcast 境外 422、小米境外 `{"status": -2}`、APIHZ/彩云海外查不到。有表就能不问。
- 自检页能区分「超出覆盖范围」与「坏了」。这个苦已经吃过：给 `Src` 加 `nordic` 标志就是为了别拿北京去探 METNO 的北欧端点。
- A 项那几个下拉只列得出有效源。

### E / F / G / J（小件）

- **E**：breezy 有 `nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER` 广播（以及 v6.1 起实验性的 ContentProvider）给智能手表供数。约 50 行 + 一个设置开关，无 schema。
- **F**：breezy 的 `location.parameters` 是 per-source KV 缓存。schema 锁着加不了列，但按 `formattedId + source` 存 SharedPreferences 就行 —— 直接受益的是小米的 `locationKey`，现在每次刷新都要重解析一遍（阶段 2 选方案 A 就是因为「无处缓存」）。
- **G**：breezy README 有一节明确写拒绝什么（不做低于 30 分钟的刷新间隔、不做打开即刷新/点 widget 即刷新、圆形天空不会回来、不接受捐赠）。这种「不做清单」是维护工具。本仓库同类决定散在 `AI_CONTEXT.md`（不改 schema、不重绘 UI、不接 FPAS、不做自动更新），搬进 README 即可。
- **J**：`fastlane/metadata/` 目录结构已在（changelogs 停在 30013）。补上就能被 IzzyOnDroid 自动收录；Obtainium 认 GitHub Releases 本来就能用。

### 不做（连同理由记下，免得反复讨论）

- **Material 3 Expressive 全量重绘 / 24 小时详图**：与「保持原 UI 风格」硬冲突。详情卡那次改造是在原风格里做增量，方向对。
- **多模块 Gradle 拆分 + kotlinx-serialization 换 Gson**：现有 DTO、`proguard-rules.pro:20` 的 `weather.json.**` keep 规则、Hilt 图全绑在单模块 + Gson 上，换栈是纯支出。
- **Normals（气候平均值）**：要新表，schema 锁在 v63。
- **FPAS / CAP XML、Natural Earth**：见本文档阶段 3/4 的裁决。
- **雷达**：breezy 自己还在可行性研究阶段。
- **「移除崩溃上报」那部分隐私姿态**：breezy 无 tracker 是因为它只做 FOSS 分发；本仓库 fdroid flavor 本来就没有 Bugly，pub flavor 留着有用。

### 一条该抄进 README 的提醒

`docs/SOURCES.md` 明写：**7 天以外的预报都不可靠，别按天数选源。** 而本仓库的源对照表把「16 天 / 384 时」当卖点。

### 建议动手顺序

**B**（一个端点换回一整套已存在的 UI + 给 Open-Meteo 补上 AQI）→ **A**（`CompositeBlock` 从常量改成偏好）→ D → C。E/F/G/J 任何时候顺手都能做。

