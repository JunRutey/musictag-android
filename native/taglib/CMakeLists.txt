# Native tag engine: upstream TagLib (MPL-1.1) plus a thin JNI bridge.
#
# Built two ways from the same sources:
#   * Android: AGP's externalNativeBuild compiles this for the packaged ABIs.
#   * Host (CI): cmake -S native/taglib -B build/host -DCMAKE_BUILD_TYPE=Release
#     producing libmusictag_jni.so that the JVM unit tests load through
#     MUSICTAG_HOST_LIB. That is what lets tag read/write be tested without a device.
#
# TagLib is consumed as an unmodified upstream release and is pinned by tag.

cmake_minimum_required(VERSION 3.22)
project(musictag_jni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)

# TagLib as a static library: one self-contained .so per ABI, no extra runtime deps.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_BINDINGS OFF CACHE BOOL "" FORCE)
set(WITH_ZLIB OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    taglib
    GIT_REPOSITORY https://github.com/taglib/taglib.git
    GIT_TAG v2.0.2
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(taglib)

add_library(musictag_jni SHARED jni_bridge.cpp)
target_link_libraries(musictag_jni PRIVATE tag)

# TagLib's build-tree layout differs from the installed layout: headers live in
# <src>/taglib/*.h while the install interface uses include/taglib. Add both roots so
# `#include <taglib/xxx.h>` works against the fetched sources as well as a system copy.
target_include_directories(musictag_jni PRIVATE
    ${taglib_SOURCE_DIR}
    ${taglib_SOURCE_DIR}/taglib
)

if(ANDROID)
    target_link_libraries(musictag_jni PRIVATE log)
    target_compile_options(musictag_jni PRIVATE -fvisibility=hidden -fexceptions)
else()
    find_package(JNI REQUIRED)
    target_include_directories(musictag_jni PRIVATE ${JNI_INCLUDE_DIRS})
endif()

# Keep the JNI entry points visible; everything else stays hidden.
set_target_properties(musictag_jni PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)
