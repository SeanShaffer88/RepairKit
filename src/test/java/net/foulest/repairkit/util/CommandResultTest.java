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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CommandResult}.
 *
 * @author Foulest
 */
class CommandResultTest {

    @Test
    void successRequiresZeroExitCodeAndNoTimeout() {
        assertTrue(result(0, false).succeeded());
        assertFalse(result(1, false).succeeded());
        assertFalse(result(0, true).succeeded());
    }

    @Test
    void tailReturnsTheLastLines() {
        CommandResult result = result(0, false, "one", "two", "three");

        assertEquals("three", result.tail(1));
        assertEquals("two\nthree", result.tail(2));
        assertEquals("one\ntwo\nthree", result.tail(10));
        assertEquals("", result.tail(0));
    }

    @Test
    void textJoinsOutput() {
        assertEquals("alpha\nbeta", result(0, false, "alpha", "beta").text());
    }

    @Test
    void containsIsCaseInsensitive() {
        CommandResult result = result(0, false, "Service is DISABLED");

        assertTrue(result.contains("disabled"));
        assertFalse(result.contains("running"));
    }

    private static CommandResult result(int exitCode, boolean timedOut, String... lines) {
        return new CommandResult("test.exe", exitCode, List.of(lines), timedOut,
                java.time.Duration.ZERO);
    }
}
