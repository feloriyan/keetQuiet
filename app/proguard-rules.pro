# ProGuard rules for VoiceTranscriber

# Keep Room
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}

# Keep Hilt
-keep class dagger.hilt.android.internal.managers.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *

# Keep sherpa-onnx
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Keep RxFFmpeg
-keep class io.microshow.rxffmpeg.** { *; }


