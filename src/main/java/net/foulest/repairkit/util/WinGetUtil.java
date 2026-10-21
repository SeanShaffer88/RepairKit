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

import com.sshtools.twoslices.Toast;
import com.sshtools.twoslices.ToastType;
import net.foulest.repairkit.util.config.ConfigLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * WinGet integration.
 * <p>
 * The old implementation installed a PowerShell module on every run and
 * inferred success from four English error strings. This version invokes
 * {@code winget.exe} directly, decides based on exit codes, and never counts an
 * excluded or failed package as updated.
 *
 * @author Foulest
 */
public final class WinGetUtil {

    private static final Duration LIST_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration UPGRADE_TIMEOUT = Duration.ofMinutes(20);
    private static final int UPDATE_NOT_APPLICABLE = -1978335189; // 0x8A15002B

    private static final Pattern PACKAGE_ID =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._+\\-]{2,}$");
    private static final Pattern COLUMN_SEPARATOR = Pattern.compile("\\s{2,}");

    private WinGetUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Updates every outdated package that is not excluded by configuration.
     *
     * @return the update summary
     */
    public static @NotNull UpdateSummary updateAllPrograms() {
        @Nullable Path winget = wingetExecutable();

        if (winget == null) {
            return new UpdateSummary(List.of(), List.of(), List.of(),
                    "WinGet is not installed, so program updates were skipped.");
        }

        @NotNull List<String> excluded = excludedPrograms();
        @NotNull CommandResult listing = CommandUtil.run(winget, LIST_TIMEOUT,
                "upgrade", "--include-unknown", "--accept-source-agreements",
                "--disable-interactivity");

        if (listing.timedOut()) {
            return new UpdateSummary(List.of(), List.of(), List.of(),
                    "WinGet did not respond while listing upgrades.");
        }

        @NotNull List<String> candidates = parseUpgradeIds(listing.output());
        @NotNull List<String> skipped = new ArrayList<>();
        @NotNull List<String> attempted = new ArrayList<>();

        for (String id : candidates) {
            if (isExcluded(id, excluded)) {
                DebugUtil.debug("Skipping excluded package: " + id);
                skipped.add(id);
            } else {
                attempted.add(id);
            }
        }

        if (attempted.isEmpty()) {
            return new UpdateSummary(List.of(), List.of(), skipped,
                    candidates.isEmpty()
                            ? "No outdated programs were found."
                            : "Every outdated program is excluded in update_programs.json.");
        }

        notifyUser("Updating " + attempted.size() + " program(s) with WinGet...");

        @NotNull List<String> updated = new ArrayList<>();
        @NotNull List<String> failed = new ArrayList<>();

        for (String id : attempted) {
            @NotNull CommandResult result = CommandUtil.run(winget, UPGRADE_TIMEOUT,
                    "upgrade", "--id", id, "--exact", "--silent",
                    "--accept-package-agreements", "--accept-source-agreements",
                    "--disable-interactivity");

            if (result.succeeded()) {
                updated.add(id);
            } else if (result.exitCode() == UPDATE_NOT_APPLICABLE) {
                skipped.add(id);
            } else {
                failed.add(id);
                DebugUtil.warn("Failed to update " + id + ": " + result.tail(5), null);
            }
        }

        @NotNull String message = "WinGet: " + updated.size() + " updated, "
                + failed.size() + " failed, " + skipped.size() + " skipped.";

        notifyUser(message);
        return new UpdateSummary(updated, failed, skipped, message);
    }

    private static void notifyUser(@NotNull String message) {
        try {
            Toast.toast(ToastType.INFO, "RepairKit", message);
        } catch (RuntimeException | LinkageError ex) {
            // Toast notifications need a desktop session and an AWT tray; a repair
            // run must not fail when they are unavailable.
            DebugUtil.debug("Toast notification unavailable: " + ex.getMessage());
        }
    }

    private static @Nullable Path wingetExecutable() {
        @NotNull Path candidate = WindowsUtil.localAppData()
                .resolve("Microsoft").resolve("WindowsApps").resolve("winget.exe");

        if (Files.isRegularFile(candidate)) {
            return candidate;
        }

        DebugUtil.debug("WinGet was not found at " + candidate);
        return null;
    }

    /**
     * Parses package IDs from the {@code winget upgrade} table.
     * <p>
     * The table layout is stable across locales even though the headings are
     * translated: data rows have at least four whitespace-separated columns and
     * the second column is the package ID.
     *
     * @param lines the command output
     * @return the package IDs
     */
    static @NotNull List<String> parseUpgradeIds(@NotNull List<String> lines) {
        @NotNull Set<String> ids = new LinkedHashSet<>();
        boolean inTable = false;

        for (String line : lines) {
            @NotNull String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                continue;
            }

            if (!inTable) {
                if (trimmed.matches("^-{5,}.*$")) {
                    inTable = true;
                }

                continue;
            }

            if (trimmed.matches("^-{5,}.*$")) {
                continue;
            }

            String[] columns = COLUMN_SEPARATOR.split(trimmed);

            if (columns.length < 4 || !PACKAGE_ID.matcher(columns[1]).matches()) {
                continue;
            }

            ids.add(columns[1]);
        }

        return new ArrayList<>(ids);
    }

    private static @NotNull List<String> excludedPrograms() {
        @NotNull ConfigLoader loader = new ConfigLoader(FileUtil.configFile("update_programs.json"));
        @NotNull Map<String, Object> excluded = loader.category("excludedPrograms");

        return ConfigLoader.strings(excluded, "values").stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
    }

    private static boolean isExcluded(@NotNull String id, @NotNull List<String> excluded) {
        @NotNull String lower = id.toLowerCase(Locale.ROOT);
        return excluded.stream().anyMatch(lower::contains);
    }

    /**
     * The outcome of an update run.
     *
     * @param updated package IDs that were upgraded
     * @param failed  package IDs that failed to upgrade
     * @param skipped package IDs that were excluded or had no applicable update
     * @param message diagnostic summary
     * @author Foulest
     */
    public record UpdateSummary(@NotNull List<String> updated,
                                @NotNull List<String> failed,
                                @NotNull List<String> skipped,
                                @NotNull String message) {

        /**
         * Checks whether every attempted upgrade succeeded.
         *
         * @return {@code true} when nothing failed
         */
        public boolean hasFailures() {
            return !failed.isEmpty();
        }
    }
}
