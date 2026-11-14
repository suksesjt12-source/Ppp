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

find_library(log-lib log)
find_library(android-lib android)

target_compile_options(
    storm_runtime_bridge
    PRIVATE
    -Wall
    -Wextra
    -Werror=return-type
    -fvisibility=hidden
)

target_link_libraries(
    storm_runtime_bridge
    ${log-lib}
    ${android-lib}
)
