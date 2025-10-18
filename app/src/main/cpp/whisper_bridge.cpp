#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <memory>
#include <cstdint>
#include <cmath>
#include <thread>
#include <cstring>
#include <cstdio>
#include <algorithm>
#include "whisper.h"

#define LOG_TAG "WhisperBridge"

namespace {

struct WhisperHandle {
    std::unique_ptr<whisper_context, void (*)(whisper_context *)> context;
    int maxThreads;
    int defaultThreads;

    WhisperHandle(whisper_context *ctx, int maxThreads, int defaultThreads)
        : context(ctx, [](whisper_context *ptr) {
              if (ptr != nullptr) {
                  whisper_free(ptr);
              }
          }),
          maxThreads(maxThreads),
          defaultThreads(defaultThreads) {}
};

constexpr const char *toCString(JNIEnv *env, jstring value) {
    return value != nullptr ? env->GetStringUTFChars(value, nullptr) : nullptr;
}

void releaseCString(JNIEnv *env, jstring value, const char *chars) {
    if (value != nullptr && chars != nullptr) {
        env->ReleaseStringUTFChars(value, chars);
    }
}

std::string buildJsonError(const std::string &message) {
    return std::string("{\"error\":\"") + message + "\"}";
}

int resolveThreads(int requested, int maxAvailable) {
    if (requested <= 0) {
        return std::max(1, maxAvailable);
    }
    return std::max(1, std::min(requested, maxAvailable));
}

}  // namespace

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_translatorapp_localmodel_WhisperNativeBridge_nativeInit(
        JNIEnv *env,
        jobject /*thiz*/,
        jstring model_path,
        jint preferred_threads) {
    const char *modelPathChars = env->GetStringUTFChars(model_path, nullptr);
    if (modelPathChars == nullptr) {
        return 0;
    }
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    auto *ctx = whisper_init_from_file_with_params(modelPathChars, cparams);
    env->ReleaseStringUTFChars(model_path, modelPathChars);
    if (ctx == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to initialize whisper context");
        return 0;
    }
    int maxThreads = static_cast<int>(std::thread::hardware_concurrency());
    if (maxThreads <= 0) {
        maxThreads = 4;
    }
    const int defaultThreads = resolveThreads(preferred_threads, maxThreads);
    auto *handle = new WhisperHandle(ctx, maxThreads, defaultThreads);
    return reinterpret_cast<jlong>(handle);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_translatorapp_localmodel_WhisperNativeBridge_nativeRelease(
        JNIEnv *env,
        jobject /*thiz*/,
        jlong handle_ptr) {
    if (handle_ptr == 0) return;
    auto *handle = reinterpret_cast<WhisperHandle *>(handle_ptr);
    delete handle;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_translatorapp_localmodel_WhisperNativeBridge_nativeProcess(
        JNIEnv *env,
        jobject /*thiz*/,
        jlong handle_ptr,
        jbyteArray audio_data,
        jint sample_rate,
        jstring source_language,
        jstring target_language,
        jboolean enable_translation,
        jint thread_override) {
    if (handle_ptr == 0) {
        auto error = buildJsonError("context_not_initialized");
        return env->NewStringUTF(error.c_str());
    }
    auto *handle = reinterpret_cast<WhisperHandle *>(handle_ptr);
    const jsize audioSize = env->GetArrayLength(audio_data);
    if (audioSize <= 0) {
        auto error = buildJsonError("empty_audio");
        return env->NewStringUTF(error.c_str());
    }

    std::vector<uint8_t> raw(static_cast<size_t>(audioSize));
    env->GetByteArrayRegion(audio_data, 0, audioSize, reinterpret_cast<jbyte *>(raw.data()));

    std::vector<float> pcm;
    pcm.reserve(static_cast<size_t>(audioSize / 2));
    for (jsize i = 0; i + 1 < audioSize; i += 2) {
        int16_t sample = static_cast<int16_t>(raw[i] | (raw[i + 1] << 8));
        pcm.push_back(static_cast<float>(sample) / 32768.0f);
    }

    const char *sourceLangChars = toCString(env, source_language);
    const char *targetLangChars = toCString(env, target_language);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = enable_translation == JNI_TRUE;
    params.no_context = true;
    params.single_segment = false;
    params.audio_ctx = 0;
    const int threads = thread_override > 0
        ? resolveThreads(thread_override, handle->maxThreads)
        : handle->defaultThreads;
    params.n_threads = threads;

    int requestedLangId = -1;
    if (sourceLangChars != nullptr && std::strlen(sourceLangChars) > 0) {
        requestedLangId = whisper_lang_id(sourceLangChars);
        if (requestedLangId >= 0) {
            params.detect_language = false;
            params.language = sourceLangChars;
        } else {
            params.detect_language = true;
        }
    } else {
        params.detect_language = true;
    }

    int status = whisper_full(handle->context.get(), params, pcm.data(), static_cast<int>(pcm.size()));
    if (status != 0) {
        releaseCString(env, source_language, sourceLangChars);
        releaseCString(env, target_language, targetLangChars);
        auto error = buildJsonError("inference_failed");
        return env->NewStringUTF(error.c_str());
    }

    std::string transcript;
    const int segments = whisper_full_n_segments(handle->context.get());
    for (int i = 0; i < segments; ++i) {
        const char *text = whisper_full_get_segment_text(handle->context.get(), i);
        if (text != nullptr) {
            transcript += text;
        }
    }

    int detectedLangId = -1;
    if (params.detect_language) {
        std::vector<float> langProbs(whisper_lang_max_id() + 1);
        detectedLangId = whisper_lang_auto_detect(handle->context.get(), 0, params.n_threads, langProbs.data());
    } else if (requestedLangId >= 0) {
        detectedLangId = requestedLangId;
    }

    std::string detectedCode;
    if (detectedLangId >= 0) {
        detectedCode = whisper_lang_str(detectedLangId);
    }

    std::string translation;
    if (params.translate) {
        translation = transcript;
    } else {
        if (targetLangChars != nullptr && std::strlen(targetLangChars) > 0) {
            translation = transcript;
        } else {
            translation = transcript;
        }
    }

    releaseCString(env, source_language, sourceLangChars);
    releaseCString(env, target_language, targetLangChars);

    std::string json = "{\"transcript\":\"";
    auto appendEscaped = [](const std::string &input, std::string &out) {
        for (const char c : input) {
            switch (c) {
                case '\"': out += "\\\""; break;
                case '\\': out += "\\\\"; break;
                case '\b': out += "\\b"; break;
                case '\f': out += "\\f"; break;
                case '\n': out += "\\n"; break;
                case '\r': out += "\\r"; break;
                case '\t': out += "\\t"; break;
                default:
                    if (static_cast<unsigned char>(c) < 0x20) {
                        char buffer[7];
                        std::snprintf(buffer, sizeof(buffer), "\\u%04x", c);
                        out += buffer;
                    } else {
                        out += c;
                    }
                    break;
            }
        }
    };
    appendEscaped(transcript, json);
    json += "\",\"translation\":\"";
    appendEscaped(translation, json);
    json += "\",\"language\":\"";
    appendEscaped(detectedCode, json);
    json += "\"}";

    return env->NewStringUTF(json.c_str());
}
