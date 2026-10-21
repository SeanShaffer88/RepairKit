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

import net.foulest.repairkit.util.config.ConfigMigrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ConfigMigrator}.
 *
 * @author Foulest
 */
class ConfigMigratorTest {

    @TempDir
    Path directory;

    @Test
    void addsMissingCategoriesAndKeepsUserValues() throws IOException {
        Path file = directory.resolve("system_tweaks.json");
        Files.writeString(file, """
                {
                  "tweaks": {
                    "disableTelemetry": false
                  }
                }
                """, StandardCharsets.UTF_8);

        List<String> added = ConfigMigrator.migrate(file, "config/system_tweaks.json");

        assertEquals(List.of("advancedTweaks"), added);

        String migrated = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(migrated.contains("disableTelemetry"));
        assertTrue(migrated.contains("advancedTweaks"));
        assertTrue(Files.isRegularFile(directory.resolve("system_tweaks.json.bak")));
    }

    @Test
    void leavesCurrentFilesUntouched() throws IOException {
        Path file = directory.resolve("current.json");
        Files.writeString(file, """
                {"tweaks": {"disableTelemetry": true}, "advancedTweaks": {}}
                """, StandardCharsets.UTF_8);

        List<String> added = ConfigMigrator.migrate(file, "config/system_tweaks.json");

        assertTrue(added.isEmpty());
        assertFalse(Files.exists(directory.resolve("current.json.bak")));
    }
}
