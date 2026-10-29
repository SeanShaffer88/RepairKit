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

import com.sun.jna.platform.win32.WinReg;
import net.foulest.repairkit.model.RepairOption;
import net.foulest.repairkit.model.RepairTask;
import net.foulest.repairkit.model.RiskLevel;
import net.foulest.repairkit.model.StepResult;
import net.foulest.repairkit.util.CommandResult;
import net.foulest.repairkit.util.CommandUtil;
import net.foulest.repairkit.util.DebugUtil;
import net.foulest.repairkit.util.FileUtil;
import net.foulest.repairkit.util.JunkFileUtil;
import net.foulest.repairkit.util.ProcessUtil;
import net.foulest.repairkit.util.RegistryBackup;
import net.foulest.repairkit.util.RegistryUtil;
import net.foulest.repairkit.util.WinGetUtil;
import net.foulest.repairkit.util.WindowsUtil;
import net.foulest.repairkit.util.config.ConfigLoader;
import net.foulest.repairkit.util.config.tasks.ConfigTaskRunner;
import net.foulest.repairkit.util.config.tasks.types.BloatwareTaskRunner;
import net.foulest.repairkit.util.config.tasks.types.FeaturesTaskRunner;
import net.foulest.repairkit.util.config.tasks.types.RegistryTaskRunner;
import net.foulest.repairkit.util.config.tasks.types.ServicesTaskRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The repair catalogue: high-level options and the concrete tasks they expand
 * into.
 * <p>
 * All system changes live here rather than in the Swing panel. Every command
 * uses an absolute system path, runs synchronously, and is checked by exit code
 * so that the final report reflects what actually happened.
 *
 * @author Foulest
 */
public final class RepairOperations {

    private static final Duration SHORT = Duration.ofSeconds(60);
    private static final Duration MEDIUM = Duration.ofMinutes(10);
    private static final Duration LONG = Duration.ofMinutes(60);
    private static final Duration VERY_LONG = Duration.ofMinutes(120);

    private static final List<String> CONFIG_FILES = List.of(
            "registry_tweaks.json",
            "system_policies.json",
            "system_tweaks.json",
            "services_tweaks.json",
            "features_tweaks.json",
            "windows_defender.json",
            "junk_files.json",
            "bloatware.json",
            "disk_issues.json",
            "update_programs.json");

    private RepairOperations() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Gets the options shown on the Automatic Repairs panel, in run order.
     *
     * @return the options
     */
    public static @NotNull List<RepairOption> options() {
        return List.of(
                new RepairOption("restorePoint", "task.restore.title",
                        "task.restore.description", RiskLevel.SAFE, true),
                new RepairOption("policies", "task.policies.title",
                        "task.policies.description", RiskLevel.MODERATE, true),
                new RepairOption("registry", "task.registry.title",
                        "task.registry.description", RiskLevel.MODERATE, true),
                new RepairOption("system", "task.system.title",
                        "task.system.description", RiskLevel.MODERATE, true),
                new RepairOption("services", "task.services.title",
                        "task.services.description", RiskLevel.MODERATE, true),
                new RepairOption("features", "task.features.title",
                        "task.features.description", RiskLevel.MODERATE, true),
                new RepairOption("defender", "task.defender.title",
                        "task.defender.description", RiskLevel.MODERATE, true),
                new RepairOption("junk", "task.junk.title",
                        "task.junk.description", RiskLevel.SAFE, true),
                new RepairOption("bloatware", "task.bloatware.title",
                        "task.bloatware.description", RiskLevel.MODERATE, true),
                new RepairOption("disk", "task.disk.title",
                        "task.disk.description", RiskLevel.SAFE, true),
                new RepairOption("winget", "task.winget.title",
                        "task.winget.description", RiskLevel.SAFE, true),
                new RepairOption("advanced", "task.advanced.title",
                        "task.advanced.description", RiskLevel.HIGH, false)
        );
    }

    /**
     * Expands the selected options into ordered tasks.
     *
     * @param selected the selected option identifiers
     * @param backup   the registry backup
     * @return the tasks
     */
    public static @NotNull List<RepairTask> expand(@NotNull Set<String> selected,
                                                   @Nullable RegistryBackup backup) {
        @NotNull List<RepairTask> tasks = new ArrayList<>();

        if (selected.contains("restorePoint")) {
            tasks.add(new RepairTask("restorePoint", "task.restore.title",
                    "task.restore.description", RiskLevel.SAFE, true,
                    token -> createRestorePoint(token.isCancelled())));
        }

        if (selected.contains("policies")) {
            tasks.addAll(registryTasks("system_policies.json", "policies", backup));
        }

        if (selected.contains("registry")) {
            tasks.addAll(registryTasks("registry_tweaks.json", "registry", backup));
        }

        if (selected.contains("system")) {
            tasks.add(new RepairTask("system", "task.system.title",
                    "task.system.description", RiskLevel.MODERATE, true,
                    token -> runSystemTweaks(token.isCancelled())));
        }

        if (selected.contains("advanced")) {
            tasks.add(new RepairTask("advanced", "task.advanced.title",
                    "task.advanced.description", RiskLevel.HIGH, false,
                    token -> runAdvancedTweaks(token.isCancelled(), backup)));
        }

        if (selected.contains("services")) {
            tasks.addAll(new ServicesTaskRunner(config("services_tweaks.json")
                    .getConfig(), backup).getTasks());
        }

        if (selected.contains("features")) {
            tasks.addAll(new FeaturesTaskRunner(config("features_tweaks.json")
                    .getConfig()).getTasks());
        }

        if (selected.contains("defender")) {
            tasks.add(new RepairTask("defender", "task.defender.title",
                    "task.defender.description", RiskLevel.MODERATE, true,
                    token -> runDefenderTweaks(token.isCancelled())));
        }

        if (selected.contains("junk")) {
            tasks.add(new RepairTask("junk", "task.junk.title",
                    "task.junk.description", RiskLevel.SAFE, true,
                    token -> {
                        if (token.isCancelled()) {
                            return StepResult.cancelled();
                        }

                        @NotNull JunkFileUtil.CleanupResult result = JunkFileUtil.removeJunkFiles();

                        if (result.failures() > 0 && result.filesDeleted() == 0) {
                            return StepResult.failed(result.message());
                        }

                        if (result.failures() > 0) {
                            return StepResult.partial(result.message(),
                                    (int) Math.min(Integer.MAX_VALUE, result.filesDeleted()));
                        }

                        return StepResult.ok(result.message());
                    }));
        }

        if (selected.contains("bloatware")) {
            tasks.addAll(new BloatwareTaskRunner(config("bloatware.json")
                    .getConfig()).getTasks());
        }

        if (selected.contains("disk")) {
            tasks.add(new RepairTask("disk", "task.disk.title",
                    "task.disk.description", RiskLevel.SAFE, true,
                    token -> repairDiskIssues(token.isCancelled())));
        }

        if (selected.contains("winget")) {
            tasks.add(new RepairTask("winget", "task.winget.title",
                    "task.winget.description", RiskLevel.SAFE, true,
                    token -> {
                        if (token.isCancelled()) {
                            return StepResult.cancelled();
                        }

                        @NotNull WinGetUtil.UpdateSummary summary = WinGetUtil.updateAllPrograms();

                        if (summary.outcome() == WinGetUtil.Outcome.NOT_INSTALLED) {
                            // Not a failure: WinGet is simply optional here.
                            return StepResult.skipped(summary.message());
                        }

                        if (summary.outcome() == WinGetUtil.Outcome.QUERY_FAILED) {
                            return StepResult.failed(summary.message());
                        }

                        if (summary.hasFailures() && summary.updated().isEmpty()) {
                            return StepResult.failed(summary.message());
                        }

                        if (summary.hasFailures()) {
                            return StepResult.partial(summary.message(), summary.updated().size());
                        }

                        return StepResult.ok(summary.message());
                    }));
        }

        return tasks;
    }

    private static @NotNull ConfigLoader config(@NotNull String fileName) {
        return new ConfigLoader(FileUtil.configFile(fileName));
    }

    /**
     * Lists the configuration files that could not be parsed.
     * <p>
     * A corrupt file is reported before any repair runs, rather than silently
     * skipping part of the plan.
     *
     * @return the file names that failed to load, in a stable order
     */
    public static @NotNull List<String> invalidConfigFiles() {
        @NotNull List<String> invalid = new ArrayList<>();

        for (String fileName : CONFIG_FILES) {
            if (!config(fileName).isLoaded()) {
                invalid.add(fileName);
            }
        }

        return invalid;
    }

    private static @NotNull List<RepairTask> registryTasks(@NotNull String fileName,
                                                           @NotNull String idPrefix,
                                                           @Nullable RegistryBackup backup) {
        @NotNull ConfigTaskRunner runner = new RegistryTaskRunner(config(fileName).getConfig(),
                fileName, idPrefix, backup);
        return runner.getTasks();
    }

    // ------------------------------------------------------------------
    // Restore point
    // ------------------------------------------------------------------

    private static @NotNull StepResult createRestorePoint(boolean cancelled) {
        if (cancelled) {
            return StepResult.cancelled();
        }

        int before = restorePointCount();

        @NotNull CommandResult result = CommandUtil.runPowerShell(
                "$ErrorActionPreference = 'Stop'; "
                        + "try { Checkpoint-Computer -Description 'RepairKit' "
                        + "-RestorePointType MODIFY_SETTINGS -ErrorAction Stop; "
                        + "Write-Output 'CREATED' } "
                        + "catch { Write-Output ('ERROR|' + $_.Exception.Message) }",
                Duration.ofMinutes(15));

        if (result.text().contains("CREATED") || restorePointCount() > before) {
            return StepResult.ok("A System Restore checkpoint was created.");
        }

        @NotNull String error = result.output().stream()
                .filter(line -> line.startsWith("ERROR|"))
                .map(line -> line.substring(6))
                .findFirst()
                .orElse(result.tail(3));

        if (before > 0) {
            return StepResult.skipped("Windows allows one checkpoint per 24 hours and an "
                    + "existing checkpoint was found. Details: " + error);
        }

        return StepResult.failed("No restore point could be created. Enable System Protection "
                + "and try again. Details: " + error);
    }

    private static int restorePointCount() {
        @NotNull CommandResult result = CommandUtil.runPowerShell(
                "@(Get-ComputerRestorePoint -ErrorAction SilentlyContinue).Count",
                Duration.ofMinutes(2));

        for (String line : result.output()) {
            try {
                return Integer.parseInt(line.trim());
            } catch (NumberFormatException ignored) {
                // Keep looking for a numeric line.
            }
        }

        return 0;
    }

    // ------------------------------------------------------------------
    // System tweaks
    // ------------------------------------------------------------------

    private static @NotNull StepResult runSystemTweaks(boolean cancelled) {
        @NotNull ConfigLoader loader = config("system_tweaks.json");
        @NotNull Map<String, Object> tweaks = loader.category("tweaks");

        if (tweaks.isEmpty()) {
            return StepResult.skipped("config/system_tweaks.json has no tweaks category.");
        }

        @NotNull Outcome outcome = new Outcome();

        if (ConfigLoader.flag(tweaks, "enableScheduledDefrag", true)) {
            enableScheduledTask("\\Microsoft\\Windows\\Defrag\\ScheduledDefrag", outcome);
        }

        if (ConfigLoader.flag(tweaks, "disableTelemetry", true) && !cancelled) {
            for (String task : List.of(
                    "\\Microsoft\\Windows\\Application Experience\\Microsoft Compatibility Appraiser",
                    "\\Microsoft\\Windows\\Application Experience\\ProgramDataUpdater",
                    "\\Microsoft\\Windows\\Application Experience\\StartupAppTask",
                    "\\Microsoft\\Windows\\Customer Experience Improvement Program\\Consolidator",
                    "\\Microsoft\\Windows\\Customer Experience Improvement Program\\UsbCeip",
                    "\\Microsoft\\Windows\\Device Information\\Device",
                    "\\Microsoft\\Windows\\Windows Error Reporting\\QueueReporting")) {
                disableScheduledTask(task, outcome);
            }

            applyEnvironmentVariable("DOTNET_CLI_TELEMETRY_OPTOUT", "1", outcome);
            applyEnvironmentVariable("POWERSHELL_TELEMETRY_OPTOUT", "1", outcome);
        }

        if (ConfigLoader.flag(tweaks, "removeDefaultUser0", true) && !cancelled) {
            @NotNull CommandResult query = CommandUtil.run(WindowsUtil.system32("net.exe"),
                    SHORT, "user", "defaultuser0");

            if (query.succeeded()) {
                @NotNull CommandResult delete = CommandUtil.run(WindowsUtil.system32("net.exe"),
                        SHORT, "user", "defaultuser0", "/delete");

                if (delete.succeeded()) {
                    outcome.success();
                } else {
                    outcome.failure("Failed to delete the defaultuser0 account: " + delete.tail(2));
                }
            }
        }

        if (ConfigLoader.flag(tweaks, "fixExplorerFrame", true) && !cancelled) {
            registerDll(WindowsUtil.system32("ExplorerFrame.dll"), outcome);
        }

        if (ConfigLoader.flag(tweaks, "fixWindowsMediaPlayer", true) && !cancelled) {
            registerDll(WindowsUtil.system32("jscript.dll"), outcome);
            registerDll(WindowsUtil.system32("vbscript.dll"), outcome);
        }

        if (ConfigLoader.flag(tweaks, "fixMissingGetHelpApp", true) && !cancelled) {
            installGetHelp(outcome);
        }

        if (ConfigLoader.flag(tweaks, "fixBrokenWindowsApps", true) && !cancelled) {
            reRegisterApps(outcome);
        }

        return outcome.result("System tweaks");
    }

    private static void enableScheduledTask(@NotNull String name, @NotNull Outcome outcome) {
        if (!taskExists(name)) {
            return;
        }

        @NotNull CommandResult result = CommandUtil.run(WindowsUtil.system32("schtasks.exe"),
                SHORT, "/Change", "/ENABLE", "/TN", name);

        if (result.succeeded()) {
            outcome.success();
        } else {
            outcome.failure("Could not enable scheduled task " + name + ": " + result.tail(2));
        }
    }

    private static void disableScheduledTask(@NotNull String name, @NotNull Outcome outcome) {
        if (!taskExists(name)) {
            return;
        }

        @NotNull CommandResult result = CommandUtil.run(WindowsUtil.system32("schtasks.exe"),
                SHORT, "/Change", "/DISABLE", "/TN", name);

        if (result.succeeded()) {
            outcome.success();
        } else {
            outcome.failure("Could not disable scheduled task " + name + ": " + result.tail(2));
        }
    }

    private static boolean taskExists(@NotNull String name) {
        return CommandUtil.run(WindowsUtil.system32("schtasks.exe"), SHORT,
                "/Query", "/TN", name).succeeded();
    }

    private static void applyEnvironmentVariable(@NotNull String name, @NotNull String value,
                                                 @NotNull Outcome outcome) {
        @NotNull CommandResult result = CommandUtil.run(WindowsUtil.system32("setx.exe"),
                SHORT, name, value);

        if (result.succeeded()) {
            outcome.success();
        } else {
            outcome.failure("Could not set " + name + ": " + result.tail(2));
        }
    }

    private static void registerDll(@NotNull Path dll, @NotNull Outcome outcome) {
        if (!Files.isRegularFile(dll)) {
            return;
        }

        @NotNull CommandResult result = CommandUtil.run(WindowsUtil.system32("regsvr32.exe"),
                MEDIUM, "/s", dll.toString());

        if (result.succeeded()) {
            outcome.success();
        } else {
            outcome.failure("regsvr32 failed for " + dll.getFileName() + ": " + result.tail(2));
        }
    }

    private static void installGetHelp(@NotNull Outcome outcome) {
        @NotNull Path winget = WindowsUtil.localAppData()
                .resolve("Microsoft").resolve("WindowsApps").resolve("winget.exe");

        if (!Files.isRegularFile(winget)) {
            return;
        }

        @NotNull CommandResult result = CommandUtil.run(winget, MEDIUM,
                "install", "--id", "9PKDZBMV1H3T", "--source", "msstore",
                "--accept-package-agreements", "--accept-source-agreements",
                "--disable-interactivity");

        if (result.succeeded()) {
            outcome.success();
        } else {
            outcome.failure("Could not install the Get Help app: " + result.tail(2));
        }
    }

    private static void reRegisterApps(@NotNull Outcome outcome) {
        @NotNull CommandResult result = CommandUtil.runPowerShell(
                "$ok = 0; $fail = 0; "
                        + "Get-AppxPackage | Where-Object { $_.InstallLocation "
                        + "-and (Test-Path $_.InstallLocation) } | ForEach-Object { "
                        + "try { Add-AppxPackage -DisableDevelopmentMode -Register "
                        + "($_.InstallLocation + '\\AppxManifest.xml') -ErrorAction Stop; $ok++ } "
                        + "catch { $fail++ } }; "
                        + "Write-Output ('OK=' + $ok + ' FAIL=' + $fail)",
                LONG);

        if (result.text().contains("FAIL=0")) {
            outcome.success();
        } else {
            outcome.failure("Re-registering Windows apps reported failures: "
                    + result.tail(3));
        }
    }

    // ------------------------------------------------------------------
    // Advanced tweaks
    // ------------------------------------------------------------------

    private static @NotNull StepResult runAdvancedTweaks(boolean cancelled,
                                                         @Nullable RegistryBackup backup) {
        @NotNull ConfigLoader loader = config("system_tweaks.json");
        @NotNull Map<String, Object> tweaks = loader.category("advancedTweaks");

        if (tweaks.isEmpty()) {
            return StepResult.skipped("config/system_tweaks.json has no advancedTweaks category.");
        }

        @NotNull Outcome outcome = new Outcome();
        boolean restartRequired = false;

        if (ConfigLoader.flag(tweaks, "enablePlatformTickClock", false) && !cancelled) {
            @NotNull CommandResult set = CommandUtil.run(WindowsUtil.system32("bcdedit.exe"),
                    MEDIUM, "/set", "useplatformtick", "yes");

            if (set.succeeded()) {
                outcome.success();
            } else {
                outcome.failure("bcdedit could not enable useplatformtick: " + set.tail(2));
            }

            // Removing a missing element returns exit code 1; that is not an error here.
            CommandUtil.run(WindowsUtil.system32("bcdedit.exe"), MEDIUM,
                    "/deletevalue", "useplatformclock");
        }

        if (ConfigLoader.flag(tweaks, "clearProductKey", false) && !cancelled) {
            @NotNull CommandResult result = CommandUtil.run(WindowsUtil.system32("cscript.exe"),
                    MEDIUM, "//nologo", WindowsUtil.system32("slmgr.vbs").toString(), "/cpky");

            if (result.succeeded()) {
                outcome.success();
            } else {
                outcome.failure("slmgr /cpky failed: " + result.tail(2));
            }
        }

        if (ConfigLoader.flag(tweaks, "fixNetworkIssues", false) && !cancelled) {
            runNetworkReset(backup, outcome);
            restartRequired = true;
        }

        if (ConfigLoader.flag(tweaks, "disableNetBios", false) && !cancelled) {
            disableNetBios(backup, outcome);
        }

        @NotNull StepResult result = outcome.result("Advanced tweaks");

        if (restartRequired && result.status().isSuccessful()) {
            return result.withMessage(result.message() + " A restart is required to finish "
                    + "resetting the network stack.").withRestartRequired(true);
        }

        return result;
    }

    private static void runNetworkReset(@Nullable RegistryBackup backup,
                                        @NotNull Outcome outcome) {
        runNetworkCommand(List.of("ipconfig.exe", "/flushdns"), outcome);

        @NotNull CommandResult winsock = CommandUtil.run(WindowsUtil.system32("netsh.exe"),
                MEDIUM, "winsock", "reset");

        if (winsock.succeeded()) {
            outcome.success();
        } else {
            outcome.failure("netsh winsock reset failed: " + winsock.tail(2));
        }

        @NotNull CommandResult ip = CommandUtil.run(WindowsUtil.system32("netsh.exe"),
                MEDIUM, "int", "ip", "reset");

        if (ip.succeeded() || ip.exitCode() == 1) {
            // netsh returns 1 after a successful reset when a restart is pending.
            outcome.success();
        } else {
            outcome.failure("netsh int ip reset failed: " + ip.tail(2));
        }

        // TCP global settings are best-effort: some are unavailable on newer builds.
        CommandUtil.run(WindowsUtil.system32("netsh.exe"), MEDIUM,
                "int", "tcp", "set", "global", "rss=enabled");
        CommandUtil.run(WindowsUtil.system32("netsh.exe"), MEDIUM,
                "int", "tcp", "set", "global", "autotuninglevel=normal");
        CommandUtil.run(WindowsUtil.system32("netsh.exe"), MEDIUM,
                "int", "tcp", "set", "global", "ecncapability=default");
        CommandUtil.run(WindowsUtil.system32("netsh.exe"), MEDIUM,
                "int", "tcp", "set", "global", "timestamps=enabled");

        RegistryBackup.requireAvailable(backup).recordKeyDeletion(WinReg.HKEY_CLASSES_ROOT,
                "CLSID\\{988248f3-a1ad-49bf-9170-676cbbc36ba3}");

        RegistryUtil.deleteTree(WinReg.HKEY_CLASSES_ROOT,
                "CLSID\\{988248f3-a1ad-49bf-9170-676cbbc36ba3}");

        @NotNull CommandResult netcfg = CommandUtil.run(WindowsUtil.system32("netcfg.exe"),
                MEDIUM, "-v", "-u", "dni_dne");

        if (netcfg.succeeded()) {
            outcome.success();
        } else {
            outcome.failure("netcfg could not repair the network adapter: " + netcfg.tail(2));
        }
    }

    private static void runNetworkCommand(@NotNull List<String> command,
                                          @NotNull Outcome outcome) {
        @NotNull String executable = command.get(0);
        String[] arguments = command.subList(1, command.size()).toArray(String[]::new);
        @NotNull CommandResult result = CommandUtil.run(WindowsUtil.system32(executable),
                MEDIUM, arguments);

        if (result.succeeded()) {
            outcome.success();
        } else {
            outcome.failure("Command failed: " + String.join(" ", command));
        }
    }

    private static void disableNetBios(@Nullable RegistryBackup backup,
                                       @NotNull Outcome outcome) {
        @NotNull String baseKey = "SYSTEM\\CurrentControlSet\\services\\NetBT\\Parameters\\Interfaces";

        for (String subKey : RegistryUtil.listSubKeys(WinReg.HKEY_LOCAL_MACHINE, baseKey)) {
            @NotNull String path = baseKey + "\\" + subKey;

            RegistryBackup.requireAvailable(backup).recordValue(WinReg.HKEY_LOCAL_MACHINE, path, "NetbiosOptions");

            try {
                RegistryUtil.setInt(WinReg.HKEY_LOCAL_MACHINE, path, "NetbiosOptions", 2);
                outcome.success();
            } catch (RuntimeException ex) {
                outcome.failure("Could not disable NetBIOS on " + subKey + ": " + ex.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // Defender
    // ------------------------------------------------------------------

    private static @NotNull StepResult runDefenderTweaks(boolean cancelled) {
        @NotNull ConfigLoader loader = config("windows_defender.json");
        @NotNull Map<String, Object> defender = loader.category("windowsDefender");

        if (defender.isEmpty()) {
            return StepResult.skipped("config/windows_defender.json has no settings.");
        }

        if (!ProcessUtil.isProcessRunning("MsMpEng.exe")) {
            return StepResult.skipped("Microsoft Defender is not running. A third-party "
                    + "antivirus product may be managing this PC.");
        }

        @NotNull Outcome outcome = new Outcome();

        if (ConfigLoader.flag(defender, "enableFirewall", true) && !cancelled) {
            @NotNull CommandResult result = CommandUtil.runPowerShell(
                    "Set-NetFirewallProfile -Profile Domain,Private,Public -Enabled True "
                            + "-ErrorAction Stop", MEDIUM);

            if (result.succeeded()) {
                outcome.success();
            } else {
                outcome.failure("Could not enable the firewall: " + result.tail(3));
            }
        }

        if (ConfigLoader.flag(defender, "removeExclusions", true) && !cancelled) {
            @NotNull CommandResult result = CommandUtil.runPowerShell(
                    "$ErrorActionPreference='Stop'; $p = Get-MpPreference; $ok=0; $fail=0; "
                            + "try { Remove-MpPreference -AttackSurfaceReductionOnlyExclusions "
                            + "$p.AttackSurfaceReductionOnlyExclusions -ErrorAction Stop; $ok++ } "
                            + "catch { $fail++ }; "
                            + "try { Remove-MpPreference "
                            + "-ControlledFolderAccessAllowedApplications "
                            + "$p.ControlledFolderAccessAllowedApplications -ErrorAction Stop; "
                            + "$ok++ } catch { $fail++ }; "
                            + "try { Remove-MpPreference "
                            + "-ControlledFolderAccessProtectedFolders "
                            + "$p.ControlledFolderAccessProtectedFolders -ErrorAction Stop; "
                            + "$ok++ } catch { $fail++ }; "
                            + "Write-Output ('OK=' + $ok + ' FAIL=' + $fail)", MEDIUM);

            if (result.text().contains("FAIL=0")) {
                outcome.success();
            } else {
                outcome.failure("Some Defender exclusions could not be removed: "
                        + result.tail(3));
            }
        }

        if (ConfigLoader.flag(defender, "setRecommendedSettings", true) && !cancelled) {
            @NotNull CommandResult result = CommandUtil.runPowerShell(recommendedSettings(defender),
                    MEDIUM);

            if (result.succeeded()) {
                outcome.success();
            } else {
                outcome.failure("Set-MpPreference failed: " + result.tail(3));
            }
        }

        if (ConfigLoader.flag(defender, "setRecommendedASRRules", true) && !cancelled) {
            applyAsrRules(defender, outcome);
        }

        return outcome.result("Defender tweaks");
    }

    private static @NotNull String recommendedSettings(@NotNull Map<String, Object> defender) {
        boolean submitAll = ConfigLoader.flag(defender, "submitAllSamples", false);
        int consent = submitAll ? 3 : 1;

        return "Set-MpPreference -ErrorAction Stop"
                + " -CloudBlockLevel 4"
                + " -CloudExtendedTimeout 10"
                + " -DisableArchiveScanning 0"
                + " -DisableBehaviorMonitoring 0"
                + " -DisableBlockAtFirstSeen 0"
                + " -DisableEmailScanning 0"
                + " -DisableIOAVProtection 0"
                + " -DisableRealtimeMonitoring 0"
                + " -DisableRemovableDriveScanning 0"
                + " -DisableScanningMappedNetworkDrivesForFullScan 0"
                + " -DisableScanningNetworkFiles 0"
                + " -DisableScriptScanning 0"
                + " -EnableFileHashComputation 1"
                + " -EnableLowCpuPriority 0"
                + " -EnableNetworkProtection 1"
                + " -HighThreatDefaultAction Quarantine"
                + " -LowThreatDefaultAction Block"
                + " -MAPSReporting 2"
                + " -ModerateThreatDefaultAction Clean"
                + " -PUAProtection 1"
                + " -ScanAvgCPULoadFactor 50"
                + " -SevereThreatDefaultAction Remove"
                + " -SignatureBlobUpdateInterval 120"
                + " -SubmitSamplesConsent " + consent;
    }

    private static void applyAsrRules(@NotNull Map<String, Object> defender,
                                      @NotNull Outcome outcome) {
        @NotNull String enableRules = String.join(",",
                "26190899-1602-49e8-8b27-eb1d0a1ce869", // Adobe Reader child processes
                "3b576869-a4ec-4529-8536-b80a7769e899", // Office child processes
                "5beb7efe-fd9a-4556-801d-275e5ffc04cc", // Executable content from email
                "75668c1f-73b5-4cf0-bb93-3ecf5cb7cc84", // Obfuscated scripts
                "7674ba52-37eb-4a4f-a9a1-f0f9a1619a2c", // Scripts launching downloads
                "92e97fa1-2edf-4476-bdd6-9dd0b4dddc7b", // Office executable content
                "b2b3f03d-6a65-4f7b-a9c7-1c7ef74a9ba4", // Office code injection
                "be9ba2d9-53ea-4cdc-84e5-9b1eeee46550", // Office child processes
                "c1db55ab-c21a-4637-bb3f-a12568109d35", // WMI persistence
                "d3e037e1-3eb8-44c8-a917-57927947596d", // Untrusted USB processes
                "d4f940ab-401b-4efc-aadc-ad5f3c50688a", // Office macros
                "e6db77e5-3df2-4cf1-b95a-636979351e5b"); // Ransomware protection

        @NotNull CommandResult enable = CommandUtil.runPowerShell(
                "Add-MpPreference -ErrorAction Stop -AttackSurfaceReductionRules_Ids "
                        + enableRules + " -AttackSurfaceReductionRules_Actions Enabled", MEDIUM);

        if (enable.succeeded()) {
            outcome.success();
        } else {
            outcome.failure("Could not enable the ASR rules: " + enable.tail(3));
        }

        @NotNull String warnRules = String.join(",",
                "56a863a9-875e-4185-98a7-b882c64b5ce5", // Vulnerable signed drivers
                "a8f5898e-1dc8-49a9-9878-85004b8a61e6"); // Web shells

        @NotNull CommandResult warn = CommandUtil.runPowerShell(
                "Add-MpPreference -ErrorAction Stop -AttackSurfaceReductionRules_Ids "
                        + warnRules + " -AttackSurfaceReductionRules_Actions Warn", MEDIUM);

        if (warn.succeeded()) {
            outcome.success();
        } else {
            outcome.failure("Could not set the ASR warn rules: " + warn.tail(3));
        }

        // Disabling protection is opt-in only.
        if (ConfigLoader.flag(defender, "disableConflictingAsrRules", false)) {
            @NotNull String disableRules = String.join(",",
                    "01443614-cd74-433a-b99e-2ecdc07bfc25", // LSASS credential theft
                    "9e6c4e1f-7d60-472f-ba1a-a39ef669e4b2", // Untrusted executables
                    "d1e49aac-8f56-4280-b9ba-993a6d77406c"); // PSExec and WMI

            @NotNull CommandResult disable = CommandUtil.runPowerShell(
                    "Add-MpPreference -ErrorAction Stop -AttackSurfaceReductionRules_Ids "
                            + disableRules + " -AttackSurfaceReductionRules_Actions Disabled",
                    MEDIUM);

            if (disable.succeeded()) {
                outcome.success();
            } else {
                outcome.failure("Could not set the ASR disabled rules: " + disable.tail(3));
            }
        }
    }

    // ------------------------------------------------------------------
    // Disk repair
    // ------------------------------------------------------------------

    private static @NotNull StepResult repairDiskIssues(boolean cancelled) {
        @NotNull ConfigLoader loader = config("disk_issues.json");
        @NotNull Map<String, Object> config = loader.category("diskIssues");

        if (config.isEmpty()) {
            return StepResult.skipped("config/disk_issues.json has no diskIssues category.");
        }

        @NotNull Outcome outcome = new Outcome();
        @NotNull StringBuilder notes = new StringBuilder();

        if (ConfigLoader.flag(config, "repairWMI", true) && !cancelled) {
            @NotNull CommandResult verify = CommandUtil.run(WindowsUtil.system32("winmgmt.exe"),
                    MEDIUM, "/verifyrepository");

            if (verify.succeeded()) {
                notes.append("WMI repository is consistent. ");
            } else {
                @NotNull CommandResult salvage = CommandUtil.run(
                        WindowsUtil.system32("winmgmt.exe"), MEDIUM, "/salvagerepository");

                if (salvage.succeeded()) {
                    outcome.success();
                    notes.append("WMI repository was salvaged. ");
                } else {
                    @NotNull CommandResult reset = CommandUtil.run(
                            WindowsUtil.system32("winmgmt.exe"), MEDIUM, "/resetrepository");

                    if (reset.succeeded()) {
                        outcome.success();
                        notes.append("WMI repository was reset. ");
                    } else {
                        outcome.failure("The WMI repository could not be repaired.");
                    }
                }
            }
        }

        if (ConfigLoader.flag(config, "repairWithDISM", true) && !cancelled) {
            @NotNull CommandResult dism = CommandUtil.run(WindowsUtil.system32("dism.exe"),
                    VERY_LONG, "/Online", "/Cleanup-Image", "/RestoreHealth");

            if (dism.exitCode() == 0 || dism.exitCode() == 3010) {
                outcome.success();
                notes.append("DISM RestoreHealth completed. ");
            } else {
                outcome.failure("DISM RestoreHealth exited with " + dism.exitCode() + ": "
                        + dism.tail(3));
            }
        }

        if (ConfigLoader.flag(config, "repairWithSFC", true) && !cancelled) {
            @NotNull CommandResult sfc = CommandUtil.run(WindowsUtil.system32("sfc.exe"),
                    VERY_LONG, "/scannow");

            // 0 = no violations, 1 = violations found and repaired.
            if (sfc.exitCode() == 0 || sfc.exitCode() == 1) {
                outcome.success();
                notes.append(sfc.exitCode() == 1
                        ? "SFC repaired protected system files. "
                        : "SFC found no integrity violations. ");
            } else {
                outcome.failure("SFC exited with " + sfc.exitCode()
                        + " (the repair could not be completed): " + sfc.tail(3));
            }
        }

        @NotNull StepResult result = outcome.result("Disk repair");
        boolean restart = ConfigLoader.flag(config, "repairWithDISM", true)
                || ConfigLoader.flag(config, "repairWithSFC", true);
        return result.withMessage((notes + result.message()).trim())
                .withRestartRequired(restart);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Collects per-action outcomes so a task can report partial success.
     */
    private static final class Outcome {

        private int applied;
        private int failed;
        private final List<String> failures = new ArrayList<>();

        private void success() {
            applied++;
        }

        private void failure(@NotNull String message) {
            failed++;
            failures.add(message);
            DebugUtil.warn(message, null);
        }

        private @NotNull StepResult result(@NotNull String label) {
            if (failed == 0) {
                return StepResult.ok(label + ": " + applied + " action(s) completed.");
            }

            @NotNull String details = String.join(" | ", failures.stream().limit(3).toList());
            @NotNull String message = label + ": " + applied + " succeeded, " + failed
                    + " failed. " + details;

            if (applied == 0) {
                return StepResult.failed(message);
            }

            return StepResult.partial(message, applied);
        }
    }

    /**
     * Describes the selected options for the confirmation dialog.
     *
     * @param selected the selected option identifiers
     * @return the ordered selected options
     */
    public static @NotNull List<RepairOption> selectedOptions(@NotNull Set<String> selected) {
        return options().stream().filter(option -> selected.contains(option.id())).toList();
    }

    /**
     * Checks whether any of the selected options modifies the system.
     *
     * @param selected the selected option identifiers
     * @return {@code true} when a moderate or high risk option is selected
     */
    public static boolean hasSystemChanges(@NotNull Set<String> selected) {
        return options().stream()
                .anyMatch(option -> selected.contains(option.id())
                        && option.risk() != RiskLevel.SAFE);
    }

    /**
     * Gets the configured bloatware pattern count for the confirmation dialog.
     *
     * @return the number of patterns, or zero when the file is unreadable
     */
    public static int bloatwarePatternCount() {
        return ConfigLoader.strings(config("bloatware.json").category("bloatware"), "values")
                .size();
    }

    /**
     * Gets a short description of a selected option list for the log.
     *
     * @param selected the option identifiers
     * @return the description
     */
    public static @NotNull String describeSelection(@NotNull Set<String> selected) {
        return selected.isEmpty()
                ? "none"
                : String.join(", ", selected).toLowerCase(Locale.ROOT);
    }

    /**
     * Helper used by tests to look up an option.
     *
     * @param id the identifier
     * @return the option, or {@code null}
     */
    public static @Nullable RepairOption option(@NotNull String id) {
        return options().stream().filter(option -> option.id().equals(id)).findFirst().orElse(null);
    }
}
