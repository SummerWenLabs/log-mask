package io.github.summerwenlabs.log.mask;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskStrategyRegistryTest {

    @Test
    void validatesAndMatchesCustomCodesExactly() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MaskStrategyRegistry.of(Collections.singletonList(definition(""))));
        assertThrows(
                IllegalArgumentException.class,
                () -> MaskStrategyRegistry.of(Collections.singletonList(definition("   "))));
        assertThrows(
                IllegalArgumentException.class,
                () -> MaskStrategyRegistry.of(Collections.singletonList(definition(" code"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> MaskStrategyRegistry.of(Collections.singletonList(definition("code\u00A0"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> MaskStrategyRegistry.of(
                        Arrays.asList(definition("duplicate"), definition("duplicate"))));

        MaskStrategyRegistry registry = MaskStrategyRegistry.of(
                Arrays.asList(definition("PHONE"), definition("phone"), definition("internal code")));

        assertEquals("PHONE", registry.find("PHONE").get().getTypeCode());
        assertEquals("phone", registry.find("phone").get().getTypeCode());
        assertFalse(registry.find("Phone").isPresent());
        assertTrue(registry.find("internal code").isPresent());
    }

    private static MaskTypeDefinition definition(final String code) {
        return new MaskTypeDefinition() {
            @Override
            public String getTypeCode() {
                return code;
            }

            @Override
            public String mask(String value) {
                return "masked";
            }
        };
    }
}
