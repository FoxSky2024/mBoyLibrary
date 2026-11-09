cmake_minimum_required(VERSION 3.22.1)
project(gbalib LANGUAGES C)

# mGBA se descarga y compila como proyecto externo (su CMake asume ser el
# proyecto raiz), se instala en un prefijo local y luego se enlaza estatico.
include(ExternalProject)

set(MGBA_TAG "0.10.5" CACHE STRING "Tag de git de mGBA")
set(MGBA_PREFIX ${CMAKE_BINARY_DIR}/mgba-install)
set(MGBA_EP_ROOT ${CMAKE_BINARY_DIR}/mgba-ep)

ExternalProject_Add(mgba_ep
    GIT_REPOSITORY https://github.com/mgba-emu/mgba.git
    GIT_TAG ${MGBA_TAG}
    GIT_SHALLOW TRUE
    PREFIX ${MGBA_EP_ROOT}
    CMAKE_ARGS
        -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
        -DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}
        -DANDROID_ABI=${ANDROID_ABI}
        -DANDROID_PLATFORM=${ANDROID_PLATFORM}
        -DANDROID_NDK=${ANDROID_NDK}
        -DCMAKE_BUILD_TYPE=Release
        -DCMAKE_INSTALL_PREFIX=${MGBA_PREFIX}
        -DCMAKE_INSTALL_LIBDIR=lib
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON
        -DLIBMGBA_ONLY=ON
        -DBUILD_SHARED=OFF
        -DBUILD_STATIC=ON
        -DM_CORE_GBA=ON
        -DM_CORE_GB=ON
        -DBUILD_QT=OFF
        -DBUILD_SDL=OFF
        -DBUILD_LIBRETRO=OFF
        -DBUILD_GL=OFF
        -DBUILD_GLES2=OFF
        -DBUILD_GLES3=OFF
        -DBUILD_HEADLESS=OFF
        -DBUILD_PERF=OFF
        -DBUILD_TEST=OFF
        -DBUILD_SUITE=OFF
        -DBUILD_CINEMA=OFF
        -DBUILD_EXAMPLE=OFF
        -DBUILD_PYTHON=OFF
        -DUSE_ZLIB=OFF
        -DUSE_PNG=OFF
        -DUSE_LIBZIP=OFF
        -DUSE_MINIZIP=OFF
        -DUSE_LZMA=OFF
        -DUSE_7ZIP=OFF
        -DUSE_ELF=OFF
        -DUSE_EDITLINE=OFF
        -DUSE_EPOXY=OFF
        -DUSE_FFMPEG=OFF
        -DUSE_SQLITE3=OFF
        -DUSE_DISCORD_RPC=OFF
        -DUSE_JSON_C=OFF
        -DUSE_DEBUGGERS=OFF
        -DUSE_GDB_STUB=OFF
        -DENABLE_SCRIPTING=OFF
    BUILD_BYPRODUCTS ${MGBA_PREFIX}/lib/libmgba.a
)

set(MGBA_SRC ${MGBA_EP_ROOT}/src/mgba_ep)
set(MGBA_BIN ${MGBA_EP_ROOT}/src/mgba_ep-build)

add_library(gbalib SHARED native-lib.c)
add_dependencies(gbalib mgba_ep)

target_include_directories(gbalib PRIVATE
    ${MGBA_PREFIX}/include
    ${MGBA_SRC}/include
    ${MGBA_BIN}/include
)

target_compile_options(gbalib PRIVATE -O2 -Wall)

# Compatibilidad con dispositivos Android 15 de paginas de 16 KB.
target_link_options(gbalib PRIVATE -Wl,-z,max-page-size=16384)

target_link_libraries(gbalib PRIVATE
    ${MGBA_PREFIX}/lib/libmgba.a
    android
    log
    m
)
