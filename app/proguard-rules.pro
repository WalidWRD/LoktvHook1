# Keep the Xposed entry points and all hook classes (accessed via reflection).
-keep class com.nexhook.pro.MainHook { *; }
-keep class com.nexhook.pro.ModernHook { *; }
-keep class com.nexhook.pro.core.** { *; }
-keep class com.nexhook.pro.ui.** { *; }
-dontwarn de.robv.android.xposed.**
-dontwarn io.github.libxposed.**
