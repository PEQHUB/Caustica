package dev.comfyfluffy.caustica.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LocalizationContractTest {
    private static final Path LANG = Path.of("src/main/resources/assets/caustica/lang");
    private static final Pattern STATIC_TRANSLATION = Pattern.compile(
            "Component\\.translatable\\(\\s*\\\"(caustica\\.[^\\\"]+)\\\"");

    @Test
    void everyStaticCausticaTranslationKeyHasAnEnglishValue() throws Exception {
        Set<String> english = keys(LANG.resolve("en_us.json"));
        for (Path source : Files.walk(Path.of("src/main/java")).filter(Files::isRegularFile).toList()) {
            Matcher matcher = STATIC_TRANSLATION.matcher(Files.readString(source));
            while (matcher.find()) {
                String key = matcher.group(1);
                assertTrue(key.endsWith(".") || english.contains(key),
                        () -> source + " references missing English translation " + key);
            }
        }
    }

    @Test
    void partialLocalesCannotCarryStaleOrUnknownKeys() throws Exception {
        Set<String> english = keys(LANG.resolve("en_us.json"));
        for (Path locale : Files.list(LANG).filter(path -> path.toString().endsWith(".json")).toList()) {
            if (locale.getFileName().toString().equals("en_us.json")) continue;
            Set<String> extra = new HashSet<>(keys(locale));
            extra.removeAll(english);
            assertTrue(extra.isEmpty(), () -> locale + " contains keys absent from en_us: " + extra);
        }
    }

    @Test
    void settingsWorkstationDoesNotEmbedFixedEnglishLabels() throws Exception {
        String screen = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/client/CausticaSettingsScreen.java"));
        assertFalse(screen.contains("Component.literal(\"Changes save"));
        assertFalse(screen.contains("Component.literal(\"Dynamic FOV"));
        assertFalse(screen.contains("Component.literal(\"Frame Generation diagnostics"));
        assertFalse(Pattern.compile("add(?:Header|Bundle)\\(\\\"[^\\\"]+\\\",\\s*\\\"")
                .matcher(screen).find(), "headers and bundle descriptions must use localization keys");
    }

    @Test
    void dlssdPresetLabelsAreComplete() throws Exception {
        Set<String> english = keys(LANG.resolve("en_us.json"));
        assertTrue(english.contains("caustica.options.rt.dlssPreset"));
        assertTrue(english.contains("caustica.options.rt.dlssPreset.tooltip"));
        assertTrue(english.contains("caustica.options.rt.dlssPreset.default"));
        assertTrue(english.contains("caustica.options.rt.dlssPreset.d"));
        assertTrue(english.contains("caustica.options.rt.dlssPreset.e"));
    }

    @Test
    void captureAndFeatureStatusSurfacesDoNotEmbedFixedEnglishProse() throws Exception {
        String offline = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/client/OfflineGroundTruth.java"));
        String screenshot = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/client/UltraScreenshot.java"));
        String fg = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/client/RtFrameGenerationOptionsScreen.java"));
        String diagnostics = Files.readString(Path.of(
                "src/main/java/dev/comfyfluffy/caustica/client/RtFrameGenerationDiagnosticsScreen.java"));
        assertFalse(offline.contains("notify(minecraft, \""));
        assertFalse(screenshot.contains("notify(minecraft, \""));
        assertFalse(fg.contains("Component.literal(\"DLSS"));
        assertFalse(diagnostics.contains("Component.literal(\"Streamline"));
        assertFalse(diagnostics.contains("Component.literal(\"Runtime:"));
    }

    private static Set<String> keys(Path path) throws Exception {
        JsonObject object = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        return object.keySet();
    }
}
