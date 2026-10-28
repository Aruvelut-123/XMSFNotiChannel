# 你这运营消息根本就全都是广告吧

[![Xposed](https://img.shields.io/badge/-Xposed-green?style=flat&logo=Android&logoColor=white)](#)
[![GitHub](https://img.shields.io/github/license/Aruvelut-123/XMSFNotiChannel)](https://github.com/Aruvelut-123/XMSFNotiChannel/blob/master/LICENSE)

一键管理所有应用的**运营消息**通知通道，仅支持 MIUI / HyperOS。

## 测试环境

> 小米（HyperOS 4.0 / Android 17 / SDK 37）
> LSPosed 2.2.1

## 使用方法

1. 在 Xposed 管理器（如 LSPosed）中激活模块
2. 作用域勾选 `系统框架(android)`
3. 重启手机（现代 API 下 hook 在 system_server 启动时装载，必须重启）

## 实现原理

模块在 `system_server` 中 hook 了 `NotificationManagerService#isCallerSystemOrPhone()`：

- 当 Binder 调用者为本模块时返回 `true`，其余情况原样透传
- 应用侧再通过 `INotificationManager` 读取 / 修改各应用的通知通道重要性

Android 17 上系统的所有相关校验都收敛到这一个方法，因此单点 hook 即可覆盖
`getNotificationChannelsForPackage` 与 `updateNotificationChannelForPackage`。

## 版本适配说明

### 1.5.0 — 迁移到 YukiHookAPI 1.5.x

| 变更 | 原因 |
| --- | --- |
| hook 框架改为 **yukihook-runtime-libxposed 1.5.0** | 1.5.x 是全新架构，`runtime-libxposed` 提供基于 libxposed API 的运行时，`java_init.list` / `module.prop` / `scope.list` 由 KSP 生成 |
| 额外显式声明 **yukihook-core 1.5.0** | `runtime-libxposed` 的 API 变体只暴露 `kotlin-stdlib`，core 走 runtime 作用域，不加的话编译期找不到 `YukiHookXposedModule` |
| 入口改为 `@YukiHookLibXposedEntry(scope = ["system"])` | libxposed 的系统框架作用域要用虚拟包名 `system`，只写 `android` 时 LSPosed 不会把模块装进 system 进程 |
| 用 `LibXposed102Entry` 手写入口 | KSP 编译器目前没有支持 Kotlin 2.4 的版本，手写等价入口并手工维护 `META-INF/xposed` 三件套 |
| 改用外部 **Kotlin 2.4.21** 插件 | YukiHook 1.5.0 由 Kotlin 2.4 编译，AGP 9 内置的 Kotlin 2.2 读不了它的 metadata |
| 新增 GitHub Actions 自动构建 | push / PR / 手动触发时构建 debug APK 并上传 artifact |

### 1.3.0 — Android 17 (SDK 37) 适配

| 变更 | 原因 |
| --- | --- |
| YukiHookAPI → **libxposed API 102** | 现代 Xposed API，入口改为 `META-INF/xposed/java_init.list`；本模块不再被自身 hook |
| `compileSdk` / `targetSdk` 33 → **37**，AGP 7.4.1 → **9.2.1**，Gradle 7.5 → **9.5.1** | 匹配 SDK 37 工具链 |
| 引入 **HiddenApiBypass** | `ParceledListSlice#getList()` 在 Android 17 被标为 `greylist-max-r`，`targetSdk >= 30` 时直接拦截，导致读取通道直接抛异常 |
| 改以 **通道粒度** (`getNotificationChannelsForPackage`) 为主 | Android 17 的 `getNotificationChannelGroupsForPackage` 引入 bundle channel 过滤，分组接口会漏通道 |
| 通道标识由 **名称** 改为 **通道 ID** | 通道名可能被本地化 / 重复，用名称定位在 Android 17 上会误改 |
| 激活状态改由 **XposedService + RemotePreferences** 判定 | libxposed 不再提供 `YukiHookAPI.Status` 与 `dataChannel` |
| 增加 hidden API 失败兜底 | 反射读取 `mList` 字段作为 `getList()` 的后备路径 |
| 区分「未激活 / 未勾选作用域 / 需重启 / 就绪」四种状态 | 便于定位问题，不再一律提示「未激活」 |

### 构建注意

`gradle.properties` 中默认关闭了 `android.enableResourceOptimizations`。
build-tools 的 aapt2 在 `optimize` 子命令上的参数与 AGP 9 不兼容，开启会导致
release 产物缺失 `AndroidManifest.xml` 与 `resources.arsc`。

## 无法使用

请先检查模块是否正常激活，并且作用域是否勾选。如果排查后仍有错误，请提交 Issue，并附上 LSPosed 的日志。

酷安[@迷璐](http://www.coolapk.com/u/1189245)

## 捐赠支持

点个 **Star** 也是对我的支持。

## 致谢

- 原作者 / 维护者：[迷璐](http://www.coolapk.com/u/1189245)（[酷安](http://www.coolapk.com/u/1189245)）
- Android 17 适配与 libxposed 重构：[Baymaxawa](https://github.com/Aruvelut-123)
- UI 界面改自 [MIUI 原生通知图标](https://github.com/fankes/MIUINativeNotifyIcon)
- 获取应用列表方式改自 [Hide My Applist](https://github.com/Dr-TSNG/Hide-My-Applist)
- 模块使用 [libxposed API](https://github.com/libxposed/api) 构建
