# 通知哨兵 · NotiSentry

[![libxposed](https://img.shields.io/badge/libxposed-API%20102-0a7ea4?style=flat-square)](https://github.com/libxposed/api)
[![Android](https://img.shields.io/badge/Android-13%2B-3ddc84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7f52ff?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285f4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Miuix](https://img.shields.io/badge/Miuix-0.9.4-ff6900?style=flat-square)](https://github.com/miuix-kotlin-multiplatform/miuix)
[![License](https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square)](LICENSE)

一键管理所有应用的**运营消息 / 营销推广 / 活动推荐**类通知通道。仅支持 MIUI / HyperOS。

## 使用

1. 在 LSPosed 中启用模块，作用域勾选「系统框架」
2. 重启手机
3. 授予「查询所有应用」权限（`QUERY_ALL_PACKAGES`）

第 3 步别漏——Android 11+ 默认只能看到很少的包，未授权时扫描结果几乎为空。

## 功能

- **概览**：模块状态、通道统计、设备信息
- **功能**：按应用分组列出匹配到的通道，支持搜索与单条开关，右上角菜单一键全开 / 全关
- **应用详情**：逐条切换某个应用的全部通道
- **通道导出**：列出全部已安装应用，导出其完整通知通道表为 JSON，便于分析
- **设置**：自定义规则、主题、悬浮底栏、液态玻璃、模糊、语言、配置导入导出

## 环境

已在 REDMI K80 Pro（HyperOS 4.0.0.10 / Android 17）+ LSPosed 实测通过。

## 排错

先确认模块已激活、作用域已勾选、已授予「查询所有应用」权限。仍不行请附 LSPosed 日志提 Issue。

## 致谢

- 原作者：[迷璐](http://www.coolapk.com/u/1189245)（酷安）
- Android 17 适配与重构：[Baymaxawa](https://github.com/Aruvelut-123)
- 工程地基：[MiuixGuiTemplate](https://github.com/Ianzb/MiuixGuiTemplate)
- 组件库：[Miuix](https://github.com/miuix-kotlin-multiplatform/miuix)
- 液态玻璃底栏移植自 [SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)
- 界面参考 [MIUI 原生通知图标](https://github.com/fankes/MIUINativeNotifyIcon)
- 应用列表获取参考 [Hide My Applist](https://github.com/Dr-TSNG/Hide-My-Applist)

## 许可

[AGPL-3.0](LICENSE)
