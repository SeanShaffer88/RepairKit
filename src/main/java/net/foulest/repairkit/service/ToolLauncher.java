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
package net.foulest.repairkit.service;

import net.foulest.repairkit.Messages;
import net.foulest.repairkit.model.Shortcut;
import net.foulest.repairkit.model.Tool;
import net.foulest.repairkit.util.CommandResult;
import net.foulest.repairkit.util.CommandUtil;
import net.foulest.repairkit.util.DebugUtil;
import net.foulest.repairkit.util.FileUtil;
import net.foulest.repairkit.util.WindowsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts and launches the bundled tools.
 * <p>
 * Each launch gets a fresh, randomly-named directory under
 * {@code %LOCALAPPDATA%\RepairKit\tools}. The previous code extracted every
 * archive into one shared {@code %TEMP%\RepairKit} folder, which both mixed
 * incompatible DLLs and let another process pre-plant an executable that
 * RepairKit would then start with administrator rights.
 *
 * @author Foulest
 */
public final class ToolLauncher {

    private static final Duration EXTRACT_TIMEOUT = Duration.ofMinutes(5);

    private ToolLauncher() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Extracts and starts a bundled tool.
     *
     * @param tool the tool
     * @return the launch result
     */
    public static @NotNull LaunchResult launch(@NotNull Tool tool) {
        if (!tool.isBundled()) {
            return new LaunchResult(false, Messages.get("tools.launch.notBundled"), null);
        }

        @Nullable Path directory = null;

        try {
            directory = FileUtil.createToolDirectory(tool.id());
            @NotNull Path archive = directory.resolve("tool.7z");
            FileUtil.copyResource("bin/" + tool.archive(), archive);

            @NotNull Path extractor = directory.resolve("7zr.exe");
            FileUtil.copyResource("bin/7zr.exe", extractor);

            @NotNull Path appDirectory = directory.resolve("app");
            Files.createDirectories(appDirectory);

            @NotNull CommandResult extraction = CommandUtil.run(extractor, EXTRACT_TIMEOUT,
                    "x", archive.toString(), "-y", "-o" + appDirectory);

            if (!extraction.succeeded()) {
                DebugUtil.warn("Failed to extract " + tool.archive() + ": "
                        + extraction.tail(5), null);
                cleanup(directory);
                return new LaunchResult(false, Messages.get("tools.launch.extractFailed"), null);
            }

            @NotNull Path executable = appDirectory.resolve(tool.executable()).normalize();

            if (!executable.startsWith(appDirectory) || !Files.isRegularFile(executable)) {
                DebugUtil.warn("The expected executable is missing: " + executable, null);
                cleanup(directory);
                return new LaunchResult(false, Messages.get("tools.launch.executableMissing"), null);
            }

            DebugUtil.debug("Launching " + executable + " (sha256 "
                    + FileUtil.sha256(executable) + ")");

            @Nullable Process process = CommandUtil.startDetached(List.of(executable.toString()),
                    appDirectory);

            if (process == null) {
                cleanup(directory);
                return new LaunchResult(false, Messages.get("tools.launch.startFailed"), null);
            }

            return new LaunchResult(true, Messages.get("tools.launch.started", tool.name()),
                    directory);
        } catch (IOException ex) {
            DebugUtil.warn("Failed to prepare " + tool.name(), ex);

            if (directory != null) {
                cleanup(directory);
            }

            return new LaunchResult(false, Messages.get("tools.launch.prepareFailed"), null);
        }
    }

    /**
     * Opens a Windows settings page or management tool.
     *
     * @param shortcut the shortcut
     * @return the launch result
     */
    public static @NotNull LaunchResult launch(@NotNull Shortcut shortcut) {
        if (shortcut.uri()) {
            boolean opened = WindowsUtil.openUri(shortcut.target());
            return new LaunchResult(opened, opened
                    ? Messages.get("tools.launch.opened", shortcut.name())
                    : Messages.get("common.openFailed", shortcut.name()), null);
        }

        @NotNull Path executable = WindowsUtil.system32(shortcut.target());

        if (!Files.isRegularFile(executable)) {
            return new LaunchResult(false,
                    Messages.get("tools.launch.systemMissing", executable), null);
        }

        @NotNull List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(shortcut.arguments());

        @Nullable Process process = CommandUtil.startDetached(command,
                WindowsUtil.dataDirectory());

        return new LaunchResult(process != null, process != null
                ? Messages.get("tools.launch.opened", shortcut.name())
                : Messages.get("common.openFailed", shortcut.name()), null);
    }

    /**
     * Opens a vendor URL.
     *
     * @param url the URL
     * @return {@code true} when the shell accepted the request
     */
    public static boolean visit(@NotNull String url) {
        return WindowsUtil.openUri(url);
    }

    private static void cleanup(@NotNull Path directory) {
        try {
            FileUtil.deleteRecursively(directory);
        } catch (IOException ex) {
            DebugUtil.warn("Failed to clean up " + directory, ex);
        }
    }

    /**
     * The result of a launch attempt.
     *
     * @param success   whether the tool started
     * @param message   a user-facing message
     * @param directory the extraction directory, or {@code null}
     * @author Foulest
     */
    public record LaunchResult(boolean success, @NotNull String message,
                               @Nullable Path directory) {
    }
}
