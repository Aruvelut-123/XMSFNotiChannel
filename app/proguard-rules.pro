# 本项目为开源软件，release 不做混淆（minifyEnabled false），
# 因此这里只保留「即使将来开启混淆也必须保留」的最小规则集。

# xposed 元数据：入口类按名字被框架加载，且文件内容包含类名
-keep class com.gswxxn.xmsfnotichannel.hook.** { *; }
-adaptresourcefilecontents META-INF/xposed/java_init.list
-adaptresourcefilecontents META-INF/xposed/scope.list
-adaptresourcefilecontents META-INF/xposed/module.prop

# YukiHook 通过反射构造 hooker 并回调 lambda，泛型签名不能丢
-keep class com.highcapable.yukihookapi.** { *; }
-dontwarn com.highcapable.yukihookapi.**

# 反射目标：NotificationChannel / groups 经 ParceledListSlice 传递，成员不能动
-keep class android.app.NotificationChannel { *; }
-keep class android.app.NotificationChannelGroup { *; }

-dontwarn io.github.libxposed.annotation.**