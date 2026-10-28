# 你这运营消息根本就全都是广告吧

[![Xposed](https://img.shields.io/badge/-Xposed-green?style=flat&logo=Android&logoColor=white)](#)
[![GitHub](https://img.shields.io/github/license/Aruvelut-123/XMSFNotiChannel)](https://github.com/Aruvelut-123/XMSFNotiChannel/blob/master/LICENSE)

一键管理所有应用的**运营消息**通知通道，仅支持 MIUI / HyperOS。

## 测试环境

> 小米（HyperOS 4.0 / Android 17 / SDK 37）
> LSPosed 2.2.1

## 使用方法

1. 在 Xposed 管理器（如 LSPosed）中激活模块
2. 作用域勾选 `系统框架(system)`
3. 重启手机（现代 API 下 hook 在 system_server 启动时装载，必须重启）

## 实现原理

模块在 `system_server` 中拦截 `NotificationManagerService` 的一组校验方法，
**仅当 Binder 调用者为本模块时**才返回 `true`，其余情况原样透传：

| 方法 | 作用 |
| --- | --- |
| `isCallerSystemOrPhone()` | 核心闸门，Android 17 上所有相关校验最终都收敛到这里 |
| `isCallerSystem()` | 少数路径直接判断 |
| `isCallerSystemOrSystemUi()` | 同上 |
| `isCallerSystemOrSystemUiOrShell()` | 同上 |
| `assertCallerIsSystemOrSystemUiOrShell()` | `updateNotificationChannelForPackage` 的前置断言 |

应用侧再通过 `INotificationManager` 读取 / 修改各应用的通知通道重要性：

- `getNotificationChannelsForPackage` → `enforceSystemOrSystemUI()` → `isCallerSystemOrPhone()`
- `getNotificationChannelGroupsForPackage` → 同上
- `updateNotificationChannelForPackage` → `assertCallerIsSystemOrSystemUiOrShell()` → `isCallerSystemOrPhone()`

hook 成功后模块会把 `hook_ready=true` 写入 `module_status` 远程偏好，
App 读取该值把状态条显示为「已激活」。

## 版本适配说明

### 1.5.1 — hook 目标收敛与 CI 构建
| 变更 | 原因 |
| --- | --- |
| hook 目标由 1 个方法扩展到 5 个校验方法 | 覆盖 `isCallerSystem*` 系列与 `assertCallerIsSystemOrSystemUiOrShell`，避免个别路径漏校验 |
| 修复作用域判定 `SCOPE_SYSTEM` 由 `android` 改为 `system` | 模块声明的静态作用域是 `system`，常量写 `android` 会导致界面永远提示「未包含系统框架」 |
| `scope.list` 补回 `android` | `system` 为 system_server 虚拟包名，`android` 作兼容别名一并声明 |
| hook 成功后写入 `hook_ready` | 之前从未写过，界面只能停在「需重启」，永远不会变成已激活 |
| `staticScope` 改为 `false` | 与同机器上可正常装载的模块保持一致，由管理器按作用域注入 |
| release 不再混淆 | 开源软件，保持符号完整便于审计；同时避免 YukiHook 反射构造 hooker 失败 |
| 新增 release 构建 workflow | 打 `v*` tag 或手动触发时产出 release APK 并附到 GitHub Release |

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
