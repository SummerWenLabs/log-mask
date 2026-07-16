package io.github.summerwenlabs.log.mask;

import java.util.Arrays;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.summerwenlabs.log.mask.governance.Mask;
import io.github.summerwenlabs.log.mask.governance.MaskType;
import io.github.summerwenlabs.log.mask.strategy.MaskStrategyRegistry;
import io.github.summerwenlabs.log.mask.strategy.MaskTypeDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentMaskingTest {

    @Test
    void shortMalformedEmptyAndNullValuesHaveSafeDeterministicResults() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();

        JsonNode result = mapper.readTree(masker.mask(new BoundaryValues()));

        assertEquals("**", result.path("shortPhone").textValue());
        assertEquals("*********", result.path("malformedEmail").textValue());
        assertEquals("**", result.path("malformedIdCard").textValue());
        assertEquals("*****", result.path("malformedBankCard").textValue());
        assertEquals("", result.path("emptyPhone").textValue());
        assertTrue(result.path("nullEmail").isNull());
        assertEquals("<redacted>", result.path("nonStringPhone").textValue());
    }

    @Test
    void customAndBuiltInPhoneCodesUseSeparateNamespaces() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        MaskStrategyRegistry registry = MaskStrategyRegistry.of(Arrays.asList(
                definition("PHONE", "custom-phone"),
                new MaskTypeDefinition() {
                    @Override
                    public String getTypeCode() {
                        return "UNCHANGED";
                    }

                    @Override
                    public String mask(String value) {
                        return value;
                    }
                },
                definition("EMPTY", "")));
        LogMasker masker = LogMasker.builder(mapper)
                .strategyRegistry(registry)
                .build();

        JsonNode result = mapper.readTree(masker.mask(new NamespaceValues()));

        assertEquals("138****8000", result.path("builtInPhone").textValue());
        assertEquals("custom-phone", result.path("customPhone").textValue());
        assertEquals("explicitly-unchanged", result.path("unchanged").textValue());
        assertEquals("", result.path("empty").textValue());
    }

    private static MaskTypeDefinition definition(final String code, final String result) {
        return new MaskTypeDefinition() {
            @Override
            public String getTypeCode() {
                return code;
            }

            @Override
            public String mask(String value) {
                return result;
            }
        };
    }

    private static final class BoundaryValues {
        @Mask(type = MaskType.PHONE)
        public String getShortPhone() {
            return "12";
        }

        @Mask(type = MaskType.EMAIL)
        public String getMalformedEmail() {
            return "bad-email";
        }

        @Mask(type = MaskType.ID_CARD)
        public String getMalformedIdCard() {
            return "A\uD83D\uDE00";
        }

        @Mask(type = MaskType.BANK_CARD)
        public String getMalformedBankCard() {
            return "1234x";
        }

        @Mask(type = MaskType.PHONE)
        public String getEmptyPhone() {
            return "";
        }

        @Mask(type = MaskType.EMAIL)
        public String getNullEmail() {
            return null;
        }

        @Mask(type = MaskType.PHONE)
        public int getNonStringPhone() {
            return 138;
        }
    }

    private static final class NamespaceValues {
        @Mask(type = MaskType.PHONE)
        public String getBuiltInPhone() {
            return "13800138000";
        }

        @Mask(typeCode = "PHONE")
        public String getCustomPhone() {
            return "13800138000";
        }

        @Mask(typeCode = "UNCHANGED")
        public String getUnchanged() {
            return "explicitly-unchanged";
        }

        @Mask(typeCode = "EMPTY")
        public String getEmpty() {
            return "value";
        }
    }
}
