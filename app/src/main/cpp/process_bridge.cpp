#include <jni.h>
#include <android/log.h>

#include <cerrno>
#include <csignal>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <string>
#include <sys/prctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <vector>

namespace {
constexpr const char* kTag = "StormProcessBridge";

std::vector<std::string> extractStringArray(JNIEnv* env, jobjectArray arr) {
    std::vector<std::string> out;
    if (arr == nullptr) return out;
    jsize len = env->GetArrayLength(arr);
    out.reserve(static_cast<size_t>(len));
    for (jsize i = 0; i < len; ++i) {
        auto jStr = static_cast<jstring>(env->GetObjectArrayElement(arr, i));
        if (jStr != nullptr) {
            const char* cStr = env->GetStringUTFChars(jStr, nullptr);
            if (cStr != nullptr) {
                out.emplace_back(cStr);
                env->ReleaseStringUTFChars(jStr, cStr);
            }
            env->DeleteLocalRef(jStr);
        }
    }
    return out;
}
} // namespace

extern "C" {

JNIEXPORT jint JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeSpawnProcess(
    JNIEnv* env,
    jobject /* thiz */,
    jstring jExecutable,
    jobjectArray jArgv,
    jobjectArray jEnvp,
    jstring jWorkingDir,
    jstring jLogFilePath
) {
    if (jExecutable == nullptr) return -EINVAL;

    const char* exeChars = env->GetStringUTFChars(jExecutable, nullptr);
    if (!exeChars) return -ENOMEM;
    std::string executable(exeChars);
    env->ReleaseStringUTFChars(jExecutable, exeChars);

    std::string workingDir;
    if (jWorkingDir != nullptr) {
        const char* wdChars = env->GetStringUTFChars(jWorkingDir, nullptr);
        if (wdChars) {
            workingDir = wdChars;
            env->ReleaseStringUTFChars(jWorkingDir, wdChars);
        }
    }

    std::string logFilePath;
    if (jLogFilePath != nullptr) {
        const char* logChars = env->GetStringUTFChars(jLogFilePath, nullptr);
        if (logChars) {
            logFilePath = logChars;
            env->ReleaseStringUTFChars(jLogFilePath, logChars);
        }
    }

    if (access(executable.c_str(), X_OK) != 0) {
        int err = errno;
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "Executable not runnable (%s): errno=%d",
                            executable.c_str(), err);
        return -err;
    }

    std::vector<std::string> args = extractStringArray(env, jArgv);
    std::vector<std::string> envs = extractStringArray(env, jEnvp);

    std::vector<char*> argvPtrs;
    argvPtrs.reserve(args.size() + 2);
    if (args.empty()) {
        argvPtrs.push_back(const_cast<char*>(executable.c_str()));
    } else {
        for (auto& s : args) {
            argvPtrs.push_back(const_cast<char*>(s.c_str()));
        }
    }
    argvPtrs.push_back(nullptr);

    std::vector<char*> envpPtrs;
    envpPtrs.reserve(envs.size() + 1);
    for (auto& s : envs) {
        envpPtrs.push_back(const_cast<char*>(s.c_str()));
    }
    envpPtrs.push_back(nullptr);

    pid_t pid = fork();
    if (pid < 0) {
        int err = errno;
        __android_log_print(ANDROID_LOG_ERROR, kTag, "fork() failed: errno=%d", err);
        return -err;
    }

    if (pid == 0) {
        // Child process: ensure cleanup when parent exits, chdir, redirect stdio, and execve
        prctl(PR_SET_PDEATHSIG, SIGTERM);
        setsid();

        if (!workingDir.empty()) {
            if (chdir(workingDir.c_str()) != 0) {
                _exit(126);
            }
        }

        int devNull = open("/dev/null", O_RDONLY | O_CLOEXEC);
        if (devNull >= 0) {
            dup2(devNull, STDIN_FILENO);
            if (devNull > STDERR_FILENO) close(devNull);
        }

        if (!logFilePath.empty()) {
            int logFd = open(logFilePath.c_str(), O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0644);
            if (logFd >= 0) {
                dup2(logFd, STDOUT_FILENO);
                dup2(logFd, STDERR_FILENO);
                if (logFd > STDERR_FILENO) close(logFd);
            }
        }

        execve(executable.c_str(), argvPtrs.data(), envpPtrs.data());
        _exit(127);
    }

    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "Spawned runtime process pid=%d exe=%s",
                        static_cast<int>(pid), executable.c_str());
    return static_cast<jint>(pid);
}

JNIEXPORT jintArray JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativePollProcessStatus(
    JNIEnv* env,
    jobject /* thiz */,
    jint jPid
) {
    // [stateCode (0=running, 1=exited, 2=signaled, -1=not_found/error), exitCodeOrSignal]
    jint outValues[2] = {-1, 0};
    if (jPid > 0) {
        int status = 0;
        pid_t w = waitpid(static_cast<pid_t>(jPid), &status, WNOHANG);
        if (w == 0) {
            outValues[0] = 0;
            outValues[1] = 0;
        } else if (w == static_cast<pid_t>(jPid)) {
            if (WIFEXITED(status)) {
                outValues[0] = 1;
                outValues[1] = static_cast<jint>(WEXITSTATUS(status));
            } else if (WIFSIGNALED(status)) {
                outValues[0] = 2;
                outValues[1] = static_cast<jint>(WTERMSIG(status));
            } else {
                outValues[0] = 1;
                outValues[1] = status;
            }
        } else {
            outValues[0] = -1;
            outValues[1] = errno;
        }
    }

    jintArray arr = env->NewIntArray(2);
    if (arr != nullptr) {
        env->SetIntArrayRegion(arr, 0, 2, outValues);
    }
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeTerminateProcess(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint jPid,
    jboolean forceKill
) {
    if (jPid <= 0) return JNI_FALSE;
    int sig = (forceKill == JNI_TRUE) ? SIGKILL : SIGTERM;
    int rc = kill(static_cast<pid_t>(jPid), sig);
    if (rc == 0) {
        int status = 0;
        waitpid(static_cast<pid_t>(jPid), &status, WNOHANG);
        return JNI_TRUE;
    }
    return (errno == ESRCH) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeReadProcessRssKb(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint jPid
) {
    if (jPid <= 0) return 0L;
    char path[64];
    std::snprintf(path, sizeof(path), "/proc/%d/status", static_cast<int>(jPid));
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return 0L;

    char buf[4096];
    ssize_t n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0) return 0L;
    buf[n] = '\0';

    const char* rss = std::strstr(buf, "VmRSS:");
    if (!rss) return 0L;
    rss += 6;
    while (*rss == ' ' || *rss == '\t') ++rss;
    char* endPtr = nullptr;
    long kb = std::strtol(rss, &endPtr, 10);
    return (endPtr != rss && kb > 0) ? static_cast<jlong>(kb) : 0L;
}

} // extern "C"
