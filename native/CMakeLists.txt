cmake_minimum_required(VERSION 3.22.1)
project(storm_native_workspace LANGUAGES CXX)

# Standalone host/NDK CMake entrypoint referencing the Android JNI bridge
add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../app/src/main/cpp ${CMAKE_CURRENT_BINARY_DIR}/storm_runtime_bridge)
