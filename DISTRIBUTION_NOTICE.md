# Distribution Notice

Caustica's project-owned code is distributed under the GNU Lesser General Public License v3.0 or
later. That license does not apply to the separately licensed NVIDIA binary components bundled in
the Windows x64 runtime artifact.

The NVIDIA Streamline, DLSS/NGX, Reflex, SHaRC SPIR-V shaders, and related runtime components are
incorporated into Caustica solely for use by Caustica. They are not distributed as a standalone SDK and may not be
separated and redistributed except as permitted by their applicable NVIDIA licenses.

Use of those NVIDIA components is subject to the license texts packaged under
`META-INF/licenses/nvidia/` in the Caustica JAR. In particular, NVIDIA proprietary notices must not
be removed, and the NVIDIA components may not be reverse engineered, decompiled, disassembled, or
made subject to Caustica's open-source license.

The public source distribution does not contain NVIDIA SHaRC shader source. A SHaRC-enabled build
uses the separately obtained, pinned NVIDIA SDK as a build input and packages only validated SPIR-V
object code as part of Caustica's materially larger application.

Caustica is an independent project and is not sponsored or endorsed by NVIDIA Corporation.
