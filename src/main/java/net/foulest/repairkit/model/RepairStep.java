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
 * A single unit of repair work.
 * <p>
 * Implementations may throw; the service converts an exception into a failed
 * {@link StepResult} so that one broken step cannot abort the remaining work.
 *
 * @author Foulest
 */
@FunctionalInterface
public interface RepairStep {

    /**
     * Runs the step.
     *
     * @param token cancellation token
     * @return the result of the step
     * @throws Exception when the step fails
     */
    @NotNull
    StepResult run(@NotNull CancellationToken token) throws Exception;
}
