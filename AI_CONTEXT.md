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

## 待完成

- [ ] 升级 compileSdk 34 → 35
- [ ] 升级 minSdk 21 → 24
- [ ] GreenDAO → Room
- [ ] 升级 Gradle 8.x (需先完成 GreenDAO → Room)
- [ ] 升级 AGP 8.x
- [ ] 升级 Kotlin 1.9+
- [ ] RxJava → Coroutines
- [ ] Java → Kotlin 逐步迁移
