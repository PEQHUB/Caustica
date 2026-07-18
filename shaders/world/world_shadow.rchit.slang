// Opaque shadow terminator for the portable TraceRay path. Transparent materials are ignored by
// world.rahit; reaching this shader therefore means traversal found an actual blocker.
[shader("closesthit")]
void main(inout float4 shadowVis, in BuiltInTriangleIntersectionAttributes attr) {
    shadowVis.a = -2.0;
}
