#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <cstdint>

extern "C" {

__attribute__((visibility("default")))
int storm_audio_probe_opensles(void) {
    SLObjectItf engineObject = nullptr;
    SLresult res = slCreateEngine(&engineObject, 0, nullptr, 0, nullptr, nullptr);
    if (res == SL_RESULT_SUCCESS && engineObject != nullptr) {
        (*engineObject)->Destroy(engineObject);
        return 1;
    }
    return 0;
}

__attribute__((visibility("default")))
uint32_t storm_audio_bridge_version(void) {
    return 0x00010000u;
}

} // extern "C"
