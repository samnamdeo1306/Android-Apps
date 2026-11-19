# JNI: native code looks these up by name (engine/whisper/src/main/cpp/jni_bridge.cpp).
-keep class dev.notesmaker.engine.whisper.NativeSegment {
    <init>(long, long, java.lang.String);
}
-keepclasseswithmembernames,includedescriptorclasses class dev.notesmaker.engine.whisper.WhisperJni {
    native <methods>;
}
