# Xposed API (仅编译时依赖，不打包)
-dontwarn de.robv.android.xposed.**
-keep class de.robv.android.xposed.** { *; }

# 保留模块入口类
-keep class com.synx.unlocker.SynxVipHook { *; }

# Kotlin
-keep class kotlin.** { *; }
-dontwarn kotlin.**
