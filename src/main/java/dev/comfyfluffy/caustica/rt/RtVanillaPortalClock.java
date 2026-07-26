package dev.comfyfluffy.caustica.rt;

import net.minecraft.world.level.Level;

/**
 * Reproduces the exact {@code GameTime} uniform value written by vanilla
 * Minecraft 26.2's {@code GlobalSettingsUniform.update(...)} and consumed
 * by {@code rendertype_end_portal.fsh}.
 *
 * <p>The vanilla shader declares:
 * <pre>{@code
 * layout(std140) uniform Globals {
 *     ivec3 CameraBlockPos;
 *     vec3 CameraOffset;
 *     vec2 ScreenSize;
 *     float GlintAlpha;
 *     float GameTime;
 *     int MenuBlurRadius;
 *     int UseRgss;
 * };
 * }</pre>
 *
 * <p>The portal animation uses {@code GameTime * 1.5} for its translation
 * term. The correct formula is:
 * <pre>{@code
 *   (level.getGameTime() % 24000L + partialTick) / 24000.0f
 * }</pre>
 *
 * <p>This yields a value in the range {@code [0, 1)} over one Minecraft day
 * cycle (24000 ticks = 20 real-time minutes). It wraps each in-game day.
 *
 * <p>This is <b>not</b> elapsed real-time seconds. A previous incorrect
 * implementation used {@code (level.getGameTime() + partialTick) / 20.0f},
 * which produces unbounded elapsed seconds and diverges from the vanilla
 * shader's portal animation.
 *
 * <p>This class is a pure static utility and holds no state. Frozen-time
 * management belongs in {@code RtComposite}, not here.
 */
public final class RtVanillaPortalClock {

    /** Number of ticks in one Minecraft day cycle. */
    private static final float TICKS_PER_DAY = 24000.0f;

    private RtVanillaPortalClock() {}

    /**
     * Compute the exact vanilla {@code GameTime} for the End portal shader.
     *
     * <p>The returned value cycles from 0.0 to just-under-1.0 over one
     * Minecraft day (24000 ticks). It wraps automatically, so the portal
     * animation loops seamlessly every 20 minutes of game time.
     *
     * @param level       the client level (used to read the current game tick)
     * @param partialTick sub-tick interpolation factor in {@code [0, 1)}
     * @return the {@code GameTime} uniform value in {@code [0, 1)}
     */
    public static float portalGameTime(Level level, float partialTick) {
        if (level == null) {
            return 0.0f;
        }
        return portalGameTime(level.getGameTime(), partialTick);
    }

    /** Pure form used by numerical tests and callers that already own the tick value. */
    public static float portalGameTime(long gameTime, float partialTick) {
        return ((float) Math.floorMod(gameTime, 24000L) + partialTick) / TICKS_PER_DAY;
    }
}
