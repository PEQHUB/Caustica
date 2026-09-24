# Third-Party Notices

Caustica's project-owned code is licensed under `LGPL-3.0-or-later`. This file
documents third-party components and license boundaries that are not changed by
Caustica's license.

## NVIDIA DLSS / NGX SDK

Caustica can build and distribute release artifacts that include NVIDIA DLSS/NGX
SDK runtime components, including DLSS Super Resolution, Ray Reconstruction and Frame Generation
libraries. These NVIDIA components are proprietary third-party software and are
not licensed under the LGPL.

The NVIDIA SDK components remain subject to the NVIDIA RTX SDKs license:

<https://github.com/NVIDIA/DLSS/blob/main/LICENSE.txt>

The LGPL license grant for Caustica does not grant rights to NVIDIA SDK
components. Redistribution and use of those components must comply with
NVIDIA's license terms.

This software contains source code provided by NVIDIA Corporation.

Bundled NVIDIA SDK runtime libraries may include files matching:

- `caustica/natives/windows-x64/nvngx_dlss.dll`
- `caustica/natives/windows-x64/nvngx_dlssd.dll`
- `caustica/natives/windows-x64/nvngx_dlssg.dll`
- `caustica/natives/linux-x64/libnvidia-ngx-dlss.so*`
- `caustica/natives/linux-x64/libnvidia-ngx-dlssd.so*`
- `caustica/natives/linux-x64/libnvidia-ngx-dlssg.so*`

Caustica's `ngxshim` native library is project-owned glue code and follows
Caustica's project license unless otherwise noted.

## NVIDIA Real-Time Denoisers (NRD) SDK

Caustica can build a platform-specific `nvidia-nrd` artifact containing a
project-owned native shim that statically incorporates NVIDIA NRD and NRI
object code. NRD is proprietary third-party software and is not licensed under
the LGPL. NRI is third-party software licensed under the MIT License.

The integration is pinned to NVIDIA NRD revision
`b233cc3ec5b1db2763e45fd18c9bb19793016355`. Native builds use the pinned
`third_party/NRD` submodule by default. Bundled artifacts contain NRD's
`LICENSE.txt` and NRI's license as `NRI_LICENSE.txt` beside the native library
under `caustica/natives/nrd/<revision>/<platform>/`.

NRI builds apply the repository's `nri-destroy-lifetime.patch`, which captures
allocation callbacks before object destruction. The pinned upstream revision and
MIT license are unchanged.

The NVIDIA SDK components remain subject to the NVIDIA RTX SDKs license:

<https://github.com/NVIDIA-RTX/NRD/blob/b233cc3ec5b1db2763e45fd18c9bb19793016355/LICENSE.txt>

The LGPL license grant for Caustica does not grant rights to NVIDIA SDK
components. Redistribution and use of the native artifact must comply with the
NVIDIA license, including its object-code incorporation and distribution
requirements. The NRD SDK may not be redistributed as a stand-alone product.

NRI license and source:

<https://github.com/NVIDIA-RTX/NRI/blob/main/LICENSE.txt>

## NVIDIA SHaRC (Spatially Hashed Radiance Cache) SDK

Caustica can use the NVIDIA SHaRC 1.8.0.0 shader headers (commit
`e19ccacd511f42a3df6f850052d508c13c9e9737`) when a build is made with
`-PsharcSdk=<SDK checkout>`. The headers are an external input supplied by
whoever builds or runs Caustica: they are not part of this repository, and no
build artifact contains them or code compiled from them. A `-PsharcSdk` build
records only the SDK location, in `caustica/sharc.properties`. At run time
Caustica verifies the SHA-256 of `SharcCommon.h`, `SharcTypes.h`,
`HashGridCommon.h` and `HashGridTypes.h` at that location and compiles them on
the local machine. Without the property, nothing SHaRC-related is read or
packaged.

SHaRC is proprietary software provided by NVIDIA Corporation and is not
licensed under the LGPL. Use of the SDK is subject to the NVIDIA RTX SDKs
license:

<https://github.com/NVIDIA-RTX/SHARC/blob/e19ccacd511f42a3df6f850052d508c13c9e9737/License.md>

The LGPL license grant for Caustica does not grant rights to the SHaRC SDK.

## Slang

Caustica bundles the Slang 2026.14.1 compiler shared libraries and standard module
for in-game shader compilation. Slang is licensed under
`Apache-2.0 WITH LLVM-exception`:

<https://github.com/shader-slang/slang/blob/master/LICENSE>

The Slang distribution incorporates or can depend on components under their
own permissive licenses, including glslang, LZ4, miniz, SPIR-V Headers, and
SPIR-V Tools. The upstream dependency and license list is maintained at:

<https://github.com/shader-slang/slang#license>

Caustica's `causticaslang` native library is project-owned glue code and follows
Caustica's project license unless otherwise noted.

## Khronos Box Vertex Colors glTF asset

Caustica includes the Box Vertex Colors sample model and its binary buffer from
the Khronos glTF Sample Assets repository as a renderer integration fixture.
The asset was created by Marco Hutter and is dedicated to the public domain
under Creative Commons CC0 1.0 Universal:

<https://github.com/KhronosGroup/glTF-Sample-Assets/tree/main/Models/BoxVertexColors>

<https://creativecommons.org/publicdomain/zero/1.0/legalcode>

## Khronos Lantern glTF asset

The standalone glTF viewer example includes the binary Lantern sample model from the Khronos glTF Sample
Assets repository as a textured renderer integration fixture. The asset is
dedicated to the public domain under Creative Commons CC0 1.0 Universal:

<https://github.com/KhronosGroup/glTF-Sample-Assets/tree/0e3a605bda7c758293ab58432f1d51a2a355d47a/Models/Lantern>

Bundled GLB source:

<https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/0e3a605bda7c758293ab58432f1d51a2a355d47a/Models/Lantern/glTF-Binary/Lantern.glb>

<https://creativecommons.org/publicdomain/zero/1.0/legalcode>
