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

import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinReg;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * Registry access with tolerant reads and recursive deletes.
 * <p>
 * The previous implementation deleted keys non-recursively, so a single key
 * with a subkey threw and aborted the whole category. It also created nested
 * keys on write, which is desirable, but had no backup path. Both are fixed
 * here: writes are backed up by {@link RegistryBackup}, deletes are recursive
 * and skip protected roots.
 *
 * @author Foulest
 */
public final class RegistryUtil {

    /** Registry value name that represents the default (unnamed) value. */
    public static final String DEFAULT_VALUE = "";

    private static final List<String> PROTECTED_PATHS = List.of(
            "SOFTWARE",
            "SYSTEM",
            "SYSTEM\\CurrentControlSet",
            "SYSTEM\\CurrentControlSet\\Services",
            "SOFTWARE\\Microsoft",
            "SOFTWARE\\Microsoft\\Windows",
            "SOFTWARE\\Microsoft\\Windows NT"
    );

    private RegistryUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Checks whether a key exists.
     *
     * @param hive    the registry hive
     * @param keyPath the key path
     * @return {@code true} when the key exists
     */
    public static boolean keyExists(WinReg.HKEY hive, @NotNull String keyPath) {
        try {
            return Advapi32Util.registryKeyExists(hive, keyPath);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Reads all values of a key.
     *
     * @param hive    the registry hive
     * @param keyPath the key path
     * @return the values, or an empty map when the key does not exist
     */
    public static @NotNull Map<String, Object> values(WinReg.HKEY hive, @NotNull String keyPath) {
        try {
            return new TreeMap<>(Advapi32Util.registryGetValues(hive, keyPath));
        } catch (RuntimeException ex) {
            return Collections.emptyMap();
        }
    }

    /**
     * Reads a single value.
     *
     * @param hive      the registry hive
     * @param keyPath   the key path
     * @param valueName the value name, empty for the default value
     * @return the value, or empty when it does not exist
     */
    public static @NotNull Optional<Object> value(WinReg.HKEY hive, @NotNull String keyPath,
                                                  @NotNull String valueName) {
        return Optional.ofNullable(values(hive, keyPath).get(valueName));
    }

    /**
     * Reads a string value.
     *
     * @param hive      the registry hive
     * @param keyPath   the key path
     * @param valueName the value name
     * @return the value, or empty when it is missing or not a string
     */
    public static @NotNull Optional<String> stringValue(WinReg.HKEY hive, @NotNull String keyPath,
                                                        @NotNull String valueName) {
        Object value = values(hive, keyPath).get(valueName);
        return value instanceof String text ? Optional.of(text) : Optional.empty();
    }

    /**
     * Reads an integer value.
     *
     * @param hive      the registry hive
     * @param keyPath   the key path
     * @param valueName the value name
     * @return the value, or empty when it is missing or not a number
     */
    public static @NotNull OptionalInt intValue(WinReg.HKEY hive, @NotNull String keyPath,
                                                @NotNull String valueName) {
        Object value = values(hive, keyPath).get(valueName);
        return value instanceof Integer number ? OptionalInt.of(number) : OptionalInt.empty();
    }

    /**
     * Sets a DWORD value, creating parent keys when needed.
     *
     * @param hive      the registry hive
     * @param keyPath   the key path
     * @param valueName the value name, empty for the default value
     * @param value     the value
     */
    public static void setInt(WinReg.HKEY hive, @NotNull String keyPath,
                              @NotNull String valueName, int value) {
        createKey(hive, keyPath);
        Advapi32Util.registrySetIntValue(hive, keyPath, valueName, value);
    }

    /**
     * Sets a string value, creating parent keys when needed.
     *
     * @param hive      the registry hive
     * @param keyPath   the key path
     * @param valueName the value name, empty for the default value
     * @param value     the value
     */
    public static void setString(WinReg.HKEY hive, @NotNull String keyPath,
                                 @NotNull String valueName, @NotNull String value) {
        createKey(hive, keyPath);
        Advapi32Util.registrySetStringValue(hive, keyPath, valueName, value);
    }

    private static void createKey(WinReg.HKEY hive, @NotNull String keyPath) {
        if (keyExists(hive, keyPath)) {
            return;
        }

        DebugUtil.debug("Creating registry key: " + keyPath);

        // RegCreateKeyEx creates every missing intermediate key and JNA closes
        // the returned handle internally.
        Advapi32Util.registryCreateKey(hive, keyPath);
    }

    /**
     * Deletes a value when it exists.
     *
     * @param hive      the registry hive
     * @param keyPath   the key path
     * @param valueName the value name
     * @return {@code true} when a value was deleted
     */
    public static boolean deleteValue(WinReg.HKEY hive, @NotNull String keyPath,
                                      @NotNull String valueName) {
        if (!keyExists(hive, keyPath)) {
            return false;
        }

        try {
            if (!Advapi32Util.registryValueExists(hive, keyPath, valueName)) {
                return false;
            }

            Advapi32Util.registryDeleteValue(hive, keyPath, valueName);
            return true;
        } catch (RuntimeException ex) {
            DebugUtil.warn("Failed to delete registry value " + keyPath + "\\" + valueName, ex);
            return false;
        }
    }

    /**
     * Deletes a key and all of its subkeys.
     * <p>
     * The Windows API only deletes empty keys, which is why the original code
     * failed on keys such as {@code HKCU\Software\Policies\Microsoft\MMC}.
     *
     * @param hive    the registry hive
     * @param keyPath the key path
     * @return {@code true} when the key was deleted
     */
    public static boolean deleteTree(WinReg.HKEY hive, @NotNull String keyPath) {
        @NotNull String normalized = keyPath.replaceAll("^\\\\+|\\\\+$", "");

        if (normalized.isEmpty() || PROTECTED_PATHS.stream()
                .anyMatch(protectedPath -> protectedPath.equalsIgnoreCase(normalized))) {
            DebugUtil.warn("Refusing to delete a protected registry path: " + keyPath, null);
            return false;
        }

        if (!keyExists(hive, normalized)) {
            return false;
        }

        try {
            for (String subKey : Advapi32Util.registryGetKeys(hive, normalized)) {
                deleteTree(hive, normalized + "\\" + subKey);
            }

            Advapi32Util.registryDeleteKey(hive, normalized);
            return true;
        } catch (RuntimeException ex) {
            DebugUtil.warn("Failed to delete registry key: " + normalized, ex);
            return false;
        }
    }

    /**
     * Lists the subkeys of a key.
     *
     * @param hive    the registry hive
     * @param keyPath the key path
     * @return the subkey names, or an empty list when the key is missing
     */
    public static @NotNull List<String> listSubKeys(WinReg.HKEY hive, @NotNull String keyPath) {
        if (!keyExists(hive, keyPath)) {
            return List.of();
        }

        try {
            return new ArrayList<>(List.of(Advapi32Util.registryGetKeys(hive, keyPath)));
        } catch (RuntimeException ex) {
            DebugUtil.warn("Failed to list subkeys of " + keyPath, ex);
            return List.of();
        }
    }

    /**
     * Lists the values of a key.
     *
     * @param hive    the registry hive
     * @param keyPath the key path
     * @return the value names, or an empty list when the key is missing
     */
    public static @NotNull List<String> listValues(WinReg.HKEY hive, @NotNull String keyPath) {
        return new ArrayList<>(values(hive, keyPath).keySet());
    }

    /**
     * Gets the registry path name used by {@code reg.exe} and {@code .reg} files.
     *
     * @param hive    the hive
     * @param keyPath the key path
     * @return the fully qualified path
     */
    public static @NotNull String qualifiedPath(WinReg.HKEY hive, @NotNull String keyPath) {
        return hiveName(hive) + "\\" + keyPath.replaceAll("^\\\\+", "");
    }

    /**
     * Gets the display name of a hive.
     *
     * @param hive the hive
     * @return the hive name
     */
    public static @NotNull String hiveName(@Nullable WinReg.HKEY hive) {
        if (WinReg.HKEY_LOCAL_MACHINE.equals(hive)) {
            return "HKEY_LOCAL_MACHINE";
        }

        if (WinReg.HKEY_CURRENT_USER.equals(hive)) {
            return "HKEY_CURRENT_USER";
        }

        if (WinReg.HKEY_CLASSES_ROOT.equals(hive)) {
            return "HKEY_CLASSES_ROOT";
        }

        if (WinReg.HKEY_USERS.equals(hive)) {
            return "HKEY_USERS";
        }

        if (WinReg.HKEY_CURRENT_CONFIG.equals(hive)) {
            return "HKEY_CURRENT_CONFIG";
        }

        return "HKEY_UNKNOWN";
    }

    /**
     * Opens a handle for read access. Callers must close it.
     *
     * @param hive    the hive
     * @param keyPath the key path
     * @return the handle, or {@code null} when the key cannot be opened
     */
    public static @Nullable WinReg.HKEY openForRead(WinReg.HKEY hive, @NotNull String keyPath) {
        try {
            WinReg.HKEYByReference reference =
                    Advapi32Util.registryGetKey(hive, keyPath, WinNT.KEY_READ);
            return reference.getValue();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Closes a handle opened by {@link #openForRead}.
     *
     * @param key the handle
     */
    public static void close(@Nullable WinReg.HKEY key) {
        if (key != null) {
            Advapi32.INSTANCE.RegCloseKey(key);
        }
    }
}
