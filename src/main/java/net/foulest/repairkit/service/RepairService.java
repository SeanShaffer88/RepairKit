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

import net.foulest.repairkit.model.CancellationToken;
import net.foulest.repairkit.model.RepairReport;
import net.foulest.repairkit.model.RepairTask;
import net.foulest.repairkit.model.StepResult;
import net.foulest.repairkit.model.StepStatus;
import net.foulest.repairkit.model.TaskResult;
import net.foulest.repairkit.util.DebugUtil;
import net.foulest.repairkit.util.RegistryBackup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs a repair plan sequentially and produces an honest report.
 * <p>
 * Steps run one at a time on a background thread. That avoids the previous
 * eight-way concurrency (SFC, DISM, Defender, and WinGet all competing for the
 * same servicing locks) and makes cancellation and progress reporting
 * predictable. An exception in one step becomes a failed result; it does not
 * abort the rest of the plan.
 *
 * @author Foulest
 */
public final class RepairService {

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private RepairService() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Checks whether a repair run is in progress.
     *
     * @return {@code true} while repairs are running
     */
    public static boolean isRunning() {
        return RUNNING.get();
    }

    /**
     * Executes the selected repair options.
     *
     * @param selected the selected option identifiers
     * @param token    the cancellation token
     * @param listener the progress listener
     * @return the report
     */
    public static @NotNull RepairReport execute(@NotNull Set<String> selected,
                                                @NotNull CancellationToken token,
                                                @NotNull Listener listener) {
        if (!RUNNING.compareAndSet(false, true)) {
            throw new IllegalStateException("A repair run is already in progress.");
        }

        @Nullable RegistryBackup backup = null;

        try {
            try {
                backup = RegistryBackup.create("repairs");
            } catch (IOException ex) {
                DebugUtil.warn("Registry changes for this run will not be backed up", ex);
            }

            @NotNull List<RepairTask> tasks = RepairOperations.expand(selected, backup);
            DebugUtil.debug("Starting repair run for: " + RepairOperations.describeSelection(selected)
                    + " (" + tasks.size() + " task(s))");

            @NotNull Instant startedAt = Instant.now();
            @NotNull List<TaskResult> results = new ArrayList<>();
            boolean cancelled = false;

            for (int index = 0; index < tasks.size(); index++) {
                @NotNull RepairTask task = tasks.get(index);

                if (cancelled || token.isCancelled()) {
                    cancelled = true;
                    @NotNull TaskResult skipped = new TaskResult(task, StepResult.cancelled(),
                            Duration.ZERO);
                    results.add(skipped);
                    listener.onTaskFinished(skipped, index, tasks.size());
                    continue;
                }

                listener.onTaskStarted(task, index, tasks.size());
                @NotNull Instant taskStart = Instant.now();
                @NotNull StepResult result;

                try {
                    result = task.step().run(token);
                } catch (Exception ex) {
                    DebugUtil.warn("Repair task failed: " + task.title(), ex);
                    result = StepResult.failed(ex.getClass().getSimpleName()
                            + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
                }

                if (result.status() == StepStatus.PENDING
                        || result.status() == StepStatus.RUNNING) {
                    result = StepResult.ok(result.message());
                }

                @NotNull TaskResult taskResult = new TaskResult(task, result,
                        Duration.between(taskStart, Instant.now()));
                results.add(taskResult);
                listener.onTaskFinished(taskResult, index, tasks.size());

                DebugUtil.debug("Task " + (index + 1) + "/" + tasks.size() + ": "
                        + task.title() + " -> " + result.status() + " (" + result.message() + ")");

                if (result.status() == StepStatus.CANCELLED) {
                    cancelled = true;
                }
            }

            @NotNull Instant finishedAt = Instant.now();
            @Nullable Path backupFile = backup != null && !backup.isEmpty()
                    ? backup.regFile()
                    : null;
            @NotNull RepairReport report = new RepairReport(List.copyOf(results), startedAt,
                    finishedAt, cancelled, backupFile, DebugUtil.getLogFile());

            DebugUtil.debug("Repair run finished in " + report.duration().toSeconds()
                    + "s: " + report.summary());

            if (backupFile != null) {
                DebugUtil.debug("Registry backup: " + backupFile);
            }

            return report;
        } finally {
            if (backup != null) {
                backup.close();
            }

            RUNNING.set(false);
        }
    }

    /**
     * Receives task progress. Methods are called on the worker thread.
     *
     * @author Foulest
     */
    public interface Listener {

        /**
         * Called before a task runs.
         *
         * @param task  the task
         * @param index zero-based index
         * @param total total number of tasks
         */
        void onTaskStarted(@NotNull RepairTask task, int index, int total);

        /**
         * Called after a task finishes.
         *
         * @param result the result
         * @param index  zero-based index
         * @param total  total number of tasks
         */
        void onTaskFinished(@NotNull TaskResult result, int index, int total);
    }

    /**
     * A listener that does nothing, useful for headless runs and tests.
     *
     * @return the listener
     */
    public static @NotNull Listener noopListener() {
        return new Listener() {
            @Override
            public void onTaskStarted(@NotNull RepairTask task, int index, int total) {
                // No-op.
            }

            @Override
            public void onTaskFinished(@NotNull TaskResult result, int index, int total) {
                // No-op.
            }
        };
    }
}
