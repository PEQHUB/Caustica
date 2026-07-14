# Streamline SDK 2.12.0

Caustica's DLSS Frame Generation path is pinned to NVIDIA Streamline SDK 2.12.0.

- Download: <https://github.com/NVIDIA-RTX/Streamline/releases/download/v2.12.0/streamline-sdk-v2.12.0.zip>
- SHA-256: `f5c0a3d870707dddc3570fb4bcd3655cf48a8a68c3a9d342910cfa21b77dcf48`
- Official manual-hooking guide: <https://github.com/NVIDIA-RTX/Streamline/blob/v2.12.0/docs/ProgrammingGuideManualHooking.md>

The zip is an external build input. Extract it and set `STREAMLINE_SDK` to the
extraction directory. `VULKAN_SDK` must point at a Vulkan SDK installation.
The Gradle build configures, builds, and tests the native bridge as part of the
normal task graph:

```powershell
$env:STREAMLINE_SDK = 'C:\path\to\streamline-sdk-2.12.0'
$env:VULKAN_SDK = 'C:\VulkanSDK\1.4.341.1'
./gradlew.bat check
```

Production packaging is the default and uses the signed SDK binaries from
`bin/x64`:

```powershell
./gradlew.bat clean build
```

Development packaging is an explicit diagnostic build and uses the SDK's
unsigned/watermarked `bin/x64/development` binaries:

```powershell
./gradlew.bat build -PstreamlineVariant=development
```

Caustica embeds its NVIDIA Project ID in the bridge. A numeric
`NVIDIA_APPLICATION_ID` is optional and defaults to zero. Production packaging
verifies the Authenticode signature of every packaged DLL. Before `slInit`, the
bridge also applies Streamline's secondary signature check to
`sl.interposer.dll`; NVIDIA's NGX feature DLLs are Authenticode-verified but do
not all carry that secondary Streamline signature.

The selected variant is embedded in `caustica/streamline.properties`, so the
runtime cannot accidentally initialize a production package as development.
Production refuses a loose `caustica.streamline.path` override and extracts to
a variant-specific native directory, preventing stale development configuration
from affecting production. The Java FFM layer loads only
`streamlinebridge.dll`; Java does not bind Streamline's C++ SDK structs directly.
