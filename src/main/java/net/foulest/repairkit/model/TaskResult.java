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

import java.time.Duration;

/**
 * The completed result of one repair task.
 *
 * @param task     the task that ran
 * @param result   the outcome
 * @param duration how long the task took
 * @author Foulest
 */
public record TaskResult(@NotNull RepairTask task, @NotNull StepResult result,
                         @NotNull Duration duration) {

    /**
     * Gets the localized task title.
     *
     * @return the localized task title
     */
    public @NotNull String title() {
        return task.title();
    }

    /**
     * Gets the outcome status.
     *
     * @return the outcome status
     */
    public @NotNull StepStatus status() {
        return result.status();
    }

    /**
     * Gets the diagnostic message.
     *
     * @return the diagnostic message
     */
    public @NotNull String message() {
        return result.message();
    }
}
