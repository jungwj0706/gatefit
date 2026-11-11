# Pleos SDK는 IPC 바인딩과 리스너 콜백을 사용하므로 난독화 대상에서 제외
-keep class ai.pleos.playground.** { *; }
-dontwarn ai.pleos.playground.**

# kotlinx.serialization 직렬화기 유지
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.gatefit.** {
    *** Companion;
}
-keepclasseswithmembers class com.gatefit.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn android.car.**
