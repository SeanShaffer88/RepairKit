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

/**
 * Names of the Windows desktop sounds used by the interface.
 *
 * @author Foulest
 */
public final class ConstantUtil {

    /** The Windows error sound. */
    public static final String ERROR_SOUND = "win.sound.hand";

    /** The Windows warning sound. */
    public static final String WARNING_SOUND = "win.sound.asterisk";

    /** The Windows exclamation sound. */
    public static final String EXCLAMATION_SOUND = "win.sound.exclamation";

    private ConstantUtil() {
        throw new UnsupportedOperationException("Utility class");
    }
}
