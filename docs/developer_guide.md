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

The Java renderer and shaders can be developed on Linux through the Nix shell, but the Streamline
DLSS-RR/DLSSG bridge and its NVIDIA runtime bundle are Windows x64 components:

```bash
nix develop
./gradlew compileJava compileShaders
```

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
