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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs child processes with a timeout and without relying on the working
 * directory or the parent's {@code PATH}.
 * <p>
 * All system binaries are launched from {@code %SystemRoot%\System32}. Output is
 * read on a separate thread so that a chatty process cannot deadlock the pipe,
 * is decoded with the console code page, and is capped so that a runaway tool
 * cannot exhaust the heap.
 *
 * @author Foulest
 */
public final class CommandUtil {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);
    private static final int MAX_OUTPUT_BYTES = 1024 * 1024;
    private static final int MAX_OUTPUT_LINES = 20_000;

    private static final Set<Process> ACTIVE_COMMANDS = ConcurrentHashMap.newKeySet();
    private static final Set<Process> DETACHED_PROCESSES = ConcurrentHashMap.newKeySet();
    private static final ExecutorService OUTPUT_READER =
            Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "repairkit-output");
                thread.setDaemon(true);
                return thread;
            });

    private static volatile Charset consoleCharset;

    private CommandUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Runs a system executable.
     *
     * @param executable the absolute path to the executable
     * @param args       the arguments
     * @return the result
     */
    public static @NotNull CommandResult run(@NotNull Path executable, @NotNull String... args) {
        return run(executable, DEFAULT_TIMEOUT, args);
    }

    /**
     * Runs a system executable with a timeout.
     *
     * @param executable the absolute path to the executable
     * @param timeout    the maximum run time
     * @param args       the arguments
     * @return the result
     */
    public static @NotNull CommandResult run(@NotNull Path executable, @NotNull Duration timeout,
                                             @NotNull String... args) {
        @NotNull List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(List.of(args));
        return run(command, timeout);
    }

    /**
     * Runs an argument list with a timeout.
     *
     * @param command the command and its arguments
     * @param timeout the maximum run time
     * @return the result
     */
    public static @NotNull CommandResult run(@NotNull List<String> command, @NotNull Duration timeout) {
        if (command.isEmpty()) {
            throw new IllegalArgumentException("Command must not be empty");
        }

        long started = System.nanoTime();
        @NotNull ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.directory(workingDirectory().toFile());
        applySafeEnvironment(builder.environment());

        Process process;

        try {
            process = builder.start();
        } catch (IOException ex) {
            DebugUtil.warn("Failed to start: " + String.join(" ", command), ex);
            return new CommandResult(String.join(" ", command), -1, List.of(),
                    false, Duration.ofNanos(System.nanoTime() - started));
        }

        ACTIVE_COMMANDS.add(process);
        Future<byte[]> reader = OUTPUT_READER.submit(() -> readBounded(process.getInputStream()));
        boolean finished = false;

        try {
            process.getOutputStream().close();
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

            if (!finished) {
                DebugUtil.warn("Command timed out after " + timeout.toSeconds() + "s: "
                        + String.join(" ", command), null);
                terminate(process);
            }

            byte[] bytes = awaitOutput(reader);
            @NotNull List<String> lines = decode(lines(bytes));
            int exitCode = finished ? process.exitValue() : -1;

            @NotNull CommandResult result = new CommandResult(String.join(" ", command), exitCode,
                    lines, !finished, Duration.ofNanos(System.nanoTime() - started));
            DebugUtil.debug(result.describe());
            return result;
        } catch (IOException ex) {
            terminate(process);
            DebugUtil.warn("Failed to close command input: " + String.join(" ", command), ex);
            return new CommandResult(String.join(" ", command), -1, List.of(),
                    false, Duration.ofNanos(System.nanoTime() - started));
        } catch (InterruptedException ex) {
            terminate(process);
            Thread.currentThread().interrupt();
            return new CommandResult(String.join(" ", command), -1, List.of(),
                    false, Duration.ofNanos(System.nanoTime() - started));
        } finally {
            ACTIVE_COMMANDS.remove(process);

            if (process.isAlive()) {
                terminate(process);
            }
        }
    }

    /**
     * Runs a command through {@code cmd.exe /d /s /c}.
     * <p>
     * Use this only for shell built-ins or pipelines that cannot be expressed as
     * an argument list. Values that come from configuration must be validated
     * first.
     *
     * @param commandLine the command line
     * @param timeout     the maximum run time
     * @return the result
     */
    public static @NotNull CommandResult runCmd(@NotNull String commandLine, @NotNull Duration timeout) {
        return run(List.of(WindowsUtil.system32("cmd.exe").toString(), "/d", "/s", "/c", commandLine),
                timeout);
    }

    /**
     * Runs a PowerShell script with a predictable execution policy.
     *
     * @param script  the script
     * @param timeout the maximum run time
     * @return the result
     */
    public static @NotNull CommandResult runPowerShell(@NotNull String script, @NotNull Duration timeout) {
        return run(List.of(
                WindowsUtil.system32("WindowsPowerShell\\v1.0\\powershell.exe").toString(),
                "-NoLogo",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-Command", script
        ), timeout);
    }

    /**
     * Starts a detached process, for example a bundled tool.
     *
     * @param command    the command and its arguments
     * @param workingDir the working directory
     * @return the started process, or {@code null} when it could not be started
     */
    public static @Nullable Process startDetached(@NotNull List<String> command,
                                                  @NotNull Path workingDir) {
        @NotNull ProcessBuilder builder = new ProcessBuilder(command);

        try {
            // A missing working directory makes ProcessBuilder.start() fail, which
            // would look like a broken tool rather than a missing folder.
            java.nio.file.Files.createDirectories(workingDir);
            builder.directory(workingDir.toFile());
        } catch (IOException ex) {
            DebugUtil.warn("Failed to prepare the working directory: " + workingDir, ex);
            return null;
        }

        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);

        try {
            Process process = builder.start();
            DETACHED_PROCESSES.add(process);
            process.onExit().thenRun(() -> DETACHED_PROCESSES.remove(process));
            return process;
        } catch (IOException ex) {
            DebugUtil.warn("Failed to start: " + String.join(" ", command), ex);
            return null;
        }
    }

    /**
     * Terminates the commands of the current repair run.
     * <p>
     * Detached tools that the user started from the Useful Programs panel are
     * deliberately left alone; they are only stopped when RepairKit exits.
     */
    public static void cancelActiveProcesses() {
        for (Process process : ACTIVE_COMMANDS) {
            terminate(process);
        }
    }

    /**
     * Terminates every process that RepairKit started.
     * <p>
     * Called from the shutdown hook.
     */
    public static void terminateAll() {
        cancelActiveProcesses();

        for (Process process : DETACHED_PROCESSES) {
            terminate(process);
        }
    }

    /**
     * Checks whether any command is still running.
     *
     * @return {@code true} when a command process is alive
     */
    public static boolean hasRunningProcesses() {
        return ACTIVE_COMMANDS.stream().anyMatch(Process::isAlive);
    }

    private static void terminate(@NotNull Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);

        if (process.isAlive()) {
            process.destroy();
        }

        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private static byte @NotNull [] readBounded(@NotNull InputStream input) throws IOException {
        byte[] buffer = new byte[8192];
        byte[] collected = new byte[MAX_OUTPUT_BYTES];
        int total = 0;
        int read;

        while ((read = input.read(buffer)) != -1) {
            int keep = Math.min(read, MAX_OUTPUT_BYTES - total);
            System.arraycopy(buffer, 0, collected, total, keep);
            total += keep;
            // Keep draining so the child process never blocks on a full pipe.
        }

        byte[] result = new byte[total];
        System.arraycopy(collected, 0, result, 0, total);
        return result;
    }

    private static byte @NotNull [] awaitOutput(@NotNull Future<byte[]> reader) {
        try {
            return reader.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            reader.cancel(true);
            return new byte[0];
        } catch (ExecutionException | TimeoutException ex) {
            reader.cancel(true);
            return new byte[0];
        }
    }

    private static @NotNull String lines(byte @NotNull [] bytes) {
        return new String(bytes, consoleCharset());
    }

    private static @NotNull List<String> decode(@NotNull String output) {
        @NotNull List<String> lines = new ArrayList<>();
        String[] split = output.split("\\R", -1);

        for (String line : split) {
            if (lines.size() >= MAX_OUTPUT_LINES) {
                lines.add("... output truncated ...");
                break;
            }

            String trimmed = line.stripTrailing();

            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }

        return lines;
    }

    /**
     * Gets the console output code page.
     * <p>
     * Console tools such as {@code sfc} and {@code sc} write in the OEM code
     * page, not UTF-8. Decoding with the wrong charset garbles non-ASCII paths
     * and, more importantly, breaks text matching.
     *
     * @return the console charset
     */
    public static @NotNull Charset consoleCharset() {
        Charset current = consoleCharset;

        if (current != null) {
            return current;
        }

        @NotNull Charset detected = detectConsoleCharset();
        consoleCharset = detected;
        return detected;
    }

    private static @NotNull Charset detectConsoleCharset() {
        if (!WindowsUtil.isWindows()) {
            return Charset.defaultCharset();
        }

        try {
            @NotNull ProcessBuilder builder = new ProcessBuilder(
                    WindowsUtil.system32("cmd.exe").toString(), "/d", "/s", "/c", "chcp");
            builder.redirectErrorStream(true);
            Process process = builder.start();
            byte[] bytes = readBounded(process.getInputStream());
            process.waitFor(5, TimeUnit.SECONDS);
            @NotNull String output = new String(bytes, StandardCharsets.ISO_8859_1);

            int index = output.indexOf(':');

            if (index >= 0) {
                String digits = output.substring(index + 1).replaceAll("[^0-9]", "").trim();

                if (!digits.isEmpty()) {
                    return mapCodePage(Integer.parseInt(digits));
                }
            }
        } catch (IOException | InterruptedException | NumberFormatException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        return Charset.defaultCharset();
    }

    private static @NotNull Charset mapCodePage(int codePage) {
        String name = switch (codePage) {
            case 65001 -> "UTF-8";
            case 437 -> "IBM437";
            case 850 -> "IBM850";
            case 852 -> "IBM852";
            case 855 -> "IBM855";
            case 857 -> "IBM857";
            case 866 -> "IBM866";
            case 874 -> "x-windows-874";
            case 932 -> "windows-31j";
            case 936 -> "GBK";
            case 949 -> "x-windows-949";
            case 950 -> "x-windows-950";
            case 1250 -> "windows-1250";
            case 1251 -> "windows-1251";
            case 1252 -> "windows-1252";
            case 1253 -> "windows-1253";
            case 1254 -> "windows-1254";
            case 1255 -> "windows-1255";
            case 1256 -> "windows-1256";
            case 1257 -> "windows-1257";
            case 1258 -> "windows-1258";
            case 54936 -> "GB18030";
            default -> null;
        };

        if (name == null) {
            return Charset.defaultCharset();
        }

        try {
            return Charset.forName(name);
        } catch (RuntimeException ex) {
            return Charset.defaultCharset();
        }
    }

    private static @NotNull Path workingDirectory() {
        @NotNull Path directory = WindowsUtil.dataDirectory();

        try {
            java.nio.file.Files.createDirectories(directory);
            return directory;
        } catch (IOException ex) {
            return Path.of(System.getProperty("java.io.tmpdir"));
        }
    }

    private static void applySafeEnvironment(@NotNull Map<String, String> environment) {
        String systemRoot = WindowsUtil.windowsDirectory().toString();
        environment.put("PATH", systemRoot + "\\System32;"
                + systemRoot + ";"
                + systemRoot + "\\System32\\Wbem;"
                + systemRoot + "\\System32\\WindowsPowerShell\\v1.0");

        // Remove compatibility shims that could redirect child processes.
        environment.remove("__COMPAT_LAYER");
        environment.keySet().removeIf(key -> key.startsWith("COMPLUS_"));
    }
}
