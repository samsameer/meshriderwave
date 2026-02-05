# Mesh Rider Wave - PTT Audio Module
# Following developer.android Oboe guidelines
cmake_minimum_required(VERSION 3.22.1)

project("meshriderptt" CXX)

# Set C++ standard following Kotlin/Android best practices
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Use shared STL for Oboe compatibility
# Per developer.android.com/studio/projects/gradle-external-native-builds
set(CMAKE_SHARED_LIBS YES)

# Oboe library - per developer.android.com/games/sdk/oboe
# Using Prefab from Google Maven (configured in build.gradle.kts)
find_package(oboe REQUIRED CONFIG)

# Opus codec - per 3GPP TS 26.179 MCPTT mandatory codec
# Added Feb 2026 for 10-40x bandwidth reduction (256kbps → 6-24kbps)
# Using libopus from conan or prebuilt library
include(FetchContent)

# Try to find system Opus first, otherwise fetch
find_package(Opus QUIET)

if(NOT Opus_FOUND)
    # Fetch libopus from GitHub (official repository)
    FetchContent_Declare(
        opus
        GIT_REPOSITORY https://gitlab.xiph.org/xiph/opus.git
        GIT_TAG        v1.5.2  # Latest stable as of Feb 2026
        GIT_SHALLOW    TRUE
    )
    FetchContent_MakeAvailable(opus)
else()
    message(STATUS "Found system Opus: ${Opus_DIR}")
endif()

# PTT Audio Library
add_library(meshriderptt SHARED
    ptt/AudioEngine.cpp
    ptt/AudioCapture.cpp
    ptt/AudioPlayback.cpp
    ptt/JniBridge.cpp
    ptt/RtpPacketizer.cpp
    ptt/OpusCodec.cpp  # NEW: Opus codec wrapper (Feb 2026)
)

target_include_directories(meshriderptt PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/ptt
)

target_link_libraries(meshriderptt
    PUBLIC
    oboe::oboe
    opus::opus  # NEW: Opus codec linking (Feb 2026)
    android
    log
)

# Enable RTTI and exceptions for C++20
target_compile_features(meshriderptt PRIVATE cxx_std_20)

# Compiler flags for Samsung S24+ optimization
target_compile_options(meshriderptt PRIVATE
    -fvisibility=hidden
    -ffast-math
    -fomit-frame-pointer
    -O3
)
