# 你这运营消息根本就全都是广告吧

[![Xposed](https://img.shields.io/badge/-Xposed-green?style=flat&logo=Android&logoColor=white)](#)
[![GitHub](https://img.shields.io/github/license/Aruvelut-123/XMSFNotiChannel)](https://github.com/Aruvelut-123/XMSFNotiChannel/blob/master/LICENSE)

一键管理所有应用的**营销 / 推广 / 运营消息**通知通道，仅支持 MIUI / HyperOS。

## 它到底做了什么

App 侧通过 Binder 直接调用 `INotificationManager`，
在 `system_server` 里把本模块自己的调用伪装成系统调用（hook
`NotificationManagerService` 的 `isCallerSystemOrPhone` 等校验方法），
从而可以读写**其它应用**的通知通道。

只改本模块自己发起的调用（`Binder.getCallingUid() == moduleUid`），
其它进程一律透传，把对系统的影响降到最低。

## 匹配规则

内置关键词表覆盖一整类营销通道，出自真机 774 个通道名的统计：

| 分类 | 关键词 |
| --- | --- |
| 运营推送 | 运营消息、运营活动 |
| 广告营销 | 营销、推广、广告、促销、特惠、优惠、红包、折扣、秒杀、补贴、返利、福利… |
| 活动 | 活动通知、平台活动、精选活动、大促、周年庆… |
| 推荐内容 | 推荐、精选、猜你喜欢、智能推送、资讯、热点… |
| 商城电商 | 商城、店铺、上新、新品、开售、商品 |
| 直播 | 直播提醒、直播动态、直播预告 |

同时带**排除词**，避免误关功能性通道：
`后台活动`、`模拟活动`、`安装进度`、`下载`、`版本更新`、
`安全检测`、`订单`、`物流`、`验证码`、`电量`、`播放器`、`通知栏` 等。

想自己控制范围：在关于页连点图标 5 次，可填入自定义正则（包含匹配，同时匹配通道名与通道 ID）。
留空即使用内置规则。

## 测试环境

> 红米 K80 Pro（HyperOS 4.0 / Android 17 / SDK 37）
> LSPosed 2.2.1

## 使用方法

1. 在 Xposed 管理器（如 LSPosed）中激活模块
2. 作用域勾选 `系统框架(system)`
3. 重启手机（hook 在 system_server 启动时装载，必须重启）
4. **给模块授予「查询所有应用」权限**（`QUERY_ALL_PACKAGES`）

第 4 步容易漏。Android 11+ 起应用默认只能看到很少的包，
未授权时扫描到的应用数几乎为零，表现就是「列表空的、什么都没找到」。

## 状态条说明

状态条不是靠读配置文件判断的，而是**真的跨进程读一次别的应用的通道**：

- 正常返回 → `模块已激活`
- 抛 `SecurityException` → `作用域已就绪，请重启手机让 hook 生效`

之所以不用配置文件：实测在 Android 17 / HyperOS 4.0 上
`prefs(...)` 会退化成 system_server 进程内的私有存储，App 侧读不到；
而 `/data/system/` 目录 App 无权进入，system_server 又写不进 App 私有目录。

## 无法使用

请先检查：

1. 模块是否正常激活、作用域是否勾选「系统框架」
2. 是否已授予「查询所有应用」权限
3. 安装后是否**等了一分钟再重启**（LSPosed 可能抢在数据库写入前读到旧路径）

排查后仍有问题，请提交 Issue 并附上 LSPosed 的日志。

## 捐赠支持

点个 **Star** 也是对我的支持。

## 致谢

- 原作者 / 维护者：[迷璐](http://www.coolapk.com/u/1189245)（[酷安](http://www.coolapk.com/u/1189245)）
- Android 17 适配与重构：[Baymaxawa](https://github.com/Aruvelut-123)
- UI 界面改自 [MIUI 原生通知图标](https://github.com/fankes/MIUINativeNotifyIcon)
- 获取应用列表方式改自 [Hide My Applist](https://github.com/Dr-TSNG/Hide-My-Applist)
- 模块使用 [YukiHookAPI](https://github.com/HighCapable/YukiHookAPI) 构建，底层基于 [libxposed API](https://github.com/libxposed/api)
- 状态查询使用 [libxposed service](https://github.com/libxposed/api)
- hidden API 豁免使用 [HiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass)
