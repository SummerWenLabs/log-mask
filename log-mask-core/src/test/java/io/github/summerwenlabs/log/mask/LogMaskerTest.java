package io.github.summerwenlabs.log.mask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogMaskerTest {

    @Test
    void returnsJsonUsingConfiguredJacksonPropertyNames() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        LogMasker masker = LogMasker.builder(mapper).build();

        JsonNode result = mapper.readTree(masker.mask(new Person("Ada")));

        assertEquals("Ada", result.path("display_name").textValue());
        assertFalse(result.has("displayName"));
    }

    @Test
    void excludedPropertyIsSkippedBeforeItsGetterIsRead() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();
        ExcludedValue value = new ExcludedValue();

        JsonNode result = mapper.readTree(masker.mask(value));

        assertEquals("visible", result.path("visible").textValue());
        assertFalse(result.has("secret"));
        assertEquals(0, value.secretReads);
    }

    @Test
    void excludedArrayShapedPropertyIsSkippedBeforeItsGetterIsRead() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();
        ArrayShapedValue value = new ArrayShapedValue();

        JsonNode result = mapper.readTree(masker.mask(value));

        assertEquals(1, result.size());
        assertEquals("visible", result.path(0).textValue());
        assertEquals(0, value.secretReads);
    }

    @Test
    void redactedPropertyWritesAPlaceholderWithoutReadingItsGetter() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();
        RedactedValue value = new RedactedValue(new Object());

        JsonNode result = mapper.readTree(masker.mask(value));

        assertEquals("<redacted>", result.path("secret").textValue());
        assertEquals(0, value.secretReads);
    }

    @Test
    void redactedNullStillWritesTheSamePlaceholderWithoutReadingItsGetter() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();
        RedactedValue value = new RedactedValue(null);

        JsonNode result = mapper.readTree(masker.mask(value));

        assertEquals("<redacted>", result.path("secret").textValue());
        assertEquals(0, value.secretReads);
    }

    @Test
    void exclusionOnAFieldWinsRedactionOnTheGetter() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();
        ExcludedFieldRedactedGetter value = new ExcludedFieldRedactedGetter();

        JsonNode result = mapper.readTree(masker.mask(value));

        assertFalse(result.has("secret"));
        assertEquals(0, value.secretReads);
    }

    @Test
    void mixinGovernanceUsesTheFinalJacksonPropertyName() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(MixinValue.class, MixinValueGovernance.class);
        LogMasker masker = LogMasker.builder(mapper).build();
        MixinValue value = new MixinValue();

        JsonNode result = mapper.readTree(masker.mask(value));

        assertEquals("<redacted>", result.path("external_secret").textValue());
        assertFalse(result.has("secret"));
        assertEquals(0, value.secretReads);
    }

    @Test
    void fieldGovernanceFollowsConfiguredJacksonVisibility() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        LogMasker masker = LogMasker.builder(mapper).build();

        JsonNode result = mapper.readTree(masker.mask(new FieldValue()));

        assertEquals("visible", result.path("plain").textValue());
        assertEquals("<redacted>", result.path("secret").textValue());
    }

    @Test
    void identicalFieldAndGetterDeclarationsMergeIntoOneRule() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();
        DuplicateRedaction value = new DuplicateRedaction();

        JsonNode result = mapper.readTree(masker.mask(value));

        assertEquals(1, result.size());
        assertEquals("<redacted>", result.path("secret").textValue());
        assertEquals(0, value.secretReads);
    }

    @Test
    void inheritedJacksonPropertiesKeepTheirGovernanceRules() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();
        InheritedValue value = new InheritedValue();

        JsonNode result = mapper.readTree(masker.mask(value));

        assertEquals("visible", result.path("visible").textValue());
        assertEquals("child", result.path("child").textValue());
        assertEquals("<redacted>", result.path("secret").textValue());
        assertEquals(0, value.secretReads());
    }

    @Test
    void overallSerializationFailureThrowsLogMaskException() {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();
        IllegalStateException failure = new IllegalStateException("expected failure");

        LogMaskException thrown = assertThrows(
                LogMaskException.class,
                () -> masker.mask(new FailingValue(failure)));

        assertSame(failure, rootCause(thrown));
    }

    @Test
    void maskingDoesNotModifyTheObjectOrApplicationMapper() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();
        RedactedValue value = new RedactedValue("original");

        JsonNode safeResult = mapper.readTree(masker.mask(value));

        assertEquals("<redacted>", safeResult.path("secret").textValue());
        assertEquals("original", value.secret());
        assertEquals(0, value.secretReads);

        JsonNode applicationResult = mapper.readTree(mapper.writeValueAsString(value));
        assertEquals("original", applicationResult.path("secret").textValue());
        assertEquals(1, value.secretReads);
    }

    @Test
    void oneMaskerCanBeReusedConcurrentlyWithoutCrossCallState() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();
        ExecutorService callers = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
            for (int index = 0; index < 64; index++) {
                final int valueIndex = index;
                tasks.add(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        JsonNode result = mapper.readTree(masker.mask(new ConcurrentValue(valueIndex)));
                        assertEquals(valueIndex, result.path("id").intValue());
                        assertEquals("<redacted>", result.path("secret").textValue());
                        return null;
                    }
                });
            }

            List<Future<Void>> results = callers.invokeAll(tasks);
            for (Future<Void> result : results) {
                result.get();
            }
        } finally {
            callers.shutdownNow();
        }
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable result = throwable;
        while (result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

    private static final class Person {
        private final String displayName;

        private Person(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static final class ExcludedValue {
        private int secretReads;

        public String getVisible() {
            return "visible";
        }

        @LogExclude
        public String getSecret() {
            secretReads++;
            return "must-not-be-read";
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"visible", "secret"})
    private static final class ArrayShapedValue {
        private int secretReads;

        public String getVisible() {
            return "visible";
        }

        @LogExclude
        public String getSecret() {
            secretReads++;
            return "must-not-be-read";
        }
    }

    private static final class RedactedValue {
        private final Object secret;
        private int secretReads;

        private RedactedValue(Object secret) {
            this.secret = secret;
        }

        @Mask(type = MaskType.REDACT)
        public Object getSecret() {
            secretReads++;
            return secret;
        }

        Object secret() {
            return secret;
        }
    }

    private static final class ExcludedFieldRedactedGetter {
        @LogExclude
        private final String secret = "must-not-be-read";
        private int secretReads;

        @Mask(type = MaskType.REDACT)
        public String getSecret() {
            secretReads++;
            return secret;
        }
    }

    private static final class MixinValue {
        private int secretReads;

        public String getSecret() {
            secretReads++;
            return "must-not-be-read";
        }
    }

    private abstract static class MixinValueGovernance {
        @Mask(type = MaskType.REDACT)
        @JsonProperty("external_secret")
        abstract String getSecret();
    }

    private static final class FieldValue {
        private final String plain = "visible";

        @Mask(type = MaskType.REDACT)
        private final String secret = "must-not-be-read";
    }

    private static final class DuplicateRedaction {
        @Mask(type = MaskType.REDACT)
        private final String secret = "must-not-be-read";
        private int secretReads;

        @Mask(type = MaskType.REDACT)
        public String getSecret() {
            secretReads++;
            return secret;
        }
    }

    private static class BaseValue {
        private int secretReads;

        public String getVisible() {
            return "visible";
        }

        @Mask(type = MaskType.REDACT)
        public String getSecret() {
            secretReads++;
            return "must-not-be-read";
        }

        int secretReads() {
            return secretReads;
        }
    }

    private static final class InheritedValue extends BaseValue {
        public String getChild() {
            return "child";
        }
    }

    private static final class FailingValue {
        private final IllegalStateException failure;

        private FailingValue(IllegalStateException failure) {
            this.failure = failure;
        }

        public String getVisible() {
            return "visible";
        }

        public String getFailure() {
            throw failure;
        }
    }

    private static final class ConcurrentValue {
        private final int id;

        private ConcurrentValue(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        @Mask(type = MaskType.REDACT)
        public String getSecret() {
            return "secret-" + id;
        }
    }
}
