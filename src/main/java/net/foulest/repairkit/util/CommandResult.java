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

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * The complete result of a child process.
 * <p>
 * RepairKit never treats a command as successful just because it produced no
 * error text; callers decide based on the exit code and, where the tool
 * supports it, structured output.
 *
 * @param command  the command line that ran, for diagnostics
 * @param exitCode the process exit code, or {@code -1} when it did not exit
 * @param output   the combined standard output and standard error
 * @param timedOut whether the process was terminated because it exceeded its timeout
 * @param duration how long the process ran
 * @author Foulest
 */
public record CommandResult(@NotNull String command, int exitCode, @NotNull List<String> output,
                            boolean timedOut, @NotNull Duration duration) {

    /**
     * Checks whether the process exited normally with code zero.
     *
     * @return {@code true} on success
     */
    public boolean succeeded() {
        return !timedOut && exitCode == 0;
    }

    /**
     * Joins the output into a single string.
     *
     * @return the combined output
     */
    public @NotNull String text() {
        return String.join("\n", output);
    }

    /**
     * Gets the last few output lines, which is where tools normally print errors.
     *
     * @param lines the number of lines to keep
     * @return the trailing output
     */
    public @NotNull String tail(int lines) {
        if (output.isEmpty()) {
            return "";
        }

        int from = Math.max(0, output.size() - lines);
        return String.join("\n", output.subList(from, output.size()));
    }

    /**
     * Checks whether the output contains a value, ignoring case.
     *
     * @param value the value to look for
     * @return {@code true} when the output contains the value
     */
    public boolean contains(@NotNull String value) {
        return text().toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
    }

    /**
     * Builds a one-line description for logs.
     *
     * @return the description
     */
    public @NotNull String describe() {
        return command + " -> exit " + exitCode + (timedOut ? " (timed out)" : "")
                + " in " + duration.toMillis() + " ms";
    }
}
