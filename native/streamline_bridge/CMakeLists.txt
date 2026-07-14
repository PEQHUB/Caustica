cmake_minimum_required(VERSION 3.20)
project(streamlinebridge LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT WIN32)
  message(FATAL_ERROR "The Streamline bridge is a Windows x64 production component")
endif()

set(STREAMLINE_SDK "$ENV{STREAMLINE_SDK}" CACHE PATH "Streamline SDK 2.12.0 root")
set(VULKAN_SDK "$ENV{VULKAN_SDK}" CACHE PATH "Vulkan SDK root")
if(NOT EXISTS "${STREAMLINE_SDK}/include/sl.h")
  message(FATAL_ERROR "STREAMLINE_SDK must point at the unpacked Streamline SDK 2.12.0")
endif()
if(NOT EXISTS "${VULKAN_SDK}/Include/vulkan/vulkan.h")
  message(FATAL_ERROR "VULKAN_SDK must point at a Windows Vulkan SDK")
endif()

set(STREAMLINE_BRIDGE_OUTPUT_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../build/native/streamline_bridge"
    CACHE PATH "Directory for built Streamline bridge artifacts")
set(STREAMLINE_BRIDGE_OUTPUT_DIR "${STREAMLINE_BRIDGE_OUTPUT_ROOT}/$<LOWER_CASE:$<CONFIG>>")

add_library(streamlinebridge SHARED streamline_bridge.cpp)
target_include_directories(streamlinebridge PRIVATE
    "${STREAMLINE_SDK}/include"
    "${VULKAN_SDK}/Include")
target_compile_definitions(streamlinebridge PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX)
target_link_libraries(streamlinebridge PRIVATE user32 crypt32 wintrust)
set_property(TARGET streamlinebridge PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreadedDLL")
set_target_properties(streamlinebridge PROPERTIES
    OUTPUT_NAME "streamlinebridge"
    RUNTIME_OUTPUT_DIRECTORY "${STREAMLINE_BRIDGE_OUTPUT_DIR}"
    LIBRARY_OUTPUT_DIRECTORY "${STREAMLINE_BRIDGE_OUTPUT_DIR}"
    ARCHIVE_OUTPUT_DIRECTORY "${STREAMLINE_BRIDGE_OUTPUT_DIR}")

enable_testing()
add_executable(streamlinebridge_abi_test streamline_bridge_abi_test.cpp)
target_include_directories(streamlinebridge_abi_test PRIVATE
    "${STREAMLINE_SDK}/include"
    "${VULKAN_SDK}/Include")
target_compile_definitions(streamlinebridge_abi_test PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX)
add_test(NAME streamlinebridge_abi_test COMMAND streamlinebridge_abi_test)
