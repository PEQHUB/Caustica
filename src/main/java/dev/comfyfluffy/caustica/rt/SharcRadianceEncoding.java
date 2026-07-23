package dev.comfyfluffy.caustica.rt;

/**
 * Compile-time radiance encoding mode for SHaRC cache entries.
 *
 * <p>Each mode compiles to a completely separate shader family with incompatible buffer layouts.
 * The active mode determines both the GPU pipeline selection and the per-element cache allocation
 * size. Switching modes requires a full cache reset and pipeline swap.</p>
 *
 * <p>RGB mode uses the original 16-byte accumulation + 16-byte resolved layout (40 bytes/entry
 * including the 8-byte hash key). DIRECTIONAL_SH mode adds spherical-harmonic directional encoding
 * for glossy contamination reduction at 32-byte accumulation + 24-byte resolved (64 bytes/entry).</p>
 */
public enum SharcRadianceEncoding {
    /** Original directionless RGB radiance cache. Minimal memory, no directional information. */
    RGB(
            16,
            16,
            ""
    ),

    /**
     * Low-order directional SH encoding. Reduces glossy/specular cache contamination by retaining
     * directional information alongside radiance. Costs approximately 60% more cache memory and
     * adds encoding/decoding overhead.
     */
    DIRECTIONAL_SH(
            32,
            24,
            "_sh"
    );

    private static final long HASH_ENTRY_BYTES = 8L;

    private final int accumulationStride;
    private final int resolvedStride;
    private final String shaderSuffix;

    SharcRadianceEncoding(
            int accumulationStride,
            int resolvedStride,
            String shaderSuffix
    ) {
        this.accumulationStride = accumulationStride;
        this.resolvedStride = resolvedStride;
        this.shaderSuffix = shaderSuffix;
    }

    /** Bytes per entry in the accumulation buffer (SharcAccumulationData). */
    public int accumulationStride() {
        return accumulationStride;
    }

    /** Bytes per entry in the resolved buffer (SharcPackedData). */
    public int resolvedStride() {
        return resolvedStride;
    }

    /**
     * Suffix appended to SHaRC shader filenames to select the matching compiled variant.
     * Empty for RGB, {@code "_sh"} for DIRECTIONAL_SH.
     */
    public String shaderSuffix() {
        return shaderSuffix;
    }

    /**
     * Total bytes required for the three SHaRC cache buffers (hash + accumulation + resolved)
     * at the given entry count.
     */
    public long cacheBytes(long capacity) {
        return Math.multiplyExact(
                capacity,
                HASH_ENTRY_BYTES + accumulationStride + resolvedStride
        );
    }
}
