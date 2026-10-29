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
package net.foulest.repairkit.util.config.tasks;

import net.foulest.repairkit.model.RepairTask;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Converts a configuration file into runnable, individually-reported tasks.
 *
 * @author Foulest
 */
public interface ConfigTaskRunner {

    /**
     * Builds the tasks described by the configuration.
     *
     * @return the tasks
     */
    @NotNull
    List<RepairTask> getTasks();
}
