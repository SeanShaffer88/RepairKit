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

/**
 * A selectable repair option.
 *
 * @param id             stable identifier used by the UI and tests
 * @param titleKey       message key for the option title
 * @param descriptionKey message key for the option description
 * @param risk           how invasive the option is
 * @param defaultEnabled whether the option is selected by default
 * @param step           the work to perform
 * <p>
 * Generated tasks (registry categories, services, and features) pass literal
 * text in {@code titleKey} and {@code descriptionKey}. {@link Messages} returns
 * a missing key unchanged, so literal text is displayed as-is while the
 * catalogue remains translatable through message keys.
 * @author Foulest
 */
public record RepairTask(@NotNull String id,
                         @NotNull String titleKey,
                         @NotNull String descriptionKey,
                         @NotNull RiskLevel risk,
                         boolean defaultEnabled,
                         @NotNull RepairStep step) {

    /**
     * Gets the localized title.
     *
     * @return the localized title
     */
    public @NotNull String title() {
        return Messages.get(titleKey);
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
