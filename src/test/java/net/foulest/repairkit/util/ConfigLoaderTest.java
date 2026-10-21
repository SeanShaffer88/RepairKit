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
package net.foulest.repairkit.util;

import net.foulest.repairkit.util.config.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ConfigLoader}.
 *
 * @author Foulest
 */
class ConfigLoaderTest {

    @TempDir
    Path directory;

    @Test
    void readsCategoriesAndValues() throws IOException {
        Path file = write("""
                {
                  "services": {
                    "enabled": true,
                    "values": {"DiagTrack": "disabled"}
                  }
                }
                """);

        ConfigLoader loader = new ConfigLoader(file);

        assertTrue(loader.isLoaded());
        assertEquals(1, loader.getConfig().size());
        assertTrue(ConfigLoader.flag(loader.category("services"), "enabled", false));
        assertEquals(Map.of("DiagTrack", "disabled"),
                ConfigLoader.map(loader.category("services"), "values"));
    }

    @Test
    void malformedJsonDoesNotThrow() throws IOException {
        ConfigLoader loader = new ConfigLoader(write("{ this is not json"));

        assertFalse(loader.isLoaded());
        assertTrue(loader.getConfig().isEmpty());
        assertTrue(loader.category("services").isEmpty());
    }

    @Test
    void missingFileDoesNotThrow() {
        ConfigLoader loader = new ConfigLoader(directory.resolve("missing.json"));

        assertFalse(loader.isLoaded());
        assertTrue(loader.getConfig().isEmpty());
    }

    @Test
    void typedAccessorsFallBackSafely() {
        Map<String, Object> values = Map.of(
                "enabled", "yes",
                "count", "many",
                "values", List.of("one", 2, "three"));

        assertTrue(ConfigLoader.flag(values, "enabled", true));
        assertFalse(ConfigLoader.flag(values, "enabled", false));
        assertEquals(7, ConfigLoader.integer(values, "count", 7));
        assertEquals(List.of("one", "three"), ConfigLoader.strings(values, "values"));
        assertTrue(ConfigLoader.map(values, "values").isEmpty());
    }

    private Path write(String content) throws IOException {
        Path file = directory.resolve("config.json");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
