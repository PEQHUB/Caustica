package dev.comfyfluffy.caustica.rt.pipeline;

import dev.comfyfluffy.caustica.CausticaConfig;
import java.util.List;

/**
 * Central registry of stable tone-mapper mode IDs, config names, and aliases. Owns only the
 * mode table and the immutable {@link Settings} record read by the display dispatch. Does not
 * own Vulkan resources, shader compilation, pipeline lifetime, config file I/O, or Minecraft widgets.
 *
 * <p>SDR mode 0 (AgX) and HDR mode 0 (Caustica) are the upstream defaults; unknown config values
 * fall back to those respectively. The integer IDs are mirrored as constants in
 * {@code shaders/display/tonemap/dispatch.glsl}.
 */
public final class RtToneMapping {
    private RtToneMapping() {
    }

    /** Stable SDR tone-mapper modes. ID mirrors the GLSL constant in dispatch.glsl. */
    public enum SdrMode {
        AGX(0, "agx"),
        PBR_NEUTRAL(1, "pbr-neutral"),
        REINHARD(2, "reinhard"),
        ACES(3, "aces"),
        LOTTES(4, "lottes"),
        FROSTBITE(5, "frostbite"),
        UNCHARTED_2(6, "uncharted2", "uncharted-2"),
        GT(7, "gt", "uchimura"),
        PSYCHOV11(8, "psychov11", "psychov"),
        PSYCHOV23(9, "psychov23"),
        PSYCHOV24_EXPERIMENTAL(
                10,
                "psychov24-experimental",
                "psychov24");

        private final int id;
        private final String canonicalName;
        private final List<String> aliases;

        SdrMode(int id, String canonicalName, String... aliases) {
            this.id = id;
            this.canonicalName = canonicalName;
            this.aliases = List.of(aliases);
        }

        public int id() {
            return id;
        }

        public String canonicalName() {
            return canonicalName;
        }

        /** Case-insensitive parse with whitespace trimming; unknown values fall back to {@link #AGX}. */
        public static SdrMode parse(String value) {
            if (value != null) {
                String trimmed = value.trim();
                for (SdrMode mode : values()) {
                    if (mode.canonicalName.equalsIgnoreCase(trimmed)) {
                        return mode;
                    }
                    for (String alias : mode.aliases) {
                        if (alias.equalsIgnoreCase(trimmed)) {
                            return mode;
                        }
                    }
                }
            }
            return AGX;
        }
    }

    /** Stable HDR tone-mapper modes. ID mirrors the GLSL constant in dispatch.glsl. */
    public enum HdrMode {
        CAUSTICA(0, "caustica"),
        BT2390(1, "bt2390", "bt-2390"),
        PSYCHOV11(2, "psychov11", "psychov"),
        PSYCHOV23(3, "psychov23"),
        PSYCHOV24_EXPERIMENTAL(
                4,
                "psychov24-experimental",
                "psychov24");

        private final int id;
        private final String canonicalName;
        private final List<String> aliases;

        HdrMode(int id, String canonicalName, String... aliases) {
            this.id = id;
            this.canonicalName = canonicalName;
            this.aliases = List.of(aliases);
        }

        public int id() {
            return id;
        }

        public String canonicalName() {
            return canonicalName;
        }

        /** Case-insensitive parse with whitespace trimming; unknown values fall back to {@link #CAUSTICA}. */
        public static HdrMode parse(String value) {
            if (value != null) {
                String trimmed = value.trim();
                for (HdrMode mode : values()) {
                    if (mode.canonicalName.equalsIgnoreCase(trimmed)) {
                        return mode;
                    }
                    for (String alias : mode.aliases) {
                        if (alias.equalsIgnoreCase(trimmed)) {
                            return mode;
                        }
                    }
                }
            }
            return CAUSTICA;
        }
    }

    /** Immutable snapshot of the current display tone-mapping settings, read every display dispatch. */
    public record Settings(
            boolean hdrEnabled,
            int sdrMode,
            int hdrMode,
            float paperWhiteNits,
            float headroom) {
    }

    /** Immutable canonical SDR mode names in enum order, for the Video Settings cycle control. */
    public static List<String> sdrConfigNames() {
        SdrMode[] values = SdrMode.values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].canonicalName;
        }
        return List.of(names);
    }

    /** Immutable canonical HDR mode names in enum order, for the Video Settings cycle control. */
    public static List<String> hdrConfigNames() {
        HdrMode[] values = HdrMode.values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].canonicalName;
        }
        return List.of(names);
    }

    /** Read the current sanitized config values as an immutable {@link Settings} snapshot. */
    public static Settings current() {
        return new Settings(
                CausticaConfig.Rt.Hdr.enabled(),
                SdrMode.parse(CausticaConfig.Rt.Sdr.TONE_MAPPER.get()).id(),
                HdrMode.parse(CausticaConfig.Rt.Hdr.TONE_MAPPER.get()).id(),
                CausticaConfig.Rt.Hdr.paperWhiteNits(),
                CausticaConfig.Rt.Hdr.headroom());
    }
}
