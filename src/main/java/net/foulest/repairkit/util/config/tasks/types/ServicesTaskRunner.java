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
import com.sun.jna.platform.win32.Winsvc;
import net.foulest.repairkit.model.RepairTask;
import net.foulest.repairkit.model.RiskLevel;
import net.foulest.repairkit.model.StepResult;
import net.foulest.repairkit.util.CommandResult;
import net.foulest.repairkit.util.CommandUtil;
import net.foulest.repairkit.util.DebugUtil;
import net.foulest.repairkit.util.ProcessUtil;
import net.foulest.repairkit.util.RegistryBackup;
import net.foulest.repairkit.util.WindowsUtil;
import net.foulest.repairkit.util.config.tasks.ConfigEntries;
import net.foulest.repairkit.util.config.tasks.ConfigTaskRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Changes Windows service start types.
 * <p>
 * Every {@code sc config} call in the previous implementation omitted the space
 * after {@code =} ({@code start=disabled}), which {@code sc.exe} rejects; the
 * command failed silently because exit codes were ignored. The argument list
 * here separates {@code start=} and the value, checks the exit code, and records
 * the previous start type in the registry backup.
 *
 * @author Foulest
 */
public class ServicesTaskRunner implements ConfigTaskRunner {

    private static final Duration SC_TIMEOUT = Duration.ofSeconds(60);

    private final Map<String, Map<String, Object>> config;
    private final @Nullable RegistryBackup backup;

    /**
     * Creates a runner.
     *
     * @param config the parsed configuration
     * @param backup the registry backup, may be {@code null}
     */
    public ServicesTaskRunner(@NotNull Map<String, Map<String, Object>> config,
                              @Nullable RegistryBackup backup) {
        this.config = config;
        this.backup = backup;
    }

    @Override
    public @NotNull List<RepairTask> getTasks() {
        @NotNull List<RepairTask> tasks = new ArrayList<>();
        @NotNull Map<String, Object> values = ConfigEntries.values(config.getOrDefault("services", Map.of()));

        values.forEach((serviceName, action) -> {
            if (!ProcessUtil.isValidServiceName(serviceName)) {
                DebugUtil.warn("Skipped an invalid service name: " + serviceName, null);
                return;
            }

            if (!(action instanceof String actionName)) {
                DebugUtil.warn("Skipped a service with a non-string action: " + serviceName, null);
                return;
            }

            @NotNull String scAction = actionName.toLowerCase(Locale.ROOT);

            if (!List.of("boot", "system", "auto", "demand", "disabled", "delayed-auto")
                    .contains(scAction)) {
                DebugUtil.warn("Skipped a service with an unknown action: " + actionName, null);
                return;
            }

            @NotNull String title = "Service: " + serviceName;
            @NotNull String description = "Set the start type to " + scAction + ".";
            tasks.add(new RepairTask("service." + serviceName, title, description,
                    RiskLevel.MODERATE, true,
                    token -> apply(serviceName, scAction, token.isCancelled())));
        });

        return tasks;
    }

    private @NotNull StepResult apply(@NotNull String serviceName, @NotNull String action,
                                      boolean cancelled) {
        if (cancelled) {
            return StepResult.cancelled();
        }

        OptionalInt state = ProcessUtil.serviceState(serviceName);

        if (state.isEmpty()) {
            return StepResult.skipped("The service is not installed.");
        }

        @NotNull String registryPath = "SYSTEM\\CurrentControlSet\\Services\\" + serviceName;

        if (backup != null) {
            backup.recordValue(WinReg.HKEY_LOCAL_MACHINE, registryPath, "Start");
        }

        if (action.equals("disabled")) {
            @NotNull CommandResult stop = CommandUtil.run(WindowsUtil.system32("sc.exe"),
                    SC_TIMEOUT, "stop", serviceName);

            if (!stop.succeeded()) {
                DebugUtil.debug("sc stop " + serviceName + " returned " + stop.exitCode()
                        + " (the service may already be stopped)");
            }

            waitForStop(serviceName);
        }

        @NotNull CommandResult configResult = CommandUtil.run(WindowsUtil.system32("sc.exe"),
                SC_TIMEOUT, "config", serviceName, "start=", action);

        if (!configResult.succeeded()) {
            return StepResult.failed("sc config failed with exit " + configResult.exitCode()
                    + ": " + configResult.tail(3));
        }

        OptionalInt newStartType = ProcessUtil.serviceStartType(serviceName);
        return StepResult.ok("Start type set to " + action
                + (newStartType.isPresent() ? " (Start=" + newStartType.getAsInt() + ")" : "")
                + ". Previous state: " + ProcessUtil.describeState(state.getAsInt()) + ".");
    }

    private static void waitForStop(@NotNull String serviceName) {
        for (int attempt = 0; attempt < 30; attempt++) {
            if (ProcessUtil.serviceState(serviceName).orElse(-1) == Winsvc.SERVICE_STOPPED) {
                return;
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        DebugUtil.warn("Service did not stop within 15 seconds: " + serviceName, null);
    }
}
