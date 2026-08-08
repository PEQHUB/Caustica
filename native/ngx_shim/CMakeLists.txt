cmake_minimum_required(VERSION 3.20)
project(ngxshim LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT CMAKE_CONFIGURATION_TYPES AND NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
  set_property(CACHE CMAKE_BUILD_TYPE PROPERTY STRINGS Debug Release RelWithDebInfo MinSizeRel)
endif()

get_filename_component(NGX_SHIM_REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../.." ABSOLUTE)
set(NGX_SHIM_OUTPUT_ROOT "${NGX_SHIM_REPO_ROOT}/build/native/ngx_shim"
    CACHE PATH "Directory for built NGX shim artifacts")
set(NGX_SHIM_OUTPUT_DIR "${NGX_SHIM_OUTPUT_ROOT}/$<LOWER_CASE:$<CONFIG>>")

option(NGX_SHIM_REPRODUCIBILITY_STRICT
    "Require complete and optionally expected native toolchain identities"
    OFF)
set(NGX_SHIM_REPRODUCIBILITY_RECEIPT
    "${NGX_SHIM_OUTPUT_ROOT}/reproducibility/ngxshim-toolchain.properties"
    CACHE FILEPATH "Path for the native toolchain identity receipt")
set(NGX_EXPECTED_CMAKE_VERSION "" CACHE STRING "Expected CMake version for strict reproducibility checks")
set(NGX_EXPECTED_COMPILER_ID "" CACHE STRING "Expected C++ compiler ID for strict reproducibility checks")
set(NGX_EXPECTED_COMPILER_VERSION "" CACHE STRING "Expected C++ compiler version for strict reproducibility checks")
set(NGX_EXPECTED_COMPILER_SHA256 "" CACHE STRING "Expected C++ compiler SHA-256 for strict reproducibility checks")
set(NGX_EXPECTED_VULKAN_SDK_VERSION "" CACHE STRING "Expected Vulkan SDK version for strict reproducibility checks")
set(NGX_EXPECTED_DLSS_SDK_COMMIT "" CACHE STRING "Expected DLSS SDK git commit for strict reproducibility checks")
set(NGX_DLSS_VENDOR_CONFIG "rel" CACHE STRING "DLSS vendor runtime configuration recorded in the receipt")

string(TOLOWER "${NGX_DLSS_VENDOR_CONFIG}" NGX_DLSS_VENDOR_CONFIG_NORMALIZED)
if(NGX_DLSS_VENDOR_CONFIG_NORMALIZED STREQUAL "release")
  set(NGX_DLSS_VENDOR_CONFIG_NORMALIZED "rel")
elseif(NGX_DLSS_VENDOR_CONFIG_NORMALIZED STREQUAL "debug")
  set(NGX_DLSS_VENDOR_CONFIG_NORMALIZED "dev")
elseif(NOT NGX_DLSS_VENDOR_CONFIG_NORMALIZED STREQUAL "rel"
       AND NOT NGX_DLSS_VENDOR_CONFIG_NORMALIZED STREQUAL "dev")
  message(FATAL_ERROR
      "NGX_DLSS_VENDOR_CONFIG must be rel/release or dev/debug, got '${NGX_DLSS_VENDOR_CONFIG}'")
endif()

function(ngxshim_set_artifact_dirs target)
  set_target_properties(${target} PROPERTIES
      RUNTIME_OUTPUT_DIRECTORY "${NGX_SHIM_OUTPUT_DIR}"
      LIBRARY_OUTPUT_DIRECTORY "${NGX_SHIM_OUTPUT_DIR}"
      ARCHIVE_OUTPUT_DIRECTORY "${NGX_SHIM_OUTPUT_DIR}")
endfunction()

if(NOT DEFINED ENV{DLSS_SDK})
  message(FATAL_ERROR "DLSS_SDK environment variable is not set")
endif()

if(NOT DEFINED ENV{VULKAN_SDK})
  message(FATAL_ERROR "VULKAN_SDK environment variable is not set")
endif()

get_filename_component(NGX_VULKAN_SDK_DIR "$ENV{VULKAN_SDK}" ABSOLUTE)

function(ngxshim_sha256_or_missing input output)
  if(EXISTS "${input}" AND NOT IS_DIRECTORY "${input}")
    file(SHA256 "${input}" digest)
  else()
    set(digest "missing")
  endif()
  set(${output} "${digest}" PARENT_SCOPE)
endfunction()

set(NGX_VULKAN_SDK_VERSION "unknown")
set(NGX_VULKAN_COMPONENTS "${NGX_VULKAN_SDK_DIR}/components.xml")
if(EXISTS "${NGX_VULKAN_COMPONENTS}")
  file(READ "${NGX_VULKAN_COMPONENTS}" NGX_VULKAN_COMPONENTS_TEXT)
  string(REGEX MATCH "<Version>([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+)</Version>"
      NGX_VULKAN_VERSION_MATCH "${NGX_VULKAN_COMPONENTS_TEXT}")
  if(CMAKE_MATCH_1)
    set(NGX_VULKAN_SDK_VERSION "${CMAKE_MATCH_1}")
  endif()
endif()

add_library(ngxshim SHARED ngx_shim.cpp)

set(DLSS_SDK_DIR "$ENV{DLSS_SDK}")
set(DLSS_SDK_INCLUDE_DIR "${DLSS_SDK_DIR}/include")
string(REPLACE "\\" "/" NGX_DLSS_SDK_RECEIPT_PATH "${DLSS_SDK_DIR}")

execute_process(
    COMMAND git -C "${DLSS_SDK_DIR}" rev-parse HEAD
    RESULT_VARIABLE NGX_DLSS_GIT_RESULT
    OUTPUT_VARIABLE NGX_DLSS_SDK_COMMIT
    ERROR_QUIET
    OUTPUT_STRIP_TRAILING_WHITESPACE)
if(NGX_DLSS_GIT_RESULT EQUAL 0 AND NGX_DLSS_SDK_COMMIT)
  string(STRIP "${NGX_DLSS_SDK_COMMIT}" NGX_DLSS_SDK_COMMIT)
else()
  set(NGX_DLSS_SDK_COMMIT "unavailable")
endif()


target_include_directories(ngxshim PRIVATE "${DLSS_SDK_INCLUDE_DIR}")
target_include_directories(ngxshim PRIVATE "$ENV{VULKAN_SDK}/include")
if(WIN32)
  set(NGX_STATIC_LIB "${DLSS_SDK_DIR}/lib/Windows_x86_64/x64/nvsdk_ngx_d.lib")
  set_property(TARGET ngxshim PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreadedDLL")
else()
  set(NGX_STATIC_LIB "${DLSS_SDK_DIR}/lib/Linux_x86_64/libnvsdk_ngx.a")
endif()

if(WIN32)
  set(NGX_DLSS_VENDOR_ROOT "${DLSS_SDK_DIR}/lib/Windows_x86_64/${NGX_DLSS_VENDOR_CONFIG_NORMALIZED}")
  set(NGX_DLSSD_RUNTIME "${NGX_DLSS_VENDOR_ROOT}/nvngx_dlssd.dll")
  set(NGX_DLSSG_RUNTIME "${NGX_DLSS_VENDOR_ROOT}/nvngx_dlssg.dll")
else()
  set(NGX_DLSS_VENDOR_ROOT "${DLSS_SDK_DIR}/lib/Linux_x86_64/${NGX_DLSS_VENDOR_CONFIG_NORMALIZED}")
  function(ngxshim_resolve_runtime base_name output)
    set(exact "${NGX_DLSS_VENDOR_ROOT}/${base_name}")
    if(EXISTS "${exact}")
      set(resolved "${exact}")
    else()
      file(GLOB candidates LIST_DIRECTORIES FALSE "${NGX_DLSS_VENDOR_ROOT}/${base_name}.*")
      list(SORT candidates)
      list(LENGTH candidates candidate_count)
      if(candidate_count EQUAL 1)
        list(GET candidates 0 resolved)
      elseif(candidate_count GREATER 1 AND NGX_SHIM_REPRODUCIBILITY_STRICT)
        message(FATAL_ERROR "Ambiguous ${base_name} runtime in ${NGX_DLSS_VENDOR_ROOT}")
      elseif(candidate_count GREATER 1)
        list(GET candidates 0 resolved)
      else()
        set(resolved "${exact}")
      endif()
    endif()
    set(${output} "${resolved}" PARENT_SCOPE)
  endfunction()
  ngxshim_resolve_runtime("libnvidia-ngx-dlssd.so" NGX_DLSSD_RUNTIME)
  ngxshim_resolve_runtime("libnvidia-ngx-dlssg.so" NGX_DLSSG_RUNTIME)
endif()

ngxshim_sha256_or_missing("${CMAKE_COMMAND}" NGX_CMAKE_SHA256)
ngxshim_sha256_or_missing("${CMAKE_CXX_COMPILER}" NGX_CXX_COMPILER_SHA256)
ngxshim_sha256_or_missing("${NGX_STATIC_LIB}" NGX_DLSS_STATIC_LIB_SHA256)
ngxshim_sha256_or_missing("${DLSS_SDK_INCLUDE_DIR}/nvsdk_ngx_defs.h" NGX_DLSS_DEFS_SHA256)
ngxshim_sha256_or_missing("${DLSS_SDK_INCLUDE_DIR}/nvsdk_ngx_defs_dlssd.h" NGX_DLSSD_DEFS_SHA256)
ngxshim_sha256_or_missing("${DLSS_SDK_INCLUDE_DIR}/nvsdk_ngx_defs_dlssg.h" NGX_DLSSG_DEFS_SHA256)
ngxshim_sha256_or_missing("${DLSS_SDK_INCLUDE_DIR}/nvsdk_ngx_defs_vk.h" NGX_DLSS_VK_DEFS_SHA256)
ngxshim_sha256_or_missing("${NGX_VULKAN_COMPONENTS}" NGX_VULKAN_COMPONENTS_SHA256)
ngxshim_sha256_or_missing("${NGX_VULKAN_SDK_DIR}/include/vulkan/vulkan_core.h" NGX_VULKAN_CORE_SHA256)
ngxshim_sha256_or_missing("${NGX_DLSSD_RUNTIME}" NGX_DLSSD_RUNTIME_SHA256)
ngxshim_sha256_or_missing("${NGX_DLSSG_RUNTIME}" NGX_DLSSG_RUNTIME_SHA256)
string(REPLACE "\\" "/" NGX_STATIC_LIB_RECEIPT_PATH "${NGX_STATIC_LIB}")
string(REPLACE "\\" "/" NGX_DLSSD_RUNTIME_RECEIPT_PATH "${NGX_DLSSD_RUNTIME}")
string(REPLACE "\\" "/" NGX_DLSSG_RUNTIME_RECEIPT_PATH "${NGX_DLSSG_RUNTIME}")

function(ngxshim_check_expected label actual expected)
  if(NGX_SHIM_REPRODUCIBILITY_STRICT AND NOT "${expected}" STREQUAL ""
     AND NOT "${actual}" STREQUAL "${expected}")
    message(FATAL_ERROR
        "NGX reproducibility mismatch for ${label}: observed '${actual}', expected '${expected}'")
  endif()
endfunction()

ngxshim_check_expected("CMake version" "${CMAKE_VERSION}" "${NGX_EXPECTED_CMAKE_VERSION}")
ngxshim_check_expected("C++ compiler ID" "${CMAKE_CXX_COMPILER_ID}" "${NGX_EXPECTED_COMPILER_ID}")
ngxshim_check_expected("C++ compiler version" "${CMAKE_CXX_COMPILER_VERSION}" "${NGX_EXPECTED_COMPILER_VERSION}")
ngxshim_check_expected("C++ compiler SHA-256" "${NGX_CXX_COMPILER_SHA256}" "${NGX_EXPECTED_COMPILER_SHA256}")
ngxshim_check_expected("Vulkan SDK version" "${NGX_VULKAN_SDK_VERSION}" "${NGX_EXPECTED_VULKAN_SDK_VERSION}")
ngxshim_check_expected("DLSS SDK commit" "${NGX_DLSS_SDK_COMMIT}" "${NGX_EXPECTED_DLSS_SDK_COMMIT}")

if(NGX_SHIM_REPRODUCIBILITY_STRICT)
  foreach(required_identity IN ITEMS
      "${NGX_VULKAN_SDK_VERSION}"
      "${NGX_DLSS_SDK_COMMIT}"
      "${NGX_CMAKE_SHA256}"
      "${NGX_CXX_COMPILER_SHA256}"
      "${NGX_DLSS_STATIC_LIB_SHA256}"
      "${NGX_DLSS_DEFS_SHA256}"
      "${NGX_DLSSD_DEFS_SHA256}"
      "${NGX_DLSSG_DEFS_SHA256}"
      "${NGX_DLSS_VK_DEFS_SHA256}"
      "${NGX_VULKAN_COMPONENTS_SHA256}"
      "${NGX_VULKAN_CORE_SHA256}"
      "${NGX_DLSSD_RUNTIME_SHA256}"
      "${NGX_DLSSG_RUNTIME_SHA256}")
    if(required_identity STREQUAL "unknown"
       OR required_identity STREQUAL "unavailable"
       OR required_identity STREQUAL "missing"
       OR required_identity STREQUAL "")
      message(FATAL_ERROR "NGX reproducibility identity is unavailable")
    endif()
  endforeach()
endif()

get_filename_component(NGX_REPRODUCIBILITY_RECEIPT_DIR
    "${NGX_SHIM_REPRODUCIBILITY_RECEIPT}" DIRECTORY)
file(MAKE_DIRECTORY "${NGX_REPRODUCIBILITY_RECEIPT_DIR}")
string(CONCAT NGX_REPRODUCIBILITY_CONTENT
      "schema=caustica-ngx-toolchain-v1\n"
      "cmake.version=${CMAKE_VERSION}\n"
      "cmake.path=${CMAKE_COMMAND}\n"
      "cmake.sha256=${NGX_CMAKE_SHA256}\n"
      "cmake.generator=${CMAKE_GENERATOR}\n"
      "cmake.generatorPlatform=${CMAKE_GENERATOR_PLATFORM}\n"
      "compiler.id=${CMAKE_CXX_COMPILER_ID}\n"
      "compiler.version=${CMAKE_CXX_COMPILER_VERSION}\n"
      "compiler.path=${CMAKE_CXX_COMPILER}\n"
      "compiler.sha256=${NGX_CXX_COMPILER_SHA256}\n"
      "compiler.frontend=${CMAKE_CXX_COMPILER_FRONTEND_VARIANT}\n"
      "compiler.architecture=${CMAKE_CXX_COMPILER_ARCHITECTURE_ID}\n"
      "vulkan.sdk.path=${NGX_VULKAN_SDK_DIR}\n"
      "vulkan.sdk.version=${NGX_VULKAN_SDK_VERSION}\n"
      "vulkan.components.sha256=${NGX_VULKAN_COMPONENTS_SHA256}\n"
      "vulkan.coreHeader.sha256=${NGX_VULKAN_CORE_SHA256}\n"
      "dlss.sdk.path=${NGX_DLSS_SDK_RECEIPT_PATH}\n"
      "dlss.sdk.commit=${NGX_DLSS_SDK_COMMIT}\n"
      "dlss.vendorConfig=${NGX_DLSS_VENDOR_CONFIG_NORMALIZED}\n"
      "dlss.defs.sha256=${NGX_DLSS_DEFS_SHA256}\n"
      "dlss.dlssdDefs.sha256=${NGX_DLSSD_DEFS_SHA256}\n"
      "dlss.dlssgDefs.sha256=${NGX_DLSSG_DEFS_SHA256}\n"
      "dlss.vulkanDefs.sha256=${NGX_DLSS_VK_DEFS_SHA256}\n"
      "dlss.staticLibrary.path=${NGX_STATIC_LIB_RECEIPT_PATH}\n"
      "dlss.staticLibrary.sha256=${NGX_DLSS_STATIC_LIB_SHA256}\n"
      "dlss.dlssdRuntime.path=${NGX_DLSSD_RUNTIME_RECEIPT_PATH}\n"
      "dlss.dlssdRuntime.sha256=${NGX_DLSSD_RUNTIME_SHA256}\n"
      "dlss.dlssgRuntime.path=${NGX_DLSSG_RUNTIME_RECEIPT_PATH}\n"
      "dlss.dlssgRuntime.sha256=${NGX_DLSSG_RUNTIME_SHA256}\n")
file(GENERATE
    OUTPUT "${NGX_SHIM_REPRODUCIBILITY_RECEIPT}"
    CONTENT "${NGX_REPRODUCIBILITY_CONTENT}")

message(STATUS "NGX reproducibility receipt: ${NGX_SHIM_REPRODUCIBILITY_RECEIPT}")
target_link_libraries(ngxshim PRIVATE "${NGX_STATIC_LIB}" ${CMAKE_DL_LIBS})

set_target_properties(ngxshim PROPERTIES
    OUTPUT_NAME "ngxshim")
ngxshim_set_artifact_dirs(ngxshim)
