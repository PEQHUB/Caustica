# NVIDIA SHaRC 1.6.5.0 build and distribution contract

Caustica uses NVIDIA SHaRC as a separately licensed external shader SDK. The public Caustica source
tree does not vendor the four NVIDIA headers. SHaRC-enabled builds require:

- tag `v1.6.5.0`
- commit `0b9f58bbc8c41736042d4da964830a247e424a00`
- `SHARC_SDK` set to that checkout's root

On Windows PowerShell:

```powershell
.\tools\fetch-sharc.ps1
$env:SHARC_SDK = (Resolve-Path .deps\sharc-1.6.5.0)
.\gradlew.bat compileShaders
```

Running the fetch script or using the SDK means accepting NVIDIA's license. Read it before use:
<https://github.com/NVIDIA-RTX/SHARC/blob/v1.6.5.0/License.md>.

The Gradle build verifies SHA-256 hashes for all four NVIDIA headers and `License.md`. A mismatch
fails closed. With no `SHARC_SDK`, Caustica builds only the original baseline shader family and writes
`artifacts=false` to `caustica/sharc.properties`.

With the pinned SDK present, the runtime JAR contains the SHaRC update/query/resolve SPIR-V object
code, `caustica/sharc.properties`, and the complete NVIDIA license at
`META-INF/licenses/nvidia/NVIDIA-SHARC-SDK.txt`. It does not contain NVIDIA headers or other SDK
source. The source JAR excludes all generated SPIR-V, SHaRC capability metadata, and NVIDIA license
payloads.

This packaging follows the license grant for SDK material incorporated in object-code form into an
application with material additional functionality. It also keeps that proprietary object code out
of Caustica's LGPL source license. Public visibility of NVIDIA's GitHub repository is not itself an
open-source grant. This document is an engineering distribution boundary, not legal advice; questions
can be directed to the address NVIDIA publishes in the license.
