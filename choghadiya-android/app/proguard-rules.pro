# No reflection or serialization is used; default Android rules are sufficient.
# Strip debug/verbose logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
