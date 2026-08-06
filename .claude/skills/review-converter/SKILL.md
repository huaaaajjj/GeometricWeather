---
name: review-converter
description: 审查天气数据转换器和线程安全 —— 空安全、缓存键、Room 主线程、LiveData 回调线程。改动 weather/converters/、weather/services/ 或任何碰 Room/LiveData 的代码后使用。当用户说「审一下」「检查转换器」「会不会崩」时使用。
---

# 转换器 & 线程安全体检

这两类占了本仓库历史崩溃的绝大多数。改完 `weather/converters/`、`weather/services/` 或任何碰 Room/LiveData 的代码，逐条过。

## 一、空安全（转换器）

`Weather` 模型带 `@NonNull` 断言，**构造时传 null 直接抛**，而 provider 字段随时可能缺。

- [ ] 每个从 DTO 取出的字段都有兜底，没有裸的 `result.a.b.c` 链式取值
- [ ] `Wind` / `AirQuality` / `Pollen` / `Astro`（sun & moon）宁可给**空对象**也不给 null
- [ ] `Current` / `Daily` 构造器里已有的强制兜底没被绕过
- [ ] DTO 用包装类型（`Integer`/`Double`）而不是基本类型 —— 缺字段时是 null 不是 0
- [ ] 结构不稳的字段用 `JsonElement` 兜（同一个 key 文档说数组、实际返回对象，是常态）
- [ ] list 取下标前判空、判长度

## 二、缓存键（踩过两次的坑）

- [ ] **`Base.cityId` 用 `location.getCityId()`**，绝不用接口返回的地名

用地名会同时造成两件事：`WeatherEntityGenerator` 按 `weather.getBase().getCityId()` 写、而 `readWeather`/`deleteWeather` 按 `location.getCityId()` 读删 → ① 该源的天气**永远读不到缓存**，每次冷启动重新拉；② weather 表按接口地名**无界累积**孤儿行。

## 三、预警

- [ ] 按行政区过滤 —— 接口张冠李戴是真事（WeatherAPI 对天津坐标返回过北京延庆的预警）
- [ ] 只在能拿到本地地名时才过滤，避免误删
- [ ] `deleteWeather` 清了**所有**子表（weather/daily/hourly/minutely/history/**alert**）—— 漏一张就每次刷新累积

## 四、坐标

- [ ] 中国源返回的是 **GCJ-02，必须转 WGS-84**

## 五、线程（服务/仓库/ViewModel）

- [ ] **任何 Room 访问都在 `AsyncHelper.runOnIO` 里**，主线程碰 DB 必崩
- [ ] 回调里的 `LiveData.setValue()` 已回到主线程（`AsyncHelper.delayRunOnUI`）—— 后台线程 setValue 抛 `IllegalStateException`
- [ ] 成功/失败**两条**回调路径都做了线程切换（历史上失败路径漏过）
- [ ] 服务里存 Call 用 **`List<Call>` 字段，不是单个字段** —— 单字段会被并发请求互相覆盖，取消时漏取消
- [ ] 并行多路请求用 `CountDownLatch` + `AtomicReference`

## 六、验证

```bash
./gradlew assemblePubDebug
```

装真机跑一遍改动路径，`adb logcat -b crash -d` 应为空。
多源改动可以走 设置 → 数据提供商 → **天气源可用性** → 实测刷新，一次把所有源跑通（注意真实消耗各源配额）。
