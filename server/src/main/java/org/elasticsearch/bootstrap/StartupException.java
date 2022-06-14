/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.bootstrap;

import org.elasticsearch.common.inject.CreationException;
import org.elasticsearch.common.inject.spi.Message;

import java.io.PrintStream;

/**
 * Wraps an exception in a special way that it gets formatted
 * "reasonably". This means limits on stacktrace frames and
 * cleanup for guice, and some guidance about consulting full
 * logs for the whole exception.
 */
// TODO: remove this when guice is removed, and exceptions are cleaned up
// this is horrible, but its what we must do
final class StartupException {

    /** maximum length of a stacktrace, before we truncate it */
    static final int STACKTRACE_LIMIT = 30;
    /** all lines from this package are RLE-compressed */
    static final String GUICE_PACKAGE = "org.elasticsearch.common.inject";

    /**
     * Prints a stacktrace for an exception to a print stream, possibly truncating.
     *
     * @param e The exception, which may have a long stack trace
     * @param err The error stream to print the stacktrace to
     */
    static void printStackTrace(Exception e, PrintStream err) {
        Throwable originalCause = e.getCause();
        Throwable cause = originalCause;
        if (cause instanceof CreationException) {
            cause = getFirstGuiceCause((CreationException) cause);
        }

        String message = cause.toString();
        err.println(message);

        if (cause != null) {
            // walk to the root cause
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }

            // print the root cause message, only if it differs!
            if (cause != originalCause && (message.equals(cause.toString()) == false)) {
                err.println("Likely root cause: " + cause);
            }

            // print stacktrace of cause
            StackTraceElement stack[] = cause.getStackTrace();
            int linesWritten = 0;
            for (int i = 0; i < stack.length; i++) {
                if (linesWritten == STACKTRACE_LIMIT) {
                    err.println("\t<<<truncated>>>");
                    break;
                }
                String line = stack[i].toString();

                // skip past contiguous runs of this garbage:
                if (line.startsWith(GUICE_PACKAGE)) {
                    while (i + 1 < stack.length && stack[i + 1].toString().startsWith(GUICE_PACKAGE)) {
                        i++;
                    }
                    err.println("\tat <<<guice>>>");
                    linesWritten++;
                    continue;
                }

                err.println("\tat " + line);
                linesWritten++;
            }
        }
        // if its a guice exception, the whole thing really will not be in the log, its megabytes.
        // refer to the hack in bootstrap, where we don't log it
        if (originalCause instanceof CreationException == false) {
            final String basePath = System.getProperty("es.logs.base_path");
            // It's possible to fail before logging has been configured, in which case there's no point
            // suggested that the user look in the log file.
            if (basePath != null) {
                final String logPath = System.getProperty("es.logs.base_path")
                    + System.getProperty("file.separator")
                    + System.getProperty("es.logs.cluster_name")
                    + ".log";

                err.println("For complete error details, refer to the log at " + logPath);
            }
        }
    }

    /**
     * Returns first cause from a guice error (it can have multiple).
     */
    private static Throwable getFirstGuiceCause(CreationException guice) {
        for (Message message : guice.getErrorMessages()) {
            Throwable cause = message.getCause();
            if (cause != null) {
                return cause;
            }
        }
        return guice; // we tried
    }

}
