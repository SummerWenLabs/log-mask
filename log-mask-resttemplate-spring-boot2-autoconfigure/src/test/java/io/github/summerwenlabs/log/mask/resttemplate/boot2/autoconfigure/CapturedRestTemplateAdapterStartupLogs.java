/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

/** Captures RestTemplate adapter startup diagnostics and exchange logger output. */
final class CapturedRestTemplateAdapterStartupLogs implements AutoCloseable {

    private static final String EXCHANGE_EVENT_LOGGER_NAME = "log.mask.http";

    private final Logger summaryLogger = (Logger) LoggerFactory.getLogger(
            RestTemplateAdapterStartupSummary.class);
    private final Logger exchangeEventLogger = (Logger) LoggerFactory.getLogger(
            EXCHANGE_EVENT_LOGGER_NAME);
    private final Level originalSummaryLevel = summaryLogger.getLevel();
    private final Level originalExchangeEventLevel = exchangeEventLogger.getLevel();
    private final boolean originalSummaryAdditive = summaryLogger.isAdditive();
    private final boolean originalExchangeEventAdditive = exchangeEventLogger.isAdditive();
    private final ListAppender<ILoggingEvent> summaryAppender =
            new ListAppender<ILoggingEvent>();
    private final ListAppender<ILoggingEvent> exchangeEventAppender =
            new ListAppender<ILoggingEvent>();

    CapturedRestTemplateAdapterStartupLogs(Level summaryLevel, Level exchangeEventLevel) {
        summaryAppender.start();
        exchangeEventAppender.start();
        summaryLogger.setLevel(summaryLevel);
        summaryLogger.setAdditive(false);
        summaryLogger.addAppender(summaryAppender);
        exchangeEventLogger.setLevel(exchangeEventLevel);
        exchangeEventLogger.setAdditive(false);
        exchangeEventLogger.addAppender(exchangeEventAppender);
    }

    List<ILoggingEvent> summaryEvents() {
        return snapshot(summaryAppender);
    }

    List<ILoggingEvent> exchangeEventLoggerEvents() {
        return snapshot(exchangeEventAppender);
    }

    @Override
    public void close() {
        summaryLogger.detachAppender(summaryAppender);
        summaryLogger.setLevel(originalSummaryLevel);
        summaryLogger.setAdditive(originalSummaryAdditive);
        summaryAppender.stop();
        exchangeEventLogger.detachAppender(exchangeEventAppender);
        exchangeEventLogger.setLevel(originalExchangeEventLevel);
        exchangeEventLogger.setAdditive(originalExchangeEventAdditive);
        exchangeEventAppender.stop();
    }

    private static List<ILoggingEvent> snapshot(
            ListAppender<ILoggingEvent> appender) {
        synchronized (appender) {
            return Collections.unmodifiableList(
                    new ArrayList<ILoggingEvent>(appender.list));
        }
    }
}
