/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.summerwenlabs.log.mask.governance.LogExclude;
import io.github.summerwenlabs.log.mask.governance.Mask;
import io.github.summerwenlabs.log.mask.governance.MaskType;
import io.github.summerwenlabs.log.mask.strategy.MaskStrategyRegistry;
import io.github.summerwenlabs.log.mask.strategy.MaskTypeDefinition;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void phoneMaskKeepsThePublishedPrefixAndSuffix() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();

        JsonNode result = mapper.readTree(masker.mask(new PhoneValue("13800138000")));

        assertEquals("138****8000", result.path("phone").textValue());
    }

    @Test
    void builtInContentMasksPublishStableNormalResults() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();

        JsonNode result = mapper.readTree(masker.mask(new BuiltInValues()));

        assertEquals("a****@example.com", result.path("email").textValue());
        assertEquals("110101********1234", result.path("idCard").textValue());
        assertEquals("6222********7890", result.path("bankCard").textValue());
        assertEquals("***", result.path("full").textValue());
    }

    @Test
    void customTypeCodeUsesTheRegisteredDependencyAwareDefinition() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        MaskTypeDefinition definition = new DependencyAwareMaskDefinition(
                "CUSTOMER_ID",
                new ReplacementDependency("registered-mask"));
        MaskStrategyRegistry registry = MaskStrategyRegistry.of(Collections.singletonList(definition));
        LogMasker masker = LogMasker.builder(mapper)
                .strategyRegistry(registry)
                .build();

        JsonNode result = mapper.readTree(masker.mask(new CustomCodeValue("must-not-appear")));

        assertEquals("registered-mask", result.path("customerId").textValue());
    }

    @Test
    void inlineCustomPatternUsesJavaReplacementSemantics() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();

        JsonNode result = mapper.readTree(masker.mask(new InlinePatternValue("AB-12-34")));

        assertEquals("AB-**-**", result.path("reference").textValue());
        assertEquals("AB1234", result.path("withoutSeparators").textValue());
    }

    @Test
    void invalidOrConflictingRulesRedactOnlyTheirPropertiesWithoutReadingThem() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        MaskTypeDefinition known = new DependencyAwareMaskDefinition(
                "KNOWN",
                new ReplacementDependency("known"));
        LogMasker masker = LogMasker.builder(mapper)
                .strategyRegistry(MaskStrategyRegistry.of(Collections.singletonList(known)))
                .build();
        InvalidRulesValue invalidRules = new InvalidRulesValue();
        ConflictingDeclarations conflictingDeclarations = new ConflictingDeclarations();

        JsonNode invalidResult = mapper.readTree(masker.mask(invalidRules));
        JsonNode conflictResult = mapper.readTree(masker.mask(conflictingDeclarations));

        assertEquals("visible", invalidResult.path("visible").textValue());
        assertEquals("<redacted>", invalidResult.path("unknownCode").textValue());
        assertEquals("<redacted>", invalidResult.path("invalidPattern").textValue());
        assertEquals("<redacted>", invalidResult.path("mixedMode").textValue());
        assertEquals(0, invalidRules.secretReads);
        assertEquals("<redacted>", conflictResult.path("secret").textValue());
        assertEquals(0, conflictingDeclarations.secretReads);
    }

    @Test
    void strategyFailureWritesOneValueFreeDiagnosticAndKeepsSafeJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        MaskTypeDefinition failing = new FailingMaskDefinition();
        LogMasker masker = LogMasker.builder(mapper)
                .strategyRegistry(MaskStrategyRegistry.of(Collections.singletonList(failing)))
                .build();
        try (LogCapture diagnostics = new LogCapture()) {
            JsonNode first = mapper.readTree(masker.mask(new FailingCustomValue()));
            JsonNode second = mapper.readTree(masker.mask(new FailingCustomValue()));

            assertEquals("<redacted>", first.path("secret").textValue());
            assertEquals("<redacted>", second.path("secret").textValue());
            assertEquals(1, diagnostics.events().size());
            ILoggingEvent diagnostic = diagnostics.events().get(0);
            assertEquals(Level.WARN, diagnostic.getLevel());
            assertTrue(diagnostic.getFormattedMessage().contains("strategy_failed"));
            assertTrue(diagnostic.getFormattedMessage().contains("secret"));
            assertFalse(diagnostic.getFormattedMessage().contains("must-not-appear"));
            assertFalse(diagnostic.getFormattedMessage().contains("sensitive exception message"));
            assertNull(diagnostic.getThrowableProxy());
        }
    }

    @Test
    void invalidDeclarationsWriteOneCachedDiagnosticPerProperty() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        MaskTypeDefinition known = new DependencyAwareMaskDefinition(
                "KNOWN",
                new ReplacementDependency("known"));
        LogMasker masker = LogMasker.builder(mapper)
                .strategyRegistry(MaskStrategyRegistry.of(Collections.singletonList(known)))
                .build();
        try (LogCapture diagnostics = new LogCapture()) {
            masker.mask(new InvalidRulesValue());
            masker.mask(new InvalidRulesValue());
            masker.mask(new ConflictingDeclarations());
            masker.mask(new ConflictingDeclarations());

            assertEquals(4, diagnostics.events().size());
            String messages = diagnostics.events().toString();
            assertTrue(messages.contains("unknown_type_code"));
            assertTrue(messages.contains("invalid_pattern"));
            assertTrue(messages.contains("invalid_mode"));
            assertTrue(messages.contains("conflicting_declarations"));
            assertFalse(messages.contains("must-not-appear"));
        }
    }

    @Test
    void invalidUnicodeBoundaryCodesAndUnknownCodeHaveDistinctDiagnostics() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();
        CustomCodeValidationValue value = new CustomCodeValidationValue();
        try (LogCapture diagnostics = new LogCapture()) {
            JsonNode result = mapper.readTree(masker.mask(value));

            assertEquals("<redacted>", result.path("leadingSpaceChar").textValue());
            assertEquals("<redacted>", result.path("trailingWhitespace").textValue());
            assertEquals("<redacted>", result.path("unknownCode").textValue());
            assertEquals(0, value.secretReads);
            assertEquals(3, diagnostics.events().size());
            List<String> messages = new ArrayList<String>();
            for (ILoggingEvent event : diagnostics.events()) {
                messages.add(event.getFormattedMessage());
            }
            String renderedMessages = messages.toString();
            assertTrue(renderedMessages.contains(
                    "property=leadingSpaceChar, reason=invalid_mode"));
            assertTrue(renderedMessages.contains(
                    "property=trailingWhitespace, reason=invalid_mode"));
            assertTrue(renderedMessages.contains(
                    "property=unknownCode, reason=unknown_type_code"));
        }
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
    void nestedContainersApplyGovernanceWithoutModifyingTheirValues() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();
        NestedValue pojo = new NestedValue("pojo", "13800138000");
        NestedValue listed = new NestedValue("list", "13900139000");
        NestedValue arrayed = new NestedValue("array", "13700137000");
        NestedValue mapped = new NestedValue("map", "13600136000");
        NestedValue wrapped = new NestedValue("wrapper", "13500135000");
        Map<String, NestedValue> map = new LinkedHashMap<String, NestedValue>();
        map.put("entry", mapped);
        NestedContainers value = new NestedContainers(
                pojo,
                Arrays.asList(listed),
                new NestedValue[]{arrayed},
                map,
                new GenericWrapper<NestedValue>(wrapped));

        JsonNode result = mapper.readTree(masker.mask(value));
        BoundedMaskResult bounded = masker.mask(value, 1024);
        JsonNode boundedResult = mapper.readTree(bounded.getJson());

        assertNestedValue(result.path("pojo"), "pojo", "138****8000");
        assertNestedValue(result.path("list").path(0), "list", "139****9000");
        assertNestedValue(result.path("array").path(0), "array", "137****7000");
        assertNestedValue(result.path("map").path("entry"), "map", "136****6000");
        assertNestedValue(result.path("wrapper").path("value"), "wrapper", "135****5000");
        assertEquals(result, boundedResult);
        for (NestedValue nested : Arrays.asList(pojo, listed, arrayed, mapped, wrapped)) {
            assertEquals(0, nested.excludedReads);
            assertEquals(0, nested.redactedReads);
            assertTrue(nested.phone.startsWith("13"));
            assertEquals("must-not-be-read", nested.excluded);
            assertEquals("must-not-be-read", nested.redacted);
        }
    }

    @Test
    void boundedMaskUsesDeclaredGenericTypeForNestedGovernance() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(MapperFeature.USE_STATIC_TYPING);
        LogMasker masker = LogMasker.builder(mapper).build();
        GenericSubtypeValue nested = new GenericSubtypeValue(
                "generic", "13800138000");
        GenericWrapper<GenericBaseValue> value =
                new GenericWrapper<GenericBaseValue>(nested);
        Type declaredType = new TypeReference<GenericWrapper<GenericBaseValue>>() {
        }.getType();

        BoundedMaskResult result = masker.mask(value, declaredType, 1024);

        assertFalse(result.isLimitExceeded());
        JsonNode nestedResult = mapper.readTree(result.getJson()).path("value");
        assertEquals("generic", nestedResult.path("plain").textValue());
        assertEquals("138****8000", nestedResult.path("phone").textValue());
        assertFalse(nestedResult.has("excluded"));
        assertFalse(nestedResult.has("subtypeOnly"));
        assertEquals(0, nested.excludedReads());
    }

    @Test
    void boundedMaskUsesTheFinalUtf8JsonSizeAtExactBoundaries() {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();

        BoundedMaskResult ascii = masker.mask("AB", 4);
        BoundedMaskResult unicode = masker.mask("\u4E2D", 5);
        BoundedMaskResult emoji = masker.mask("\uD83D\uDE00", 14);

        assertFalse(ascii.isLimitExceeded());
        assertEquals("\"AB\"", ascii.getJson());
        assertFalse(unicode.isLimitExceeded());
        assertEquals("\"\u4E2D\"", unicode.getJson());
        assertFalse(emoji.isLimitExceeded());
        assertEquals("\"\\uD83D\\uDE00\"", emoji.getJson());

        BoundedMaskResult asciiExceeded = masker.mask("AB", 3);
        BoundedMaskResult unicodeExceeded = masker.mask("\u4E2D", 4);
        BoundedMaskResult emojiExceeded = masker.mask("\uD83D\uDE00", 13);

        assertTrue(asciiExceeded.isLimitExceeded());
        assertTrue(unicodeExceeded.isLimitExceeded());
        assertTrue(emojiExceeded.isLimitExceeded());
        assertThrows(IllegalStateException.class, asciiExceeded::getJson);
        assertThrows(IllegalStateException.class, unicodeExceeded::getJson);
        assertThrows(IllegalStateException.class, emojiExceeded::getJson);
    }

    @Test
    void boundedMaskStopsBeforeReadingPropertiesAfterTheLimitIsKnown() {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();
        StopsAfterLimitValue value = new StopsAfterLimitValue();

        BoundedMaskResult result = masker.mask(value, 1);

        assertTrue(result.isLimitExceeded());
        assertEquals(1, value.firstReads);
        assertEquals(0, value.lateReads);
    }

    @Test
    void boundedMaskRejectsNonPositiveLimitsBeforeReadingTheValue() {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();
        StopsAfterLimitValue value = new StopsAfterLimitValue();

        assertThrows(IllegalArgumentException.class, () -> masker.mask(value, 0));
        assertThrows(IllegalArgumentException.class, () -> masker.mask(value, -1));
        assertEquals(0, value.firstReads);
        assertEquals(0, value.lateReads);
    }

    @Test
    void unboundedMaskStillReturnsJsonThatExceedsABoundedLimit() {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();
        char[] characters = new char[9000];
        Arrays.fill(characters, 'x');
        String value = new String(characters);

        String unbounded = masker.mask(value);
        BoundedMaskResult bounded = masker.mask(value, 32);

        assertEquals('"' + value + '"', unbounded);
        assertTrue(bounded.isLimitExceeded());
    }

    @Test
    void boundedSerializationFailureThrowsLogMaskExceptionWithTheOriginalRootCause() {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();
        IllegalStateException failure = new IllegalStateException("expected bounded failure");

        LogMaskException thrown = assertThrows(
                LogMaskException.class,
                () -> masker.mask(new FailingValue(failure), 1024));

        assertSame(failure, rootCause(thrown));
    }

    @Test
    void boundedCallsKeepResultsLimitsAndDiagnosticsIsolatedAcrossThreads() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper)
                .strategyRegistry(MaskStrategyRegistry.of(
                        Collections.singletonList(new FailingMaskDefinition())))
                .build();
        ExecutorService callers = Executors.newFixedThreadPool(8);
        try (LogCapture diagnostics = new LogCapture()) {
            List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
            for (int index = 0; index < 96; index++) {
                final int valueIndex = index;
                tasks.add(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        if (valueIndex % 3 == 0) {
                            BoundedMaskResult result = masker.mask(
                                    new ConcurrentValue(valueIndex),
                                    64);
                            JsonNode json = mapper.readTree(result.getJson());
                            assertEquals(valueIndex, json.path("id").intValue());
                            assertEquals("<redacted>", json.path("secret").textValue());
                        } else if (valueIndex % 3 == 1) {
                            assertTrue(masker.mask(
                                    new ConcurrentValue(valueIndex),
                                    1).isLimitExceeded());
                        } else {
                            BoundedMaskResult result = masker.mask(new FailingCustomValue(), 64);
                            assertEquals(
                                    "<redacted>",
                                    mapper.readTree(result.getJson()).path("secret").textValue());
                        }
                        return null;
                    }
                });
            }

            List<Future<Void>> results = callers.invokeAll(tasks);
            for (Future<Void> result : results) {
                result.get();
            }

            assertEquals(1, diagnostics.events().size());
            assertTrue(diagnostics.events().get(0).getFormattedMessage().contains("strategy_failed"));
            assertFalse(diagnostics.events().get(0).getFormattedMessage().contains("must-not-appear"));
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void maskingDoesNotCloseInputWhenTheApplicationMapperClosesSerializedValues() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.CLOSE_CLOSEABLE);
        LogMasker masker = LogMasker.builder(mapper).build();
        CloseableValue unbounded = new CloseableValue(null);
        CloseableValue bounded = new CloseableValue(null);
        CloseableValue exceeded = new CloseableValue(null);
        IllegalStateException failure = new IllegalStateException("expected closeable failure");
        CloseableValue unboundedFailed = new CloseableValue(failure);
        CloseableValue failed = new CloseableValue(failure);

        assertEquals("{\"value\":\"value\"}", masker.mask(unbounded));
        assertEquals("{\"value\":\"value\"}", masker.mask(bounded, 64).getJson());
        assertTrue(masker.mask(exceeded, 1).isLimitExceeded());
        assertThrows(LogMaskException.class, () -> masker.mask(unboundedFailed));
        assertThrows(LogMaskException.class, () -> masker.mask(failed, 64));

        assertFalse(unbounded.closed);
        assertFalse(bounded.closed);
        assertFalse(exceeded.closed);
        assertFalse(unboundedFailed.closed);
        assertFalse(failed.closed);
    }

    @Test
    void boundedMaskPreservesJacksonBulkArrayRepresentations() {
        ObjectMapper mapper = new ObjectMapper();
        LogMasker masker = LogMasker.builder(mapper).build();

        assertEquals("[1,2,3]", masker.mask(new int[]{1, 2, 3}, 7).getJson());
        assertEquals("[1,2]", masker.mask(new long[]{1L, 2L}, 5).getJson());
        assertEquals("[1.5,2.5]", masker.mask(new double[]{1.5D, 2.5D}, 9).getJson());
        assertEquals("[\"a\",\"b\"]", masker.mask(new String[]{"a", "b"}, 9).getJson());
        assertTrue(masker.mask(new int[]{1, 2, 3}, 6).isLimitExceeded());
    }

    private static void assertNestedValue(JsonNode result, String plain, String phone) {
        assertEquals(plain, result.path("plain").textValue());
        assertEquals(phone, result.path("phone").textValue());
        assertEquals("<redacted>", result.path("redacted").textValue());
        assertFalse(result.has("excluded"));
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

    private static final class LogCapture implements AutoCloseable {
        private final Logger rootLogger;
        private final ListAppender<ILoggingEvent> appender;

        private LogCapture() {
            rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            appender = new ListAppender<ILoggingEvent>();
            appender.start();
            rootLogger.addAppender(appender);
        }

        private List<ILoggingEvent> events() {
            return appender.list;
        }

        @Override
        public void close() {
            rootLogger.detachAppender(appender);
            appender.stop();
        }
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

    private static final class PhoneValue {
        private final String phone;

        private PhoneValue(String phone) {
            this.phone = phone;
        }

        @Mask(type = MaskType.PHONE)
        public String getPhone() {
            return phone;
        }
    }

    private static final class BuiltInValues {
        @Mask(type = MaskType.EMAIL)
        public String getEmail() {
            return "alice@example.com";
        }

        @Mask(type = MaskType.ID_CARD)
        public String getIdCard() {
            return "110101199001011234";
        }

        @Mask(type = MaskType.BANK_CARD)
        public String getBankCard() {
            return "6222021234567890";
        }

        @Mask(type = MaskType.FULL)
        public String getFull() {
            return "A\uD83D\uDE00\u4E2D";
        }
    }

    private static final class CustomCodeValue {
        private final String customerId;

        private CustomCodeValue(String customerId) {
            this.customerId = customerId;
        }

        @Mask(typeCode = "CUSTOMER_ID")
        public String getCustomerId() {
            return customerId;
        }
    }

    private static final class InlinePatternValue {
        private final String value;

        private InlinePatternValue(String value) {
            this.value = value;
        }

        @Mask(type = MaskType.CUSTOM, pattern = "[0-9]", replacement = "*")
        public String getReference() {
            return value;
        }

        @Mask(type = MaskType.CUSTOM, pattern = "-", replacement = "")
        public String getWithoutSeparators() {
            return value;
        }
    }

    private static final class InvalidRulesValue {
        private int secretReads;

        public String getVisible() {
            return "visible";
        }

        @Mask(typeCode = "UNKNOWN")
        public String getUnknownCode() {
            secretReads++;
            return "must-not-appear";
        }

        @Mask(type = MaskType.CUSTOM, pattern = "[")
        public String getInvalidPattern() {
            secretReads++;
            return "must-not-appear";
        }

        @Mask(type = MaskType.PHONE, typeCode = "KNOWN")
        public String getMixedMode() {
            secretReads++;
            return "13800138000";
        }
    }

    private static final class CustomCodeValidationValue {
        private int secretReads;

        @Mask(typeCode = "\u2007UNKNOWN")
        public String getLeadingSpaceChar() {
            secretReads++;
            return "must-not-appear";
        }

        @Mask(typeCode = "UNKNOWN\t")
        public String getTrailingWhitespace() {
            secretReads++;
            return "must-not-appear";
        }

        @Mask(typeCode = "UNKNOWN")
        public String getUnknownCode() {
            secretReads++;
            return "must-not-appear";
        }
    }

    private static final class ConflictingDeclarations {
        @Mask(type = MaskType.PHONE)
        private final String secret = "13800138000";
        private int secretReads;

        @Mask(type = MaskType.EMAIL)
        public String getSecret() {
            secretReads++;
            return secret;
        }
    }

    private static final class FailingCustomValue {
        @Mask(typeCode = "FAILING")
        public String getSecret() {
            return "must-not-appear";
        }
    }

    private static final class FailingMaskDefinition implements MaskTypeDefinition {
        @Override
        public String getTypeCode() {
            return "FAILING";
        }

        @Override
        public String mask(String value) {
            throw new IllegalStateException("sensitive exception message");
        }
    }

    private static final class DependencyAwareMaskDefinition implements MaskTypeDefinition {
        private final String typeCode;
        private final ReplacementDependency dependency;

        private DependencyAwareMaskDefinition(String typeCode, ReplacementDependency dependency) {
            this.typeCode = typeCode;
            this.dependency = dependency;
        }

        @Override
        public String getTypeCode() {
            return typeCode;
        }

        @Override
        public String mask(String value) {
            return dependency.replacement();
        }
    }

    private static final class ReplacementDependency {
        private final String replacement;

        private ReplacementDependency(String replacement) {
            this.replacement = replacement;
        }

        private String replacement() {
            return replacement;
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

    private static final class CloseableValue implements Closeable {
        private final RuntimeException failure;
        private boolean closed;

        private CloseableValue(RuntimeException failure) {
            this.failure = failure;
        }

        public String getValue() {
            if (failure != null) {
                throw failure;
            }
            return "value";
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }
    }

    @JsonPropertyOrder({"first", "late"})
    private static final class StopsAfterLimitValue {
        private int firstReads;
        private int lateReads;

        public String getFirst() {
            firstReads++;
            return "first";
        }

        public String getLate() {
            lateReads++;
            return "late";
        }
    }

    private static final class NestedContainers {
        private final NestedValue pojo;
        private final List<NestedValue> list;
        private final NestedValue[] array;
        private final Map<String, NestedValue> map;
        private final GenericWrapper<NestedValue> wrapper;

        private NestedContainers(
                NestedValue pojo,
                List<NestedValue> list,
                NestedValue[] array,
                Map<String, NestedValue> map,
                GenericWrapper<NestedValue> wrapper) {
            this.pojo = pojo;
            this.list = list;
            this.array = array;
            this.map = map;
            this.wrapper = wrapper;
        }

        public NestedValue getPojo() {
            return pojo;
        }

        public List<NestedValue> getList() {
            return list;
        }

        public NestedValue[] getArray() {
            return array;
        }

        public Map<String, NestedValue> getMap() {
            return map;
        }

        public GenericWrapper<NestedValue> getWrapper() {
            return wrapper;
        }
    }

    private static final class GenericWrapper<T> {
        private final T value;

        private GenericWrapper(T value) {
            this.value = value;
        }

        public T getValue() {
            return value;
        }
    }

    private static class GenericBaseValue {
        private final String plain;
        private final String phone;
        private int excludedReads;

        private GenericBaseValue(String plain, String phone) {
            this.plain = plain;
            this.phone = phone;
        }

        public String getPlain() {
            return plain;
        }

        @Mask(type = MaskType.PHONE)
        public String getPhone() {
            return phone;
        }

        @LogExclude
        public String getExcluded() {
            excludedReads++;
            return "must-not-be-read";
        }

        int excludedReads() {
            return excludedReads;
        }
    }

    private static final class GenericSubtypeValue extends GenericBaseValue {
        private GenericSubtypeValue(String plain, String phone) {
            super(plain, phone);
        }

        public String getSubtypeOnly() {
            return "subtype-only";
        }
    }

    private static final class NestedValue {
        private final String plain;
        private final String phone;
        private final String excluded = "must-not-be-read";
        private final String redacted = "must-not-be-read";
        private int excludedReads;
        private int redactedReads;

        private NestedValue(String plain, String phone) {
            this.plain = plain;
            this.phone = phone;
        }

        public String getPlain() {
            return plain;
        }

        @Mask(type = MaskType.PHONE)
        public String getPhone() {
            return phone;
        }

        @LogExclude
        public String getExcluded() {
            excludedReads++;
            return excluded;
        }

        @Mask(type = MaskType.REDACT)
        public String getRedacted() {
            redactedReads++;
            return redacted;
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
