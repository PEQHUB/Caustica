# Streamline SDK 2.12.0

Caustica's DLSS Frame Generation path is pinned to NVIDIA Streamline SDK 2.12.0.

- Download: <https://github.com/NVIDIA-RTX/Streamline/releases/download/v2.12.0/streamline-sdk-v2.12.0.zip>
- SHA-256: `f5c0a3d870707dddc3570fb4bcd3655cf48a8a68c3a9d342910cfa21b77dcf48`
- Official manual-hooking guide: <https://github.com/NVIDIA-RTX/Streamline/blob/v2.12.0/docs/ProgrammingGuideManualHooking.md>

The zip is an external build input. Extract it and set `STREAMLINE_SDK` to the
extraction directory. Build the bridge before assembling a Windows x64 jar:

```powershell
cmake -S native/streamline_bridge -B native/streamline_bridge/build -G "Visual Studio 17 2022" -A x64 `
  -DSTREAMLINE_SDK="$env:STREAMLINE_SDK" -DVULKAN_SDK="$env:VULKAN_SDK"
cmake --build native/streamline_bridge/build --config Release --target streamlinebridge streamlinebridge_abi_test
ctest --test-dir native/streamline_bridge/build -C Release --output-on-failure
```

Development packaging is the default and uses the SDK's unsigned/watermarked
`bin/x64/development` binaries. Production packaging is explicit:

```powershell
./gradlew.bat build -PstreamlineVariant=production
```

Production requires `NVIDIA_APPLICATION_ID`, uses the non-development
`bin/x64` directory, and verifies Authenticode signatures during packaging.
The selected variant is embedded in `caustica/streamline.properties`, so the
runtime cannot accidentally initialize a production package as development.
The bridge repeats signature verification before `slInit`. The Java FFM layer
loads only `streamlinebridge.dll`; Java does not bind Streamline's C++ SDK
structs directly.
