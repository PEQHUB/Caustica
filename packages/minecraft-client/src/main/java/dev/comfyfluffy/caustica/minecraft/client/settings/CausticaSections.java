package dev.comfyfluffy.caustica.minecraft.client.settings;

import dev.comfyfluffy.caustica.minecraft.client.config.CausticaConfig;
import dev.comfyfluffy.caustica.minecraft.client.MinecraftOptions;
import dev.comfyfluffy.caustica.minecraft.client.config.CausticaOptions;
import dev.comfyfluffy.caustica.minecraft.client.MinecraftDisplayText;
import dev.comfyfluffy.caustica.settings.FeatureSettings;
import dev.comfyfluffy.caustica.settings.Option;
import dev.comfyfluffy.caustica.settings.ResourceId;
import dev.comfyfluffy.caustica.settings.SettingsRegistry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Builds the screen's pages from engine settings and extension settings. Nothing here knows about widgets, so the
 * whole model is exercised in tests without a GUI stack.
 */
public final class CausticaSections {
    /**
     * Engine groups in the order they appear. Explicit because {@code Option.group()} carries the
     * membership but not the ordering, and a screen that reorders itself between launches is worse than a
     * list to maintain.
     */
    private static final List<String> ENGINE_GROUPS =
            List.of("general", "quality", "radiance-cache", "upscaling", "exposure", "look", "output", "entities",
                    "debug");

    private static final int ACCENT_ENGINE = 0xFF4FC3F7;
    private static final int ACCENT_BLOOM = 0xFFFFB74D;
    /**
     * Accents for third-party features, picked by a stable hash so a given extension keeps its colour across
     * launches. Hues from Minecraft's own material vocabulary rather than arbitrary saturated colour.
     */
    private static final int[] ACCENT_WHEEL = {
            0xFF81C784, 0xFFF06292, 0xFF4DD0E1, 0xFFFFD54F,
            0xFFA1887F, 0xFF9575CD, 0xFF4DB6AC, 0xFFFF8A65};

    private CausticaSections() {
    }

    public static List<SettingsSection> build(SettingsRegistry registry, CausticaOptions options) {
        return build(registry, options, ignored -> true);
    }

    public static List<SettingsSection> build(SettingsRegistry registry, CausticaOptions options,
                                               Predicate<Option<?>> available) {
        List<SettingsSection> sections = new ArrayList<>();
        sections.add(engine(options, available));
        for (FeatureSettings feature : registry.all()) {
            if (feature.id().equals(CausticaConfig.FEATURE)) continue;
            SettingsSection section = feature(feature, options);
            if (section != null) {
                sections.add(section);
            }
        }
        return List.copyOf(sections);
    }

    static SettingsSection engine(CausticaOptions options, Predicate<Option<?>> available) {
        Map<String, List<SettingControl>> byGroup = new LinkedHashMap<>();
        for (String group : ENGINE_GROUPS) {
            byGroup.put(group, new ArrayList<>());
        }
        for (Option<?> setting : MinecraftOptions.allSettings()) {
            List<SettingControl> rows = byGroup.get(setting.group());
            if (rows == null) {
                continue;
            }
            SettingControl control = OptionControls.of(options, CausticaConfig.FEATURE, setting, available);
            if (control != null) {
                rows.add(control);
            }
        }
        List<SettingGroup> groups = new ArrayList<>();
        byGroup.forEach((id, rows) -> {
            if (!rows.isEmpty()) {
                groups.add(new SettingGroup(id, LangKeys.engineGroup(id), null, rows));
            }
        });
        return new SettingsSection("engine", Component.translatable("caustica.section.engine"),
                ACCENT_ENGINE, groups);
    }

    /** Null for a feature without options that the native screen can display. */
    static SettingsSection feature(FeatureSettings feature, CausticaOptions options) {
        if (feature.options().isEmpty()) {
            return null;
        }
        List<SettingGroup> groups = new ArrayList<>();
        for (String groupId : feature.optionGroups()) {
            SettingControl.BoolControl header = null;
            List<SettingControl> rows = new ArrayList<>();
            for (Option<?> option : feature.options()) {
                if (!groupId.equals(option.group())) {
                    continue;
                }
                SettingControl control = OptionControls.of(options, feature.id(), option);
                if (control == null) continue;
                if (option.isGroupHeader()) {
                    header = (SettingControl.BoolControl) control;
                } else {
                    rows.add(control);
                }
            }
            if (header != null || !rows.isEmpty()) {
                groups.add(new SettingGroup(groupId, LangKeys.optionGroup(feature.id(), groupId), header, rows));
            }
        }
        List<SettingControl> ungrouped = new ArrayList<>();
        for (Option<?> option : feature.options()) {
            if (option.group() == null) {
                SettingControl control = OptionControls.of(options, feature.id(), option);
                if (control != null) ungrouped.add(control);
            }
        }
        if (!ungrouped.isEmpty()) {
            groups.add(new SettingGroup("other", Component.translatable("caustica.group.other"), null, ungrouped));
        }
        if (groups.isEmpty()) return null;
        return new SettingsSection(feature.id().toString(), MinecraftDisplayText.component(feature.title()),
                accentFor(feature.id()), groups);
    }

    static int accentFor(ResourceId featureId) {
        if (featureId.equals(ResourceId.of("caustica", "bloom"))) {
            return ACCENT_BLOOM;
        }
        return ACCENT_WHEEL[Math.floorMod(featureId.hashCode(), ACCENT_WHEEL.length)];
    }
}
