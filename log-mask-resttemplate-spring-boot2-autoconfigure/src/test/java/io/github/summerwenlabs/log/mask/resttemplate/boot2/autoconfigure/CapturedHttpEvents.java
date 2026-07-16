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

public final class CapturedHttpEvents implements AutoCloseable {

    private static final String EVENT_LOGGER_NAME = "log.mask.http";

    private final Logger logger = (Logger) LoggerFactory.getLogger(EVENT_LOGGER_NAME);
    private final Level originalLevel = logger.getLevel();
    private final boolean originalAdditive = logger.isAdditive();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<ILoggingEvent>();

    public CapturedHttpEvents() {
        appender.start();
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        logger.addAppender(appender);
    }

    public List<ILoggingEvent> getEvents() {
        synchronized (appender) {
            return Collections.unmodifiableList(
                    new ArrayList<ILoggingEvent>(appender.list));
        }
    }

    public void disableInfo() {
        logger.setLevel(Level.WARN);
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
        logger.setAdditive(originalAdditive);
        appender.stop();
    }
}
