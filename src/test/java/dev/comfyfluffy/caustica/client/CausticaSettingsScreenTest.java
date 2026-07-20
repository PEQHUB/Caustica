package dev.comfyfluffy.caustica.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CausticaSettingsScreenTest {
    @Test
    void orderedRetainsControlsOmittedFromThePreferredPermutation() {
        assertEquals(List.of("third", "first", "second", "fourth"),
                CausticaSettingsScreen.ordered(List.of("first", "second", "third", "fourth"), 2, 0));
    }

    @Test
    void orderedIgnoresInvalidAndDuplicateIndicesWithoutBreakingTheMenu() {
        assertEquals(List.of("second", "first", "third"),
                CausticaSettingsScreen.ordered(List.of("first", "second", "third"), 1, 1, -1, 99));
    }
}
