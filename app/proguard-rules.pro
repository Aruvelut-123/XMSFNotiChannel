# libxposed: keep module entry classes and rewrite java_init.list when obfuscated.
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# The hook entry is loaded by the framework by name, so never strip it.
-keep class com.gswxxn.xmsfnotichannel.hook.** { *; }

# Yuki-free reflection targets: NotificationChannel / groups are passed through
# ParceledListSlice, keep their members intact.
-keep class android.app.NotificationChannel { *; }
-keep class android.app.NotificationChannelGroup { *; }