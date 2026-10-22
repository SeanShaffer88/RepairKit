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

import net.foulest.repairkit.util.config.ConfigLoader;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Type-tolerant access to the entries of a configuration category.
 * <p>
 * A malformed entry is skipped with a log message instead of throwing, so one
 * bad line in a hand-edited config cannot abort the remaining repairs.
 *
 * @author Foulest
 */
public final class ConfigEntries {

    private ConfigEntries() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Checks whether a category is enabled (defaults to true).
     *
     * @param entries the category
     * @return {@code true} when the category should run
     */
    public static boolean isEnabled(@NotNull Map<String, Object> entries) {
        return ConfigLoader.flag(entries, "enabled", true);
    }

    /**
     * Reads the {@code values} map of a category.
     *
     * @param entries the category
     * @return the values, or an empty map
     */
    public static @NotNull Map<String, Object> values(@NotNull Map<String, Object> entries) {
        return ConfigLoader.map(entries, "values");
    }

    /**
     * Reads the {@code values} array of a category.
     *
     * @param entries the category
     * @return the values, or an empty list
     */
    public static @NotNull List<String> valueList(@NotNull Map<String, Object> entries) {
        return ConfigLoader.strings(entries, "values");
    }

    /**
     * Converts a collection of objects to strings, keeping only string entries.
     *
     * @param value the collection
     * @return the strings
     */
    public static @NotNull List<String> strings(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }

        return collection.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    /**
     * Copies a map of objects into a typed map.
     *
     * @param value the map
     * @return the copy
     */
    public static @NotNull Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }

        @NotNull Map<String, Object> result = new LinkedHashMap<>();

        map.forEach((key, entry) -> {
            if (key instanceof String text) {
                result.put(text, entry);
            }
        });

        return result;
    }
}
