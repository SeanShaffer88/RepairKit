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
import com.sun.jna.platform.win32.Winsvc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * Process and Windows service queries.
 * <p>
 * Service state is read through the Service Control Manager rather than by
 * searching for the word {@code RUNNING} in localized {@code sc} output.
 *
 * @author Foulest
 */
public final class ProcessUtil {

    private static final Pattern PROCESS_NAME = Pattern.compile("^[A-Za-z0-9._-]+$");

    private ProcessUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Checks whether a process is running.
     *
     * @param processName the image name, for example {@code chrome.exe}
     * @return {@code true} when at least one matching process exists
     */
    public static boolean isProcessRunning(@NotNull String processName) {
        if (!PROCESS_NAME.matcher(processName).matches()) {
            DebugUtil.warn("Refusing to query an invalid process name: " + processName, null);
            return false;
        }

        @NotNull CommandResult result = CommandUtil.run(
                WindowsUtil.system32("tasklist.exe"), Duration.ofSeconds(30),
                "/FO", "CSV", "/NH", "/FI", "IMAGENAME eq " + processName);

        if (!result.succeeded()) {
            return false;
        }

        for (String line : result.output()) {
            @NotNull String trimmed = line.trim();

            if (!trimmed.startsWith("\"")) {
                continue;
            }

            int end = trimmed.indexOf("\",", 1);

            if (end < 0) {
                continue;
            }

            @NotNull String imageName = trimmed.substring(1, end);

            if (imageName.equalsIgnoreCase(processName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Terminates every process with the given image name.
     *
     * @param processName the image name
     * @return {@code true} when the command succeeded
     */
    public static boolean killProcess(@NotNull String processName) {
        if (!PROCESS_NAME.matcher(processName).matches()) {
            return false;
        }

        @NotNull CommandResult result = CommandUtil.run(
                WindowsUtil.system32("taskkill.exe"), Duration.ofSeconds(30),
                "/F", "/T", "/IM", processName);

        return result.succeeded();
    }

    /**
     * Reads the current state of a service.
     *
     * @param serviceName the service name
     * @return the {@code SERVICE_*} state, or empty when the service does not exist
     */
    public static @NotNull OptionalInt serviceState(@NotNull String serviceName) {
        Winsvc.SC_HANDLE manager = Advapi32.INSTANCE.OpenSCManager(null, null,
                Winsvc.SC_MANAGER_CONNECT);

        if (manager == null) {
            return OptionalInt.empty();
        }

        try {
            Winsvc.SC_HANDLE service = Advapi32.INSTANCE.OpenService(manager, serviceName,
                    Winsvc.SERVICE_QUERY_STATUS);

            if (service == null) {
                return OptionalInt.empty();
            }

            try {
                Winsvc.SERVICE_STATUS status = new Winsvc.SERVICE_STATUS();

                if (!Advapi32.INSTANCE.QueryServiceStatus(service, status)) {
                    return OptionalInt.empty();
                }

                return OptionalInt.of(status.dwCurrentState);
            } finally {
                Advapi32.INSTANCE.CloseServiceHandle(service);
            }
        } finally {
            Advapi32.INSTANCE.CloseServiceHandle(manager);
        }
    }

    /**
     * Checks whether a service is running.
     *
     * @param serviceName the service name
     * @return {@code true} when the service exists and is running
     */
    public static boolean isServiceRunning(@NotNull String serviceName) {
        return serviceState(serviceName).orElse(-1) == Winsvc.SERVICE_RUNNING;
    }

    /**
     * Checks whether a service exists.
     *
     * @param serviceName the service name
     * @return {@code true} when the service is installed
     */
    public static boolean serviceExists(@NotNull String serviceName) {
        return serviceState(serviceName).isPresent();
    }

    /**
     * Reads the configured start type of a service from the registry.
     *
     * @param serviceName the service name
     * @return the start type (0 boot, 1 system, 2 automatic, 3 manual, 4 disabled),
     * or empty when it cannot be read
     */
    public static @NotNull OptionalInt serviceStartType(@NotNull String serviceName) {
        return RegistryUtil.intValue(com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE,
                "SYSTEM\\CurrentControlSet\\Services\\" + serviceName, "Start");
    }

    /**
     * Gets the image names of all running processes.
     *
     * @return the image names, or an empty list when the query fails
     */
    public static @NotNull List<String> runningProcessNames() {
        @NotNull CommandResult result = CommandUtil.run(
                WindowsUtil.system32("tasklist.exe"), Duration.ofSeconds(30), "/FO", "CSV", "/NH");

        if (!result.succeeded()) {
            return List.of();
        }

        return result.output().stream()
                .filter(line -> line.startsWith("\""))
                .map(line -> {
                    int end = line.indexOf("\",", 1);
                    return end < 0 ? "" : line.substring(1, end);
                })
                .filter(name -> !name.isEmpty())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
    }

    /**
     * Formats a Windows service state for logs.
     *
     * @param state the state
     * @return the readable name
     */
    public static @NotNull String describeState(int state) {
        return switch (state) {
            case Winsvc.SERVICE_STOPPED -> "stopped";
            case Winsvc.SERVICE_START_PENDING -> "start pending";
            case Winsvc.SERVICE_STOP_PENDING -> "stop pending";
            case Winsvc.SERVICE_RUNNING -> "running";
            case Winsvc.SERVICE_CONTINUE_PENDING -> "continue pending";
            case Winsvc.SERVICE_PAUSE_PENDING -> "pause pending";
            case Winsvc.SERVICE_PAUSED -> "paused";
            default -> "unknown (" + state + ")";
        };
    }

    /**
     * Checks whether a service name is syntactically valid.
     *
     * @param serviceName the name
     * @return {@code true} when the name is safe to pass to {@code sc.exe}
     */
    public static boolean isValidServiceName(@Nullable String serviceName) {
        return serviceName != null
                && serviceName.matches("^[A-Za-z0-9._-]{1,255}$");
    }
}
