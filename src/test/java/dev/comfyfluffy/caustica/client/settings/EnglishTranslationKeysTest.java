package dev.comfyfluffy.caustica.client.settings;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class EnglishTranslationKeysTest {
    private static final Pattern TOP_LEVEL_KEY =
            Pattern.compile("(?m)^\\s*\"([^\"]+)\"\\s*:");

    @Test
    void englishTranslationKeysAreUnique() throws IOException {
        Path languageFile = Path.of(
                "src/main/resources/assets/caustica/lang/en_us.json");

        String source = Files.readString(languageFile, UTF_8);

        Matcher matcher = TOP_LEVEL_KEY.matcher(source);

        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new TreeSet<>();

        while (matcher.find()) {
            String key = matcher.group(1);
            if (!seen.add(key)) {
                duplicates.add(key);
            }
        }

        assertTrue(duplicates.isEmpty(),
                () -> "Duplicate English translation keys: " + duplicates);
    }
}
