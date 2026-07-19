/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestTemplateAdapterStartupSummaryTest {

    @Test
    void publishesSortedStartupSnapshotThroughItsClassLogger() {
        try (CapturedRestTemplateAdapterStartupLogs logs =
                     new CapturedRestTemplateAdapterStartupLogs(Level.INFO, Level.INFO)) {
            RestTemplateAdapterStartupSummary summary =
                    new RestTemplateAdapterStartupSummary("application");
            RestTemplateObservationSnapshot snapshot =
                    new RestTemplateObservationSnapshot(
                            2,
                            Arrays.asList("riskRestTemplate", "paymentRestTemplate"));

            summary.publish(snapshot);

            List<ILoggingEvent> events = logs.summaryEvents();
            assertEquals(1, events.size());
            assertEquals(Level.INFO, events.get(0).getLevel());
            assertEquals(
                    "Log Mask RestTemplate adapter initialized: "
                            + "version=0.1.0, "
                            + "contextId=application, "
                            + "observedInstanceCountAtStartup=2, "
                            + "observedBeanNamesAtStartup="
                            + "[paymentRestTemplate, riskRestTemplate], "
                            + "exchangeEventsEnabledAtStartup=true",
                    events.get(0).getFormattedMessage());
            assertTrue(logs.exchangeEventLoggerEvents().isEmpty());
        }
    }

    @Test
    void twentyBeanNamesRemainInTheSingleInfoSnapshot() {
        try (CapturedRestTemplateAdapterStartupLogs logs =
                     new CapturedRestTemplateAdapterStartupLogs(Level.INFO, Level.INFO)) {
            RestTemplateAdapterStartupSummary summary =
                    new RestTemplateAdapterStartupSummary("application");
            RestTemplateObservationSnapshot snapshot =
                    new RestTemplateObservationSnapshot(
                            20,
                            twentyOneBeanNames().subList(0, 20));

            summary.publish(snapshot);

            List<ILoggingEvent> events = logs.summaryEvents();
            assertEquals(1, events.size());
            assertEquals(Level.INFO, events.get(0).getLevel());
            assertTrue(events.get(0).getFormattedMessage().contains("bean20"));
            assertFalse(events.get(0).getFormattedMessage().contains(
                    "omittedBeanNameCount"));
        }
    }

    @Test
    void twentyOneBeanNamesUseBoundedInfoAndOneFullDebugSnapshot() {
        try (CapturedRestTemplateAdapterStartupLogs logs =
                     new CapturedRestTemplateAdapterStartupLogs(Level.DEBUG, Level.INFO)) {
            RestTemplateAdapterStartupSummary summary =
                    new RestTemplateAdapterStartupSummary("application");
            RestTemplateObservationSnapshot snapshot =
                    new RestTemplateObservationSnapshot(21, twentyOneBeanNames());

            summary.publish(snapshot);

            List<ILoggingEvent> events = logs.summaryEvents();
            assertEquals(2, events.size());
            assertEquals(Level.INFO, events.get(0).getLevel());
            assertEquals(
                    "Log Mask RestTemplate adapter initialized: "
                            + "version=0.1.0, "
                            + "contextId=application, "
                            + "observedInstanceCountAtStartup=21, "
                            + "observedBeanNamesAtStartup="
                            + "[bean01, bean02, bean03, bean04, bean05, "
                            + "bean06, bean07, bean08, bean09, bean10, "
                            + "bean11, bean12, bean13, bean14, bean15, "
                            + "bean16, bean17, bean18, bean19, bean20], "
                            + "omittedBeanNameCount=1, "
                            + "exchangeEventsEnabledAtStartup=true",
                    events.get(0).getFormattedMessage());
            assertEquals(Level.DEBUG, events.get(1).getLevel());
            assertEquals(
                    "Log Mask RestTemplate adapter full Bean name snapshot: "
                            + "contextId=application, "
                            + "observedBeanNamesAtStartup="
                            + "[bean01, bean02, bean03, bean04, bean05, "
                            + "bean06, bean07, bean08, bean09, bean10, "
                            + "bean11, bean12, bean13, bean14, bean15, "
                            + "bean16, bean17, bean18, bean19, bean20, bean21]",
                    events.get(1).getFormattedMessage());
        }
    }

    @Test
    void escapesControlCharactersInInfoAndFullDebugSnapshots() {
        List<String> beanNames = new ArrayList<String>(twentyOneBeanNames());
        beanNames.set(
                0,
                "bean21\\tail\r\n\t\b\f\u0000\u007f\u0085\u2028\u2029");
        try (CapturedRestTemplateAdapterStartupLogs logs =
                     new CapturedRestTemplateAdapterStartupLogs(Level.DEBUG, Level.INFO)) {
            RestTemplateAdapterStartupSummary summary =
                    new RestTemplateAdapterStartupSummary(
                            "context\\id\r\n\t\b\f\u0000\u007f\u0085\u2028\u2029");

            summary.publish(new RestTemplateObservationSnapshot(21, beanNames));

            List<ILoggingEvent> events = logs.summaryEvents();
            assertEquals(2, events.size());
            for (ILoggingEvent event : events) {
                String message = event.getFormattedMessage();
                assertSingleLine(message);
                assertTrue(message.contains("context\\\\id"));
                assertTrue(message.contains("\\r"));
                assertTrue(message.contains("\\n"));
                assertTrue(message.contains("\\t"));
                assertTrue(message.contains("\\b"));
                assertTrue(message.contains("\\f"));
                assertTrue(message.contains("\\u0000"));
                assertTrue(message.contains("\\u007F"));
                assertTrue(message.contains("\\u0085"));
                assertTrue(message.contains("\\u2028"));
                assertTrue(message.contains("\\u2029"));
            }
        }
    }

    @Test
    void capturesDisabledExchangeEventLoggerAtPublicationTime() {
        try (CapturedRestTemplateAdapterStartupLogs logs =
                     new CapturedRestTemplateAdapterStartupLogs(Level.INFO, Level.WARN)) {
            RestTemplateAdapterStartupSummary summary =
                    new RestTemplateAdapterStartupSummary("application");

            summary.publish(RestTemplateObservationSnapshot.empty());

            assertEquals(1, logs.summaryEvents().size());
            assertTrue(logs.summaryEvents().get(0).getFormattedMessage().endsWith(
                    "exchangeEventsEnabledAtStartup=false"));
        }
    }

    @Test
    void exchangeLoggerFailuresFallBackToDisabledAndKeepPublishing() {
        assertExchangeLoggerFailureFallsBack(
                new IllegalStateException("exchange logger failure"));
        assertExchangeLoggerFailureFallsBack(
                new AssertionError("exchange logger failure"));
    }

    @Test
    void loggerRuntimeExceptionsAndErrorsNeverEscapePublication() {
        for (LoggerFailurePoint failurePoint : LoggerFailurePoint.values()) {
            assertLoggerFailureIsTransparent(
                    failurePoint,
                    new IllegalStateException(failurePoint.name()));
            assertLoggerFailureIsTransparent(
                    failurePoint,
                    new AssertionError(failurePoint.name()));
        }
    }

    private static List<String> twentyOneBeanNames() {
        return Arrays.asList(
                "bean21", "bean20", "bean19", "bean18", "bean17", "bean16",
                "bean15", "bean14", "bean13", "bean12", "bean11", "bean10",
                "bean09", "bean08", "bean07", "bean06", "bean05", "bean04",
                "bean03", "bean02", "bean01");
    }

    private static void assertSingleLine(String message) {
        for (int index = 0; index < message.length(); index++) {
            char character = message.charAt(index);
            assertFalse(Character.isISOControl(character));
            assertFalse(character == '\u2028' || character == '\u2029');
        }
    }

    private static void assertExchangeLoggerFailureFallsBack(Throwable failure) {
        Logger summaryLogger = mock(Logger.class);
        Logger exchangeEventLogger = mock(Logger.class);
        when(summaryLogger.isInfoEnabled()).thenReturn(true);
        when(summaryLogger.isDebugEnabled()).thenReturn(true);
        doThrow(failure).when(exchangeEventLogger).isInfoEnabled();
        RestTemplateAdapterStartupSummary summary =
                new RestTemplateAdapterStartupSummary(
                        "application",
                        summaryLogger,
                        exchangeEventLogger);

        assertDoesNotThrow(() -> summary.publish(
                new RestTemplateObservationSnapshot(21, twentyOneBeanNames())));

        verify(summaryLogger).info(contains(
                "exchangeEventsEnabledAtStartup=false"));
        verify(summaryLogger).debug(anyString());
    }

    private static void assertLoggerFailureIsTransparent(
            LoggerFailurePoint failurePoint,
            Throwable failure) {
        Logger summaryLogger = mock(Logger.class);
        Logger exchangeEventLogger = mock(Logger.class);
        when(summaryLogger.isInfoEnabled()).thenReturn(true);
        when(summaryLogger.isDebugEnabled()).thenReturn(true);
        when(exchangeEventLogger.isInfoEnabled()).thenReturn(true);
        switch (failurePoint) {
            case SUMMARY_INFO_ENABLED:
                doThrow(failure).when(summaryLogger).isInfoEnabled();
                break;
            case EXCHANGE_INFO_ENABLED:
                doThrow(failure).when(exchangeEventLogger).isInfoEnabled();
                break;
            case SUMMARY_INFO:
                doThrow(failure).when(summaryLogger).info(anyString());
                break;
            case SUMMARY_DEBUG_ENABLED:
                doThrow(failure).when(summaryLogger).isDebugEnabled();
                break;
            case SUMMARY_DEBUG:
                doThrow(failure).when(summaryLogger).debug(anyString());
                break;
            default:
                throw new AssertionError("Unhandled failure point " + failurePoint);
        }
        RestTemplateAdapterStartupSummary summary =
                new RestTemplateAdapterStartupSummary(
                        "application",
                        summaryLogger,
                        exchangeEventLogger);

        assertDoesNotThrow(
                () -> summary.publish(new RestTemplateObservationSnapshot(
                        21,
                        twentyOneBeanNames())),
                failurePoint + " must be transparent for "
                        + failure.getClass().getSimpleName());
    }

    private enum LoggerFailurePoint {
        SUMMARY_INFO_ENABLED,
        EXCHANGE_INFO_ENABLED,
        SUMMARY_INFO,
        SUMMARY_DEBUG_ENABLED,
        SUMMARY_DEBUG
    }
}
