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

import java.util.Locale;

/**
 * The outcome of a single repair task.
 *
 * @author Foulest
 */
public enum StepStatus {

    /** The task has not run yet. */
    PENDING,

    /** The task is currently running. */
    RUNNING,

    /** Every change the task attempted succeeded. */
    OK,

    /** Some changes succeeded and some failed. */
    PARTIAL,

    /** The task failed and made no useful change. */
    FAILED,

    /** The task was not applicable and was skipped. */
    SKIPPED,

    /** The user cancelled the run before or during the task. */
    CANCELLED;

    /**
     * Gets the localized display name.
     *
     * @return the localized display name
     */
    public @NotNull String displayName() {
        return Messages.get("status." + name().toLowerCase(Locale.ROOT));
    }

    /**
     * Checks whether the status represents a completed, successful task.
     *
     * @return {@code true} for {@link #OK}
     */
    public boolean isSuccessful() {
        return this == OK;
    }
}
