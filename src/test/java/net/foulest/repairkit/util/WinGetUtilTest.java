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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the WinGet output parser.
 * <p>
 * The parser reads a fixed-width table, so it must cope with product names that
 * contain runs of spaces and with output that has no table at all.
 *
 * @author Foulest
 */
class WinGetUtilTest {

    private static final List<String> REAL_TABLE = List.of(
            "Name                                  Id                     Version      Available    Source",
            "----------------------------------------------------------------------------------------------",
            "Microsoft Visual Studio Code          Microsoft.VisualStudioCode  1.90.0  1.91.0  winget",
            "7-Zip                                 7zip.7zip             24.06        24.07        winget",
            "Git                                   Git.Git               2.45.2       2.46.0       winget",
            "");

    @Test
    void parsesTheIdColumn() {
        assertEquals(List.of("Microsoft.VisualStudioCode", "7zip.7zip", "Git.Git"),
                WinGetUtil.parseUpgradeIds(REAL_TABLE));
    }

    @Test
    void findsTheIdWhenTheNameContainsSpaces() {
        List<String> table = List.of(
                "Name              Id              Version  Available  Source",
                "----------------------------------------------------------------",
                "Windows Terminal  Microsoft.WindowsTerminal  1.20.0  1.21.0  winget",
                "Node.js LTS 22    OpenJS.NodeJS.LTS  22.1.0  22.4.0  winget");

        assertEquals(List.of("Microsoft.WindowsTerminal", "OpenJS.NodeJS.LTS"),
                WinGetUtil.parseUpgradeIds(table));
    }

    @Test
    void ignoresAnEmptyTable() {
        assertTrue(WinGetUtil.parseUpgradeIds(List.of()).isEmpty());
    }

    @Test
    void ignoresOutputWithoutASeparator() {
        assertTrue(WinGetUtil.parseUpgradeIds(List.of(
                "No installed package found matching input criteria.")).isEmpty());
    }

    @Test
    void ignoresRowsWithoutAVersionAfterTheId() {
        assertTrue(WinGetUtil.parseUpgradeIds(List.of(
                "Name        Id          Version  Available  Source",
                "----------------------------------------------",
                "Broken      Some.Package           ")).isEmpty());
    }

    @Test
    void deDuplicatesRepeatedPackages() {
        List<String> table = List.of(
                "Name    Id          Version  Available  Source",
                "----------------------------------------------",
                "App     Vendor.App  1.0      2.0        winget",
                "App     Vendor.App  1.0      2.0        winget");

        assertEquals(List.of("Vendor.App"), WinGetUtil.parseUpgradeIds(table));
    }

    @Test
    void stopsAtTheEndOfTheTable() {
        List<String> table = List.of(
                "Name    Id          Version  Available  Source",
                "----------------------------------------------",
                "App     Vendor.App  1.0      2.0        winget",
                "",
                "2 upgrades available.",
                "other   Other.App   1.0      2.0        winget");

        // The trailing prose is not part of the table and must not be parsed as
        // a package row, even though it happens to be shaped like one.
        assertEquals(List.of("Vendor.App"), WinGetUtil.parseUpgradeIds(table));
    }
}
