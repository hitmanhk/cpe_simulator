package com.cpe.simulator.desktop.bridge;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Logback appender that mirrors log events into {@link UiLogSink} for the
 * desktop log view. Installed programmatically at GUI startup — logback.xml
 * and CLI logging behavior are untouched.
 */
public class UiLogAppender extends AppenderBase<ILoggingEvent> {

    public static final String NAME = "UI_LOG_SINK";

    @Override
    protected void append(ILoggingEvent event) {
        if (UiLogSink.hasListeners()) {
            UiLogSink.publish(new UiLogSink.LogEntry(
                    Instant.ofEpochMilli(event.getTimeStamp()),
                    event.getLevel().toString(),
                    event.getLoggerName(),
                    event.getThreadName(),
                    event.getFormattedMessage()));
        }
    }

    /** Attaches the appender to the root logger. No-op if not running on logback. */
    public static void install() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
            return;
        }
        ch.qos.logback.classic.Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        if (root.getAppender(NAME) != null) {
            return;
        }
        UiLogAppender appender = new UiLogAppender();
        appender.setName(NAME);
        appender.setContext(context);
        appender.start();
        root.addAppender(appender);
    }

    /** Detaches the appender from the root logger. */
    public static void uninstall() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
            return;
        }
        ch.qos.logback.classic.Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        if (root.getAppender(NAME) != null) {
            root.detachAppender(NAME);
        }
    }
}
