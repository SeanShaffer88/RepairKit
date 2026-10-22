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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Version}.
 *
 * @author Foulest
 */
class VersionTest {

    @Test
    void parsesPlainAndPrefixedTags() {
        assertEquals(new Version(1, 4, 0, ""), Version.parse("1.4.0"));
        assertEquals(new Version(1, 4, 0, ""), Version.parse("v1.4.0"));
        assertEquals(new Version(1, 4, 0, ""), Version.parse("V1.4.0"));
        assertEquals(new Version(2, 0, 0, ""), Version.parse("2.0"));
        assertEquals(new Version(3, 0, 0, ""), Version.parse("3"));
        assertEquals(new Version(1, 4, 1, "beta.1"), Version.parse("1.4.1-beta.1"));
    }

    @Test
    void rejectsNonVersions() {
        assertNull(Version.parse(null));
        assertNull(Version.parse(""));
        assertNull(Version.parse("latest"));
        assertNull(Version.parse("1.4.0.2.5.6"));
    }

    @Test
    void comparesNumericallyNotLexically() {
        // A string comparison reports 1.10.0 < 1.9.0, which offers a downgrade.
        assertTrue(Version.parse("1.10.0").isNewerThan(Version.parse("1.9.0")));
        assertTrue(Version.parse("1.4.10").isNewerThan(Version.parse("1.4.9")));
        assertFalse(Version.parse("1.4.0").isNewerThan(Version.parse("1.4.0")));
    }

    @Test
    void finalReleaseIsNewerThanPrerelease() {
        Version prerelease = Version.parse("1.5.0-rc.1");
        Version release = Version.parse("1.5.0");

        assertNotNull(prerelease);
        assertNotNull(release);
        assertTrue(release.isNewerThan(prerelease));
        assertFalse(prerelease.isNewerThan(release));
    }

    @Test
    void formatsItself() {
        assertEquals("1.4.0", new Version(1, 4, 0, "").toString());
        assertEquals("1.4.0-beta.2", new Version(1, 4, 0, "beta.2").toString());
    }
}
