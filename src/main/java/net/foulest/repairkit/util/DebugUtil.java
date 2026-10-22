/*
 * RepairKit - an all-in-one Java-based Windows repair and maintenance toolkit.
 * Copyright (C) 2026 Foulest (https://github.com/Foulest)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.foulest.repairkit.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Application logging.
 * <p>
 * A single buffered writer is held open for the life of the process. The log
 * lives under {@code %LOCALAPPDATA%\RepairKit\logs} so that it survives the
 * temporary folder cleanup that RepairKit itself performs.
 *
 * @author Foulest
 */
public final class DebugUtil {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Object LOCK = new Object();

    private static volatile Path logFile;
    private static volatile BufferedWriter writer;

    private DebugUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Gets the log file for this run, creating it when needed.
     *
     * @return the log file
     */
    public static @NotNull Path getLogFile() {
        Path current = logFile;

        if (current != null) {
            return current;
        }

        synchronized (LOCK) {
            if (logFile == null) {
                logFile = createLogFile();
            }

            return logFile;
        }
    }

    private static @NotNull Path createLogFile() {
        @NotNull Path directory = WindowsUtil.logDirectory();

        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            directory = Path.of(System.getProperty("java.io.tmpdir"), "RepairKit", "logs");

            try {
                Files.createDirectories(directory);
            } catch (IOException ignored) {
                // Fall through to the last resort below.
            }
        }

        @NotNull String name = "repairkit-"
                + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
                + ".log";
        return directory.resolve(name);
    }

    private static @NotNull BufferedWriter writer() throws IOException {
        BufferedWriter current = writer;

        if (current != null) {
            return current;
        }

        synchronized (LOCK) {
            if (writer == null) {
                Path file = getLogFile();
                writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }

            return writer;
        }
    }

    /**
     * Writes an informational message.
     *
     * @param message the message
     */
    public static void debug(@NotNull String message) {
        write("INFO", message, null);
    }

    /**
     * Writes a warning.
     *
     * @param message the message
     * @param ex      the exception, may be {@code null}
     */
    public static void warn(@NotNull String message, @Nullable Throwable ex) {
        write("WARN", message, ex);
    }

    /**
     * Writes an error.
     *
     * @param message the message
     * @param ex      the exception, may be {@code null}
     */
    public static void error(@NotNull String message, @Nullable Throwable ex) {
        write("ERROR", message, ex);
    }

    private static void write(@NotNull String level, @NotNull String message, @Nullable Throwable ex) {
        @NotNull String line = "[" + TIMESTAMP.format(LocalDateTime.now()) + "] ["
                + Thread.currentThread().getName() + "] " + level + " " + message;

        System.out.println(line);

        synchronized (LOCK) {
            try {
                BufferedWriter out = writer();

                if (out == null) {
                    return;
                }

                out.write(line);
                out.newLine();

                if (ex != null) {
                    @NotNull StringWriter trace = new StringWriter();
                    ex.printStackTrace(new PrintWriter(trace));
                    out.write(trace.toString());
                }

                out.flush();
            } catch (IOException ex2) {
                System.err.println("Failed to write to the RepairKit log: " + ex2.getMessage());
            }
        }
    }

    /**
     * Writes the environment header to the log.
     *
     * @param args the command line arguments
     */
    public static void logEnvironment(String[] args) {
        debug("Starting RepairKit with arguments: \"" + String.join(" ", args) + "\"");
        debug("RepairKit version: " + readVersion());
        debug("Operating system: " + System.getProperty("os.name") + " "
                + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")");
        debug("Windows build: " + WindowsUtil.osInfo().describe());
        debug("Java: " + System.getProperty("java.version") + " ("
                + System.getProperty("java.vendor") + ") at " + System.getProperty("java.home"));

        if (WindowsUtil.isWindows()) {
            debug("Elevated: " + WindowsUtil.isAdministrator());
            debug("Safe mode: " + WindowsUtil.isSafeMode());
        }

        debug("Working directory: " + System.getProperty("user.dir"));
        debug("Data directory: " + WindowsUtil.dataDirectory());
        debug("Log file: " + getLogFile());
    }

    private static @NotNull String readVersion() {
        @NotNull Properties properties = new Properties();

        try (@Nullable InputStream input = DebugUtil.class.getResourceAsStream("/version.properties")) {
            if (input == null) {
                return "unknown";
            }

            properties.load(input);
            return properties.getProperty("version", "unknown");
        } catch (IOException ex) {
            return "unknown";
        }
    }

    /**
     * Flushes and closes the log.
     */
    public static void close() {
        synchronized (LOCK) {
            if (writer != null) {
                try {
                    writer.flush();
                    writer.close();
                } catch (IOException ex) {
                    System.err.println("Failed to close the RepairKit log: " + ex.getMessage());
                } finally {
                    writer = null;
                }
            }
        }
    }
}
