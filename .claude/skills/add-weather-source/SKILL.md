---
name: add-weather-source
description: 新增或修复一个天气数据源（provider）。涉及 4 层流水线（json DTO / apis / converters / services）和 6 处注册点。当用户说「加个天气源」「接入 XX 天气」「新增 provider」「某某源不出数据」时使用。
---

# 新增天气源

4 层流水线 + 6 处注册点，**少一处就是「源出现在列表里但没数据」或「切过去直接崩」**。

## 先做：抓真实响应

写 DTO 之前先拿到真的 JSON，别照文档猜字段。文档和实际返回对不上是常态（APIHZ 的 `daily.air_quality` 文档说数组、实际是对象）。

```bash
curl -s "<endpoint>" | python -m json.tool | head -60
```

## 一、新增 4 个文件

| 层 | 路径 | 要点 |
|---|---|---|
| DTO | `weather/json/<src>/<Src>WeatherResult.java` | Gson；字段可能缺就用包装类型（`Integer` 不是 `int`）；结构不定的用 `JsonElement` 兜 |
| API | `weather/apis/<Src>Api.java` | Retrofit 接口，返回 **`Call<T>`**（本仓库已去 RxJava，不要写 `Observable`） |
| 转换器 | `weather/converters/<Src>ResultConverter.java` | DTO → `common/basic/models/weather/Weather`。**崩溃高发区**，见下方空安全 |
| 服务 | `weather/services/<Src>WeatherService.java` | `extends WeatherService`；实现 `requestWeather` + `requestLocation` |

服务里并行多路请求用 `CountDownLatch` + `AtomicReference`（照抄 `AccuWeatherService`）。
**取消用的 Call 必须是 `List<Call>` 字段，不能是单个字段** —— 单字段会被并发请求互相覆盖。

## 二、注册 6 处（漏一个就不生效）

1. **`common/basic/models/options/provider/WeatherSource.kt`**
   - 枚举加 `SRC("src", 0xFFxxxxxx.toInt(), "host.com")`
   - `getInstance()` 加 `contains("src")` 分支。⚠️ 分支**按顺序短路**，且现有 `caiyun` 分支写的是 `contains("caiyun") || contains("cn")` —— 新 id 里带 `cn` 会被彩云吃掉，取 id 时避开。
   - 匹配不中一律 fallback 到 `ACCU`。

2. **`weather/WeatherServiceSet.java`** —— 三处一起改：
   - 构造器加参数
   - `mWeatherServices` 数组加元素
   - `get()` 的 `switch` 加 `case`。⚠️ 用的是**手写下标**（`mWeatherServices[7]`），下标必须和数组里的位置对上，加在中间会全错位 —— 一律**追加到末尾**。

3. **`weather/di/ApiModule.java`** —— 一个独立 `@Provides`，每个源自己的 Retrofit 实例 + 自己的 `BuildConfig.XXX_BASE_URL`。

4. **`app/build.gradle`**
   - `it.buildConfigField "String", "XXX_BASE_URL", localProperties.getProperty("XXX_BASE_URL", "\"https://…/\"")`
   - 要 API Key 的话：`def EMBEDDED_XXX_KEY = decodeBase64("…")`（base64 是为了躲 secret scanning），再 `buildConfigField`。`local.properties` 优先级更高，本地可覆盖。
   - **不要在源码里硬编码 key。**

5. **`res/values/arrays.xml`** —— `weather_source_values` / `weather_sources` / `weather_source_voices` **三个数组按下标一一对应**，顺序是「展示顺序（好用的在前）」，**不是枚举顺序**。三个都要加，位置一致。

6. **`res/values-zh-rCN/arrays.xml`** —— 只有 `weather_sources` + `weather_source_voices` 两个（`weather_source_values` 存的是 id，不翻译，只在默认 `values/` 里）。位置同样要和默认 arrays 对齐。

> 漏了 locale 数组不会崩（`Utils.getName/getVoice` 有 `?: id` 兜底），但界面会显示原始 id。

可选：`settings/compose/WeatherSourceStatusScreen.kt` 加进「天气源可用性」实测页。

## 三、转换器必做的空安全

`Weather` 模型带 `@NonNull` 断言，**传 null 直接抛**。逐字段兜底：

- `Wind` / `AirQuality` / `Pollen` / `Astro` 宁可给空对象也不给 null（`Current`/`Daily` 构造器已有强制兜底，别绕过）
- `Base.cityId` **必须用 `location.getCityId()`**，不要用接口返回的地名 —— 用地名会导致缓存键随接口漂移，天气永远读不到缓存 + weather 表无界累积（3.4.13/3.4.14 的原坑）
- 中国源返回的坐标是 **GCJ-02，要转 WGS-84**
- 预警按行政区过滤（接口张冠李戴是真事，WeatherAPI 给天津坐标返回过北京延庆的预警）

## 四、其它硬约束

- **Room 禁止主线程访问** —— 一律 `AsyncHelper.runOnIO`
- **数据库 schema 锁定 v63，禁止改**
- 回调要回主线程再 `LiveData.setValue()`（`AsyncHelper.delayRunOnUI`），后台线程 setValue 会抛 `IllegalStateException`
- 网页抓取型的源（如 CMA）注意 WAF：默认 `okhttp/4.x` UA 会被 403，需要给该 API 单独加浏览器 `@Headers("User-Agent: …")`，**别动共享 OkHttp**
- R8：`proguard-rules.pro` 的 `weather.json.**` 通配已覆盖新包，DTO 不会被误删

## 五、验证

```bash
./gradlew assemblePubDebug
```
装真机 → 设置 → 数据提供商 → **天气源可用性** → 实测刷新，确认新源那行有天/时/温度。
然后按 `CLAUDE.md` 在 `AI_CONTEXT.md` 变更日志追加一行（中文），记下**接口的怪癖**（参数格式、覆盖范围、限流），下次接别的源能省一轮试错。
