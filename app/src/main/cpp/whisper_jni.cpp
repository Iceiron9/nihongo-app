// Kotlin 與 whisper.cpp 之間的橋樑
#include <jni.h>
#include <string>
#include <cstdio>
#include "whisper.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_ice_nihongo_whisper_WhisperLib_init(JNIEnv* env, jobject, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context* ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    return reinterpret_cast<jlong>(ctx);
}

// 輸入：16kHz 單聲道 float 音訊
// 輸出：UTF-8 位元組，每行一句，格式為「開始毫秒|結束毫秒|平均信心(0~1)|文字」
// （用位元組而不是字串回傳，因為 Whisper 偶爾會切斷多位元組字元，直接轉 jstring 會當機）
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_ice_nihongo_whisper_WhisperLib_transcribe(JNIEnv* env, jobject, jlong h,
        jfloatArray audio, jint threads, jint beam, jstring prompt) {
    auto* ctx = reinterpret_cast<whisper_context*>(h);
    if (ctx == nullptr) return env->NewByteArray(0);

    jfloat* data = env->GetFloatArrayElements(audio, nullptr);
    jsize n = env->GetArrayLength(audio);
    const char* pr = env->GetStringUTFChars(prompt, nullptr);

    whisper_full_params wp = whisper_full_default_params(
            beam > 1 ? WHISPER_SAMPLING_BEAM_SEARCH : WHISPER_SAMPLING_GREEDY);
    wp.language = "ja";
    wp.translate = false;
    wp.n_threads = threads;
    wp.beam_search.beam_size = beam;
    wp.initial_prompt = pr[0] ? pr : nullptr; // 前文當提示，讓 Whisper 依上下文判斷
    wp.print_progress = false;
    wp.print_realtime = false;
    wp.print_timestamps = false;
    wp.print_special = false;
    wp.suppress_blank = true;

    std::string out;
    if (whisper_full(ctx, wp, data, n) == 0) {
        const int segs = whisper_full_n_segments(ctx);
        char head[64];
        for (int i = 0; i < segs; i++) {
            const int nt = whisper_full_n_tokens(ctx, i);
            float sum = 0.0f;
            int cnt = 0;
            for (int j = 0; j < nt; j++) {
                const char* t = whisper_full_get_token_text(ctx, i, j);
                if (t != nullptr && t[0] == '[') continue; // 跳過 [_BEG_] 之類的特殊記號
                sum += whisper_full_get_token_p(ctx, i, j);
                cnt++;
            }
            const float conf = cnt > 0 ? sum / cnt : 0.0f;
            snprintf(head, sizeof(head), "%lld|%lld|%.3f|",
                     (long long) whisper_full_get_segment_t0(ctx, i) * 10,
                     (long long) whisper_full_get_segment_t1(ctx, i) * 10,
                     conf);
            out += head;
            std::string text = whisper_full_get_segment_text(ctx, i);
            for (char& c : text) if (c == '\n') c = ' ';
            out += text;
            out += "\n";
        }
    }

    env->ReleaseStringUTFChars(prompt, pr);
    env->ReleaseFloatArrayElements(audio, data, JNI_ABORT);
    jbyteArray result = env->NewByteArray((jsize) out.size());
    env->SetByteArrayRegion(result, 0, (jsize) out.size(), reinterpret_cast<const jbyte*>(out.data()));
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ice_nihongo_whisper_WhisperLib_free(JNIEnv*, jobject, jlong h) {
    if (h != 0) whisper_free(reinterpret_cast<whisper_context*>(h));
}
