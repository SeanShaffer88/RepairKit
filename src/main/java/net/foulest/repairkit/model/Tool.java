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
import org.jetbrains.annotations.Nullable;

/**
 * An entry on the Useful Programs panel.
 * <p>
 * Either the tool is bundled ({@code archive} and {@code executable} are set)
 * or it is a link to the vendor's download page ({@code url} is set).
 *
 * @param id          stable identifier, also used for the extraction folder
 * @param name        display name
 * @param subtitle    version or price text
 * @param description one-line description
 * @param icon        classpath icon resource
 * @param archive     bundled {@code .7z} resource name, or {@code null}
 * @param executable  executable name inside the archive, or {@code null}
 * @param url         vendor URL, or {@code null}
 * @param windows10Only whether the tool requires Windows 10 or later
 * @author Foulest
 */
public record Tool(@NotNull String id,
                   @NotNull String name,
                   @NotNull String subtitle,
                   @NotNull String description,
                   @NotNull String icon,
                   @Nullable String archive,
                   @Nullable String executable,
                   @Nullable String url,
                   boolean windows10Only) {

    /**
     * Checks whether this is a bundled tool rather than a link.
     *
     * @return {@code true} when the tool ships with RepairKit
     */
    public boolean isBundled() {
        return archive != null && executable != null;
    }
}
