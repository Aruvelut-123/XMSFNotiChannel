# 主 dex 保留规则（multiDexKeepProguard）
#
# 背景：LSPosed 启动时读 META-INF/xposed/java_init.list 里的类名，
# 用主 dex 的类加载器 Class.forName 加载入口类。入口类如果被 R8
# 排到 classes2.dex 之后，就会 ClassNotFoundException，模块被
# 静默跳过 —— 表现为「元数据正确、作用域正确，但永远不注入，且无日志」。

# YukiHook 手写入口（java_init.list 指向它）
-keep class com.gswxxn.xmsfnotichannel.hook.YukiHookEntry { *; }

# 它继承的 libxposed 基类，构造时必须能在同一个加载器里解析
-keep class com.highcapable.yukihookapi.runtime.libxposed.entry.LibXposed102Entry { *; }
-keep class com.highcapable.yukihookapi.runtime.libxposed.entry.base.LibXposedBaseEntry { *; }
-keep class io.github.libxposed.api.XposedModule { *; }
-keep class io.github.libxposed.api.XposedModuleInterface { *; }
-keep class io.github.libxposed.api.XposedModuleInterface$ModuleLoadedParam { *; }

# 用户实际 hook 逻辑的入口 object
-keep class com.gswxxn.xmsfnotichannel.hook.HookEntry { *; }
