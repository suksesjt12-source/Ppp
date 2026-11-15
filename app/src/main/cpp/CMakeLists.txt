cmake_minimum_required(VERSION 3.22.1)
project(storm_runtime_bridge LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(
    storm_runtime_bridge
    SHARED
    runtime_bridge.cpp
    process_bridge.cpp
    input_bridge.cpp
)

add_library(
    storm_vulkan_wrapper
    SHARED
    vulkan_wrapper.cpp
)

add_library(
    storm_audio_bridge
    SHARED
    audio_bridge.cpp
)

find_library(log-lib log)
find_library(android-lib android)
find_library(opensles-lib OpenSLES)

foreach(tgt storm_runtime_bridge storm_vulkan_wrapper storm_audio_bridge)
    target_compile_options(
        ${tgt}
        PRIVATE
        -Wall
        -Wextra
        -Werror=return-type
        -fvisibility=hidden
    )
endforeach()

target_link_libraries(storm_runtime_bridge ${log-lib} ${android-lib})
target_link_libraries(storm_vulkan_wrapper dl ${log-lib})
target_link_libraries(storm_audio_bridge ${opensles-lib} ${log-lib})
