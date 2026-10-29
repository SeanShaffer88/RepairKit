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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the cleanup-root guard.
 *
 * @author Foulest
 */
@EnabledOnOs(OS.WINDOWS)
class JunkFileUtilTest {

    @Test
    void acceptsTemporaryFolders() {
        assertTrue(JunkFileUtil.isSafeCleanupRoot(
                Path.of("C:\\Users\\user\\AppData\\Local\\Temp")));
        assertTrue(JunkFileUtil.isSafeCleanupRoot(
                Path.of("C:\\Windows\\Temp")));
        assertTrue(JunkFileUtil.isSafeCleanupRoot(
                Path.of("D:\\Cache")));
    }

    @Test
    void rejectsDangerousRoots() {
        assertFalse(JunkFileUtil.isSafeCleanupRoot(Path.of("C:\\")));
        assertFalse(JunkFileUtil.isSafeCleanupRoot(Path.of("C:\\Users")));
        assertFalse(JunkFileUtil.isSafeCleanupRoot(Path.of("C:\\Users\\user")));
        assertFalse(JunkFileUtil.isSafeCleanupRoot(Path.of("C:\\Users\\user\\Documents")));
        assertFalse(JunkFileUtil.isSafeCleanupRoot(Path.of("C:\\Windows")));
        assertFalse(JunkFileUtil.isSafeCleanupRoot(Path.of("C:\\Program Files")));
    }

    @Test
    void rejectsProtectedRootsEvenWhenTheyContainTemporaryNames() {
        assertFalse(JunkFileUtil.isSafeCleanupRoot(Path.of("C:\\Users\\Temp")));
        assertFalse(JunkFileUtil.isSafeCleanupRoot(Path.of("C:\\Users\\Cache")));
        assertFalse(JunkFileUtil.isSafeCleanupRoot(Path.of("C:\\Program Files\\Product\\Cache")));
        assertFalse(JunkFileUtil.isSafeCleanupRoot(Path.of("C:\\Program Files (x86)\\Product\\Temp")));
        assertFalse(JunkFileUtil.isSafeCleanupRoot(Path.of("C:\\Temp\\Users\\Sean")));
        assertFalse(JunkFileUtil.isSafeCleanupRoot(Path.of("C:\\Users\\Temp\\Documents")));
        assertFalse(JunkFileUtil.isSafeCleanupRoot(Path.of(System.getProperty("user.home"))));
    }

    @Test
    void rejectsJunctionInAnyAncestor(@TempDir Path directory) throws Exception {
        Path target = Files.createDirectory(directory.resolve("target"));
        Path cache = Files.createDirectory(target.resolve("Cache"));
        Files.writeString(cache.resolve("important.txt"), "must survive");
        Path link = directory.resolve("alias");
        Process creation = new ProcessBuilder(WindowsUtil.system32("cmd.exe").toString(),
                "/d", "/c", "mklink", "/J", link.toString(), target.toString())
                .redirectErrorStream(true).start();
        String output = new String(creation.getInputStream().readAllBytes());
        assertEquals(0, creation.waitFor(), output);
        try {
            assertTrue(JunkFileUtil.isSafeCleanupRoot(cache));
            assertFalse(JunkFileUtil.isSafeCleanupRoot(link.resolve("Cache")));
            assertEquals("must survive", Files.readString(cache.resolve("important.txt")));
        } finally {
            Files.delete(link); // Delete the junction itself, never its target tree.
        }
    }
}
