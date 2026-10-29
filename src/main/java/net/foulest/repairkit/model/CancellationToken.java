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

import net.foulest.repairkit.util.CommandUtil;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation for a repair run.
 * <p>
 * Cancelling stops the next queued step and terminates any child process that
 * is currently running; long-running steps check the token between items.
 *
 * @author Foulest
 */
public final class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * Requests cancellation.
     */
    public void cancel() {
        cancelled.set(true);
        CommandUtil.cancelActiveProcesses();
    }

    /**
     * Checks whether cancellation was requested.
     *
     * @return {@code true} when cancellation was requested
     */
    public boolean isCancelled() {
        return cancelled.get();
    }
}
