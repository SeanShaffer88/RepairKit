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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Removes Appx bloatware for every user and from the provisioned image.
 * <p>
 * The previous version only removed packages for the signed-in user, so a new
 * profile received the same bloatware again. Provisioned packages are removed
 * as well, which prevents that.
 *
 * @author Foulest
 */
public class BloatwareTaskRunner implements ConfigTaskRunner {

    private static final Duration LIST_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration REMOVE_TIMEOUT = Duration.ofMinutes(30);
    private static final Pattern PACKAGE_FULL_NAME = Pattern.compile("^[A-Za-z0-9._~+\\-]{3,200}$");

    private final Map<String, Map<String, Object>> config;

    /**
     * Creates a runner.
     *
     * @param config the parsed configuration
     */
    public BloatwareTaskRunner(@NotNull Map<String, Map<String, Object>> config) {
        this.config = config;
    }

    @Override
    public @NotNull List<RepairTask> getTasks() {
        @NotNull List<String> patterns = ConfigEntries.valueList(
                config.getOrDefault("bloatware", Map.of()));

        if (patterns.isEmpty()) {
            return List.of();
        }

        return List.of(new RepairTask("bloatware", "Bloatware removal",
                patterns.size() + " package pattern(s) from config/bloatware.json.",
                RiskLevel.MODERATE, true, token -> removeAll(patterns, token.isCancelled())));
    }

    private @NotNull StepResult removeAll(@NotNull List<String> patterns, boolean cancelled) {
        if (cancelled) {
            return StepResult.cancelled();
        }

        if (!WindowsUtil.isAdministrator()) {
            return StepResult.skipped("Removing packages for all users requires elevation.");
        }

        @NotNull List<Pattern> compiled = patterns.stream()
                .map(BloatwareTaskRunner::toPattern)
                .toList();

        @NotNull List<String> installed = query("Get-AppxPackage -AllUsers | ForEach-Object"
                + " { $_.Name + '|' + $_.PackageFullName }", LIST_TIMEOUT);

        @NotNull List<String> installedMatches = new ArrayList<>();

        for (String line : installed) {
            int separator = line.indexOf('|');

            if (separator <= 0) {
                continue;
            }

            @NotNull String name = line.substring(0, separator);
            @NotNull String fullName = line.substring(separator + 1);

            if (matches(compiled, name) && PACKAGE_FULL_NAME.matcher(fullName).matches()) {
                installedMatches.add(fullName);
            }
        }

        if (installedMatches.isEmpty()) {
            return StepResult.skipped("No matching Appx packages are installed.");
        }

        int removed = 0;
        @NotNull List<String> failures = new ArrayList<>();

        for (String chunk : chunks(installedMatches, 15)) {
            @NotNull CommandResult result = CommandUtil.runPowerShell(buildRemovalScript(chunk),
                    REMOVE_TIMEOUT);

            for (String line : result.output()) {
                if (line.startsWith("OK|")) {
                    removed++;
                } else if (line.startsWith("FAIL|")) {
                    failures.add(describeFailure(line));
                    DebugUtil.warn("Failed to remove package: " + line, null);
                }
            }
        }

        int provisionedRemoved = removeProvisioned(compiled);

        @NotNull String message = removed + " package(s) removed for all users, "
                + provisionedRemoved + " provisioned package(s) removed, "
                + failures.size() + " failure(s)."
                + (failures.isEmpty()
                ? ""
                : " Failed: " + String.join(", ", failures.stream().limit(5).toList())
                + (failures.size() > 5 ? " and " + (failures.size() - 5) + " more" : "") + ".");

        if (failures.isEmpty()) {
            return StepResult.ok(message);
        }

        return removed + provisionedRemoved > 0
                ? StepResult.partial(message, removed + provisionedRemoved)
                : StepResult.failed(message);
    }

    private static @NotNull String describeFailure(@NotNull String line) {
        String[] parts = line.split("\\|", 3);

        if (parts.length > 1) {
            String packageName = parts[1];
            return packageName.length() > 60 ? packageName.substring(0, 57) + "..." : packageName;
        }

        return "unknown package";
    }

    private int removeProvisioned(@NotNull List<Pattern> patterns) {
        @NotNull List<String> provisioned = query("Get-AppxProvisionedPackage -Online"
                + " | ForEach-Object { $_.DisplayName + '|' + $_.PackageName }", LIST_TIMEOUT);
        @NotNull List<String> matches = new ArrayList<>();

        for (String line : provisioned) {
            int separator = line.indexOf('|');

            if (separator <= 0) {
                continue;
            }

            @NotNull String name = line.substring(0, separator);
            @NotNull String packageName = line.substring(separator + 1);

            if (matches(patterns, name) && PACKAGE_FULL_NAME.matcher(packageName).matches()) {
                matches.add(packageName);
            }
        }

        if (matches.isEmpty()) {
            return 0;
        }

        @NotNull StringBuilder script = new StringBuilder();
        script.append("$pkgs = @(").append(quoteList(matches)).append("); ");
        script.append("foreach ($p in $pkgs) { try { Remove-AppxProvisionedPackage -Online ")
                .append("-PackageName $p -ErrorAction Stop; Write-Output ('OK|' + $p) } ")
                .append("catch { Write-Output ('FAIL|' + $p + '|' + $_.Exception.Message) } }");

        @NotNull CommandResult result = CommandUtil.runPowerShell(script.toString(), REMOVE_TIMEOUT);
        int removed = 0;

        for (String line : result.output()) {
            if (line.startsWith("OK|")) {
                removed++;
            } else if (line.startsWith("FAIL|")) {
                DebugUtil.warn("Failed to remove provisioned package: " + line, null);
            }
        }

        return removed;
    }

    private static @NotNull String buildRemovalScript(@NotNull List<String> packages) {
        return "$pkgs = @(" + quoteList(packages) + "); "
                + "foreach ($p in $pkgs) { try { Remove-AppxPackage -Package $p -AllUsers "
                + "-ErrorAction Stop; Write-Output ('OK|' + $p) } "
                + "catch { Write-Output ('FAIL|' + $p + '|' + $_.Exception.Message) } }";
    }

    private static @NotNull String quoteList(@NotNull List<String> values) {
        @NotNull StringBuilder builder = new StringBuilder();

        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }

            builder.append('\'').append(values.get(index).replace("'", "''")).append('\'');
        }

        return builder.toString();
    }

    private static @NotNull List<String> query(@NotNull String script, @NotNull Duration timeout) {
        @NotNull CommandResult result = CommandUtil.runPowerShell(script, timeout);

        if (!result.succeeded()) {
            DebugUtil.warn("PowerShell query failed: " + result.tail(3), null);
            return List.of();
        }

        return result.output();
    }

    private static boolean matches(@NotNull List<Pattern> patterns, @NotNull String name) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(name).matches());
    }

    static @NotNull Pattern toPattern(@NotNull String wildcard) {
        @NotNull StringBuilder regex = new StringBuilder("^");

        for (char character : wildcard.toCharArray()) {
            switch (character) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> regex.append(Pattern.quote(String.valueOf(character)));
            }
        }

        regex.append('$');
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    private static @NotNull List<List<String>> chunks(@NotNull List<String> values, int size) {
        @NotNull List<List<String>> chunks = new ArrayList<>();

        for (int index = 0; index < values.size(); index += size) {
            chunks.add(new ArrayList<>(values.subList(index, Math.min(values.size(), index + size))));
        }

        return chunks;
    }

    /**
     * Gets the pattern count, used by the confirmation dialog.
     *
     * @return the number of configured patterns
     */
    public int patternCount() {
        @NotNull Set<String> unique = new LinkedHashSet<>(ConfigEntries.valueList(
                config.getOrDefault("bloatware", Map.of())));
        return unique.size();
    }

    /**
     * Checks a package name against a wildcard pattern.
     *
     * @param wildcard the pattern
     * @param name     the package name
     * @return {@code true} on a case-insensitive match
     */
    public static boolean matches(@NotNull String wildcard, @NotNull String name) {
        return toPattern(wildcard).matcher(name).matches();
    }

    /**
     * Normalizes a pattern for logging.
     *
     * @param pattern the pattern
     * @return the lower-case pattern
     */
    public static @NotNull String normalize(@NotNull String pattern) {
        return pattern.toLowerCase(Locale.ROOT);
    }
}
