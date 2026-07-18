/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes the bounded RestTemplate adapter startup snapshot for one context.
 *
 * @author SummerWen
 * @since 0.1
 */
final class RestTemplateAdapterStartupSummary {

    private static final int MAX_INFO_BEAN_NAMES = 20;
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(
            RestTemplateAdapterStartupSummary.class);
    private static final Logger DEFAULT_EXCHANGE_EVENT_LOGGER = LoggerFactory.getLogger(
            "log.mask.http");

    private final String contextId;
    private final Logger logger;
    private final Logger exchangeEventLogger;

    RestTemplateAdapterStartupSummary(String contextId) {
        this(contextId, DEFAULT_LOGGER, DEFAULT_EXCHANGE_EVENT_LOGGER);
    }

    RestTemplateAdapterStartupSummary(
            String contextId,
            Logger logger,
            Logger exchangeEventLogger) {
        this.contextId = contextId;
        this.logger = logger;
        this.exchangeEventLogger = exchangeEventLogger;
    }

    void publish(RestTemplateObservationSnapshot snapshot) {
        boolean infoEnabled;
        try {
            infoEnabled = logger.isInfoEnabled();
        } catch (RuntimeException | Error ignored) {
            return;
        }
        if (!infoEnabled) {
            return;
        }
        List<String> beanNames = new ArrayList<String>(
                snapshot.getObservedBeanNamesAtStartup());
        Collections.sort(beanNames);
        int displayedBeanNameCount = Math.min(MAX_INFO_BEAN_NAMES, beanNames.size());
        int omittedBeanNameCount = beanNames.size() - displayedBeanNameCount;
        boolean exchangeEventsEnabledAtStartup = false;
        try {
            exchangeEventsEnabledAtStartup = exchangeEventLogger.isInfoEnabled();
        } catch (RuntimeException | Error ignored) {
            // A failed status query must not suppress an otherwise usable summary.
        }
        StringBuilder infoMessage = new StringBuilder(
                "Log Mask RestTemplate adapter initialized: ")
                .append("version=")
                .append(escapeSingleLine(RestTemplateAdapterVersion.get()))
                .append(", contextId=").append(escapeSingleLine(contextId))
                .append(", observedInstanceCountAtStartup=")
                .append(snapshot.getObservedInstanceCountAtStartup())
                .append(", observedBeanNamesAtStartup=")
                .append(formatBeanNames(
                        beanNames.subList(0, displayedBeanNameCount)));
        if (omittedBeanNameCount > 0) {
            infoMessage.append(", omittedBeanNameCount=")
                    .append(omittedBeanNameCount);
        }
        infoMessage.append(", exchangeEventsEnabledAtStartup=")
                .append(exchangeEventsEnabledAtStartup);
        try {
            logger.info(infoMessage.toString());
        } catch (RuntimeException | Error ignored) {
            return;
        }
        if (omittedBeanNameCount > 0) {
            boolean debugEnabled;
            try {
                debugEnabled = logger.isDebugEnabled();
            } catch (RuntimeException | Error ignored) {
                return;
            }
            if (debugEnabled) {
                try {
                    logger.debug("Log Mask RestTemplate adapter full Bean name snapshot: "
                            + "contextId=" + escapeSingleLine(contextId)
                            + ", observedBeanNamesAtStartup="
                            + formatBeanNames(beanNames));
                } catch (RuntimeException | Error ignored) {
                    // Startup diagnostics must not change application initialization.
                }
            }
        }
    }

    private static String formatBeanNames(List<String> beanNames) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < beanNames.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append(escapeSingleLine(beanNames.get(index)));
        }
        return result.append(']').toString();
    }

    private static String escapeSingleLine(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (Character.isISOControl(character)
                            || character == '\u2028'
                            || character == '\u2029') {
                        appendUnicodeEscape(escaped, character);
                    } else {
                        escaped.append(character);
                    }
            }
        }
        return escaped.toString();
    }

    private static void appendUnicodeEscape(StringBuilder result, char character) {
        result.append("\\u")
                .append(HEX_DIGITS[(character >>> 12) & 0x0F])
                .append(HEX_DIGITS[(character >>> 8) & 0x0F])
                .append(HEX_DIGITS[(character >>> 4) & 0x0F])
                .append(HEX_DIGITS[character & 0x0F]);
    }
}
