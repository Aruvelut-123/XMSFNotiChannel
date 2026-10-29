# 你这运营消息根本就全都是广告吧

[![Xposed](https://img.shields.io/badge/-Xposed-green?style=flat&logo=Android&logoColor=white)](#)
[![GitHub](https://img.shields.io/github/license/Aruvelut-123/XMSFNotiChannel)](https://github.com/Aruvelut-123/XMSFNotiChannel/blob/master/LICENSE)

一键管理所有应用的**运营消息 / 营销推广 / 活动推荐**类通知通道，仅支持 MIUI / HyperOS。

> 2.1.0 起整个工程以 [Ianzb/MiuixGuiTemplate](https://github.com/Ianzb/MiuixGuiTemplate) 为地基重建：
> UI 全部换成 Miuix（Compose）+ 液态玻璃悬浮底栏 + shader 背景，hook 侧抛弃 YukiHook，
> 直接使用 **libxposed API 102** 原生接口。包名改为 `com.baymaxawa.xmsfnotichannel`，
> 属于全新应用，**安装前请先卸载旧版**。

## 使用方法

1. 在 Xposed 管理器（如 LSPosed）中激活模块
2. 作用域勾选 `系统框架(system)`
3. 重启手机
4. 授予模块「查询所有应用」权限（`QUERY_ALL_PACKAGES`）

第 4 步别漏。Android 11+ 应用默认只能看到很少的包，未授权时扫描结果几乎为空。

## 功能

- **首页**：按应用分组列出所有匹配到的通知通道，每条可单独开关，应用级总开关可一次改全部
- **右上角菜单**：一键全部开启 / 全部关闭
- **应用详情**：点应用进入通道列表，逐条查看与切换
- **设置**：主题模式 / 悬浮底栏 / 液态玻璃 / 模糊 / 语言 / 隐藏桌面图标 / 配置导入导出
- **关于**：shader 动态背景，第三方许可清单

## 实现原理

模块自身以 `Binder.getCallingUid()` 的身份读写 `NotificationManagerService`，
在 system_server 侧拦截 `isCallerSystemOrPhone` / `isCallerSystem` /
`isCallerSystemOrSystemUi` / `isCallerSystemOrSystemUiOrShell` /
`assertCallerIsSystemOrSystemUiOrShell` 这五个校验方法，
**仅放行本模块自己的调用**，其余进程一律透传原逻辑，把对系统的影响降到最小。

`SystemServiceManager#startService` 被用作「等待目标服务实例化」的信号，
避免在 `onSystemServerStarting` 阶段 `Class.forName` 失败。

## 自定义范围

在设置页可填入自定义关键词 / 正则，同时匹配通道名与通道 ID。留空则使用内置关键词表。

## 签名

仓库内置 `xmsfnotichannel.keystore`（alias / 密码均为 `xmsfnotichannel`），
debug 与 release 共用同一份签名，因此任何一次 CI 产出的 APK 都能直接覆盖安装。

SHA256 指纹：

```
8F:3C:F2:0B:84:67:5D:6C:87:CB:C4:E4:CE:E3:A5:3E:B7:55:7B:58:E7:9A:0B:40:D1:FC:06:76:BE:32:41:97
```

> 这是公开的自签名证书，仅用于保证「同一作者的多份构建可互相覆盖」，
> 不构成任何身份保证。

## 无法使用

请先检查模块是否正常激活、作用域是否勾选、是否已授予「查询所有应用」权限。
排查后仍有错误，请提交 Issue 并附上 LSPosed 的日志。

## 捐赠支持

点个 **Star** 也是对我的支持。

## 致谢

- 原作者 / 维护者：[迷璐](http://www.coolapk.com/u/1189245)（[酷安](http://www.coolapk.com/u/1189245)）
- Android 17 适配与重构：[Baymaxawa](https://github.com/Aruvelut-123)
- 工程地基 / UI 框架：[MiuixGuiTemplate](https://github.com/Ianzb/MiuixGuiTemplate)
- Miuix 组件库：[Miuix](https://github.com/miuix-kotlin-multiplatform/miuix)
- 液态玻璃底栏移植自 [SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)
- UI 界面改自 [MIUI 原生通知图标](https://github.com/fankes/MIUINativeNotifyIcon)
- 获取应用列表方式改自 [Hide My Applist](https://github.com/Dr-TSNG/Hide-My-Applist)

## 许可

[AGPL-3.0](LICENSE)
