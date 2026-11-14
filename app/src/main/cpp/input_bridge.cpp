#include <jni.h>
#include <android/log.h>

#include <cerrno>
#include <cstdint>
#include <cstring>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

namespace {
constexpr const char* kTag = "StormInputBridge";

struct __attribute__((packed)) StormXInputPacket {
    uint32_t magic;        // 'STRM' = 0x5354524D
    uint32_t buttonsMask;  // XInput bitmask
    uint8_t leftTrigger;
    uint8_t rightTrigger;
    int16_t thumbLX;
    int16_t thumbLY;
    int16_t thumbRX;
    int16_t thumbRY;
    uint32_t sequence;
};

static_assert(sizeof(StormXInputPacket) == 22, "StormXInputPacket must remain 22 bytes");
} // namespace

extern "C" {

JNIEXPORT jint JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeOpenInputSocket(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jstring /* jSocketPath */
) {
    int fd = socket(AF_UNIX, SOCK_DGRAM | SOCK_NONBLOCK | SOCK_CLOEXEC, 0);
    if (fd < 0) {
        int err = errno;
        __android_log_print(ANDROID_LOG_WARN, kTag, "socket(AF_UNIX) failed: errno=%d", err);
        return -err;
    }
    return static_cast<jint>(fd);
}

JNIEXPORT jboolean JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeSendXInputState(
    JNIEnv* env,
    jobject /* thiz */,
    jint fd,
    jstring jSocketPath,
    jint buttonsMask,
    jint leftTrigger,
    jint rightTrigger,
    jshort thumbLX,
    jshort thumbLY,
    jshort thumbRX,
    jshort thumbRY,
    jint sequence
) {
    if (fd < 0 || jSocketPath == nullptr) return JNI_FALSE;

    const char* path = env->GetStringUTFChars(jSocketPath, nullptr);
    if (!path) return JNI_FALSE;

    struct sockaddr_un addr {};
    addr.sun_family = AF_UNIX;
    const size_t pathLen = std::strlen(path);
    if (pathLen >= sizeof(addr.sun_path)) {
        env->ReleaseStringUTFChars(jSocketPath, path);
        return JNI_FALSE;
    }
    std::memcpy(addr.sun_path, path, pathLen + 1);
    env->ReleaseStringUTFChars(jSocketPath, path);

    StormXInputPacket pkt {
        .magic = 0x5354524Du,
        .buttonsMask = static_cast<uint32_t>(buttonsMask),
        .leftTrigger = static_cast<uint8_t>(leftTrigger & 0xFF),
        .rightTrigger = static_cast<uint8_t>(rightTrigger & 0xFF),
        .thumbLX = static_cast<int16_t>(thumbLX),
        .thumbLY = static_cast<int16_t>(thumbLY),
        .thumbRX = static_cast<int16_t>(thumbRX),
        .thumbRY = static_cast<int16_t>(thumbRY),
        .sequence = static_cast<uint32_t>(sequence)
    };

    ssize_t sent = sendto(
        fd,
        &pkt,
        sizeof(pkt),
        MSG_DONTWAIT,
        reinterpret_cast<const struct sockaddr*>(&addr),
        sizeof(addr)
    );

    return (sent == static_cast<ssize_t>(sizeof(pkt))) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeCloseInputSocket(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint fd
) {
    if (fd >= 0) {
        close(fd);
    }
}

} // extern "C"
