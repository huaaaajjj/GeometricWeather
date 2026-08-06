---
name: crash-triage
description: 抓 Android 真机崩溃并定位根因。adb logcat 抓栈、release 包用 R8 mapping 反混淆、对照本仓库已知的四类崩溃根因。当用户说「崩了」「闪退」「查一下 logcat」「真机报错」时使用。
---

# 崩溃分诊

## 一、抓栈

```bash
adb devices                      # 先确认设备在
adb logcat -c                    # 清空，避免翻到上次的旧栈
# —— 在手机上复现 ——
adb logcat -b crash -d           # 只抓 crash buffer，最干净
```

抓不到（进程被杀 / ANR / native）就退到全量按包名过滤：

```bash
adb logcat -d | grep -A40 -i "wangdaye.com.geometricweather"
adb logcat -d -b main -b crash -v time | tail -200
```

想边复现边看：`adb logcat -b crash` 不带 `-d`，用 Monitor 工具跟。

## 二、release 包反混淆

debug 包不混淆，栈直接可读。**release 包必须反混淆**，否则全是 `a.b.c`。

mapping 文件（跟 APK 一一对应，重新构建就变，别拿旧的对新包）：

```
app/build/outputs/mapping/pubRelease/mapping.txt
```

没有 retrace 工具，直接 grep（文件 200 MB+，但 grep 够快）。格式是 `原名 -> 混淆名:`，所以按**混淆名**反查：

```bash
grep -n " -> a\.b\.c:" app/build/outputs/mapping/pubRelease/mapping.txt   # 类
grep -n "^wangdaye.*-> a\.b\.c:" app/build/outputs/mapping/pubRelease/mapping.txt
```

行号→源码行：方法项格式是 `混淆起:混淆止:返回值 方法签名:原始起:原始止 -> 混淆方法名`，例如

```
    7:16:...Location convert(...):56:56 -> a
```

栈里报的行号落在**前面**的 `7:16` 区间内，**尾部**的 `56:56` 才是原始源码行。类名下一行的 `# {"id":"sourceFile","fileName":"…"}` 给出原始文件名。

## 三、先对照这四类（本仓库历史崩溃的绝大多数）

| 症状 | 根因 | 查哪 |
|---|---|---|
| `IllegalStateException: Cannot access database on the main thread` | Room 主线程访问 | 找那次 DB 调用，包进 `AsyncHelper.runOnIO` |
| `NullPointerException` / `@NonNull` 断言炸在 `Weather`/`Current`/`Daily` 构造 | 转换器没兜底，provider 字段为 null | `weather/converters/*ResultConverter.java` 逐字段兜底 |
| `IllegalStateException: Cannot invoke setValue on a background thread` | 回调在 IO 线程直接 `LiveData.setValue()` | 改走 `AsyncHelper.delayRunOnUI` 回主线程 |
| `IndexOutOfBoundsException` / 首装就崩 | 空 list 没判空（如空 `validList`） | 加边界判断 |

其它见过的：`ClassCastException BinderProxy`（MIUI Activity recreate）、`Surface has already been released`（动态壁纸绘制没判 `isValid()`）。

## 四、定位到根因，不是症状

改之前 **grep 一遍要改的那个函数的所有调用方**。同一个坑通常在多个 caller 上都存在 —— 在共享函数里加一道守卫，比在每个 caller 上各补一次的 diff 更小，也不会漏掉别的路径。

## 五、验证

改完 `./gradlew assemblePubDebug` → 装真机 → 复现原路径 → `adb logcat -b crash -d` 应为空。
按 `CLAUDE.md` 在 `AI_CONTEXT.md` 追加一行（中文），写清**根因**而不只是「修了崩溃」。
