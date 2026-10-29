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
package net.foulest.repairkit.service;

import net.foulest.repairkit.model.Shortcut;
import net.foulest.repairkit.model.Tool;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the catalogues reference resources that actually exist.
 * <p>
 * A missing icon or archive would otherwise only show up when a user clicks the
 * card, because the class loader does not fail at build time.
 *
 * @author Foulest
 */
class CatalogTest {

    @Test
    void pagesContainEveryTool() {
        List<List<Tool>> pages = ToolCatalog.pages();

        assertEquals(3, pages.size());
        assertEquals(12, pages.get(0).size());
        assertEquals(12, pages.get(1).size());
        assertEquals(8, pages.get(2).size());
        assertEquals(32, pages.stream().mapToInt(List::size).sum());
        assertEquals(24, ToolCatalog.bundledTools().size());
    }

    @Test
    void toolIdentifiersAreUnique() {
        Set<String> ids = new HashSet<>();

        for (List<Tool> page : ToolCatalog.pages()) {
            for (Tool tool : page) {
                assertTrue(ids.add(tool.id()), "Duplicate tool id: " + tool.id());
                assertFalse(tool.name().isBlank());
                assertFalse(tool.description().isBlank());
            }
        }
    }

    @Test
    void toolResourcesExist() {
        for (Tool tool : ToolCatalog.bundledTools()) {
            assertNotNull(resource(tool.icon()), "Missing icon: " + tool.icon());
            assertNotNull(resource("bin/" + tool.archive()), "Missing archive: " + tool.archive());
            assertNotNull(tool.executable(), "Bundled tool without an executable");
            assertFalse(tool.executable().isBlank());
        }

        assertNotNull(resource("bin/7zr.exe"), "The bundled 7-Zip extractor is missing");
    }

    @Test
    void linkToolsHaveUrls() {
        for (List<Tool> page : ToolCatalog.pages()) {
            for (Tool tool : page) {
                if (!tool.isBundled()) {
                    assertNotNull(tool.url(), "Link without a URL: " + tool.id());
                    assertTrue(tool.url().startsWith("https://"),
                            "Link should use HTTPS: " + tool.id());
                }
            }
        }
    }

    @Test
    void shortcutsReferenceExistingIcons() {
        List<Shortcut> shortcuts = ShortcutCatalog.shortcuts();

        assertEquals(12, shortcuts.size());
        Set<String> ids = new HashSet<>();

        for (Shortcut shortcut : shortcuts) {
            assertTrue(ids.add(shortcut.id()), "Duplicate shortcut id: " + shortcut.id());
            assertNotNull(resource(shortcut.icon()), "Missing icon: " + shortcut.icon());
            assertFalse(shortcut.target().isBlank());
            assertFalse(shortcut.name().isBlank());
        }
    }

    private static URL resource(String path) {
        return CatalogTest.class.getClassLoader().getResource(path);
    }
}
