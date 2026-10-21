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

import org.jetbrains.annotations.NotNull;

/**
 * The result of a repair task.
 * <p>
 * Messages are diagnostic text and are written to the log; user-facing
 * status wording comes from {@link StepStatus#displayName()}.
 *
 * @param status  outcome of the task
 * @param message short diagnostic description of what happened
 * @param changes         number of individual changes that were applied
 * @param restartRequired whether Windows must restart before the change takes effect
 * @author Foulest
 */
public record StepResult(@NotNull StepStatus status, @NotNull String message, int changes,
                         boolean restartRequired) {

    public static @NotNull StepResult ok(int changes) {
        return new StepResult(StepStatus.OK, changes + " change(s) applied.", changes, false);
    }

    public static @NotNull StepResult ok(@NotNull String message) {
        return new StepResult(StepStatus.OK, message, 0, false);
    }

    public static @NotNull StepResult ok(@NotNull String message, boolean restartRequired) {
        return new StepResult(StepStatus.OK, message, 0, restartRequired);
    }

    public static @NotNull StepResult partial(@NotNull String message, int changes) {
        return new StepResult(StepStatus.PARTIAL, message, changes, false);
    }

    public static @NotNull StepResult failed(@NotNull String message) {
        return new StepResult(StepStatus.FAILED, message, 0, false);
    }

    public static @NotNull StepResult skipped(@NotNull String message) {
        return new StepResult(StepStatus.SKIPPED, message, 0, false);
    }

    public static @NotNull StepResult cancelled() {
        return new StepResult(StepStatus.CANCELLED, "Cancelled by the user.", 0, false);
    }

    /**
     * Returns a copy with a different message.
     *
     * @param newMessage the new message
     * @return the copy
     */
    public @NotNull StepResult withMessage(@NotNull String newMessage) {
        return new StepResult(status, newMessage, changes, restartRequired);
    }

    /**
     * Returns a copy marked as requiring a restart.
     *
     * @param restart whether a restart is required
     * @return the copy
     */
    public @NotNull StepResult withRestartRequired(boolean restart) {
        return new StepResult(status, message, changes, restart);
    }
}
