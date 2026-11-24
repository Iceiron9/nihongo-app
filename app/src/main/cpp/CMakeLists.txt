cmake_minimum_required(VERSION 3.22)
project(nihongo_whisper C CXX)

set(CMAKE_CXX_STANDARD 17)
# whisper 編成靜態庫後要塞進我們的 .so，所以必須是位置無關程式碼
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# whisper.cpp 由 GitHub Actions 下載到 whisper.cpp/ 資料夾
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

if (ANDROID_ABI STREQUAL "arm64-v8a")
    # ROG Phone 5（Snapdragon 888）支援 fp16 與 dotprod，速度快很多
    set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16+dotprod" CACHE STRING "" FORCE)
endif()

add_subdirectory(whisper.cpp)

add_library(nihongo_whisper SHARED whisper_jni.cpp)
target_link_libraries(nihongo_whisper PRIVATE whisper)

if (ANDROID)
    find_library(LOG_LIB log)
    target_link_libraries(nihongo_whisper PRIVATE ${LOG_LIB} android)
endif()
