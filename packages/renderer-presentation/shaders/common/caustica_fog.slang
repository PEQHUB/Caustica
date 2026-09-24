module caustica_fog;
import caustica_fog_types;
import caustica_fog_medium;
[[vk::push_constant]] FogPush pc;

float3 unproject(float2 uv, float depth) {
    float4 q = pc.clipX * (uv.x * 2.0 - 1.0) + pc.clipY * (uv.y * 2.0 - 1.0)
             + pc.clipZ * depth + pc.clipW;
    return q.xyz / q.w;
}
float fogDepthLimit(uint2 pixel, uint2 extent, float maximumDistance) {
    Texture2D<float> depth = ResourceDescriptorHeap[pc.depthIndex];
    uint w,h; depth.GetDimensions(w,h);
    // A fog texel contributes across its full bilinear support. Include every jittered depth
    // footprint that composition can read there, including the adjacent depth sample.
    float2 lowUv = max(float2(pixel) - 0.5, 0.0) / float2(extent);
    float2 highUv = min(float2(pixel) + 1.5, float2(extent)) / float2(extent);
    int2 low = clamp(int2(floor(lowUv * float2(w,h) - 0.5 - pc.traceData.xy)), int2(0), int2(w-1,h-1));
    int2 high = clamp(int2(floor(highUv * float2(w,h) - 0.5 - pc.traceData.xy)) + 1, int2(0), int2(w-1,h-1));
    float minimumDepth = 1.0, maximumDepth = 0.0;
    for (int y=low.y;y<=high.y;y++) for (int x=low.x;x<=high.x;x++) {
        float value = depth.Load(int3(x,y,0));
        if (value <= 0.0) return maximumDistance;
        minimumDepth = min(minimumDepth,value);
        maximumDepth = max(maximumDepth,value);
    }
    // The clipped view frustum is convex; its greatest distance is at a corner.
    float limit = 0.0;
    for (uint y=0;y<2;y++) for (uint x=0;x<2;x++) {
        float2 uv = float2(x == 0 ? lowUv.x : highUv.x, y == 0 ? lowUv.y : highUv.y);
        limit = max(limit, length(unproject(uv,minimumDepth)));
        limit = max(limit, length(unproject(uv,maximumDepth)));
    }
    return min(maximumDistance, limit + max(1e-4,limit*1e-4));
}
float2 fogInterval(uint step, float maximumDistance) {
    float2 t = float2(step, step + 1u) / float(pc.steps);
    float2 bounds = maximumDistance * t * t;
    return float2(bounds.x, bounds.y - bounds.x);
}
float intervalDensity(FogVolumeData medium, float3 direction, float2 interval) {
    // Four-point Gauss integration resolves density variation inside distant, wide cells.
    const float4 nodes = float4(0.0694318442, 0.3300094782, 0.6699905218, 0.9305681558);
    const float4 weights = float4(0.1739274226, 0.3260725774, 0.3260725774, 0.1739274226);
    float density = 0.0;
    for (uint i = 0; i < 4; i++) {
        float3 p = direction * (interval.x + nodes[i] * interval.y) + pc.mediumCamera.xyz;
        density += weights[i] * fogDensity(mediumFromFogVolume(medium), p);
    }
    return density;
}
float fogSamplePhase(uint2 pixel, uint step) {
    uint hash = pixel.x * 0x9e3779b9u ^ pixel.y * 0x85ebca6bu ^ step * 0xc2b2ae35u
              ^ asuint(pc.mediumCamera.w) * 0x27d4eb2du;
    hash ^= hash >> 16; hash *= 0x7feb352du; hash ^= hash >> 15; hash *= 0x846ca68bu; hash ^= hash >> 16;
    return (float(hash >> 8) + 0.5) / 16777216.0;
}
float4 fogPrefixAt(uint2 pixel, uint2 extent, float distance, float maximumDistance) {
    float cell = sqrt(saturate(distance / maximumDistance)) * float(pc.steps);
    uint step = min(uint(cell), pc.steps - 1u);
    float2 interval = fogInterval(step, maximumDistance);
    float4* prefix = (float4*)pc.prefixAddress;
    uint planeSize = extent.x * extent.y;
    // The terminal prefix integrates only to its conservative depth limit.
    float limit = prefix[pixel.x + pixel.y * extent.x + (pc.steps + 1u) * planeSize].x;
    interval.y = min(interval.y, max(0.0, limit - interval.x));
    uint base = pixel.x + pixel.y * extent.x + step * planeSize;
    float4 before = prefix[base], after = prefix[base + planeSize];
    if (interval.y <= 0.0) return before;
    float fraction = saturate((distance - interval.x) / interval.y);
    if (fraction <= 0.0) return before;
    if (fraction >= 1.0) return after;
    // Integrate the partial cell with the same homogeneous coefficients as its stored endpoints.
    if (before.a <= 0.0) return before;
    float segmentT = saturate(after.a / before.a);
    float partialT = pow(segmentT, fraction);
    float scatteringFraction = 1.0 - segmentT > 1e-5 ? (1.0 - partialT) / (1.0 - segmentT) : fraction;
    return float4(lerp(before.rgb, after.rgb, scatteringFraction), before.a * partialT);
}
float fogDistance(float2 uv, float depth, float maximumDistance) {
    return depth <= 0.0 ? maximumDistance : min(length(unproject(uv, depth)), maximumDistance);
}
// Fraction of a pixel's path that a column may run past its own first surface while keeping half its weight.
static const float FOG_COLUMN_PENETRATION = 0.1;
float4 fogTransportAt(float2 uv, float depth, uint2 extent, float maximumDistance) {
    float distance = fogDistance(uv, depth, maximumDistance);
    Texture2D<float> primaryDepth = ResourceDescriptorHeap[pc.depthIndex];
    uint w,h; primaryDepth.GetDimensions(w,h);
    float2 grid = uv * float2(extent) - 0.5;
    int2 base = int2(floor(grid));
    float2 blend = grid - floor(grid);
    float4 volume = 0.0;
    float total = 0.0;
    for (int y=0;y<2;y++) for (int x=0;x<2;x++) {
        int2 p = clamp(base+int2(x,y), int2(0), int2(extent-1));
        // A column integrates along its center ray through every surface. The jittered primary sample
        // nearest that ray gives the column's first physical surface.
        float2 center = (float2(p) + 0.5) / float2(extent) * float2(w,h);
        int2 sample = clamp(int2(floor(center - pc.traceData.xy)), int2(0), int2(w-1,h-1));
        float columnDistance = fogDistance((float2(sample) + 0.5 + pc.traceData.xy) / float2(w,h),
                primaryDepth.Load(int3(sample,0)), maximumDistance);
        // Past that surface the column's transport lies inside or behind an occluder beside this pixel's ray.
        // Columns that stay clear to this pixel's endpoint keep their bilinear weight.
        float penetration = max(0.0, distance - columnDistance) / (FOG_COLUMN_PENETRATION * distance);
        penetration *= penetration;
        float weight = (x == 0 ? 1-blend.x : blend.x)*(y == 0 ? 1-blend.y : blend.y)
                     / (1.0 + penetration * penetration);
        volume += fogPrefixAt(uint2(p), extent, distance, maximumDistance) * weight;
        total += weight;
    }
    return volume / total;
}
float4 previousLighting(float3 center, uint2 extent, float maximumDistance, float3 lightDirection) {
    float4 q = pc.previousClipX * center.x + pc.previousClipY * center.y
             + pc.previousClipZ * center.z + pc.previousClipW;
    if (q.w <= 0.0) return 0.0;
    float4* history = (float4*)pc.previousHistoryAddress;
    float4 header = history[0];
    if (asuint(header.w) != asuint(pc.mediumCamera.w) - 1u
        || dot(header.xyz, lightDirection) < 0.9999) return 0.0;
    float2 uv = q.xy / q.w * 0.5 + 0.5;
    if (any(uv < 0.0) || any(uv > 1.0)) return 0.0;
    float4 previousPoint = history[1] * q.x + history[2] * q.y + history[3] * q.z + history[4] * q.w;
    float distance = length(previousPoint.xyz / previousPoint.w);
    if (distance >= maximumDistance) return 0.0;
    float depthCell = sqrt(max(0.0, distance * float(pc.steps * pc.steps) / maximumDistance - 0.25)) - 0.5;
    float3 grid = float3(uv * float2(extent) - 0.5, depthCell);
    int3 base = int3(floor(grid));
    float3 blend = grid - floor(grid);
    float4 result = 0.0;
    for (int z = 0; z < 2; z++) for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
        int3 cell = clamp(base + int3(x,y,z), int3(0), int3(extent - 1u, pc.steps - 1u));
        float weight = (x == 0 ? 1.0-blend.x : blend.x) * (y == 0 ? 1.0-blend.y : blend.y)
                     * (z == 0 ? 1.0-blend.z : blend.z);
        float4 sample = history[5u + uint(cell.x) + uint(cell.y) * extent.x + uint(cell.z) * extent.x * extent.y];
        result.rgb += sample.rgb * (sample.a * weight);
        result.a += sample.a * weight;
    }
    if (result.a > 0.0) result.rgb /= result.a;
    return result;
}
public void runFog(uint3 id) {
    RWTexture2D<float4> output = ResourceDescriptorHeap[pc.destinationIndex];
    uint w,h; output.GetDimensions(w,h);
    if(any(id.xy >= uint2(w,h))) return;
    float2 uv = (float2(id.xy)+0.5)/float2(w,h);
    FogVolumeData medium = ((FogVolumeData*)pc.volumeAddress)[0];
    uint visibilityCount = 8;
    uint firstStep = uint(pc.traceData.w);
    uint visibilityBase = (id.x+id.y*w)*visibilityCount;
    if(pc.mode == 2) {
        float3 direction = normalize(unproject(uv,0.0001));
        float4* rays = (float4*)pc.visibilityRaysAddress;
        float4* prefix = (float4*)pc.prefixAddress;
        // Each depth plane is contiguous so neighboring invocations access neighboring transport values.
        uint prefixBase = id.x + id.y * w;
        // The extra plane retains the conservative limit across all eight-sample batches.
        uint limitIndex = prefixBase + (pc.steps+1u)*w*h;
        if (firstStep == 0u) prefix[limitIndex] = float4(fogDepthLimit(id.xy,uint2(w,h),medium.profile.w),0,0,0);
        float depthLimit = prefix[limitIndex].x;
        for(uint j=0;j<visibilityCount;j++) {
            uint step = firstStep+j;
            float2 interval = fogInterval(step, medium.profile.w);
            // Sampling beyond the terminal limit can import light from behind the visible boundary.
            interval.y = min(interval.y, max(0.0, depthLimit - interval.x));
            float3 p = direction*(interval.x+fogSamplePhase(id.xy,step)*interval.y);
            float sigma = step < pc.steps && interval.y > 0.0
                    ? intervalDensity(medium, direction, interval) : 0.0;
            // The integration dispatch consumes this density before replacing it with cumulative transport.
            if (step < pc.steps) prefix[prefixBase+(step+1u)*w*h] = float4(0,0,0,sigma);
            uint base = (visibilityBase+j)*4;
            rays[base] = float4(p+pc.tlasCamera.xyz,pc.tlasCamera.w);
            rays[base+1] = float4(direction, 0.45);
            uint seed = (id.x + id.y*w) * 0x9e3779b9u ^ step * 0x85ebca6bu ^ asuint(pc.mediumCamera.w) * 0xc2b2ae35u;
            rays[base+2] = float4(uv, asfloat(seed), sigma > 0.0 ? 1.0 : 0.0);
            rays[base+3] = float4(medium.ambientRadiance.xyz, 0.0);
        }
        return;
    }
    if(pc.mode == 0) {
        float3 direction = normalize(unproject(uv,0.0001));
        float4 accumulated = firstStep == 0 ? float4(0,0,0,1) : output[id.xy];
        float transmittance = accumulated.a;
        float3 scattering = accumulated.rgb;
        float4* prefix = (float4*)pc.prefixAddress;
        float4* history = (float4*)pc.historyAddress;
        if (firstStep == 0u && all(id.xy == uint2(0))) {
            history[0] = float4(medium.lightDirection.xyz, pc.mediumCamera.w);
            history[1] = pc.clipX; history[2] = pc.clipY; history[3] = pc.clipZ; history[4] = pc.clipW;
        }
        uint prefixBase = id.x + id.y * w;
        if (firstStep == 0u) prefix[prefixBase] = accumulated;
        float3 lighting = 0.0;
        float4* visibilityResults = (float4*)pc.visibilityResultsAddress;
        for(uint i=firstStep;i<min(firstStep+visibilityCount,pc.steps);i++) {
            float2 interval = fogInterval(i, medium.profile.w);
            float depthLimit = prefix[prefixBase + (pc.steps + 1u)*w*h].x;
            interval.y = min(interval.y, max(0.0, depthLimit - interval.x));
            float segmentLength = interval.y;
            float3 p = direction*(interval.x+0.5*segmentLength);
            float sigma = prefix[prefixBase+(i+1u)*w*h].a;
            if (sigma == 0.0) {
                history[5u + id.x + id.y * w + i*w*h] = 0.0;
                prefix[prefixBase+(i+1u)*w*h] = float4(scattering,transmittance);
                continue;
            }
            lighting = visibilityResults[visibilityBase+i-firstStep].rgb;
            float4 previous = previousLighting(p, uint2(w,h), medium.profile.w, medium.lightDirection.xyz);
            float3 minimumLighting = lighting, maximumLighting = lighting;
            float4* rays = (float4*)pc.visibilityRaysAddress;
            for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
                uint2 neighbor = uint2(clamp(int2(id.xy) + int2(x,y), int2(0), int2(w-1,h-1)));
                uint sampleIndex = (neighbor.x + neighbor.y * w) * visibilityCount + i - firstStep;
                if (rays[sampleIndex * 4u + 2u].w == 0.0) continue;
                float3 currentLighting = visibilityResults[sampleIndex].rgb;
                minimumLighting = min(minimumLighting, currentLighting);
                maximumLighting = max(maximumLighting, currentLighting);
            }
            // Current local lighting bounds retire stale radiance when lights or occluders change.
            previous.rgb = clamp(previous.rgb, minimumLighting, maximumLighting);
            float confidence = min(previous.a + 1.0, 8.0);
            lighting = lerp(previous.rgb, lighting, 1.0 / confidence);
            history[5u + id.x + id.y * w + i*w*h] = float4(lighting, confidence);
            float segmentT = exp(-sigma*segmentLength);
            float3 source = lighting * pc.traceData.z;
            scattering += transmittance * (1.0-segmentT) * source * 0.95;
            transmittance *= segmentT;
            prefix[prefixBase+(i+1u)*w*h] = float4(scattering,transmittance);
        }
        output[id.xy] = float4(scattering,transmittance);
    } else {
        Texture2D<float4> fog = ResourceDescriptorHeap[pc.fogIndex];
        uint fw,fh; fog.GetDimensions(fw,fh);
        // Scene effects share the traced pixel's jitter and physical endpoint before reconstruction.
        Texture2D<float> depth = ResourceDescriptorHeap[pc.depthIndex];
        uv += pc.traceData.xy / float2(w,h);
        float4 volume = fogTransportAt(uv, depth.Load(int3(id.xy,0)), uint2(fw,fh), medium.profile.w);
        if(pc.debug == 1) { output[id.xy] = float4(volume.aaa,1.0); return; }
        if(pc.debug == 2) { output[id.xy] = float4(volume.rgb,1.0); return; }
        Texture2D<float4> scene = ResourceDescriptorHeap[pc.sceneIndex];
        float4 color = scene.Load(int3(id.xy,0));
        output[id.xy] = float4(color.rgb*volume.a+volume.rgb,color.a);
    }
}
