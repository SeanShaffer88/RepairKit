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
package net.foulest.repairkit.util.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.foulest.repairkit.util.DebugUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a RepairKit JSON configuration file.
 * <p>
 * Loading never throws: a missing, unreadable, or malformed file produces an
 * empty configuration and a log entry, so a typo in a config file cannot crash
 * the application before the UI appears.
 *
 * @author Foulest
 */
public class ConfigLoader {

    private static final Type CONFIG_TYPE = new TypeToken<Map<String, Map<String, Object>>>() {
    }.getType();

    private final Map<String, Map<String, Object>> config;
    private final Path file;
    private final boolean loaded;

    /**
     * Loads a configuration file.
     *
     * @param file the file to load
     */
    public ConfigLoader(@NotNull Path file) {
        this.file = file;
        Map<String, Map<String, Object>> parsed = null;

        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                parsed = new Gson().fromJson(reader, CONFIG_TYPE);
            } catch (IOException | RuntimeException ex) {
                DebugUtil.warn("Failed to read configuration file: " + file, ex);
            }
        } else {
            DebugUtil.warn("Configuration file does not exist: " + file, null);
        }

        this.config = parsed == null ? Map.of() : new LinkedHashMap<>(parsed);
        this.loaded = parsed != null;
    }

    /**
     * Gets the parsed configuration.
     *
     * @return the configuration, keyed by category
     */
    public @NotNull Map<String, Map<String, Object>> getConfig() {
        return config;
    }

    /**
     * Gets the source file.
     *
     * @return the source file
     */
    public @NotNull Path getFile() {
        return file;
    }

    /**
     * Checks whether the file was read successfully.
     *
     * @return {@code true} when the file parsed
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Gets a category.
     *
     * @param name the category name
     * @return the category, or an empty map when it is missing
     */
    public @NotNull Map<String, Object> category(@NotNull String name) {
        Map<String, Object> category = config.get(name);
        return category == null ? Map.of() : category;
    }

    /**
     * Reads a boolean flag, defaulting to {@code defaultValue} when absent or
     * when the JSON value is not a boolean.
     *
     * @param values       the map to read
     * @param key          the key
     * @param defaultValue the fallback value
     * @return the flag
     */
    public static boolean flag(@NotNull Map<String, Object> values, @NotNull String key,
                               boolean defaultValue) {
        Object value = values.get(key);
        return value instanceof Boolean flag ? flag : defaultValue;
    }

    /**
     * Reads a list of strings, ignoring non-string entries.
     *
     * @param values the map to read
     * @param key    the key
     * @return the strings
     */
    public static @NotNull List<String> strings(@NotNull Map<String, Object> values,
                                                @NotNull String key) {
        Object value = values.get(key);

        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }

        @NotNull List<String> strings = new ArrayList<>();

        for (Object entry : collection) {
            if (entry instanceof String text && !text.isBlank()) {
                strings.add(text);
            }
        }

        return strings;
    }

    /**
     * Reads a nested map, returning an empty map for a missing or mistyped value.
     *
     * @param values the map to read
     * @param key    the key
     * @return the nested map
     */
    public static @NotNull Map<String, Object> map(@NotNull Map<String, Object> values,
                                                   @NotNull String key) {
        Object value = values.get(key);

        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }

        @NotNull Map<String, Object> result = new LinkedHashMap<>();

        map.forEach((entryKey, entryValue) -> {
            if (entryKey instanceof String text) {
                result.put(text, entryValue);
            }
        });

        return result;
    }

    /**
     * Reads an integer, defaulting when absent or mistyped.
     *
     * @param values       the map to read
     * @param key          the key
     * @param defaultValue the fallback
     * @return the integer
     */
    public static int integer(@NotNull Map<String, Object> values, @NotNull String key,
                              int defaultValue) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }
}
