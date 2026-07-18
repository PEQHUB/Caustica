cmake_minimum_required(VERSION 3.22)
project(nrdbridge LANGUAGES CXX)

include(FetchContent)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
if(MSVC)
  set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()

# Pin the exact NRD revision reviewed for this bridge. NRD's NRI integration is Vulkan-native and
# supports Windows and Linux; no CUDA, NGX, NVAPI, or vendor-specific device extension is used.
set(NRD_STATIC_LIBRARY ON CACHE BOOL "" FORCE)
set(NRD_NRI ON CACHE BOOL "" FORCE)
set(NRD_EMBEDS_SPIRV_SHADERS ON CACHE BOOL "" FORCE)
set(NRD_EMBEDS_DXBC_SHADERS OFF CACHE BOOL "" FORCE)
set(NRD_EMBEDS_DXIL_SHADERS OFF CACHE BOOL "" FORCE)
set(NRD_NORMAL_ENCODING 4 CACHE STRING "" FORCE) # RGBA16_SNORM; Caustica stores float world normals
set(NRD_ROUGHNESS_ENCODING 1 CACHE STRING "" FORCE) # linear roughness in alpha
set(NRI_ENABLE_VK_SUPPORT ON CACHE BOOL "" FORCE)
set(NRI_STATIC_LIBRARY ON CACHE BOOL "" FORCE)
set(NRI_ENABLE_D3D11_SUPPORT OFF CACHE BOOL "" FORCE)
set(NRI_ENABLE_D3D12_SUPPORT OFF CACHE BOOL "" FORCE)
set(NRI_ENABLE_NVAPI OFF CACHE BOOL "" FORCE)
set(NRI_ENABLE_AMDAGS OFF CACHE BOOL "" FORCE)

if(WIN32 AND NOT SHADERMAKE_FXC_PATH)
  file(GLOB _fxc_candidates "C:/Program Files (x86)/Windows Kits/10/bin/*/x64/fxc.exe")
  list(SORT _fxc_candidates COMPARE NATURAL ORDER DESCENDING)
  if(_fxc_candidates)
    list(GET _fxc_candidates 0 _fxc)
    set(SHADERMAKE_FXC_PATH "${_fxc}" CACHE FILEPATH "" FORCE)
  endif()
endif()

FetchContent_Declare(nrd
  URL https://github.com/NVIDIA-RTX/NRD/archive/792eff196afdd350fd9c3f862119017ccb438a0e.zip
  DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
FetchContent_MakeAvailable(nrd)

add_library(nrdbridge SHARED nrd_bridge.cpp)
target_link_libraries(nrdbridge PRIVATE NRD NRDIntegration NRI)
target_include_directories(nrdbridge PRIVATE
  "${nrd_SOURCE_DIR}/Include"
  "${nrd_SOURCE_DIR}/Integration"
  "${nri_SOURCE_DIR}/Include")
target_compile_definitions(nrdbridge PRIVATE NRD_STATIC_LIBRARY=1)

if(MSVC)
  target_compile_options(nrdbridge PRIVATE /W4 /WX /wd4324)
  set_property(TARGET nrdbridge PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
else()
  # Keep Caustica warnings fatal while insulating the pinned NRD/NRI headers from GCC diagnostics
  # produced by their intentionally partial aggregate initializers and presence-marker constants.
  target_compile_options(nrdbridge PRIVATE -Wall -Wextra -Werror
    -Wno-missing-field-initializers -Wno-unused-variable -fvisibility=hidden)
endif()

set(NRD_BRIDGE_OUTPUT_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../build/native/nrd_bridge"
    CACHE PATH "Directory for built NRD bridge artifacts")
set(NRD_BRIDGE_OUTPUT_DIR "${NRD_BRIDGE_OUTPUT_ROOT}/$<LOWER_CASE:$<CONFIG>>")
set_target_properties(nrdbridge PROPERTIES
  OUTPUT_NAME "nrdbridge"
  RUNTIME_OUTPUT_DIRECTORY "${NRD_BRIDGE_OUTPUT_DIR}"
  LIBRARY_OUTPUT_DIRECTORY "${NRD_BRIDGE_OUTPUT_DIR}"
  ARCHIVE_OUTPUT_DIRECTORY "${NRD_BRIDGE_OUTPUT_DIR}")
