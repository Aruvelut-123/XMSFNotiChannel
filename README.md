# 你这运营消息根本就全都是广告吧

[![Xposed](https://img.shields.io/badge/-Xposed-green?style=flat&logo=Android&logoColor=white)](#)
[![GitHub](https://img.shields.io/github/license/Aruvelut-123/XMSFNotiChannel)](https://github.com/Aruvelut-123/XMSFNotiChannel/blob/master/LICENSE)

一键管理所有应用的**运营消息 / 营销推广 / 活动推荐**类通知通道，仅支持 MIUI / HyperOS。

## 使用方法

1. 在 Xposed 管理器（如 LSPosed）中激活模块
2. 作用域勾选 `系统框架(system)`
3. 重启手机
4. 授予模块「查询所有应用」权限（`QUERY_ALL_PACKAGES`）

第 4 步别漏。Android 11+ 应用默认只能看到很少的包，未授权时扫描结果几乎为空。

## 自定义范围

在关于页连点图标 5 次可填入自定义正则，同时匹配通道名与通道 ID。
留空则使用内置关键词表。

## 无法使用

请先检查模块是否正常激活、作用域是否勾选、是否已授予「查询所有应用」权限。
排查后仍有错误，请提交 Issue 并附上 LSPosed 的日志。

## 捐赠支持

点个 **Star** 也是对我的支持。

## 致谢

- 原作者 / 维护者：[迷璐](http://www.coolapk.com/u/1189245)（[酷安](http://www.coolapk.com/u/1189245)）
- Android 17 适配与重构：[Baymaxawa](https://github.com/Aruvelut-123)
- UI 界面改自 [MIUI 原生通知图标](https://github.com/fankes/MIUINativeNotifyIcon)
- 获取应用列表方式改自 [Hide My Applist](https://github.com/Dr-TSNG/Hide-My-Applist)
- 模块使用 [YukiHookAPI](https://github.com/HighCapable/YukiHookAPI) 构建，底层基于 [libxposed API](https://github.com/libxposed/api)