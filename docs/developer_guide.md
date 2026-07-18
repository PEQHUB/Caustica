# Developer Guide

## Windows

1. Install the Vulkan SDK from <https://vulkan.lunarg.com/sdk/home>.
   The installer sets `VULKAN_SDK` automatically.
2. Download NVIDIA Streamline SDK 2.12.0 and set `STREAMLINE_SDK` to the extracted root.

   To set it permanently for your Windows user account, run PowerShell with:

   ```powershell
   [Environment]::SetEnvironmentVariable("STREAMLINE_SDK", "C:\path\to\streamline-sdk", "User")
   ```

   Restart your terminal after setting it. To set it only for the current
   PowerShell session, use:

   ```powershell
   $env:STREAMLINE_SDK = "C:\path\to\streamline-sdk"
   ```

3. Configure, build, and test the Streamline bridge:

```powershell
cmake -S native/streamline_bridge -B native/streamline_bridge/build -A x64
cmake --build native/streamline_bridge/build --config Release
ctest --test-dir native/streamline_bridge/build -C Release --output-on-failure
```

4. Run the client:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Xmx8G -XX:+UseCompactObjectHeaders -XX:+AlwaysPreTouch -XX:+UseStringDeduplication -XX:+UseZGC"
.\gradlew.bat runClient --args="--renderDebugLabels --graphicsBackend VULKAN"
```

## Linux

The Java renderer, shaders, and NRD Vulkan bridge build on Linux through the Nix shell. NRD is the
automatic reconstruction backend on Linux (AMD, NVIDIA, or Intel); Streamline DLSS-RR/DLSSG remains
a Windows x64 component:

```bash
nix develop
./gradlew buildNrdBridge compileJava compileShaders
```

`build` packages `build/native/nrd_bridge/release/libnrdbridge.so` under
`caustica/natives/linux-x64`. The production-candidate workflow builds Linux NRD separately and feeds
that exact payload to the Windows assembly so one review JAR contains both `libnrdbridge.so` and
`nrdbridge.dll`.

## Reconstruction policy

The Reconstruction tab owns one global selector:

- `Auto`: NRD on Linux, AMD, and Intel; DLSS Ray Reconstruction on Windows NVIDIA while supported;
  if DLSS-RR initialization fails, Auto falls back to NRD.
- `NRD`: force the Vulkan NRD path on any vendor, including NVIDIA.
- `DLSS Ray Reconstruction`: force Streamline DLSS-RR where it is available.
- `Off / Native Reference`: native-resolution noisy reference with no temporal reconstruction.

NRD runs at native resolution because it is a denoiser, not an upscaler. REBLUR is the default;
RELAX and directional SH/SG are live opt-ins. Every NRD control is applied per frame, while changes
that alter resource identity (backend, denoiser family, SH mode) recreate resources and clear history.

## Native Bundling

On Windows x64, Gradle bundles the Streamline bridge and the pinned Streamline DLSS-RR, DLSSG,
Reflex, and PCL runtime libraries:

```powershell
.\gradlew.bat build
```

DLSS-RR and DLSSG share one Streamline frame token for each application frame. DLSS-RR evaluates
on viewport `1` with the raw render-space constants and resources; DLSSG uses viewport `0` with
the final present-space constants and resources. Keep those viewport IDs distinct: Streamline
accepts only one common-constants packet for a given frame token and viewport.

Run the Vulkan RT client on Windows with:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Xmx8G -XX:+UseCompactObjectHeaders -XX:+AlwaysPreTouch -XX:+UseStringDeduplication -XX:+UseZGC"
.\gradlew.bat runClient --args="--renderDebugLabels --graphicsBackend VULKAN"
```
