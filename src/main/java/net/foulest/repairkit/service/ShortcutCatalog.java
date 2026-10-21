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
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The System Shortcuts catalogue.
 *
 * @author Foulest
 */
public final class ShortcutCatalog {

    private ShortcutCatalog() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Gets the shortcuts, in display order.
     *
     * @return the shortcuts
     */
    public static @NotNull List<Shortcut> shortcuts() {
        return List.of(
                settings("apps", "settings.apps", "settings.apps.description",
                        "AppsFeatures.png", "ms-settings:appsfeatures", false),
                settings("startup", "settings.startup", "settings.startup.description",
                        "StartupApps.png", "ms-settings:startupapps", false),
                settings("update", "settings.update", "settings.update.description",
                        "WindowsUpdate.png", "ms-settings:windowsupdate", true),
                settings("security", "settings.security", "settings.security.description",
                        "WindowsSecurity.png", "windowsdefender:", true),
                settings("display", "settings.display", "settings.display.description",
                        "DisplaySettings.png", "ms-settings:display", false),
                settings("storage", "settings.storage", "settings.storage.description",
                        "StorageSettings.png", "ms-settings:storagesense", false),
                settings("sound", "settings.sound", "settings.sound.description",
                        "SoundSettings.png", "ms-settings:sound", false),
                tool("system-info", "settings.systemInfo", "settings.systemInfo.description",
                        "SystemInformation.png", "msinfo32.exe", List.of(), false),
                tool("task-manager", "settings.taskManager", "settings.taskManager.description",
                        "TaskManager.png", "taskmgr.exe", List.of(), false),
                tool("device-manager", "settings.deviceManager",
                        "settings.deviceManager.description", "DeviceManager.png",
                        "mmc.exe", List.of("devmgmt.msc"), false),
                tool("disk-cleanup", "settings.diskCleanup", "settings.diskCleanup.description",
                        "DiskCleanup.png", "cleanmgr.exe", List.of(), false),
                tool("msconfig", "settings.msconfig", "settings.msconfig.description",
                        "MSConfig.png", "msconfig.exe", List.of(), false)
        );
    }

    private static @NotNull Shortcut settings(@NotNull String id, @NotNull String nameKey,
                                              @NotNull String descriptionKey,
                                              @NotNull String icon, @NotNull String uri,
                                              boolean blockedInSafeMode) {
        return new Shortcut(id, nameKey, descriptionKey, "icons/" + icon, uri, List.of(),
                true, blockedInSafeMode);
    }

    private static @NotNull Shortcut tool(@NotNull String id, @NotNull String nameKey,
                                          @NotNull String descriptionKey, @NotNull String icon,
                                          @NotNull String executable,
                                          @NotNull List<String> arguments,
                                          boolean blockedInSafeMode) {
        return new Shortcut(id, nameKey, descriptionKey, "icons/" + icon, executable,
                arguments, false, blockedInSafeMode);
    }
}
