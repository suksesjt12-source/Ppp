#include <dlfcn.h>
#include <cstdint>

extern "C" {

__attribute__((visibility("default")))
int storm_vk_probe_mali(void) {
    const char* candidates[] = {
        "/vendor/lib64/hw/vulkan.mt6789.so",
        "/vendor/lib64/hw/vulkan.mali.so",
        "/vendor/lib64/libvulkan.so",
        "/system/lib64/libvulkan.so"
    };
    for (const char* path : candidates) {
        void* handle = dlopen(path, RTLD_LAZY | RTLD_LOCAL);
        if (handle != nullptr) {
            dlclose(handle);
            return 1;
        }
    }
    return 0;
}

__attribute__((visibility("default")))
uint32_t storm_vk_wrapper_abi_version(void) {
    return 0x00010000u;
}

} // extern "C"
