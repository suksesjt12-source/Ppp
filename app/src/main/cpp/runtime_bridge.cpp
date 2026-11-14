#include <jni.h>
#include <android/log.h>

#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dirent.h>
#include <fcntl.h>
#include <string>
#include <unistd.h>

namespace {
constexpr const char* kTag = "StormRuntimeBridge";

long parseMemInfoKb(const char* buffer, const char* key) {
    const char* found = std::strstr(buffer, key);
    if (!found) return -1L;
    found += std::strlen(key);
    while (*found == ' ' || *found == '\t') ++found;
    char* endPtr = nullptr;
    long value = std::strtol(found, &endPtr, 10);
    return (endPtr != found) ? value : -1L;
}
} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeGetBridgeVersion(
    JNIEnv* env,
    jobject /* thiz */
) {
    return env->NewStringUTF("storm_runtime_bridge/1.0.0-arm64-cpp20");
}

JNIEXPORT jintArray JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeVerifyElfBinary(
    JNIEnv* env,
    jobject /* thiz */,
    jstring jPath
) {
    // Returns [isValidElf (0|1), elfClass (0|1=32|2=64), e_machine, isExecutable (0|1)]
    jint result[4] = {0, 0, 0, 0};
    if (jPath != nullptr) {
        const char* path = env->GetStringUTFChars(jPath, nullptr);
        if (path != nullptr) {
            result[3] = (access(path, X_OK) == 0) ? 1 : 0;
            int fd = open(path, O_RDONLY | O_CLOEXEC);
            if (fd >= 0) {
                uint8_t header[20] = {0};
                ssize_t n = read(fd, header, sizeof(header));
                if (n >= 20 &&
                    header[0] == 0x7F &&
                    header[1] == 'E' &&
                    header[2] == 'L' &&
                    header[3] == 'F') {
                    result[0] = 1;
                    result[1] = static_cast<jint>(header[4]); // 1=ELF32, 2=ELF64
                    result[2] = static_cast<jint>(
                        static_cast<uint16_t>(header[18]) |
                        (static_cast<uint16_t>(header[19]) << 8)
                    );
                }
                close(fd);
            } else {
                __android_log_print(ANDROID_LOG_DEBUG, kTag,
                                    "open(%s) failed: errno=%d", path, errno);
            }
            env->ReleaseStringUTFChars(jPath, path);
        }
    }

    jintArray out = env->NewIntArray(4);
    if (out != nullptr) {
        env->SetIntArrayRegion(out, 0, 4, result);
    }
    return out;
}

JNIEXPORT jlongArray JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeReadProcMemoryStats(
    JNIEnv* env,
    jobject /* thiz */
) {
    jlong stats[3] = {0L, 0L, 0L};
    int fd = open("/proc/meminfo", O_RDONLY | O_CLOEXEC);
    if (fd >= 0) {
        char buf[4096];
        ssize_t n = read(fd, buf, sizeof(buf) - 1);
        if (n > 0) {
            buf[n] = '\0';
            long total = parseMemInfoKb(buf, "MemTotal:");
            long avail = parseMemInfoKb(buf, "MemAvailable:");
            long freeKb = parseMemInfoKb(buf, "MemFree:");
            if (total > 0) stats[0] = total;
            if (avail > 0) stats[1] = avail;
            if (freeKb > 0) stats[2] = freeKb;
        }
        close(fd);
    }
    jlongArray out = env->NewLongArray(3);
    if (out != nullptr) {
        env->SetLongArrayRegion(out, 0, 3, stats);
    }
    return out;
}

JNIEXPORT jfloat JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeReadThermalCelsius(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    float maxTempC = -1.0f;
    DIR* dir = opendir("/sys/class/thermal");
    if (!dir) return maxTempC;

    struct dirent* entry = nullptr;
    while ((entry = readdir(dir)) != nullptr) {
        if (std::strncmp(entry->d_name, "thermal_zone", 12) != 0) continue;
        std::string tempPath = std::string("/sys/class/thermal/") + entry->d_name + "/temp";
        int fd = open(tempPath.c_str(), O_RDONLY | O_CLOEXEC);
        if (fd < 0) continue;
        char buf[64];
        ssize_t n = read(fd, buf, sizeof(buf) - 1);
        close(fd);
        if (n > 0) {
            buf[n] = '\0';
            char* endPtr = nullptr;
            long raw = std::strtol(buf, &endPtr, 10);
            if (endPtr != buf && raw > 0) {
                float c = (raw > 1000L) ? (static_cast<float>(raw) / 1000.0f)
                                        : static_cast<float>(raw);
                if (c > 5.0f && c < 130.0f && c > maxTempC) {
                    maxTempC = c;
                }
            }
        }
    }
    closedir(dir);
    return maxTempC;
}

JNIEXPORT jint JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeReadCpuCount(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    long cpus = sysconf(_SC_NPROCESSORS_ONLN);
    return (cpus > 0) ? static_cast<jint>(cpus) : 1;
}

JNIEXPORT jboolean JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeCheckVulkanDriverNode(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    const char* candidates[] = {
        "/vendor/lib64/hw/vulkan.mt6789.so",
        "/vendor/lib64/hw/vulkan.mali.so",
        "/vendor/lib64/libvulkan.so",
        "/system/lib64/libvulkan.so"
    };
    for (const char* path : candidates) {
        if (access(path, R_OK) == 0) {
            return JNI_TRUE;
        }
    }
    return JNI_FALSE;
}

} // extern "C"
