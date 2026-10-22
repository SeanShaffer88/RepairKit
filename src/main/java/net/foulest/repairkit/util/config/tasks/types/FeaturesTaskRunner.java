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

import net.foulest.repairkit.model.RepairTask;
import net.foulest.repairkit.model.RiskLevel;
import net.foulest.repairkit.model.StepResult;
import net.foulest.repairkit.util.CommandResult;
import net.foulest.repairkit.util.CommandUtil;
import net.foulest.repairkit.util.DebugUtil;
import net.foulest.repairkit.util.WindowsUtil;
import net.foulest.repairkit.util.config.tasks.ConfigEntries;
import net.foulest.repairkit.util.config.tasks.ConfigTaskRunner;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Disables Windows optional features.
 * <p>
 * Feature state is queried explicitly instead of searching for the word
 * {@code Enabled} in possibly empty output, and DISM exit codes are checked.
 * The old {@code wuauclt.exe} guard is gone: it is not a reliable indicator of
 * servicing activity and it silently skipped every feature when present.
 *
 * @author Foulest
 */
public class FeaturesTaskRunner implements ConfigTaskRunner {

    private static final Duration QUERY_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration DISM_TIMEOUT = Duration.ofMinutes(30);

    private final Map<String, Map<String, Object>> config;

    /**
     * Creates a runner.
     *
     * @param config the parsed configuration
     */
    public FeaturesTaskRunner(@NotNull Map<String, Map<String, Object>> config) {
        this.config = config;
    }

    @Override
    public @NotNull List<RepairTask> getTasks() {
        @NotNull List<RepairTask> tasks = new ArrayList<>();

        for (String feature : ConfigEntries.valueList(config.getOrDefault("features", Map.of()))) {
            if (!feature.matches("^[A-Za-z0-9._-]{1,128}$")) {
                DebugUtil.warn("Skipped an invalid feature name: " + feature, null);
                continue;
            }

            @NotNull String title = "Feature: " + feature;
            tasks.add(new RepairTask("feature." + feature, title,
                    "Disable this optional Windows feature.", RiskLevel.MODERATE, true,
                    token -> disable(feature, token.isCancelled())));
        }

        return tasks;
    }

    private @NotNull StepResult disable(@NotNull String feature, boolean cancelled) {
        if (cancelled) {
            return StepResult.cancelled();
        }

        @NotNull CommandResult query = CommandUtil.runPowerShell(
                "$f = Get-WindowsOptionalFeature -Online -FeatureName '" + feature
                        + "' -ErrorAction Stop; Write-Output $f.State", QUERY_TIMEOUT);

        if (!query.succeeded() || query.output().isEmpty()) {
            return StepResult.skipped("The feature is not available on this edition.");
        }

        @NotNull String state = String.join(" ", query.output()).trim();

        if (!state.equalsIgnoreCase("Enabled")) {
            return StepResult.skipped("The feature is already " + state + ".");
        }

        @NotNull CommandResult result = CommandUtil.run(WindowsUtil.system32("dism.exe"),
                DISM_TIMEOUT, "/Online", "/Disable-Feature",
                "/FeatureName:" + feature, "/NoRestart");

        // 3010 means the change succeeded but needs a restart.
        if (result.exitCode() == 0 || result.exitCode() == 3010) {
            return StepResult.ok("The feature was disabled"
                    + (result.exitCode() == 3010 ? " and will finish after a restart." : "."),
                    result.exitCode() == 3010);
        }

        return StepResult.failed("DISM exited with " + result.exitCode() + ": "
                + result.tail(3));
    }
}
