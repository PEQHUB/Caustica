module retained_trace_reordered;

import retained_trace_policy;
import bindings;
import world_minimal;

public struct RetainedReorderedTrace : IRetainedRadianceTrace {
    public void trace(inout WorldPayload payload, RayDesc ray, uint pathPhaseHint) {
        HitObject hit = HitObject::TraceRay(worldAccelerationStructure(), RAY_FLAG_NONE,
                WORLD_RAY_MASK_SECONDARY, 0u, 1u, 0u, ray, payload);
        ReorderThread(hit, pathPhaseHint << 1u, 4u);
        if (hit.IsMiss()) {
            payload.hitDistance = -1.0;
            payload.pathFlags |= WORLD_PATH_MISS;
        } else {
            payload.geometryRecordIndex = hit.GetInstanceID() + hit.GetGeometryIndex();
            payload.primitiveIndex = hit.GetPrimitiveIndex();
            payload.barycentrics = hit.GetAttributes<BuiltInTriangleIntersectionAttributes>().barycentrics;
            // A hit object's ray limit is the accepted hit parameter.
            payload.hitDistance = hit.GetRayDesc().TMax;
            payload.pathFlags &= ~WORLD_PATH_MISS;
        }
    }
};
