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
package net.foulest.repairkit.model;

import net.foulest.repairkit.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The result of a complete repair run.
 *
 * @param results    per-task results, in execution order
 * @param startedAt  when the run started
 * @param finishedAt when the run finished
 * @param cancelled  whether the user cancelled the run
 * @param backupFile the registry backup file, or {@code null} when nothing was backed up
 * @param logFile    the log file for this run
 * @author Foulest
 */
public record RepairReport(@NotNull List<TaskResult> results,
                           @NotNull Instant startedAt,
                           @NotNull Instant finishedAt,
                           boolean cancelled,
                           @Nullable Path backupFile,
                           @NotNull Path logFile) {

    /**
     * Counts results with a given status.
     *
     * @param status the status to count
     * @return the number of results with that status
     */
    public int count(@NotNull StepStatus status) {
        return (int) results.stream().filter(result -> result.status() == status).count();
    }

    /**
     * Checks whether every selected task completed successfully.
     *
     * @return {@code true} when no task failed, partially failed, or was cancelled
     */
    public boolean isFullySuccessful() {
        return results.stream().allMatch(result -> result.status() == StepStatus.OK
                || result.status() == StepStatus.SKIPPED);
    }

    /**
     * Checks whether any task asked for a restart.
     *
     * @return {@code true} when Windows should be restarted
     */
    public boolean requiresRestart() {
        return results.stream().anyMatch(result -> result.result().restartRequired());
    }

    /**
     * Gets the localised completion summary.
     *
     * @return the completion summary
     */
    public @NotNull String summary() {
        return Messages.get("repairs.finished.message",
                count(StepStatus.OK),
                count(StepStatus.PARTIAL),
                count(StepStatus.FAILED),
                count(StepStatus.SKIPPED),
                count(StepStatus.CANCELLED));
    }

    /**
     * Gets the total run time.
     *
     * @return the total run time
     */
    public @NotNull Duration duration() {
        return Duration.between(startedAt, finishedAt);
    }
}
