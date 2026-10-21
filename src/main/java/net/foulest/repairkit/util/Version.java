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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A semantic version that can be compared safely.
 * <p>
 * Update checks must never compare release tags as raw strings: tags such as {@code v1.4.0},
 * {@code 1.4} or {@code 1.4.1-beta.1} all occur in practice, and a string comparison can
 * either miss an update or offer a downgrade.
 *
 * @param major      major version component
 * @param minor      minor version component
 * @param patch      patch version component
 * @param preRelease pre-release suffix, or an empty string for a final release
 * @author Foulest
 */
public record Version(int major, int minor, int patch, @NotNull String preRelease)
        implements Comparable<Version> {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^[vV]?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:[-+]([0-9A-Za-z.+-]+))?$");

    /**
     * Parses a version string.
     *
     * @param raw the raw version string, may be {@code null}
     * @return the parsed version, or {@code null} when the input is not a version
     */
    public static @Nullable Version parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }

        @NotNull String trimmed = raw.trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        @NotNull Matcher matcher = VERSION_PATTERN.matcher(trimmed);

        if (!matcher.matches()) {
            return null;
        }

        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
            @NotNull String preRelease = matcher.group(4) == null
                    ? ""
                    : matcher.group(4).toLowerCase(Locale.ROOT);
            return new Version(major, minor, patch, preRelease);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Checks whether this version is newer than another version.
     *
     * @param other the version to compare against
     * @return {@code true} when this version is newer
     */
    public boolean isNewerThan(@NotNull Version other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(@NotNull Version other) {
        int result = Integer.compare(major, other.major);

        if (result != 0) {
            return result;
        }

        result = Integer.compare(minor, other.minor);

        if (result != 0) {
            return result;
        }

        result = Integer.compare(patch, other.patch);

        if (result != 0) {
            return result;
        }

        // A final release is newer than any pre-release of the same version.
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) {
            return 0;
        }

        if (preRelease.isEmpty()) {
            return 1;
        }

        if (other.preRelease.isEmpty()) {
            return -1;
        }

        return preRelease.compareTo(other.preRelease);
    }

    @Override
    public @NotNull String toString() {
        @NotNull String base = major + "." + minor + "." + patch;
        return preRelease.isEmpty() ? base : base + "-" + preRelease;
    }
}
