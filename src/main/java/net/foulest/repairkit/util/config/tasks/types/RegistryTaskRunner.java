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
package net.foulest.repairkit.util.config.tasks.types;

import com.sun.jna.platform.win32.WinReg;
import net.foulest.repairkit.model.RepairTask;
import net.foulest.repairkit.model.RiskLevel;
import net.foulest.repairkit.model.StepResult;
import net.foulest.repairkit.util.DebugUtil;
import net.foulest.repairkit.util.RegistryBackup;
import net.foulest.repairkit.util.RegistryUtil;
import net.foulest.repairkit.util.config.tasks.ConfigEntries;
import net.foulest.repairkit.util.config.tasks.ConfigTaskRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Applies registry values from a configuration file.
 * <p>
 * Each category becomes one task so that the results table shows which part of
 * the registry failed. Every value is backed up before it is changed, and a
 * single bad key is recorded rather than aborting the category.
 *
 * @author Foulest
 */
public class RegistryTaskRunner implements ConfigTaskRunner {

    private final Map<String, Map<String, Object>> config;
    private final String fileLabel;
    private final String idPrefix;
    private final @Nullable RegistryBackup backup;

    /**
     * Creates a runner.
     *
     * @param config    the parsed configuration
     * @param fileLabel the file name shown in the UI
     * @param idPrefix  prefix for task identifiers, so categories from different
     *                  files cannot collide
     * @param backup    the registry backup, may be {@code null}
     */
    public RegistryTaskRunner(@NotNull Map<String, Map<String, Object>> config,
                              @NotNull String fileLabel,
                              @NotNull String idPrefix,
                              @Nullable RegistryBackup backup) {
        this.config = config;
        this.fileLabel = fileLabel;
        this.idPrefix = idPrefix;
        this.backup = backup;
    }

    @Override
    public @NotNull List<RepairTask> getTasks() {
        @NotNull List<RepairTask> tasks = new ArrayList<>();

        config.forEach((category, entries) -> {
            if (!ConfigEntries.isEnabled(entries)) {
                DebugUtil.debug("Registry category is disabled: " + category);
                return;
            }

            @NotNull Map<String, Object> values = ConfigEntries.values(entries);

            if (values.isEmpty()) {
                return;
            }

            @NotNull String title = "Registry: " + category;
            @NotNull String description = values.size() + " value(s) from " + fileLabel + ".";

            tasks.add(new RepairTask(idPrefix + "." + category, title, description,
                    RiskLevel.MODERATE, true, token -> apply(category, values, token.isCancelled())));
        });

        return tasks;
    }

    private @NotNull StepResult apply(@NotNull String category,
                                      @NotNull Map<String, Object> values,
                                      boolean cancelledAtStart) {
        if (cancelledAtStart) {
            return StepResult.cancelled();
        }

        int applied = 0;
        int failed = 0;
        int skipped = 0;

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            @Nullable ParsedEntry parsed = ParsedEntry.parse(entry.getKey(), entry.getValue());

            if (parsed == null) {
                skipped++;
                DebugUtil.warn("Skipped an invalid registry entry in " + category + ": "
                        + entry.getKey(), null);
                continue;
            }

            try {
                applyEntry(parsed);
                applied++;
            } catch (RuntimeException ex) {
                failed++;
                DebugUtil.warn("Failed to apply " + entry.getKey() + " in " + category, ex);
            }
        }

        @NotNull String message = applied + " applied, " + failed + " failed, "
                + skipped + " invalid.";

        if (failed == 0 && skipped == 0) {
            return StepResult.ok(applied);
        }

        if (applied == 0) {
            return StepResult.failed(message);
        }

        return StepResult.partial(message, applied);
    }

    private void applyEntry(@NotNull ParsedEntry entry) {
        switch (entry.action()) {
            case SET_INT -> {
                backupValue(entry);
                RegistryUtil.setInt(entry.hive(), entry.keyPath(), entry.valueName(),
                        entry.intValue());
            }

            case SET_STRING -> {
                backupValue(entry);
                RegistryUtil.setString(entry.hive(), entry.keyPath(), entry.valueName(),
                        entry.stringValue());
            }

            case DELETE_VALUE -> {
                backupValue(entry);
                RegistryUtil.deleteValue(entry.hive(), entry.keyPath(), entry.valueName());
            }

            case DELETE_KEY -> {
                if (backup != null) {
                    backup.recordKeyDeletion(entry.hive(), entry.keyPath());
                }

                RegistryUtil.deleteTree(entry.hive(), entry.keyPath());
            }
        }
    }

    private void backupValue(@NotNull ParsedEntry entry) {
        if (backup != null) {
            backup.recordValue(entry.hive(), entry.keyPath(), entry.valueName());
        }
    }

    private enum Action {
        SET_INT,
        SET_STRING,
        DELETE_VALUE,
        DELETE_KEY
    }

    private record ParsedEntry(WinReg.HKEY hive, String keyPath, String valueName,
                               Action action, int intValue, String stringValue) {

        private static final int MAX_KEY_LENGTH = 512;

        static @Nullable ParsedEntry parse(@NotNull String key, @Nullable Object value) {
            if (key.length() > MAX_KEY_LENGTH || !key.contains("\\")) {
                return null;
            }

            WinReg.HKEY hive;
            String path;

            if (key.startsWith("HKEY_LOCAL_MACHINE\\") || key.startsWith("HKLM\\")) {
                hive = WinReg.HKEY_LOCAL_MACHINE;
                path = key.startsWith("HKLM\\") ? key.substring(5) : key.substring(19);
            } else if (key.startsWith("HKEY_CURRENT_USER\\") || key.startsWith("HKCU\\")) {
                hive = WinReg.HKEY_CURRENT_USER;
                path = key.startsWith("HKCU\\") ? key.substring(5) : key.substring(18);
            } else if (key.startsWith("HKEY_CLASSES_ROOT\\") || key.startsWith("HKCR\\")) {
                hive = WinReg.HKEY_CLASSES_ROOT;
                path = key.startsWith("HKCR\\") ? key.substring(5) : key.substring(18);
            } else {
                return null;
            }

            if (path.isBlank()) {
                return null;
            }

            if ("DELETE_KEY".equals(value)) {
                return new ParsedEntry(hive, path, "", Action.DELETE_KEY, 0, "");
            }

            int index = path.lastIndexOf('\\');

            if (index <= 0 || index == path.length() - 1) {
                return null;
            }

            @NotNull String valueName = path.substring(index + 1);
            @NotNull String keyPath = path.substring(0, index);

            // Older configuration files used "(Default)"; the registry calls it "".
            if (valueName.equals("(Default)") || valueName.equals("@")) {
                valueName = RegistryUtil.DEFAULT_VALUE;
            }

            if ("DELETE_VALUE".equals(value)) {
                return new ParsedEntry(hive, keyPath, valueName, Action.DELETE_VALUE, 0, "");
            }

            if (value instanceof Integer number) {
                return new ParsedEntry(hive, keyPath, valueName, Action.SET_INT, number, "");
            }

            if (value instanceof Double number) {
                return new ParsedEntry(hive, keyPath, valueName, Action.SET_INT,
                        number.intValue(), "");
            }

            if (value instanceof String text) {
                return new ParsedEntry(hive, keyPath, valueName, Action.SET_STRING, 0, text);
            }

            return null;
        }
    }
}
