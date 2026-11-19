# Native build of :engine:whisper (ADR-24, ADR-32, LLD Section 1.3).
#
# whisper.cpp (with its bundled ggml) is downloaded as a tagged release archive, verified against a
# pinned SHA-256 and linked statically into one JNI library, libnotesmaker_whisper.so.
# The version and checksum come from gradle/libs.versions.toml through the Gradle build.
cmake_minimum_required(VERSION 3.22.1)

# A debug whisper.cpp is too slow to test with, so native code is optimized in every app build type
# (LLD Section 1.3). Pass -DNOTESMAKER_NATIVE_DEBUG=ON to step through native code.
option(NOTESMAKER_NATIVE_DEBUG "Build native code without optimization" OFF)
if(NOT NOTESMAKER_NATIVE_DEBUG)
    set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
endif()

project(notesmaker_whisper LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(NOT NOTESMAKER_WHISPER_VERSION OR NOT NOTESMAKER_WHISPER_SHA256)
    message(FATAL_ERROR "NOTESMAKER_WHISPER_VERSION and NOTESMAKER_WHISPER_SHA256 must be passed by the Gradle build (gradle/libs.versions.toml)")
endif()
set(NOTESMAKER_WHISPER_ARCHIVE_URL "https://github.com/ggml-org/whisper.cpp/archive/refs/tags/v${NOTESMAKER_WHISPER_VERSION}.tar.gz"
    CACHE STRING "whisper.cpp release archive; override to use a mirror, the checksum still applies")

# Only the library: no examples, tests, server, curl or SDL. Static so everything links into one .so.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
# We build a release tag, so whisper_version() reports "1.9.5", not "1.9.5-dev".
set(WHISPER_BUILD_IS_DEV OFF CACHE BOOL "" FORCE)
# ggml's own thread pool instead of OpenMP (no libomp to ship); no -march=native when cross-compiling;
# CPU backend linked in statically rather than loaded as a plug-in.
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)

include(FetchContent)
# Extracted files get the extraction time, so a changed archive always rebuilds (CMake 3.24+ policy).
if(POLICY CMP0135)
    cmake_policy(SET CMP0135 NEW)
endif()
FetchContent_Declare(whisper
    URL "${NOTESMAKER_WHISPER_ARCHIVE_URL}"
    URL_HASH SHA256=${NOTESMAKER_WHISPER_SHA256}
)
FetchContent_MakeAvailable(whisper)

add_library(notesmaker_whisper SHARED jni_bridge.cpp)
target_link_libraries(notesmaker_whisper PRIVATE whisper)
target_compile_options(notesmaker_whisper PRIVATE -Wall -Wextra -fvisibility=hidden)

if(ANDROID)
    find_library(LOG_LIB log)
    target_link_libraries(notesmaker_whisper PRIVATE ${LOG_LIB})
    # 16 KB page alignment, required for apps targeting Android 15+ on Google Play. NDK r28+ does this
    # by default; stating it keeps the requirement visible if the NDK is ever changed.
    target_link_options(notesmaker_whisper PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,--gc-sections")
else()
    # Host build, for compiling the bridge on a desktop JDK without the Android toolchain.
    find_package(JNI REQUIRED)
    target_include_directories(notesmaker_whisper PRIVATE ${JNI_INCLUDE_DIRS})
endif()
