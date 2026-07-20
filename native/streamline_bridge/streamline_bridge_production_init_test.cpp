#include <filesystem>
#include <iostream>
#include <string>

#include "streamline_bridge.h"

int wmain(int argc, wchar_t** argv) {
    if (argc != 3) {
        std::wcerr << L"usage: streamlinebridge_production_init_test <plugin-directory> <log-directory>\n";
        return 2;
    }

    slbridge_abi_info abi{};
    if (slbridge_get_abi_info(&abi) != 0
            || abi.abi_version != SLBRIDGE_ABI_VERSION
            || abi.trace_state_size != sizeof(slbridge_trace_state)
            || abi.dlssd_options_size != sizeof(slbridge_dlssd_options)) {
        std::cerr << "bridge ABI metadata mismatch\n";
        return 3;
    }

    const std::filesystem::path pluginDirectory(argv[1]);
    const std::filesystem::path logDirectory(argv[2]);
    std::filesystem::create_directories(logDirectory);
    if (!std::filesystem::is_regular_file(pluginDirectory / L"sl.interposer.dll")
            || !std::filesystem::is_regular_file(pluginDirectory / L"sl.dlss_g.dll")
            || !std::filesystem::is_regular_file(pluginDirectory / L"sl.dlss_d.dll")) {
        std::cerr << "packaged production plugin directory is incomplete\n";
        return 4;
    }

    const int32_t initializeResult = slbridge_initialize(pluginDirectory.c_str(), logDirectory.c_str(),
            0, SLBRIDGE_VARIANT_PRODUCTION);
    if (initializeResult != 0 || slbridge_is_initialized() != 1) {
        std::cerr << "production initialization failed: " << slbridge_last_error() << '\n';
        return 5;
    }
    if (slbridge_last_result() != 0 || !std::string(slbridge_last_error()).empty()) {
        std::cerr << "successful initialization retained an error: " << slbridge_last_error() << '\n';
        return 6;
    }
    if (slbridge_shutdown() != 0 || slbridge_is_initialized() != 0) {
        std::cerr << "production shutdown failed: " << slbridge_last_error() << '\n';
        return 7;
    }
    return 0;
}
