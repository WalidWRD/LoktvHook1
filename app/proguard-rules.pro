# Keep the Xposed entry point and all hook classes (accessed via reflection).
-keep class com.loktv.hookpro.HookEntry { *; }
-keep class com.loktv.hookpro.core.** { *; }
-keep class com.loktv.hookpro.ui.** { *; }
-dontwarn de.robv.android.xposed.**
