# 你这运营消息根本全都是广告吧

[![Xposed](https://img.shields.io/badge/-Xposed-green?style=flat&logo=Android&logoColor=white)](#)
[![License](https://img.shields.io/github/license/Aruvelut-123/XMSFNotiChannel)](https://github.com/Aruvelut-123/XMSFNotiChannel/blob/master/LICENSE)

一键管理所有应用的**运营消息 / 营销推广 / 活动推荐**类通知通道，仅支持 MIUI / HyperOS。

## 测试环境

已在本人的 REDMI K80 Pro（HyperOS 4.0.0.10 / Android 17）+ LSPosed 上实测通过。

## 使用方法

1. 在 LSPosed 中激活模块，作用域勾选「系统框架」
2. 重启手机
3. 授予模块「查询所有应用」权限（`QUERY_ALL_PACKAGES`）

第 3 步别漏。Android 11+ 应用默认只能看到很少的包，未授权时扫描结果几乎为空。

## 功能

- **首页**：模块状态、通道统计、设备信息概览
- **功能**：按应用分组列出所有匹配到的通知通道，单条可开关；右上角菜单一键全开 / 全关
- **应用详情**：逐条查看与切换某个应用的全部通道
- **设置**：通道规则、主题模式、悬浮底栏、液态玻璃、模糊、语言、隐藏桌面图标、配置导入导出

## 无法使用？

先确认模块已激活、作用域已勾选、已授予「查询所有应用」权限；仍不行请附 LSPosed 日志提 Issue。

## 致谢

- 原作者 / 维护者：[迷璐](http://www.coolapk.com/u/1189245)（酷安）
- Android 17 适配与重构：[Baymaxawa](https://github.com/Aruvelut-123)
- 工程地基 / UI 框架：[MiuixGuiTemplate](https://github.com/Ianzb/MiuixGuiTemplate)
- 组件库：[Miuix](https://github.com/miuix-kotlin-multiplatform/miuix)
- 液态玻璃底栏移植自 [SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)
- 界面改自 [MIUI 原生通知图标](https://github.com/fankes/MIUINativeNotifyIcon)
- 应用列表获取方式改自 [Hide My Applist](https://github.com/Dr-TSNG/Hide-My-Applist)

## 许可

[AGPL-3.0](LICENSE)
