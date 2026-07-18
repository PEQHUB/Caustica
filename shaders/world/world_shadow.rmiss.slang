// A visibility miss preserves the raygen-provided transmittance and water-crossing sentinel.
[shader("miss")]
void main(inout float4 shadowVis) {
}
