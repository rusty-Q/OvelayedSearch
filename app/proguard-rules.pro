# Keep application classes
-keep class com.overlayed.search.** { *; }

# Keep Service classes
-keep class * extends android.app.Service

# Keep Notification related classes
-keep class androidx.core.app.NotificationCompat** { *; }

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Remove debug logs
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}