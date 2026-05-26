-keep class com.mapbox.** { *; }
-keep class com.mapbox.maps.** { *; }
-keep class com.mapbox.navigation.** { *; }
-dontwarn com.mapbox.**

-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName *;
    @kotlinx.serialization.Serializable *;
}

-keep class androidx.car.app.** { *; }
-dontwarn androidx.car.app.**
