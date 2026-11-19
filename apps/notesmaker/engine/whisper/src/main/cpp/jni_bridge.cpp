// JNI bridge between WhisperJni.kt and whisper.cpp (LLD Section 6.1).
//
// Deliberately thin: it converts types, runs whisper_full on one PCM window, and copies segments
// out. Windowing, resume, boundary handling, VAD and error mapping are Kotlin (testable on the JVM).

#include <jni.h>

#include <atomic>
#include <string>
#include <vector>

#include "whisper.h"

#if defined(__ANDROID__)
#include <android/log.h>
#define LOG_TAG "NotesMakerWhisper"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#else
#include <cstdio>
#define LOGW(...) (std::fprintf(stderr, __VA_ARGS__), std::fprintf(stderr, "\n"))
#endif

namespace {

// One per loaded model. The Kotlin side holds its address as a Long handle.
struct Ctx {
    whisper_context *whisper = nullptr;
    std::atomic<bool> abort{false};
};

Ctx *fromHandle(jlong handle) { return reinterpret_cast<Ctx *>(handle); }

// Called by whisper.cpp between decoder steps; returning true stops the current whisper_full call.
bool shouldAbort(void *userData) {
    return static_cast<Ctx *>(userData)->abort.load(std::memory_order_relaxed);
}

// RAII for strings borrowed from the JVM.
class JString {
public:
    JString(JNIEnv *env, jstring s) : env_(env), s_(s), chars_(s ? env->GetStringUTFChars(s, nullptr) : nullptr) {}
    ~JString() { if (chars_) env_->ReleaseStringUTFChars(s_, chars_); }
    JString(const JString &) = delete;
    JString &operator=(const JString &) = delete;
    const char *get() const { return chars_; }

private:
    JNIEnv *env_;
    jstring s_;
    const char *chars_;
};

void throwIllegalState(JNIEnv *env, const std::string &message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls != nullptr) env->ThrowNew(cls, message.c_str());
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_dev_notesmaker_engine_whisper_WhisperJni_initContext(JNIEnv *env, jobject, jstring modelPath, jboolean useGpu) {
    JString path(env, modelPath);
    if (path.get() == nullptr) return 0;

    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = useGpu == JNI_TRUE;

    whisper_context *whisper = whisper_init_from_file_with_params(path.get(), params);
    if (whisper == nullptr) {
        LOGW("whisper_init_from_file_with_params failed");
        return 0;
    }
    auto *ctx = new Ctx();
    ctx->whisper = whisper;
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_dev_notesmaker_engine_whisper_WhisperJni_freeContext(JNIEnv *, jobject, jlong handle) {
    Ctx *ctx = fromHandle(handle);
    if (ctx == nullptr) return;
    whisper_free(ctx->whisper);
    delete ctx;
}

JNIEXPORT void JNICALL
Java_dev_notesmaker_engine_whisper_WhisperJni_requestAbort(JNIEnv *, jobject, jlong handle) {
    if (Ctx *ctx = fromHandle(handle)) ctx->abort.store(true, std::memory_order_relaxed);
}

JNIEXPORT void JNICALL
Java_dev_notesmaker_engine_whisper_WhisperJni_clearAbort(JNIEnv *, jobject, jlong handle) {
    if (Ctx *ctx = fromHandle(handle)) ctx->abort.store(false, std::memory_order_relaxed);
}

JNIEXPORT jobjectArray JNICALL
Java_dev_notesmaker_engine_whisper_WhisperJni_transcribe(
        JNIEnv *env, jobject, jlong handle, jfloatArray samples, jstring language, jstring initialPrompt,
        jint threads, jboolean useVad, jstring vadModelPath) {
    Ctx *ctx = fromHandle(handle);
    if (ctx == nullptr || samples == nullptr) {
        throwIllegalState(env, "transcribe called without a context or samples");
        return nullptr;
    }

    JString lang(env, language);
    JString prompt(env, initialPrompt);
    JString vadPath(env, vadModelPath);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads > 0 ? threads : 1;
    params.language = lang.get() != nullptr ? lang.get() : "en";
    params.translate = false;
    // Each window is independent and resumable; context across windows comes from initial_prompt.
    params.no_context = true;
    params.single_segment = false;
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.initial_prompt = prompt.get();
    params.abort_callback = shouldAbort;
    params.abort_callback_user_data = ctx;
    params.vad = useVad == JNI_TRUE && vadPath.get() != nullptr;
    params.vad_model_path = vadPath.get();

    const jsize count = env->GetArrayLength(samples);
    std::vector<float> pcm(static_cast<size_t>(count));
    env->GetFloatArrayRegion(samples, 0, count, pcm.data());

    const int result = whisper_full(ctx->whisper, params, pcm.data(), static_cast<int>(pcm.size()));
    if (ctx->abort.load(std::memory_order_relaxed)) {
        return nullptr;  // aborted: the Kotlin side treats null as cancellation
    }
    if (result != 0) {
        throwIllegalState(env, "whisper_full failed with code " + std::to_string(result));
        return nullptr;
    }

    jclass segmentClass = env->FindClass("dev/notesmaker/engine/whisper/NativeSegment");
    if (segmentClass == nullptr) return nullptr;  // NoClassDefFoundError pending
    jmethodID constructor = env->GetMethodID(segmentClass, "<init>", "(JJLjava/lang/String;)V");
    if (constructor == nullptr) return nullptr;

    const int n = whisper_full_n_segments(ctx->whisper);
    jobjectArray out = env->NewObjectArray(n, segmentClass, nullptr);
    if (out == nullptr) return nullptr;
    for (int i = 0; i < n; ++i) {
        jstring text = env->NewStringUTF(whisper_full_get_segment_text(ctx->whisper, i));
        jobject segment = env->NewObject(segmentClass, constructor,
                                         static_cast<jlong>(whisper_full_get_segment_t0(ctx->whisper, i)),
                                         static_cast<jlong>(whisper_full_get_segment_t1(ctx->whisper, i)),
                                         text);
        env->SetObjectArrayElement(out, i, segment);
        env->DeleteLocalRef(segment);
        env->DeleteLocalRef(text);
    }
    return out;
}

JNIEXPORT jstring JNICALL
Java_dev_notesmaker_engine_whisper_WhisperJni_systemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(whisper_print_system_info());
}

JNIEXPORT jstring JNICALL
Java_dev_notesmaker_engine_whisper_WhisperJni_version(JNIEnv *env, jobject) {
    return env->NewStringUTF(whisper_version());
}

}  // extern "C"
