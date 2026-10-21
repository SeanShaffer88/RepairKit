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

import java.util.List;

/**
 * A Windows settings or management tool shown on the System Shortcuts panel.
 *
 * @param id              stable identifier
 * @param nameKey         message key for the name
 * @param descriptionKey  message key for the description
 * @param icon            classpath icon resource
 * @param target          a URI such as {@code ms-settings:display}, or a system executable
 * @param arguments       arguments for a system executable
 * @param uri             whether {@code target} is a shell URI rather than an executable
 * @param blockedInSafeMode whether the tool cannot run in Safe Mode
 * @author Foulest
 */
public record Shortcut(@NotNull String id,
                       @NotNull String nameKey,
                       @NotNull String descriptionKey,
                       @NotNull String icon,
                       @NotNull String target,
                       @NotNull List<String> arguments,
                       boolean uri,
                       boolean blockedInSafeMode) {

    /**
     * Gets the localized name.
     *
     * @return the localized name
     */
    public @NotNull String name() {
        return Messages.get(nameKey);
    }

    /**
     * Gets the localized description.
     *
     * @return the localized description
     */
    public @NotNull String description() {
        return Messages.get(descriptionKey);
    }
}
